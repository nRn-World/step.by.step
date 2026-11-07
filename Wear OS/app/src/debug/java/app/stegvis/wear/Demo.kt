package app.stegvis.wear

import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Debug-only demo mode for screenshots, no account, sensor or network needed:
 * adb shell am start -n app.stegvis.mobile/app.stegvis.wear.MainActivity --ez demo true --es page <today|group|week|settings|login|permission> [--ez celebrate true]
 */
object Demo {
    fun config(intent: Intent): DemoConfig? =
        if (intent.getBooleanExtra("demo", false)) DemoConfig(intent.getStringExtra("page") ?: "today", intent.getBooleanExtra("celebrate", false)) else null

    fun apply(model: AppModel, cfg: DemoConfig) {
        val now = System.currentTimeMillis()
        val today = dayKey(now)
        val steps = if (cfg.celebrate) 10_000 else 7_214
        val vault = model.vault
        vault.goal = 10_000
        vault.celebratedDay = null
        vault.accountName = "robin"
        vault.session = Session("demo", "demo", now + 3_600_000L * 24, false)
        vault.sensor = SensorState(today, 0, now, steps)
        val week = listOf(8_200, 11_500, 6_300, 9_800, 4_100, 10_400, steps)
        val dates = (6 downTo 0).map { LocalDate.now(STOCKHOLM).minusDays(it.toLong()).toString() }
        dates.forEachIndexed { i, d -> vault.recordSteps(d, week[i]) }

        fun meters(s: Int) = Math.round(s * METERS_PER_STEP).toInt()
        val mine = JSONObject()
        dates.forEachIndexed { i, d -> mine.put(d, meters(week[i])) }
        fun member(id: String, name: String, s: Int) =
            JSONObject().put("id", id).put("name", name).put("distances", JSONObject().put(today, meters(s)))
        val json = JSONObject()
            .put("me", JSONObject().put("id", "me").put("name", "robin").put("distances", mine))
            .put("groups", JSONArray().put(JSONObject().put("id", "g1").put("name", "Familjen").put("members", JSONArray()
                .put(member("m1", "Maja", 11_842)).put(member("me", "robin", steps)).put(member("m2", "Elin", 9_530)).put(member("m3", "Oskar", 5_120)))))
            .toString()
        model.demoPage = cfg.page
        model.steps = steps
        model.goal = 10_000
        model.signedIn = true
        model.phoneSession = false
        model.accountName = "robin"
        model.hasSensor = true
        model.offline = false
        val parsed = parseState(json, today)
        model.state = parsed
        model.fetch = Fetch.Ok(parsed, json)
    }
}
