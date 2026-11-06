package app.ulap.shared.rules

import app.ulap.shared.model.CheckIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** The Reports segmented control (design-spec.md §7.7). */
enum class ReportPeriodType { DAILY, WEEKLY, MONTHLY }

/**
 * The stretch of local days a report covers, both ends included, as days since 1970-01-01 (the
 * same numbers as [localEpochDay], so a check-in lands in the same day here as in Logs).
 *
 * - Daily: one day.
 * - Weekly: 7 days ending on the anchor day ("Oct 29 – Nov 4"), not a Monday-to-Sunday week.
 * - Monthly: the calendar month the anchor day falls in (28–31 days).
 *
 * (technical-plan.md §10, PHASES.md Phase 4)
 */
data class ReportPeriod(val type: ReportPeriodType, val startEpochDay: Long, val endEpochDay: Long) {
    init {
        require(startEpochDay <= endEpochDay) { "A period starts on or before it ends" }
    }

    /** 1, 7, or 28–31. Weekly and Monthly trend charts draw one bar per day. */
    val dayCount: Int get() = (endEpochDay - startEpochDay + 1).toInt()

    /** Every day in the period, oldest first. */
    val epochDays: LongRange get() = startEpochDay..endEpochDay

    operator fun contains(checkIn: CheckIn): Boolean = checkIn.localEpochDay in epochDays

    /** The period just before this one, used for "change vs. previous period" and the ‹ arrow. */
    fun previous(): ReportPeriod = when (type) {
        ReportPeriodType.MONTHLY -> monthOf(type, startEpochDay - 1)
        else -> shiftedBy(-dayCount.toLong())
    }

    /** The period just after this one, for the › arrow. */
    fun next(): ReportPeriod = when (type) {
        ReportPeriodType.MONTHLY -> monthOf(type, endEpochDay + 1)
        else -> shiftedBy(dayCount.toLong())
    }

    /** False once the period reaches [todayEpochDay], so the › arrow stops at "now". */
    fun hasNext(todayEpochDay: Long): Boolean = endEpochDay < todayEpochDay

    private fun shiftedBy(days: Long) = copy(startEpochDay = startEpochDay + days, endEpochDay = endEpochDay + days)

    companion object {
        private const val WEEK_DAYS = 7

        /** The period of [type] that ends on, or contains, [anchorEpochDay] (usually today). */
        fun of(type: ReportPeriodType, anchorEpochDay: Long): ReportPeriod = when (type) {
            ReportPeriodType.DAILY -> ReportPeriod(type, anchorEpochDay, anchorEpochDay)
            ReportPeriodType.WEEKLY -> ReportPeriod(type, anchorEpochDay - (WEEK_DAYS - 1), anchorEpochDay)
            ReportPeriodType.MONTHLY -> monthOf(type, anchorEpochDay)
        }

        private fun monthOf(type: ReportPeriodType, epochDay: Long): ReportPeriod {
            val date = LocalDate.fromEpochDays(epochDay)
            val first = LocalDate(date.year, date.month, 1)
            val last = first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
            return ReportPeriod(type, first.toEpochDays(), last.toEpochDays())
        }
    }
}

/** The check-ins whose local day falls in [period], in the order they were given. */
fun List<CheckIn>.inPeriod(period: ReportPeriod): List<CheckIn> = filter { it in period }
