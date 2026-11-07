package app.stegvis.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StepMathTest {
    private val hour = 3600_000L

    @Test fun firstReadingStartsAtZero() {
        val s = addReading(null, 5000, "2026-11-07", 0)
        assertEquals(0, s.steps)
        assertEquals(5000L, s.last)
    }

    @Test fun sameDayAddsDelta() {
        val first = addReading(null, 1000, "2026-11-07", 0)
        val next = addReading(first, 1600, "2026-11-07", hour)
        assertEquals(600, next.steps)
    }

    @Test fun rebootRestartsCounterAndCountsFromZero() {
        val saved = SensorState("2026-11-07", 9000, 0, 400)
        val next = addReading(saved, 150, "2026-11-07", hour)
        assertEquals(550, next.steps) // 400 earlier + 150 since the reboot
    }

    @Test fun capsAtOneHundredThousand() {
        val saved = SensorState("2026-11-07", 0, 0, 99_900)
        val next = addReading(saved, 5000, "2026-11-07", hour)
        assertEquals(100_000, next.steps)
    }

    @Test fun newDayShortlyAfterLastReadingCreditsDelta() {
        val saved = SensorState("2026-11-06", 1000, 0, 8000)
        val next = addReading(saved, 1200, "2026-11-07", 2 * hour)
        assertEquals(200, next.steps)
    }

    @Test fun newDayAfterLongGapCreditsNothing() {
        val saved = SensorState("2026-11-05", 1000, 0, 8000)
        val next = addReading(saved, 1500, "2026-11-07", 10 * hour)
        assertEquals(0, next.steps)
    }

    @Test fun metersUseSeventyFivePerCentOfASteps() {
        assertEquals(7500, metersFor(10_000))
        assertEquals(10_000, stepsFromMeters(7500.0))
    }

    @Test fun progressIsClampedToZeroAndOne() {
        assertEquals(0.5f, goalProgress(5000, 10_000), 0.0001f)
        assertEquals(1f, goalProgress(25_000, 10_000), 0.0001f)
        assertEquals(0f, goalProgress(0, 10_000), 0.0001f)
    }

    @Test fun goalIsClampedToAllowedRange() {
        assertEquals(MIN_GOAL, clampGoal(10))
        assertEquals(MAX_GOAL, clampGoal(999_999))
        assertEquals(10_000, clampGoal(10_000))
    }

    @Test fun dayKeyUsesStockholmDate() {
        // 2026-11-06 23:30 UTC is already 2026-11-07 00:30 in Stockholm.
        val utc = java.time.Instant.parse("2026-11-06T23:30:00Z").toEpochMilli()
        assertEquals("2026-11-07", dayKey(utc))
        assertNull(null as String?)
    }
}
