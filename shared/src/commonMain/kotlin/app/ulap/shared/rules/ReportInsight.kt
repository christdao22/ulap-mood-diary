package app.ulap.shared.rules

import app.ulap.shared.model.CheckIn
import app.ulap.shared.model.Trigger
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * What goes in the "Ulap noticed: …" bubble (technical-plan.md §10, design-spec.md §7.7). Each
 * one describes a pattern ("Mondays tend to be harder for you") and never grades the person. The
 * app turns it into words; shared only decides which pattern is real enough to mention.
 */
sealed interface ReportInsight {
    /** Monthly only: this weekday's stress runs clearly above the period's average. */
    data class HardestWeekday(val day: DayOfWeek) : ReportInsight

    /** This time block's stress runs clearly above the period's average. */
    data class HardestTimeOfDay(val block: TimeOfDay) : ReportInsight

    /** "Work came up in 4 of your 7 check-ins." */
    data class TopTriggerShare(val trigger: Trigger, val count: Int, val total: Int) : ReportInsight

    companion object {
        /** Fewer check-ins than this shows the not-enough-data state instead (design-spec.md §7.7). */
        const val MIN_CHECK_INS = 3

        /** A group needs at least this many check-ins (or dates), so one hard moment isn't a pattern. */
        private const val MIN_GROUP = 2

        /** How far (on the 1–10 scale) a group must sit above the period average to count. */
        private const val STRESS_GAP = 1.0

        /** The top trigger must be in at least this share of the period's check-ins. */
        private const val TRIGGER_SHARE = 0.4

        /**
         * Every insight that holds for [period], best first: weekday, then time of day, then
         * trigger. The bubble shows the first. Empty when nothing stands out or there are fewer
         * than [MIN_CHECK_INS] check-ins.
         */
        fun of(checkIns: List<CheckIn>, period: ReportPeriod): List<ReportInsight> {
            val items = checkIns.inPeriod(period)
            if (items.size < MIN_CHECK_INS) return emptyList()
            val overall = items.map { it.stress }.average()
            return listOfNotNull(
                if (period.type == ReportPeriodType.MONTHLY) hardestWeekday(items, overall) else null,
                hardestTimeOfDay(items, overall),
                topTriggerShare(items),
            )
        }

        // A weekday only "tends to" be anything once it has come up on two different dates, so
        // Daily and Weekly (each weekday at most once) never get this one.
        private fun hardestWeekday(items: List<CheckIn>, overall: Double): ReportInsight? = items
            .groupBy { LocalDate.fromEpochDays(it.localEpochDay).dayOfWeek }
            .filterValues { group -> group.map { it.localEpochDay }.distinct().size >= MIN_GROUP }
            .standsOut(items.size, overall)
            ?.let { HardestWeekday(it) }

        private fun hardestTimeOfDay(items: List<CheckIn>, overall: Double): ReportInsight? = items
            .groupBy { it.timeOfDay }
            .filterValues { it.size >= MIN_GROUP }
            .standsOut(items.size, overall)
            ?.let { HardestTimeOfDay(it) }

        private fun topTriggerShare(items: List<CheckIn>): ReportInsight? {
            val (trigger, group) = items.rankBy { it.trigger }.firstOrNull() ?: return null
            val enough = group.size >= MIN_GROUP && group.size >= items.size * TRIGGER_SHARE
            return if (enough) TopTriggerShare(trigger, group.size, items.size) else null
        }

        /**
         * The key with the highest average stress, if some check-ins in the period fall outside
         * it and its average is [STRESS_GAP] or more above [overall]. Ties go to the key whose first
         * check-in comes first in the list (groupBy keeps that order), not to the earlier weekday or block.
         */
        private fun <K> Map<K, List<CheckIn>>.standsOut(total: Int, overall: Double): K? {
            val best = entries.maxByOrNull { (_, g) -> g.map { it.stress }.average() } ?: return null
            val average = best.value.map { it.stress }.average()
            return if (total > best.value.size && average >= overall + STRESS_GAP) best.key else null
        }
    }
}
