package app.stegvis.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalsTest {
    private val today = LocalDate.of(2026, 11, 7) // Saturday
    private val mine = mapOf(
        "2026-11-01" to 6_590 * 0.75, "2026-11-02" to 4_600 * 0.75, "2026-11-03" to 6_100 * 0.75, "2026-11-04" to 3_400 * 0.75,
        "2026-11-05" to 7_600 * 0.75, "2026-11-06" to 5_668 * 0.75, "2026-11-07" to 6_842 * 0.75,
    )
    private fun state(goals: List<GoalDef> = emptyList()): Pair<ServerState, Group> {
        val me = Member("me", "robin", 6_842 * 0.75, true, mine)
        val g = Group("g1", "Lunch", listOf(me), goals)
        return ServerState("me", "robin", mine, listOf(g)) to g
    }

    @Test fun percentRounds() {
        assertEquals(86, percent(6_842, 8_000))
        assertEquals(68, percent(34_210, 50_000))
        assertEquals(0, percent(5, 0.coerceAtLeast(0)).coerceAtMost(0)) // goal 0 never divides by zero
    }

    @Test fun defaultsAndWeekMonthSums() {
        val (s, _) = state()
        val goals = computeGoals(s, null, today, 6_842, DEFAULT_GOAL)
        assertEquals(8_000, goals.day.target)
        assertEquals(86, goals.day.pct)
        assertEquals(34_210, goals.week.value) // Monday 2 Nov to Saturday 7 Nov
        assertEquals(68, goals.week.pct)
        assertEquals(34, goals.month.pct) // 40 800 of 120 000
        assertEquals(0, goals.doneCount)
    }

    @Test fun groupGoalsSetTargetsAndLocalStepsWinForToday() {
        val defs = listOf(GoalDef("day", 5_000, false), GoalDef("week", 20_000, false))
        val (s, g) = state(defs)
        val goals = computeGoals(s, g, today, 7_000, DEFAULT_GOAL) // watch counted more than the server knows
        assertEquals(5_000, goals.day.target)
        assertTrue(goals.day.done)
        assertEquals(20_000, goals.week.target)
        assertEquals(34_210 - 6_842 + 7_000, goals.week.value)
        assertEquals(2, goals.doneCount)
    }

    @Test fun parsesGroupGoalsAndLikes() {
        val json = """{"me":{"id":"m","name":"A","distances":{}},"groups":[{"id":"g","name":"G","members":[{"id":"m","name":"A","likes":3,"distances":{"2026-11-07":750}}],
            "goals":[{"id":"1","type":"day","target":8000,"scope":"each"},{"id":"2","type":"week","target":50000,"scope":"together"},{"type":"year","target":0}]}]}"""
        val g = parseState(json, "2026-11-07").groups[0]
        assertEquals(3, g.members[0].likes)
        assertEquals(2, g.goals.size) // the goal with target 0 is dropped
        assertTrue(g.goals[1].together)
    }

    @Test fun vaultHapticsAndLightBandDefaults() {
        val v = Vault(MemoryStore())
        assertTrue(v.haptics)
        assertEquals(false, v.lightBand)
        v.haptics = false
        assertEquals(false, v.haptics)
    }
}
