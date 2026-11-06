package app.ulap.shared.rules

import app.ulap.shared.model.CheckIn
import app.ulap.shared.model.SupportType
import app.ulap.shared.rules.ReportPeriodType.DAILY
import app.ulap.shared.rules.ReportPeriodType.MONTHLY
import app.ulap.shared.rules.ReportPeriodType.WEEKLY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class ReportPeriodTest {
    /** Days since 1970-01-01 of an ISO date, e.g. "2026-11-04". */
    private fun day(isoDate: String): Long = Instant.parse("${isoDate}T00:00:00Z").epochSeconds / 86_400

    private fun period(type: ReportPeriodType, start: String, end: String) = ReportPeriod(type, day(start), day(end))

    private fun checkIn(id: String, loggedAt: String, tzOffsetMin: Int = 480) = CheckIn(
        id = id,
        moodCode = "calm",
        energy = 5,
        stress = 5,
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
    fun `daily is the one day`() {
        val p = ReportPeriod.of(DAILY, day("2026-11-04"))
        assertEquals(period(DAILY, "2026-11-04", "2026-11-04"), p)
        assertEquals(1, p.dayCount)
        assertEquals(period(DAILY, "2026-11-03", "2026-11-03"), p.previous())
        assertEquals(period(DAILY, "2026-11-05", "2026-11-05"), p.next())
    }

    @Test
    fun `weekly is the 7 days ending on the anchor`() {
        val p = ReportPeriod.of(WEEKLY, day("2026-11-04"))
        assertEquals(period(WEEKLY, "2026-10-29", "2026-11-04"), p)
        assertEquals(7, p.dayCount)
        assertEquals(period(WEEKLY, "2026-10-22", "2026-10-28"), p.previous())
        assertEquals(period(WEEKLY, "2026-11-05", "2026-11-11"), p.next())
    }

    @Test
    fun `monthly is the calendar month`() {
        val p = ReportPeriod.of(MONTHLY, day("2026-11-04"))
        assertEquals(period(MONTHLY, "2026-11-01", "2026-11-30"), p)
        assertEquals(30, p.dayCount)
        assertEquals(period(MONTHLY, "2026-10-01", "2026-10-31"), p.previous())
        assertEquals(period(MONTHLY, "2026-12-01", "2026-12-31"), p.next())
        assertEquals(period(MONTHLY, "2027-01-01", "2027-01-31"), p.next().next())
    }

    @Test
    fun `february has 28 or 29 days`() {
        assertEquals(28, ReportPeriod.of(MONTHLY, day("2026-02-14")).dayCount)
        assertEquals(29, ReportPeriod.of(MONTHLY, day("2028-02-29")).dayCount)
        assertEquals(
            period(MONTHLY, "2026-02-01", "2026-02-28"),
            ReportPeriod.of(MONTHLY, day("2026-03-31")).previous(),
        )
    }

    @Test
    fun `next stops at today`() {
        val today = day("2026-11-04")
        assertFalse(ReportPeriod.of(WEEKLY, today).hasNext(today))
        assertFalse(ReportPeriod.of(MONTHLY, today).hasNext(today))
        assertTrue(ReportPeriod.of(DAILY, today).previous().hasNext(today))
        assertTrue(ReportPeriod.of(MONTHLY, today).previous().hasNext(today))
    }

    @Test
    fun `check-ins are picked by their local day`() {
        val p = ReportPeriod.of(DAILY, day("2026-11-04"))
        // 4:30 PM UTC Nov 3 is 12:30 AM Nov 4 in Manila, but still Nov 3 in New York.
        val manila = checkIn("a", "2026-11-03T16:30:00Z", tzOffsetMin = 480)
        val newYork = checkIn("b", "2026-11-03T16:30:00Z", tzOffsetMin = -300)
        val lateNov4 = checkIn("c", "2026-11-04T15:59:00Z", tzOffsetMin = 480)
        val nov5 = checkIn("d", "2026-11-04T16:00:00Z", tzOffsetMin = 480)
        assertEquals(listOf(manila, lateNov4), listOf(manila, newYork, lateNov4, nov5).inPeriod(p))
    }

    @Test
    fun `a period cannot end before it starts`() {
        assertFailsWith<IllegalArgumentException> { ReportPeriod(DAILY, 10, 9) }
    }
}
