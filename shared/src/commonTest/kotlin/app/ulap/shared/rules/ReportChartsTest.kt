package app.ulap.shared.rules

import app.ulap.shared.model.CheckIn
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import app.ulap.shared.model.Zone
import app.ulap.shared.rules.ReportPeriodType.DAILY
import app.ulap.shared.rules.ReportPeriodType.MONTHLY
import app.ulap.shared.rules.ReportPeriodType.WEEKLY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek

/** Every expected number below was worked out by hand from the listed check-ins. */
class ReportChartsTest {
    private fun day(isoDate: String): Long = Instant.parse("${isoDate}T00:00:00Z").epochSeconds / 86_400

    /** [at] is a local time "2026-11-04T09:30" in Manila (UTC+8). */
    private fun checkIn(
        at: String,
        mood: String = "calm",
        stress: Int = 5,
        trigger: Trigger? = null,
        /** The support used and the stress after it. */
        after: Pair<SupportType, Int?> = SupportType.NONE to null,
    ): CheckIn {
        val time = Instant.parse("$at:00+08:00")
        return CheckIn(
            id = at,
            moodCode = mood,
            energy = 5,
            stress = stress,
            stressAfter = after.second,
            trigger = trigger,
            triggerOther = null,
            note = null,
            supportUsed = after.first,
            isGoodMoment = false,
            loggedAt = time,
            tzOffsetMin = 480,
            createdAt = time,
            updatedAt = time,
        )
    }

    @Test
    fun `local minute and block use the logged offset, not UTC`() {
        val c = checkIn("2026-11-04T23:15")
        assertEquals(23 * 60 + 15, c.localMinuteOfDay)
        assertEquals(TimeOfDay.NIGHT, c.timeOfDay)
        assertEquals(TimeOfDay.MORNING, checkIn("2026-11-04T05:00").timeOfDay)
    }

    @Test
    fun `daily trend is one bar per check-in, earliest first`() {
        val data = listOf(checkIn("2026-11-04T20:00", stress = 2), checkIn("2026-11-04T08:00", stress = 9))
        val bars = stressTrend(data, ReportPeriod.of(DAILY, day("2026-11-04")))
        assertEquals(listOf(9.0, 2.0), bars.map { it.stress })
        assertEquals(listOf(480, 1200), bars.map { it.minuteOfDay })
    }

    @Test
    fun `weekly trend is 7 day averages with gaps for empty days`() {
        val data = listOf(
            checkIn("2026-10-29T08:00", stress = 8),
            checkIn("2026-10-29T20:00", stress = 3),
            checkIn("2026-11-04T12:00", stress = 6),
            checkIn("2026-10-28T12:00", stress = 10), // the week before
        )
        val bars = stressTrend(data, ReportPeriod.of(WEEKLY, day("2026-11-04")))
        assertEquals(7, bars.size)
        assertEquals(day("2026-10-29"), bars.first().epochDay)
        assertEquals(5.5, bars.first().stress)
        assertEquals(2, bars.first().checkInCount)
        assertNull(bars[1].stress)
        assertEquals(6.0, bars.last().stress)
        assertTrue(bars.all { it.minuteOfDay == null })
    }

    @Test
    fun `monthly trend has a bar for every day of the month`() {
        assertEquals(30, stressTrend(emptyList(), ReportPeriod.of(MONTHLY, day("2026-11-04"))).size)
    }

    @Test
    fun `zone mix lists all zones and adds to 100`() {
        val data = listOf(
            checkIn("2026-11-04T08:00", mood = "anxious"),
            checkIn("2026-11-04T09:00", mood = "anxious"),
            checkIn("2026-11-04T10:00", mood = "calm"),
            checkIn("2026-11-04T11:00", mood = "calm"),
            checkIn("2026-11-04T12:00", mood = "no-such-mood"),
        )
        val mix = zoneMix(data)
        assertEquals(listOf(Zone.RED, Zone.BLUE, Zone.YELLOW, Zone.GREEN), mix.map { it.key })
        assertEquals(listOf(2, 0, 0, 2), mix.map { it.count })
        assertEquals(listOf(50.0, 0.0, 0.0, 50.0), mix.map { it.percent })
        assertEquals(listOf(0.0, 0.0, 0.0, 0.0), zoneMix(emptyList()).map { it.percent })
    }

    @Test
    fun `mood frequency keeps the top 5, ties to the most recent`() {
        val moods = listOf("a", "b", "c", "d", "e", "f")
        val data = moods.mapIndexed { i, m -> checkIn("2026-11-04T0$i:00", mood = m) } +
            checkIn("2026-11-04T09:00", mood = "c")
        val top = moodFrequency(data)
        assertEquals(listOf("c", "f", "e", "d", "b"), top.map { it.key })
        assertEquals(2, top.first().count)
        assertEquals(200.0 / 7, top.first().percent)
    }

    @Test
    fun `top triggers count and average stress`() {
        val data = listOf(
            checkIn("2026-11-04T08:00", stress = 8, trigger = Trigger.WORK),
            checkIn("2026-11-04T09:00", stress = 6, trigger = Trigger.WORK),
            checkIn("2026-11-04T10:00", stress = 3, trigger = Trigger.FAMILY),
            checkIn("2026-11-04T11:00", stress = 9),
        )
        assertEquals(
            listOf(TriggerStat(Trigger.WORK, 2, 7.0), TriggerStat(Trigger.FAMILY, 1, 3.0)),
            topTriggers(data),
        )
    }

    @Test
    fun `time of day averages cover all four blocks`() {
        val data = listOf(
            checkIn("2026-11-04T06:00", stress = 4),
            checkIn("2026-11-04T11:59", stress = 6),
            checkIn("2026-11-04T22:00", stress = 9),
            checkIn("2026-11-04T04:59", stress = 7),
        )
        assertEquals(
            listOf(
                TimeOfDayStat(TimeOfDay.MORNING, 2, 5.0),
                TimeOfDayStat(TimeOfDay.AFTERNOON, 0, null),
                TimeOfDayStat(TimeOfDay.EVENING, 0, null),
                TimeOfDayStat(TimeOfDay.NIGHT, 2, 8.0),
            ),
            timeOfDayAverages(data),
        )
    }

    @Test
    fun `what helped averages relief and skips no support or no after value`() {
        val data = listOf(
            checkIn("2026-11-04T08:00", stress = 8, after = SupportType.BREATHE to 5),
            checkIn("2026-11-04T09:00", stress = 7, after = SupportType.BREATHE to 6),
            checkIn("2026-11-04T10:00", stress = 6, after = SupportType.SMALL_HELPS to 2),
            checkIn("2026-11-04T11:00", stress = 9, after = SupportType.SMALL_HELPS to null),
            checkIn("2026-11-04T12:00", stress = 9, after = SupportType.NONE to 1),
        )
        assertEquals(
            listOf(SupportStat(SupportType.SMALL_HELPS, 1, 4.0), SupportStat(SupportType.BREATHE, 2, 2.0)),
            whatHelped(data),
        )
    }

    // "Ulap noticed"

    @Test
    fun `no insight below 3 check-ins`() {
        val data = listOf(
            checkIn("2026-11-04T08:00", trigger = Trigger.WORK),
            checkIn("2026-11-04T09:00", trigger = Trigger.WORK),
        )
        assertEquals(emptyList(), ReportInsight.of(data, ReportPeriod.of(DAILY, day("2026-11-04"))))
    }

    @Test
    fun `evenings stand out and the top trigger has a big share`() {
        val data = listOf(
            checkIn("2026-11-04T08:00", stress = 3, trigger = Trigger.WORK),
            checkIn("2026-11-04T09:00", stress = 3),
            checkIn("2026-11-04T19:00", stress = 8, trigger = Trigger.WORK),
            checkIn("2026-11-04T20:00", stress = 8),
        )
        // Overall 5.5; evenings 8.0 (≥ 6.5). Work in 2 of 4 (≥ 40%).
        assertEquals(
            listOf(
                ReportInsight.HardestTimeOfDay(TimeOfDay.EVENING),
                ReportInsight.TopTriggerShare(Trigger.WORK, 2, 4),
            ),
            ReportInsight.of(data, ReportPeriod.of(DAILY, day("2026-11-04"))),
        )
    }

    @Test
    fun `nothing stands out when stress is even and triggers are spread`() {
        val data = listOf(
            checkIn("2026-11-04T08:00", stress = 5, trigger = Trigger.WORK),
            checkIn("2026-11-04T09:00", stress = 5, trigger = Trigger.MONEY),
            checkIn("2026-11-04T19:00", stress = 6, trigger = Trigger.FAMILY),
            checkIn("2026-11-04T20:00", stress = 6),
        )
        assertEquals(emptyList(), ReportInsight.of(data, ReportPeriod.of(DAILY, day("2026-11-04"))))
    }

    @Test
    fun `one time block alone is not a pattern`() {
        val data = (0..3).map { checkIn("2026-11-04T0${it + 6}:00", stress = 9) }
        assertEquals(emptyList(), ReportInsight.of(data, ReportPeriod.of(DAILY, day("2026-11-04"))))
    }

    @Test
    fun `monthly weekday needs the same weekday on two dates`() {
        // Nov 2 and 9, 2026 are Mondays. All at noon so time of day can't stand out.
        val data = listOf(
            checkIn("2026-11-02T12:00", stress = 9),
            checkIn("2026-11-09T12:00", stress = 8),
            checkIn("2026-11-04T12:00", stress = 3),
            checkIn("2026-11-05T12:00", stress = 2),
        )
        val month = ReportPeriod.of(MONTHLY, day("2026-11-20"))
        assertEquals(listOf(ReportInsight.HardestWeekday(DayOfWeek.MONDAY)), ReportInsight.of(data, month))

        val week = ReportPeriod.of(WEEKLY, day("2026-11-09"))
        assertEquals(emptyList(), ReportInsight.of(data, week))
    }
}
