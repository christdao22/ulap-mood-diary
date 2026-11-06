package app.ulap.shared.rules

import app.ulap.shared.model.CheckIn
import app.ulap.shared.model.SupportType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class LocalDayTest {
    private val manila = 480
    private val newYork = -300

    /** Days since 1970-01-01 of an ISO date, e.g. "2026-11-04". */
    private fun day(isoDate: String): Long = Instant.parse("${isoDate}T00:00:00Z").epochSeconds / 86_400

    private fun checkIn(id: String, loggedAt: String, tzOffsetMin: Int = manila, stress: Int = 5) = CheckIn(
        id = id,
        moodCode = "calm",
        energy = 5,
        stress = stress,
        stressAfter = null,
        trigger = null,
        triggerOther = null,
        note = null,
        supportUsed = SupportType.NONE,
        isGoodMoment = false,
        loggedAt = Instant.parse(loggedAt),
        tzOffsetMin = tzOffsetMin,
        createdAt = Instant.parse(loggedAt),
        updatedAt = Instant.parse(loggedAt),
    )

    @Test
    fun `the day is on the clock of the time zone it was logged in`() {
        // 4:30 PM UTC on Nov 3 is 12:30 AM Nov 4 in Manila, and 11:30 AM Nov 3 in New York.
        assertEquals(day("2026-11-04"), checkIn("a", "2026-11-03T16:30:00Z", manila).localEpochDay)
        assertEquals(day("2026-11-03"), checkIn("b", "2026-11-03T16:30:00Z", newYork).localEpochDay)
    }

    @Test
    fun `midnight starts the new day, a minute before stays on the old one`() {
        // Manila midnight on Nov 4 is 4:00 PM UTC on Nov 3.
        assertEquals(day("2026-11-03"), localEpochDay(Instant.parse("2026-11-03T15:59:59.999Z"), manila))
        assertEquals(day("2026-11-04"), localEpochDay(Instant.parse("2026-11-03T16:00:00Z"), manila))
    }

    @Test
    fun `a moment before 1970 lands on the day before, not day 0`() {
        assertEquals(-1, localEpochDay(Instant.parse("1969-12-31T23:00:00Z"), 0))
    }

    @Test
    fun `groups keep the given order, of days and inside each day`() {
        val newestFirst = listOf(
            checkIn("c", "2026-11-04T10:00:00Z"), // 6 PM Nov 4 in Manila
            checkIn("b", "2026-11-04T01:00:00Z"), // 9 AM Nov 4
            checkIn("a", "2026-11-03T14:00:00Z"), // 10 PM Nov 3
        )

        val days = newestFirst.groupByLocalDay()

        assertEquals(listOf(day("2026-11-04"), day("2026-11-03")), days.map { it.epochDay })
        assertEquals(listOf("c", "b"), days[0].checkIns.map { it.id })
        assertEquals(listOf("a"), days[1].checkIns.map { it.id })
    }

    @Test
    fun `a check-in after midnight in another time zone joins that zone's day`() {
        // Both at 11 PM Nov 3 on their own clocks, so both are on Nov 3, though 13 hours apart.
        val days = listOf(
            checkIn("ny", "2026-11-04T04:00:00Z", newYork),
            checkIn("mnl", "2026-11-03T15:00:00Z", manila),
        ).groupByLocalDay()

        assertEquals(1, days.size)
        assertEquals(day("2026-11-03"), days.single().epochDay)
    }

    @Test
    fun `a day counts its check-ins and averages their logged stress`() {
        val day = listOf(
            checkIn("a", "2026-11-04T01:00:00Z", stress = 8),
            checkIn("b", "2026-11-04T02:00:00Z", stress = 3),
            checkIn("c", "2026-11-04T03:00:00Z", stress = 2),
        ).groupByLocalDay().single()

        assertEquals(3, day.count)
        assertEquals(13.0 / 3, day.averageStress)
    }

    @Test
    fun `no check-ins make no days, and a day is never empty`() {
        assertEquals(emptyList(), emptyList<CheckIn>().groupByLocalDay())
        assertFailsWith<IllegalArgumentException> { CheckInDay(epochDay = 0, checkIns = emptyList()) }
    }
}
