package app.ulap.shared.rules

import app.ulap.shared.model.CheckIn
import kotlin.time.Instant

// "Day" means the person's local day when they logged the check-in: `logged_at` shifted by
// `tz_offset_min` (technical-plan.md §10). Logs groups by it and Reports averages by it, so both
// use these functions and can never put a check-in on different days.

private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_DAY = 86_400_000L

/**
 * The local day of a moment, as days since 1970-01-01 (kotlinx-datetime's
 * `LocalDate.toEpochDays()`, java.time's `LocalDate.toEpochDay()`).
 *
 * Floor division, so a moment before 1970 lands on the day before, not on day 0.
 */
fun localEpochDay(at: Instant, tzOffsetMin: Int): Long =
    (at.toEpochMilliseconds() + tzOffsetMin * MILLIS_PER_MINUTE).floorDiv(MILLIS_PER_DAY)

/**
 * The day on the phone's clock when this check-in was logged. 11:30 PM in Manila stays on that
 * day even if the phone later moves to another time zone.
 */
val CheckIn.localEpochDay: Long get() = localEpochDay(loggedAt, tzOffsetMin)

/** One local day's check-ins, in the order they were given (design-spec.md §6.13 day header). */
data class CheckInDay(
    /** Days since 1970-01-01. */
    val epochDay: Long,
    /** Never empty. */
    val checkIns: List<CheckIn>,
) {
    init {
        require(checkIns.isNotEmpty()) { "A day has at least one check-in" }
    }

    val count: Int get() = checkIns.size

    /** The mean of the logged `stress` (1–10), not `stress_after`. */
    val averageStress: Double get() = checkIns.sumOf { it.stress }.toDouble() / count
}

/**
 * Groups check-ins by [localEpochDay]: newest day first, and inside each day the order they came
 * in. Given newest first, that puts each day's newest check-in on top, which is how Logs shows
 * them (design-spec.md §7.6).
 *
 * The days are sorted rather than kept in arrival order because real time and the calendar can
 * disagree after a trip: a check-in at 10 PM Nov 4 in New York happens after one at 12:30 AM
 * Nov 5 in Manila, so the database lists it first, yet its day is the earlier one.
 */
fun List<CheckIn>.groupByLocalDay(): List<CheckInDay> = groupBy { it.localEpochDay }
    .map { (day, checkIns) -> CheckInDay(day, checkIns) }
    .sortedByDescending { it.epochDay }
