package app.stegvis.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StateAndVaultTest {
    private val today = "2026-11-07"
    private val stateJson = """
        {
          "me": {"id": "m1", "name": "Anna", "distances": {"2026-11-06": 3000, "2026-11-07": 5625}},
          "groups": [
            {"id": "g1", "name": "Team", "members": [
              {"id": "m1", "name": "Anna", "distances": {"2026-11-07": 5625}, "isAdmin": true},
              {"id": "m2", "name": "Kim", "distances": {"2026-11-07": 9000}}
            ]},
            {"id": "g2", "name": "Empty", "members": []}
          ]
        }
    """.trimIndent()

    @Test fun parsesGroupsMembersAndWeek() {
        val s = parseState(stateJson, today)
        assertEquals("m1", s.meId)
        assertEquals(2, s.groups.size)
        val team = s.groups[0]
        assertEquals("Team", team.name)
        assertEquals(5625.0, team.members[0].meters, 0.001)
        assertTrue(team.members[0].isMe)
        assertFalse(team.members[1].isMe)
        assertEquals(3000.0, s.weekMeters["2026-11-06"]!!, 0.001)
        assertEquals(4000, stepsFromMeters(s.weekMeters["2026-11-06"]!!))
    }

    @Test fun memberWithoutTodayCountsAsZero() {
        val s = parseState(stateJson.replace("2026-11-07\": 9000", "2026-11-05\": 9000"), today)
        assertEquals(0.0, s.groups[0].members[1].meters, 0.001)
    }

    @Test fun missingGroupsGivesEmptyList() {
        assertTrue(parseState("""{"me":{"id":"x","name":"X"}}""", today).groups.isEmpty())
    }

    @Test(expected = Exception::class)
    fun malformedStateThrows() {
        parseState("{not json", today)
    }

    @Test fun vaultStoresSessionRoundTripAndClearsOnSignOut() {
        val vault = Vault(MemoryStore())
        assertNull(vault.session)
        vault.session = Session("a", "r", 42L, fromPhone = false)
        assertEquals(Session("a", "r", 42L, false), vault.session)
        vault.groupCache = "{}"
        vault.clearAll()
        assertNull(vault.session)
        assertNull(vault.groupCache)
    }

    @Test fun phoneTokenReplacesOnlyPhoneSessionOnEmpty() {
        val vault = Vault(MemoryStore())
        vault.savePhoneToken("", 0L)
        assertNull(vault.session)
        vault.session = Session("watch", "r", 1L, fromPhone = false)
        vault.savePhoneToken("", 0L)
        assertEquals("watch", vault.session!!.accessToken) // an empty phone token never removes the watch's own sign-in
    }

    @Test fun goalAndLanguageDefaultsAndClamp() {
        val vault = Vault(MemoryStore())
        assertEquals(DEFAULT_GOAL, vault.goal)
        assertEquals("system", vault.language)
        vault.goal = 1
        assertEquals(MIN_GOAL, vault.goal)
    }

    @Test fun historyKeepsLastFourteenDays() {
        val vault = Vault(MemoryStore())
        for (d in 1..20) vault.recordSteps("2026-11-%02d".format(d), d)
        assertEquals(14, vault.history.size)
        assertTrue(vault.history.containsKey("2026-11-20"))
        assertFalse(vault.history.containsKey("2026-11-01"))
    }

    @Test fun todayStepsIsZeroForOtherDay() {
        val vault = Vault(MemoryStore())
        val now = java.time.Instant.parse("2026-11-07T10:00:00Z").toEpochMilli()
        vault.sensor = SensorState("2026-11-06", 0, 0, 8000)
        assertEquals(0, vault.todaySteps(now))
        vault.sensor = SensorState(dayKey(now), 0, 0, 4321)
        assertEquals(4321, vault.todaySteps(now))
    }
}
