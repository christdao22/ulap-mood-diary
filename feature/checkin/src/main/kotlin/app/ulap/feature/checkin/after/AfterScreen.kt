package app.ulap.feature.checkin.after

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ulap.core.designsystem.component.UlapPrimaryButton
import app.ulap.core.designsystem.component.UlapSecondaryButton
import app.ulap.core.designsystem.icon.UlapIcons
import app.ulap.core.designsystem.mascot.Ulap
import app.ulap.core.designsystem.mascot.UlapSize
import app.ulap.core.designsystem.theme.UlapShape
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.ui.HotlineCard
import app.ulap.core.ui.R
import app.ulap.core.ui.toMascot
import app.ulap.shared.catalog.MoodCatalog
import app.ulap.shared.model.Zone
import app.ulap.shared.rules.ResponseRules
import app.ulap.shared.rules.StressChange

/** Check in · After (design-spec.md §7.5), wired to its ViewModel. */
@Composable
internal fun AfterScreen(
    onDone: () -> Unit,
    onSeeMyWeek: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AfterViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val showUlap by viewModel.showUlap.collectAsStateWithLifecycle()
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(state) { if (state == AfterUiState.Missing) currentOnDone() }

    // Loading is a single read from the phone's database, too quick to need a spinner.
    (state as? AfterUiState.Ready)?.let { ready ->
        AfterContent(
            state = ready,
            showUlap = showUlap,
            onDone = onDone,
            onSeeMyWeek = onSeeMyWeek,
            modifier = modifier,
        )
    }
}

/**
 * Ulap, "Before 8 → Now 5", the title and sub for lower, same or higher (design-spec.md §10.4),
 * then Done and See my week. The zone tint carries over from Response, so the check-in reads as
 * one flow.
 */
@Composable
internal fun AfterContent(
    state: AfterUiState.Ready,
    showUlap: Boolean,
    onDone: () -> Unit,
    onSeeMyWeek: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UlapTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.zone(state.zone).tint)
            .verticalScroll(rememberScrollState())
            .padding(UlapSpacing.ResponseScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(UlapSpacing.S),
    ) {
        // Decorative: the title says the same thing (design-spec.md §11).
        if (showUlap) {
            Ulap(
                expression = state.result.expression.toMascot(),
                contentDescription = null,
                size = UlapSize.After,
                float = state.result.floats,
            )
        }
        BeforeNowPills(before = state.before, now = state.now)
        Text(
            text = afterTitle(state),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = afterSub(state),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.ink,
            textAlign = TextAlign.Center,
        )
        // Hopeless (🔒): the 1553 card again, whichever way stress went, so help stays one tap away
        // for the whole check-in (design-spec.md §7.5).
        if (state.result.showHotline) {
            Spacer(Modifier.height(UlapSpacing.S))
            HotlineCard()
        }
        Spacer(Modifier.height(UlapSpacing.S))
        AfterButtons(callIsPrimary = state.result.showHotline, onDone = onDone, onSeeMyWeek = onSeeMyWeek)
    }
}

/**
 * Done and See my week. With the hotline card above them, both are secondary, so "Call 1553" is
 * the one primary action on the screen (design-spec.md §6.6, §7.5).
 */
@Composable
private fun AfterButtons(callIsPrimary: Boolean, onDone: () -> Unit, onSeeMyWeek: () -> Unit) {
    val done = stringResource(R.string.resp_action_done)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(UlapSpacing.S)) {
        if (callIsPrimary) {
            UlapSecondaryButton(text = done, onClick = onDone, modifier = Modifier.fillMaxWidth())
        } else {
            UlapPrimaryButton(text = done, onClick = onDone, modifier = Modifier.fillMaxWidth())
        }
        UlapSecondaryButton(
            text = stringResource(R.string.after_action_week),
            onClick = onSeeMyWeek,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** "Before 8 → Now 5". The arrow is decorative, so TalkBack reads "Before 8, Now 5". */
@Composable
private fun BeforeNowPills(before: Int, now: Int) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(UlapSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StressPill(text = stringResource(R.string.after_before, before))
        Icon(imageVector = UlapIcons.Arrow, contentDescription = null, tint = UlapTheme.colors.ink3)
        StressPill(text = stringResource(R.string.after_now, now))
    }
}

/** A plain pill: neutral on purpose, because stress band colors are for Reports only (design-spec.md §2.3). */
@Composable
private fun StressPill(text: String) {
    val colors = UlapTheme.colors
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = colors.ink,
        modifier = Modifier
            .background(colors.surface, UlapShape.Pill)
            .border(UlapSpacing.Border, colors.line, UlapShape.Pill)
            .padding(horizontal = UlapSpacing.M, vertical = UlapSpacing.Xs),
    )
}

/** The §10.4 title: "Your stress went from 8 to 5." / "Still at 8, and that's okay." / "It went up to 9." */
@Composable
private fun afterTitle(state: AfterUiState.Ready): String = when (state.result.change) {
    StressChange.LOWER -> stringResource(R.string.after_lower, state.before, state.now)
    StressChange.SAME -> stringResource(R.string.after_same, state.now)
    StressChange.HIGHER -> stringResource(R.string.after_higher, state.now)
}

/**
 * The §10.4 sub. "That's 3 points lower" needs a plural for 1 point. Hopeless (🔒) has its own
 * lower and higher subs, which lean on the hotline card right below them instead of "if it keeps
 * rising" or "remember what helped".
 */
@Composable
private fun afterSub(state: AfterUiState.Ready): String {
    val hopeless = state.result.showHotline
    return when (state.result.change) {
        StressChange.LOWER if hopeless -> stringResource(R.string.after_lower_sub_hopeless)

        StressChange.LOWER -> {
            val points = state.before - state.now
            pluralStringResource(R.plurals.after_lower_sub, points, points)
        }

        StressChange.SAME -> stringResource(R.string.after_same_sub)

        StressChange.HIGHER if hopeless -> stringResource(R.string.after_higher_sub_hopeless)

        StressChange.HIGHER -> stringResource(R.string.after_higher_sub)
    }
}

@Preview(widthDp = 360, heightDp = 760)
@Composable
private fun AfterContentPreview() {
    UlapTheme {
        AfterContent(
            state = AfterUiState.Ready(
                zone = Zone.RED,
                before = 8,
                now = 5,
                result = ResponseRules.afterCheck(checkNotNull(MoodCatalog.byCode("worried")), before = 8, now = 5),
            ),
            showUlap = true,
            onDone = {},
            onSeeMyWeek = {},
        )
    }
}
