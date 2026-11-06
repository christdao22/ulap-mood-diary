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
 * Groups check-ins by [localEpochDay], keeping the order they came in, both of the days (by each
 * day's first check-in) and inside each day. Given newest first, that's newest day first with its
 * newest check-in on top, which is how Logs shows them (design-spec.md §7.6).
 */
fun List<CheckIn>.groupByLocalDay(): List<CheckInDay> =
    groupBy { it.localEpochDay }.map { (day, checkIns) -> CheckInDay(day, checkIns) }
