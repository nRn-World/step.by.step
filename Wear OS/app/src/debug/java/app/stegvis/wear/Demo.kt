package app.stegvis.wear

import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Debug-only demo mode for screenshots: no account, sensor or network needed. Uses the numbers in Robin's images
 * (6 842 of 8 000 = 86 %, week 34 210 of 50 000 = 68 %, Saturday 7 November 2026).
 * adb shell am start -S -W -n app.stegvis.mobile/app.stegvis.wear.MainActivity --ez demo true --es page <today|group|week|goals|top|goal_reached|ambient|settings|pair|login|permission> [--ez celebrate true]
 */
object Demo {
    fun config(intent: Intent): DemoConfig? =
        if (intent.getBooleanExtra("demo", false)) DemoConfig(intent.getStringExtra("page") ?: "today", intent.getBooleanExtra("celebrate", false)) else null

    fun apply(model: AppModel, cfg: DemoConfig) {
        val date = LocalDate.of(2026, 11, 7)
        val now = System.currentTimeMillis()
        val today = date.toString()
        val reached = cfg.celebrate || cfg.page == "goal_reached"
        val steps = if (reached) 8_000 else 6_842
        val vault = model.vault
        vault.goal = 8_000
        vault.celebratedDay = null
        vault.accountName = "robin"
        vault.session = Session("demo", "demo", now + 3_600_000L * 24, false)
        vault.sensor = SensorState(today, 0, now, steps)

        // Monday 2 Nov to Saturday 7 Nov (sum 34 210) plus Sunday 1 Nov, so the month is 34 % of 120 000.
        val perDay = linkedMapOf("2026-11-01" to 6_590, "2026-11-02" to 4_600, "2026-11-03" to 6_100, "2026-11-04" to 3_400, "2026-11-05" to 7_600, "2026-11-06" to 5_668, today to steps)
        perDay.forEach { (d, s) -> vault.recordSteps(d, s) }
        fun meters(s: Int) = s * METERS_PER_STEP
        val mine = JSONObject().also { j -> perDay.forEach { (d, s) -> j.put(d, meters(s)) } }
        fun member(id: String, name: String, s: Int) =
            JSONObject().put("id", id).put("name", name).put("likes", if (id == "m1") 4 else 0).put("active", id == "m1" || id == "m2" || id == "m3").put("distances", JSONObject().put(today, meters(s)))
        fun group(id: String, name: String, goals: Boolean, vararg members: JSONObject) = JSONObject().put("id", id).put("name", name)
            .put("partners", JSONArray().also { a ->
                if (id == "g1") a.put(JSONObject().put("id", "g2").put("name", "Kontorsgänget").put("steps", JSONObject().put("week", 40_940)).put("race", JSONObject().put("period", "week").put("target", 100_000)))
            })
            .put("members", JSONArray().also { a -> members.forEach { a.put(it) } })
            .put("goals", JSONArray().also { a ->
                if (goals) {
                    a.put(JSONObject().put("type", "day").put("target", 8000).put("scope", "each"))
                    a.put(JSONObject().put("type", "week").put("target", 50000).put("scope", "each"))
                    a.put(JSONObject().put("type", "month").put("target", 120000).put("scope", "each"))
                }
            })
        val meMember = JSONObject().put("id", "me").put("name", "robin").put("distances", mine)
        val json = JSONObject()
            .put("me", JSONObject().put("id", "me").put("name", "robin").put("distances", mine))
            .put("groups", JSONArray()
                .put(group("g1", "Lunchpromenaden", true, member("m1", "Morgonstjärnan", 8_214), meMember, member("m2", "Glada Gåsen", 6_118), member("m3", "Skogsmulle", 7_287)))
                .put(group("g2", "Kontorsgänget", false, member("k1", "Anna", 5_000), member("k2", "Bo", 4_000), member("k3", "Cia", 3_000), member("k4", "Dag", 2_000), member("k5", "Eli", 1_000), member("k6", "Fia", 900)))
                .put(group("g3", "Söndagspromenaden", false, member("s1", "Greta", 2_000), member("s2", "Hugo", 1_500))))
            .toString()
        model.demoPage = cfg.page
        model.demoGroupWeek = 42_180
        model.demoDate = date
        model.demoDone = if (reached) 4 to 4 else null
        model.steps = steps
        model.goal = 8_000
        model.signedIn = cfg.page != "pair"
        model.phoneSession = false
        model.accountName = "robin"
        model.hasSensor = true
        model.offline = false
        val parsed = parseState(json, today)
        model.state = parsed
        model.fetch = Fetch.Ok(parsed, json)
        model.selectedGroup = "g1"
    }
}
