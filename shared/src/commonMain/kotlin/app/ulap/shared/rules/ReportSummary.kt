package app.ulap.shared.rules

import app.ulap.shared.model.CheckIn
import app.ulap.shared.model.Trigger

/**
 * The Reports stat tiles for one period, plus the same numbers for the period before it
 * (technical-plan.md §10 "Summary tiles", design-spec.md §7.7).
 *
 * Averages are null when there is nothing to average, so the screen can show "—" instead of 0.
 */
data class ReportSummary(
    val checkInCount: Int,
    val averageStress: Double?,
    val averageEnergy: Double?,
    /** Mood code. Ties go to the mood logged most recently. */
    val mostCommonMoodCode: String?,
    /** Ties go to the trigger logged most recently. Check-ins without a trigger are skipped. */
    val topTrigger: Trigger?,
    /** Same numbers for [ReportPeriod.previous], or null when that period has no check-ins. */
    val previous: ReportSummary?,
) {
    /** This period's count minus the previous one's (previous counts as 0 when empty). */
    val checkInCountChange: Int get() = checkInCount - (previous?.checkInCount ?: 0)

    /** Null unless both periods have an average. Negative means less stressed than before. */
    val averageStressChange: Double? get() = diff(averageStress, previous?.averageStress)

    val averageEnergyChange: Double? get() = diff(averageEnergy, previous?.averageEnergy)

    companion object {
        /** Summary of [period] from all of the person's check-ins (any order, any dates). */
        fun of(checkIns: List<CheckIn>, period: ReportPeriod): ReportSummary {
            val prev = checkIns.inPeriod(period.previous())
            return summarize(checkIns.inPeriod(period), previous = if (prev.isEmpty()) null else summarize(prev, null))
        }

        private fun summarize(items: List<CheckIn>, previous: ReportSummary?) = ReportSummary(
            checkInCount = items.size,
            averageStress = items.map { it.stress }.averageOrNull(),
            averageEnergy = items.map { it.energy }.averageOrNull(),
            mostCommonMoodCode = items.rankBy { it.moodCode }.firstOrNull()?.first,
            topTrigger = items.rankBy { it.trigger }.firstOrNull()?.first,
            previous = previous,
        )

        private fun diff(now: Double?, before: Double?): Double? =
            if (now == null || before == null) null else now - before
    }
}
