package app.stegvis.wear

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId

// Pure logic, no Android types, so it can be unit tested on the JVM. Mirrors src/stepSensor.ts, src/username.ts and server.ts.

const val DEFAULT_GOAL = 10_000
const val MIN_GOAL = 1_000
const val MAX_GOAL = 100_000
const val MAX_STEPS = 100_000
const val METERS_PER_STEP = 0.75
const val USERNAME_DOMAIN = "users.nrnworld.one"
const val API_BASE = "https://step-by-step-0rsz.onrender.com"
const val TOKEN_PATH = "/stegvis/token"

private const val SIX_HOURS = 6 * 3600_000L
private const val REFRESH_MARGIN = 60_000L
val STOCKHOLM: ZoneId = ZoneId.of("Europe/Stockholm")

// ---- Steps -------------------------------------------------------------------------------

/** Debug-only demo request (see src/debug). Never created in release builds. */
class DemoConfig(val page: String, val celebrate: Boolean)

data class SensorState(val day: String, val last: Long, val at: Long, val steps: Int)

/** Adds a since-boot counter reading to today's total. Same rules as addReading() in src/stepSensor.ts. */
fun addReading(saved: SensorState?, total: Long, day: String, now: Long): SensorState {
    if (saved == null) return SensorState(day, total, now, 0)
    val delta = if (total >= saved.last) total - saved.last else total // the counter restarts from 0 after a reboot
    val carried = when {
        saved.day == day -> saved.steps + delta
        now - saved.at <= SIX_HOURS -> delta // around midnight; a longer gap is never credited
        else -> 0L
    }
    return SensorState(day, total, now, minOf(carried, MAX_STEPS.toLong()).toInt())
}

fun dayKey(now: Long): String = Instant.ofEpochMilli(now).atZone(STOCKHOLM).toLocalDate().toString()

fun metersFor(steps: Int): Int = Math.round(steps * METERS_PER_STEP).toInt()

fun stepsFromMeters(meters: Double): Int = Math.round(meters / METERS_PER_STEP).toInt()

fun goalProgress(steps: Int, goal: Int): Float =
    (steps.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)

fun clampGoal(goal: Int): Int = goal.coerceIn(MIN_GOAL, MAX_GOAL)

/** The language the UI uses: the stored choice, else the watch language when it is sv/da, else English. */
fun effectiveLanguage(stored: String, systemLang: String): String =
    if (stored in listOf("sv", "da", "en")) stored else if (systemLang in listOf("sv", "da")) systemLang else "en"

/** "12 345" with a space as thousands separator. */
fun formatSteps(n: Int): String = n.toString().reversed().chunked(3).joinToString(" ").reversed()

/** "950", "8k", "12k": short form for chart labels. */
fun compactSteps(n: Int): String = if (n < 1000) n.toString() else "${(n + 500) / 1000}k"

/** Kilometres with two decimals; comma for sv/da, point for en. */
fun formatKm(steps: Int, lang: String): String {
    val s = String.format(java.util.Locale.US, "%.2f", metersFor(steps) / 1000.0)
    return if (lang == "en") s else s.replace('.', ',')
}

// ---- Username (src/username.ts) ----------------------------------------------------------

object Username {
    private val pattern = Regex("^[a-z0-9åäö][a-z0-9åäö._-]*[a-z0-9åäö]$")

    /** Returns the name when valid, otherwise null. Same rules as cleanUsername(). */
    fun clean(raw: String): String? {
        val name = Normalizer.normalize(raw.trim(), Normalizer.Form.NFC)
        if (name.length < 3 || name.length > 20 || name.contains("..")) return null
        return if (pattern.matches(name.lowercase())) name else null
    }

    /** The made-up address used for name-only accounts. Same mapping as usernameToEmail(). */
    fun toEmail(name: String): String {
        val local = Normalizer.normalize(name, Normalizer.Form.NFC).lowercase()
            .replace("å", "+a").replace("ä", "+b").replace("ö", "+c")
        return "$local@$USERNAME_DOMAIN"
    }
}

// ---- Session -----------------------------------------------------------------------------

/** A password session owns its refresh token. A phone session only has the access token the phone sent: the watch never refreshes it. */
data class Session(val accessToken: String, val refreshToken: String?, val expiresAt: Long, val fromPhone: Boolean)

fun needsRefresh(s: Session, now: Long): Boolean =
    !s.fromPhone && s.refreshToken != null && s.expiresAt - REFRESH_MARGIN <= now

/** Reads the exp claim of a JWT without verifying it (the server verifies). Null when the token is not a JWT. */
fun jwtExpiry(token: String): Long? = try {
    val payload = token.split('.')[1]
    val json = JSONObject(String(java.util.Base64.getUrlDecoder().decode(payload), Charsets.UTF_8))
    json.optLong("exp", 0L).takeIf { it > 0 }?.times(1000)
} catch (e: Exception) {
    null
}

/** Parses a Supabase /auth/v1/token response. Throws on a body without access_token. */
fun parseTokenResponse(body: String, now: Long): Session {
    val j = JSONObject(body)
    val access = j.getString("access_token")
    val refresh = j.optString("refresh_token", "").ifEmpty { null }
    val expiresIn = j.optLong("expires_in", 3600L)
    return Session(access, refresh, now + expiresIn * 1000, fromPhone = false)
}

// ---- /api/state (server.ts view()) ------------------------------------------------------

data class Member(val id: String, val name: String, val meters: Double, val isMe: Boolean)
data class Group(val id: String, val name: String, val members: List<Member>)
data class ServerState(
    val meId: String,
    val meName: String,
    val weekMeters: Map<String, Double>,
    val groups: List<Group>,
)

/** Reads GET /api/state. Today's meters are members[].distances[today], as the server stores them. */
fun parseState(body: String, today: String): ServerState {
    val j = JSONObject(body)
    val me = j.getJSONObject("me")
    val meId = me.getString("id")
    val weekMeters = me.optJSONObject("distances")?.let { metersMap(it) } ?: emptyMap()
    val groupsJson = j.optJSONArray("groups") ?: JSONArray()
    val groups = (0 until groupsJson.length()).map { i ->
        val g = groupsJson.getJSONObject(i)
        val membersJson = g.optJSONArray("members") ?: JSONArray()
        val members = (0 until membersJson.length()).map { k ->
            val m = membersJson.getJSONObject(k)
            val id = m.getString("id")
            Member(
                id = id,
                name = m.optString("name", ""),
                meters = m.optJSONObject("distances")?.optDouble(today, 0.0) ?: 0.0,
                isMe = id == meId,
            )
        }
        Group(g.getString("id"), g.optString("name", ""), members)
    }
    return ServerState(meId, me.optString("name", ""), weekMeters, groups)
}

private fun metersMap(o: JSONObject): Map<String, Double> {
    val out = HashMap<String, Double>()
    val keys = o.keys()
    while (keys.hasNext()) {
        val k = keys.next()
        out[k] = o.optDouble(k, 0.0)
    }
    return out
}

// ---- Storage model -----------------------------------------------------------------------

interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}

class MemoryStore : KeyValueStore {
    private val map = HashMap<String, String>()
    override fun get(key: String): String? = map[key]
    override fun put(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }
}

/** Everything the watch keeps: session, step state, goal, language, history and the last group data. */
class Vault(private val kv: KeyValueStore) {

    var goal: Int
        get() = kv.get("goal")?.toIntOrNull()?.let(::clampGoal) ?: DEFAULT_GOAL
        set(value) = kv.put("goal", clampGoal(value).toString())

    var language: String
        get() = kv.get("lang") ?: "system"
        set(value) = kv.put("lang", value)

    /** The day (yyyy-MM-dd) the goal celebration last ran, so it plays once per day. */
    var celebratedDay: String?
        get() = kv.get("celebrated")
        set(value) = kv.put("celebrated", value)

    /** Name shown under Account in Settings. */
    var accountName: String?
        get() = kv.get("acct")
        set(value) = kv.put("acct", value)

    /** Page to open after the app restarts itself (language change); read once. */
    var resumePage: Int?
        get() = kv.get("resume")?.toIntOrNull()
        set(value) = kv.put("resume", value?.toString())

    var selectedGroup: String?
        get() = kv.get("group")
        set(value) = kv.put("group", value)

    var groupCache: String?
        get() = kv.get("groupCache")
        set(value) = kv.put("groupCache", value)

    var sensor: SensorState?
        get() = kv.get("sensor")?.let { s ->
            try {
                val j = JSONObject(s)
                SensorState(j.getString("day"), j.getLong("last"), j.getLong("at"), j.getInt("steps"))
            } catch (e: Exception) {
                null
            }
        }
        set(value) = kv.put(
            "sensor",
            value?.let { JSONObject().put("day", it.day).put("last", it.last).put("at", it.at).put("steps", it.steps).toString() },
        )

    /** Steps per day for the week view, at most the last 14 days. */
    var history: Map<String, Int>
        get() {
            val j = try { JSONObject(kv.get("history") ?: "{}") } catch (e: Exception) { JSONObject() }
            return j.keys().asSequence().associateWith { j.optInt(it, 0) }
        }
        set(value) {
            val j = JSONObject()
            value.toSortedMap().entries.toList().takeLast(14).forEach { j.put(it.key, it.value) }
            kv.put("history", j.toString())
        }

    fun todaySteps(now: Long): Int {
        val s = sensor ?: return 0
        return if (s.day == dayKey(now)) s.steps else 0
    }

    fun recordSteps(day: String, steps: Int) {
        history = history + (day to steps)
    }

    var session: Session?
        get() {
            val s = kv.get("session") ?: return null
            return try {
                val j = JSONObject(s)
                Session(
                    accessToken = j.getString("a"),
                    refreshToken = j.optString("r", "").ifEmpty { null },
                    expiresAt = j.getLong("e"),
                    fromPhone = j.optBoolean("p", false),
                )
            } catch (e: Exception) {
                null
            }
        }
        set(value) = kv.put(
            "session",
            value?.let {
                JSONObject().put("a", it.accessToken).put("r", it.refreshToken ?: "")
                    .put("e", it.expiresAt).put("p", it.fromPhone).toString()
            },
        )

    /** Stores a token from the phone. An empty token clears only a phone session, never the watch's own sign-in. */
    fun savePhoneToken(token: String, now: Long) {
        val current = session
        if (token.isBlank()) {
            if (current?.fromPhone == true) session = null
            return
        }
        session = Session(token, null, jwtExpiry(token) ?: (now + 3600_000L), fromPhone = true)
    }

    /** Sign-out: removes the session and the cached group data. Step history stays on the watch. */
    fun clearAll() {
        listOf("session", "groupCache", "group", "acct").forEach { kv.put(it, null) }
    }
}
