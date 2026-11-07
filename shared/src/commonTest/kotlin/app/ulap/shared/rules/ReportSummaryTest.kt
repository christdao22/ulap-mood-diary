package app.ulap.shared.rules

import app.ulap.shared.model.CheckIn
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import app.ulap.shared.rules.ReportPeriodType.DAILY
import app.ulap.shared.rules.ReportPeriodType.WEEKLY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class ReportSummaryTest {
    private fun day(isoDate: String): Long = Instant.parse("${isoDate}T00:00:00Z").epochSeconds / 86_400

    /** Logged at noon UTC with tzOffset 0, so the local day is the UTC date. */
    private fun checkIn(
        /** "2026-11-04", or "2026-11-04T09" for a set hour (UTC, offset 0). Default hour is noon. */
        at: String,
        mood: String = "calm",
        stress: Int = 5,
        energy: Int = 5,
        trigger: Trigger? = null,
    ): CheckIn {
        val time = Instant.parse(if ("T" in at) "$at:00:00Z" else "${at}T12:00:00Z")
        return CheckIn(
            id = "$at-$mood",
            moodCode = mood,
            energy = energy,
            stress = stress,
            stressAfter = null,
            trigger = trigger,
            triggerOther = null,
            note = null,
            supportUsed = SupportType.NONE,
            isGoodMoment = false,
            loggedAt = time,
            tzOffsetMin = 0,
            createdAt = time,
            updatedAt = time,
        )
    }

    @Test
    fun `hand-checked week with a previous week`() {
        val data = listOf(
            // This week: Oct 29 – Nov 4
            checkIn("2026-10-29", mood = "anxious", stress = 8, energy = 3, trigger = Trigger.WORK),
            checkIn("2026-11-01", mood = "calm", stress = 3, energy = 6, trigger = Trigger.FAMILY),
            checkIn("2026-11-04", mood = "anxious", stress = 7, energy = 4, trigger = Trigger.WORK),
            // Previous week: Oct 22 – 28
            checkIn("2026-10-22", stress = 4, energy = 5),
            checkIn("2026-10-28", stress = 6, energy = 7),
            // Outside both
            checkIn("2026-10-21", stress = 10),
        )
        val s = ReportSummary.of(data, ReportPeriod.of(WEEKLY, day("2026-11-04")))

        assertEquals(3, s.checkInCount)
        assertEquals(6.0, s.averageStress) // (8+3+7)/3
        assertEquals(13.0 / 3, s.averageEnergy)
        assertEquals("anxious", s.mostCommonMoodCode)
        assertEquals(Trigger.WORK, s.topTrigger)
        assertEquals(2, s.previous?.checkInCount)
        assertEquals(1, s.checkInCountChange)
        assertEquals(1.0, s.averageStressChange) // 6 - 5
        assertEquals(13.0 / 3 - 6.0, s.averageEnergyChange)
    }

    @Test
    fun `empty period has no averages and no winners`() {
        val s = ReportSummary.of(emptyList(), ReportPeriod.of(DAILY, day("2026-11-04")))
        assertEquals(0, s.checkInCount)
        assertNull(s.averageStress)
        assertNull(s.averageEnergy)
        assertNull(s.mostCommonMoodCode)
        assertNull(s.topTrigger)
        assertNull(s.previous)
        assertEquals(0, s.checkInCountChange)
        assertNull(s.averageStressChange)
    }

    @Test
    fun `no previous check-ins means no stress change but count change is the full count`() {
        val s = ReportSummary.of(listOf(checkIn("2026-11-04")), ReportPeriod.of(DAILY, day("2026-11-04")))
        assertNull(s.previous)
        assertEquals(1, s.checkInCountChange)
        assertNull(s.averageStressChange)
    }

    @Test
    fun `ties go to the most recently logged`() {
        val data = listOf(
            checkIn("2026-11-04T09", mood = "sad", trigger = Trigger.MONEY),
            checkIn("2026-11-04T15", mood = "calm", trigger = Trigger.HEALTH),
        )
        val s = ReportSummary.of(data.reversed(), ReportPeriod.of(DAILY, day("2026-11-04")))
        assertEquals("calm", s.mostCommonMoodCode)
        assertEquals(Trigger.HEALTH, s.topTrigger)
    }

    @Test
    fun `check-ins without a trigger do not win top trigger`() {
        val data = listOf(
            checkIn("2026-11-04T08"),
            checkIn("2026-11-04T09"),
            checkIn("2026-11-04T10", trigger = Trigger.SCHOOL),
        )
        assertEquals(Trigger.SCHOOL, ReportSummary.of(data, ReportPeriod.of(DAILY, day("2026-11-04"))).topTrigger)
    }
}
