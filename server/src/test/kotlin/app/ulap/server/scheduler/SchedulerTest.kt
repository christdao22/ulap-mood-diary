package app.ulap.server.scheduler

import java.time.Instant
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

/** When the `scheduler` service runs its daily jobs (ulap-reference.md §6.6). Manila is UTC+8. */
class SchedulerTest {
    private val threeAm = LocalTime.of(3, 0)

    @Test
    fun beforeThreeInManilaRunsTheSameDay() {
        // 01:00 Manila on 15 November.
        assertEquals(Instant.parse("2026-11-14T19:00:00Z"), nextRun(Instant.parse("2026-11-14T17:00:00Z"), threeAm))
    }

    @Test
    fun afterThreeInManilaRunsTheNextDay() {
        // 09:00 Manila on 15 November.
        assertEquals(Instant.parse("2026-11-15T19:00:00Z"), nextRun(Instant.parse("2026-11-15T01:00:00Z"), threeAm))
    }

    @Test
    fun exactlyAtThreeWaitsForTomorrowSoAJobNeverRunsTwice() {
        assertEquals(Instant.parse("2026-11-15T19:00:00Z"), nextRun(Instant.parse("2026-11-14T19:00:00Z"), threeAm))
    }
}
