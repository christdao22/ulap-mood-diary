package app.ulap.feature.logs

import android.text.format.DateFormat
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ulap.core.designsystem.component.LogDayHeader
import app.ulap.core.designsystem.component.LogRow
import app.ulap.core.designsystem.component.SearchField
import app.ulap.core.designsystem.component.UlapConfirmDialog
import app.ulap.core.designsystem.component.UlapMenuItem
import app.ulap.core.designsystem.component.UlapOverflowMenu
import app.ulap.core.designsystem.component.ZoneFilterChip
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.ui.R
import app.ulap.core.ui.nameRes
import app.ulap.core.ui.shortNameRes
import app.ulap.shared.catalog.MoodCatalog
import app.ulap.shared.model.CheckIn
import app.ulap.shared.model.Trigger
import app.ulap.shared.model.Zone
import app.ulap.shared.rules.SearchNames
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.toJavaInstant

/**
 * Logs (design-spec.md §7.6), wired to its ViewModel.
 *
 * @param onEditCheckIn Edit in a row's menu, with that check-in's ID.
 * @param onCheckInNow "Check in now" on the empty diary: opens the Check in tab.
 */
@Composable
internal fun LogsScreen(
    onEditCheckIn: (String) -> Unit,
    onCheckInNow: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LogsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showUlap by viewModel.showUlap.collectAsStateWithLifecycle()
    // Search matches the names on screen, which only resources know, in the app's language.
    val searchNames = rememberSearchNames()
    LaunchedEffect(viewModel, searchNames) { viewModel.onSearchNames(searchNames) }
    LogsContent(
        uiState = uiState,
        showUlap = showUlap,
        onCheckInNow = onCheckInNow,
        query = viewModel.query,
        zone = viewModel.zone,
        onQueryChange = viewModel::onQueryChange,
        onZoneClick = viewModel::onZoneClick,
        onEditClick = onEditCheckIn,
        onDeleteClick = viewModel::onDeleteClick,
        pendingDeleteId = viewModel.pendingDeleteId,
        onDeleteConfirm = viewModel::onDeleteConfirm,
        onDeleteDismiss = viewModel::onDeleteDismiss,
        modifier = modifier,
    )
}

/**
 * The title, the search field and zone chips, then one group per local day, newest first: a day
 * header ("Today", "3 check-ins · avg stress 4.4"), then that day's log rows (design-spec.md
 * §6.13). Search and chips are hidden until there's something to search: before the first
 * check-in, Ulap says where they'll show up and offers "Check in now". Each row's menu has Edit
 * and Delete; while [pendingDeleteId] is set, "Delete this check-in?" asks before deleting.
 */
@Composable
internal fun LogsContent(
    uiState: LogsUiState,
    modifier: Modifier = Modifier,
    showUlap: Boolean = true,
    onCheckInNow: () -> Unit = {},
    query: String = "",
    zone: Zone? = null,
    onQueryChange: (String) -> Unit = {},
    onZoneClick: (Zone?) -> Unit = {},
    onEditClick: (String) -> Unit = {},
    onDeleteClick: (String) -> Unit = {},
    pendingDeleteId: String? = null,
    onDeleteConfirm: () -> Unit = {},
    onDeleteDismiss: () -> Unit = {},
) {
    val formats = rememberLogFormats()
    val rowActions = remember(onEditClick, onDeleteClick) { RowActions(onEditClick, onDeleteClick) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(UlapSpacing.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(UlapSpacing.Xs),
    ) {
        item(key = "title", contentType = "title") {
            Text(
                text = stringResource(R.string.tab_logs),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
                color = UlapTheme.colors.ink,
            )
        }
        if (uiState is LogsUiState.Loaded && !uiState.isDiaryEmpty) {
            item(key = "search", contentType = "search") {
                SearchField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = stringResource(R.string.logs_search_hint),
                    clearLabel = stringResource(R.string.logs_search_clear),
                    modifier = Modifier.padding(top = UlapSpacing.S),
                )
            }
            item(key = "zones", contentType = "zones") {
                ZoneChips(selected = zone, onClick = onZoneClick)
            }
            if (uiState.isNoMatch) {
                item(key = "no-match", contentType = "no-match") { NoMatch() }
            }
            logDays(uiState.days, formats, rowActions)
        }
        if (uiState is LogsUiState.Loaded && uiState.isDiaryEmpty) {
            item(key = "empty", contentType = "empty") {
                EmptyDiary(showUlap = showUlap, onCheckInNow = onCheckInNow)
            }
        }
    }
    if (pendingDeleteId != null) {
        UlapConfirmDialog(
            title = stringResource(R.string.logs_delete_title),
            text = stringResource(R.string.logs_delete_text),
            confirmLabel = stringResource(R.string.logs_delete),
            dismissLabel = stringResource(R.string.logs_delete_keep),
            onConfirm = onDeleteConfirm,
            onDismiss = onDeleteDismiss,
        )
    }
}

/** What a row's menu items do, each given that row's check-in ID. */
private class RowActions(val onEdit: (String) -> Unit, val onDelete: (String) -> Unit)

/** Keys and content types let the list reuse rows while scrolling a long history. */
private fun LazyListScope.logDays(days: List<LogDay>, formats: LogFormats, actions: RowActions) {
    days.forEach { logDay ->
        item(key = "day-${logDay.day.epochDay}", contentType = "day") {
            LogDayHeader(
                day = dayLabelText(logDay.label, formats),
                summary = pluralStringResource(
                    R.plurals.logs_day_summary,
                    logDay.day.count,
                    logDay.day.count,
                    formats.averageStress(logDay.day.averageStress),
                ),
                // The gap above a day is wider than the gap between its rows.
                modifier = Modifier.padding(top = UlapSpacing.M),
            )
        }
        items(logDay.day.checkIns, key = { it.id }, contentType = { "row" }) { checkIn ->
            CheckInRow(checkIn = checkIn, formats = formats, actions = actions)
        }
    }
}

/** All · Red · Blue · Yellow · Green, scrolling sideways when they don't fit (design-spec.md §6.12). */
@Composable
private fun ZoneChips(selected: Zone?, onClick: (Zone?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(UlapSpacing.Xs),
    ) {
        ZoneFilterChip(
            label = stringResource(R.string.logs_filter_all),
            zone = null,
            selected = selected == null,
            onClick = { onClick(null) },
        )
        Zone.entries.forEach { zone ->
            ZoneFilterChip(
                label = stringResource(zone.shortNameRes),
                zone = zone,
                selected = selected == zone,
                onClick = { onClick(zone) },
            )
        }
    }
}

/** Every mood and trigger name in the app's language, read again when the language changes. */
@Composable
private fun rememberSearchNames(): SearchNames {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(resources, locale) {
        SearchNames(
            moods = MoodCatalog.all.associate { it.code to resources.getString(it.nameRes) },
            triggers = Trigger.entries.associateWith { resources.getString(it.nameRes) },
        )
    }
}

@Composable
private fun CheckInRow(checkIn: CheckIn, formats: LogFormats, actions: RowActions) {
    // The ViewModel drops unknown mood codes, so the catalog always has this one.
    val mood = MoodCatalog.byCode(checkIn.moodCode) ?: return
    val time = formats.time(checkIn)
    val trigger = checkIn.trigger?.let { triggerText(it, checkIn.triggerOther) }
    LogRow(
        zone = mood.zone,
        mood = stringResource(mood.nameRes),
        meta = if (trigger == null) {
            stringResource(R.string.logs_row_meta, time, checkIn.stress)
        } else {
            stringResource(R.string.logs_row_meta_trigger, time, checkIn.stress, trigger)
        },
        note = checkIn.note,
    ) {
        UlapOverflowMenu(contentDescription = stringResource(R.string.logs_row_menu)) { close ->
            UlapMenuItem(
                text = stringResource(R.string.logs_edit),
                onClick = {
                    close()
                    actions.onEdit(checkIn.id)
                },
            )
            UlapMenuItem(
                text = stringResource(R.string.logs_delete),
                onClick = {
                    close()
                    actions.onDelete(checkIn.id)
                },
            )
        }
    }
}

/** "Work", or the person's own words when they picked Other and wrote some. */
@Composable
private fun triggerText(trigger: Trigger, triggerOther: String?): String =
    if (trigger == Trigger.OTHER && !triggerOther.isNullOrBlank()) triggerOther else stringResource(trigger.nameRes)

@Composable
private fun dayLabelText(label: DayLabel, formats: LogFormats): String = when (label) {
    DayLabel.Today -> stringResource(R.string.logs_today)
    DayLabel.Yesterday -> stringResource(R.string.logs_yesterday)
    is DayLabel.Date -> formats.date(label)
}

/**
 * Dates, times and averages in the app's language. The patterns come from the locale, so
 * Filipino and Bisaya get their own order, and the time is 12- or 24-hour as the person set it.
 */
private class LogFormats(locale: Locale, is24Hour: Boolean) {
    private val timeFormat =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (is24Hour) "Hm" else "hma"), locale)
    private val dateFormat = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEMMMd"), locale)
    private val dateWithYearFormat =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEMMMdy"), locale)
    private val averageFormat = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
        roundingMode = RoundingMode.HALF_UP
    }

    /** The time on the clock it was logged on, the same clock that picks its day. */
    fun time(checkIn: CheckIn): String = timeFormat.format(
        checkIn.loggedAt.toJavaInstant().atOffset(ZoneOffset.ofTotalSeconds(checkIn.tzOffsetMin * SECONDS_PER_MINUTE)),
    )

    fun date(label: DayLabel.Date): String = (if (label.showYear) dateWithYearFormat else dateFormat).format(label.date)

    /** "4.4": always one decimal, so the header doesn't change width between days. */
    fun averageStress(value: Double): String = averageFormat.format(value)

    private companion object {
        const val SECONDS_PER_MINUTE = 60
    }
}

@Composable
private fun rememberLogFormats(): LogFormats {
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(locale, is24Hour) { LogFormats(locale, is24Hour) }
}
