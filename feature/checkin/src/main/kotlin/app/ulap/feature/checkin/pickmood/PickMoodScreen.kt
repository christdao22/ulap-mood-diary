package app.ulap.feature.checkin.pickmood

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ulap.core.designsystem.component.MoodTile
import app.ulap.core.designsystem.component.UlapSays
import app.ulap.core.designsystem.component.ZoneSection
import app.ulap.core.designsystem.mascot.UlapExpression
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.ui.R
import app.ulap.core.ui.descriptorRes
import app.ulap.core.ui.nameRes
import app.ulap.shared.catalog.MoodCatalog
import app.ulap.shared.model.Mood
import app.ulap.shared.model.Zone
import app.ulap.shared.rules.TimeOfDay

/** Check in · Pick a mood (design-spec.md §7.2), wired to its ViewModel. */
@Composable
internal fun PickMoodScreen(
    onMoodClick: (moodCode: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PickMoodViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PickMoodContent(uiState = uiState, onMoodClick = onMoodClick, modifier = modifier)
}

/**
 * Ulap's greeting, then one card per zone (Red → Blue → Yellow → Green, §6.2) with its moods in
 * a three-column grid. Tapping a mood opens Details; the grid has no selected state (§6.1).
 */
@Composable
internal fun PickMoodContent(
    uiState: PickMoodUiState,
    onMoodClick: (moodCode: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(UlapSpacing.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(UlapSpacing.CardGap),
    ) {
        item(key = "greeting") {
            UlapSays(
                expression = UlapExpression.NEUTRAL,
                context = stringResource(uiState.timeOfDay.greetingRes),
                message = stringResource(R.string.pick_mood_question),
                supporting = stringResource(R.string.pick_mood_reassurance),
                showUlap = uiState.showUlap,
            )
        }
        // Zone's declaration order is the display order (technical-plan.md §5).
        items(Zone.entries, key = { it.name }) { zone ->
            ZoneMoods(zone = zone, onMoodClick = onMoodClick)
        }
    }
}

@Composable
private fun ZoneMoods(zone: Zone, onMoodClick: (moodCode: String) -> Unit) {
    val zoneName = stringResource(zone.nameRes)
    ZoneSection(zone = zone, title = zoneName, descriptor = stringResource(zone.descriptorRes)) {
        Column(verticalArrangement = Arrangement.spacedBy(UlapSpacing.MoodGridGap)) {
            MoodCatalog.inZone(zone).chunked(COLUMNS).forEach { row ->
                MoodRow(moods = row, zone = zone, zoneName = zoneName, onMoodClick = onMoodClick)
            }
        }
    }
}

@Composable
private fun MoodRow(moods: List<Mood>, zone: Zone, zoneName: String, onMoodClick: (moodCode: String) -> Unit) {
    // IntrinsicSize.Min lets every tile in a row match the tallest one when a long name wraps.
    Row(
        modifier = Modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(UlapSpacing.MoodGridGap),
    ) {
        moods.forEach { mood ->
            val name = stringResource(mood.nameRes)
            MoodTile(
                name = name,
                stressText = stringResource(R.string.mood_tile_stress, mood.defaultStress),
                zone = zone,
                contentDescription = stringResource(R.string.mood_tile_description, name, zoneName, mood.defaultStress),
                onClick = { onMoodClick(mood.code) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
        // A short last row keeps each tile at a third of the width (§6.1).
        repeat(COLUMNS - moods.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** Night has no greeting of its own; "Good evening" reads naturally then too. */
@get:StringRes
private val TimeOfDay.greetingRes: Int
    get() = when (this) {
        TimeOfDay.MORNING -> R.string.greeting_morning
        TimeOfDay.AFTERNOON -> R.string.greeting_afternoon
        TimeOfDay.EVENING, TimeOfDay.NIGHT -> R.string.greeting_evening
    }

private const val COLUMNS = 3

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F8, widthDp = 360, heightDp = 1200)
@Composable
private fun PickMoodContentPreview() {
    UlapTheme {
        PickMoodContent(
            uiState = PickMoodUiState(timeOfDay = TimeOfDay.AFTERNOON, showUlap = true),
            onMoodClick = {},
        )
    }
}
