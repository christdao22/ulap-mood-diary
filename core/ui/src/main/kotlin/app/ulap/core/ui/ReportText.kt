package app.ulap.core.ui

import android.content.res.Resources
import androidx.annotation.StringRes
import app.ulap.shared.rules.ReportInsight
import app.ulap.shared.rules.TimeOfDay
import kotlinx.datetime.DayOfWeek

// The "Ulap noticed: …" lines (technical-plan.md §10, design-spec.md §7.7 and §10). Like
// CheckInText.kt, every value maps to its string by hand.

/** The bubble text after "Ulap noticed:", in the app's language. */
fun ReportInsight.text(resources: Resources): String = when (this) {
    is ReportInsight.HardestWeekday -> resources.getString(day.insightRes)

    is ReportInsight.HardestTimeOfDay -> resources.getString(block.insightRes)

    is ReportInsight.TopTriggerShare ->
        resources.getQuantityString(
            R.plurals.insight_trigger_share,
            total,
            resources.getString(trigger.nameRes),
            count,
            total,
        )
}

@get:StringRes
private val DayOfWeek.insightRes: Int
    get() = when (this) {
        DayOfWeek.MONDAY -> R.string.insight_weekday_monday
        DayOfWeek.TUESDAY -> R.string.insight_weekday_tuesday
        DayOfWeek.WEDNESDAY -> R.string.insight_weekday_wednesday
        DayOfWeek.THURSDAY -> R.string.insight_weekday_thursday
        DayOfWeek.FRIDAY -> R.string.insight_weekday_friday
        DayOfWeek.SATURDAY -> R.string.insight_weekday_saturday
        DayOfWeek.SUNDAY -> R.string.insight_weekday_sunday
    }

@get:StringRes
private val TimeOfDay.insightRes: Int
    get() = when (this) {
        TimeOfDay.MORNING -> R.string.insight_time_morning
        TimeOfDay.AFTERNOON -> R.string.insight_time_afternoon
        TimeOfDay.EVENING -> R.string.insight_time_evening
        TimeOfDay.NIGHT -> R.string.insight_time_night
    }
