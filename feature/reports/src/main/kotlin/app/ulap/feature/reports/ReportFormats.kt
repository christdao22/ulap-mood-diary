package app.ulap.feature.reports

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import app.ulap.shared.rules.ReportPeriod
import app.ulap.shared.rules.ReportPeriodType
import app.ulap.shared.rules.StressBar
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun rememberReportFormats(): ReportFormats {
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(locale, is24Hour) { ReportFormats(locale, is24Hour) }
}

/** Dates, times and numbers in the app's language and the phone's 12/24-hour setting. */
internal class ReportFormats(locale: Locale, is24Hour: Boolean) {
    private fun pattern(skeleton: String, locale: Locale) =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

    private val time = pattern(if (is24Hour) "Hm" else "hma", locale)
    private val dayWithWeekday = pattern("EEEMMMd", locale)
    private val dayWithYear = pattern("EEEMMMdy", locale)
    private val monthDay = pattern("MMMd", locale)
    private val monthDayYear = pattern("MMMdy", locale)
    private val monthYear = pattern("MMMMy", locale)
    private val shortWeekday = pattern("EEE", locale)
    private val longWeekday = pattern("EEEE", locale)
    private val longMonthDay = pattern("MMMMd", locale)
    private val dayOfMonth = pattern("d", locale)
    private val decimal = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
        roundingMode = RoundingMode.HALF_UP
    }

    /** "0.0" in this language, to tell "no change" after rounding. */
    val zero: String = decimal.format(0.0)

    fun oneDecimal(value: Double): String = decimal.format(value)

    /** "Today", "Wed, Nov 4", "Oct 29 – Nov 4" or "November 2026". The year shows only when it isn't this one. */
    fun periodLabel(period: ReportPeriod, todayEpochDay: Long, today: String): String {
        val start = LocalDate.ofEpochDay(period.startEpochDay)
        val end = LocalDate.ofEpochDay(period.endEpochDay)
        val thisYear = LocalDate.ofEpochDay(todayEpochDay).year
        return when (period.type) {
            ReportPeriodType.DAILY -> when {
                period.startEpochDay == todayEpochDay -> today
                start.year == thisYear -> dayWithWeekday.format(start)
                else -> dayWithYear.format(start)
            }

            ReportPeriodType.WEEKLY -> {
                val endFormat = if (end.year == thisYear && start.year == thisYear) monthDay else monthDayYear
                val startFormat = if (start.year == end.year) monthDay else monthDayYear
                "${startFormat.format(start)} – ${endFormat.format(end)}"
            }

            ReportPeriodType.MONTHLY -> monthYear.format(start)
        }
    }

    /** Under a bar: "9:15 AM" (Daily), "Mon" (Weekly) or the day number (Monthly). */
    fun axisLabel(bar: StressBar, type: ReportPeriodType): String = when (type) {
        ReportPeriodType.DAILY -> bar.minuteOfDay?.let {
            time.format(LocalTime.of(it / MINUTES, it % MINUTES))
        }.orEmpty()

        ReportPeriodType.WEEKLY -> shortWeekday.format(LocalDate.ofEpochDay(bar.epochDay))

        ReportPeriodType.MONTHLY -> dayOfMonth.format(LocalDate.ofEpochDay(bar.epochDay))
    }

    /** For TalkBack: "9:15 AM", or "Monday" in a week, or "November 4" in a month. */
    fun spokenLabel(bar: StressBar, type: ReportPeriodType): String = when (type) {
        ReportPeriodType.DAILY -> axisLabel(bar, type)
        ReportPeriodType.WEEKLY -> longWeekday.format(LocalDate.ofEpochDay(bar.epochDay))
        ReportPeriodType.MONTHLY -> longMonthDay.format(LocalDate.ofEpochDay(bar.epochDay))
    }

    private companion object {
        const val MINUTES = 60
    }
}
