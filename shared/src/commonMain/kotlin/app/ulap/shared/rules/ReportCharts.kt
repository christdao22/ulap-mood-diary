package app.ulap.shared.rules

import app.ulap.shared.catalog.MoodCatalog
import app.ulap.shared.model.CheckIn
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import app.ulap.shared.model.Zone

// The Reports calculations after the summary tiles (technical-plan.md §10). Every function takes
// the check-ins already filtered to one period (List<CheckIn>.inPeriod) and returns plain numbers
// the screen draws. Nothing here reads a clock: "day" and "hour" are the local ones at logging time.

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_DAY = 1_440L
private const val MINUTES_PER_HOUR = 60
private const val TOP_MOODS = 5
private const val PERCENT = 100.0

/** Minutes after local midnight when the check-in was logged (0..1439). */
val CheckIn.localMinuteOfDay: Int
    get() = (loggedAt.toEpochMilliseconds() / MILLIS_PER_MINUTE + tzOffsetMin).mod(MINUTES_PER_DAY).toInt()

/** The [TimeOfDay] block of the local hour it was logged in. */
val CheckIn.timeOfDay: TimeOfDay get() = TimeOfDay.ofHour(localMinuteOfDay / MINUTES_PER_HOUR)

/** One bar of the stress trend chart. */
data class StressBar(
    val epochDay: Long,
    /** Daily only: when the check-in was logged, in local minutes after midnight. Null for day bars. */
    val minuteOfDay: Int?,
    /** Null for a day with no check-ins, so the chart leaves a gap instead of drawing 0. */
    val stress: Double?,
    val checkInCount: Int,
)

/**
 * Daily: one bar per check-in, earliest first. Weekly and Monthly: one bar per day of [period]
 * (7 or 28–31), each the day's average stress, oldest first.
 */
fun stressTrend(checkIns: List<CheckIn>, period: ReportPeriod): List<StressBar> {
    val inside = checkIns.inPeriod(period)
    if (period.type == ReportPeriodType.DAILY) {
        return inside.sortedBy { it.loggedAt }
            .map { StressBar(it.localEpochDay, it.localMinuteOfDay, it.stress.toDouble(), 1) }
    }
    val byDay = inside.groupBy { it.localEpochDay }
    return period.epochDays.map { day ->
        val items = byDay[day].orEmpty()
        StressBar(day, null, items.map { it.stress }.averageOrNull(), items.size)
    }
}

/** A count and its share of the whole, 0–100. Shares are not rounded, so they add to 100. */
data class Share<K>(val key: K, val count: Int, val percent: Double)

/**
 * Check-ins per zone, every zone listed (tier order, Red first) even when its count is 0.
 * Check-ins with an unknown mood code are left out of both the counts and the total.
 */
fun zoneMix(checkIns: List<CheckIn>): List<Share<Zone>> {
    val zones = checkIns.mapNotNull { MoodCatalog.byCode(it.moodCode)?.zone }
    return Zone.entries.sortedBy { it.tier }.map { zone ->
        val count = zones.count { it == zone }
        Share(zone, count, if (zones.isEmpty()) 0.0 else count * PERCENT / zones.size)
    }
}

/** The top 5 moods by count, most first. Ties go to the mood logged most recently. */
fun moodFrequency(checkIns: List<CheckIn>): List<Share<String>> =
    checkIns.rankBy { it.moodCode }.take(TOP_MOODS).map { (code, items) ->
        Share(code, items.size, items.size * PERCENT / checkIns.size)
    }

data class TriggerStat(val trigger: Trigger, val count: Int, val averageStress: Double)

/** Every trigger that was picked, most picked first (ties: most recent). No-trigger check-ins skipped. */
fun topTriggers(checkIns: List<CheckIn>): List<TriggerStat> = checkIns.rankBy { it.trigger }.map { (trigger, items) ->
    TriggerStat(trigger, items.size, items.map { it.stress }.average())
}

data class TimeOfDayStat(val block: TimeOfDay, val count: Int, val averageStress: Double?)

/** All four blocks, Morning to Night. A block with no check-ins has a null average. */
fun timeOfDayAverages(checkIns: List<CheckIn>): List<TimeOfDayStat> {
    val byBlock = checkIns.groupBy { it.timeOfDay }
    return TimeOfDay.entries.map { block ->
        val items = byBlock[block].orEmpty()
        TimeOfDayStat(block, items.size, items.map { it.stress }.averageOrNull())
    }
}

data class SupportStat(
    val support: SupportType,
    val count: Int,
    /** Mean of `stress − stressAfter`. Positive means stress went down after it. */
    val averageRelief: Double,
)

/**
 * "What helped most": check-ins that have `stressAfter`, grouped by the support used, biggest
 * average relief first. [SupportType.NONE] is left out because it isn't something that helped.
 */
fun whatHelped(checkIns: List<CheckIn>): List<SupportStat> = checkIns
    .filter { it.stressAfter != null && it.supportUsed != SupportType.NONE }
    .groupBy { it.supportUsed }
    .map { (support, items) -> SupportStat(support, items.size, items.map { it.stress - it.stressAfter!! }.average()) }
    .sortedWith(compareByDescending<SupportStat> { it.averageRelief }.thenByDescending { it.count })

internal fun List<Int>.averageOrNull(): Double? = if (isEmpty()) null else average()

/**
 * Groups by a non-null key, most check-ins first; a tie goes to the key whose latest check-in is
 * newest. Shared by the summary tiles and the bar lists so "top" always means the same thing.
 */
internal fun <K : Any> List<CheckIn>.rankBy(key: (CheckIn) -> K?): List<Pair<K, List<CheckIn>>> =
    mapNotNull { c -> key(c)?.let { it to c } }
        .groupBy({ it.first }, { it.second })
        .toList()
        .sortedWith(
            compareByDescending<Pair<K, List<CheckIn>>> { it.second.size }
                .thenByDescending { group -> group.second.maxOf { it.loggedAt } },
        )

/** The Reports color bands for a stress value (design-spec.md §2.3): Low 0–3.9, Moderate 4–6.9, High 7–10. */
enum class StressBand {
    LOW,
    MODERATE,
    HIGH,
    ;

    companion object {
        private const val MODERATE_FROM = 4.0
        private const val HIGH_FROM = 7.0

        fun of(stress: Double): StressBand = when {
            stress >= HIGH_FROM -> HIGH
            stress >= MODERATE_FROM -> MODERATE
            else -> LOW
        }
    }
}
