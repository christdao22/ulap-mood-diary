package app.ulap.feature.checkin.response

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ulap.core.designsystem.component.UlapPrimaryButton
import app.ulap.core.designsystem.component.UlapSecondaryButton
import app.ulap.core.designsystem.mascot.Ulap
import app.ulap.core.designsystem.mascot.UlapSize
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.ui.R
import app.ulap.core.ui.bodyRes
import app.ulap.core.ui.nameRes
import app.ulap.core.ui.offerRes
import app.ulap.core.ui.responseLineRes
import app.ulap.core.ui.titleRes
import app.ulap.core.ui.toMascot
import app.ulap.shared.catalog.MoodCatalog
import app.ulap.shared.model.Trigger
import app.ulap.shared.rules.ResponseActions
import app.ulap.shared.rules.ResponseRules

/** Check in · Response (design-spec.md §7.4), wired to its ViewModel. */
@Composable
internal fun ResponseScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ResponseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val showUlap by viewModel.showUlap.collectAsStateWithLifecycle()
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(state) { if (state == ResponseUiState.Missing) currentOnDone() }

    // Loading is a single read from the phone's database, too quick to need a spinner.
    (state as? ResponseUiState.Ready)?.let { ready ->
        ResponseContent(
            state = ready,
            showUlap = showUlap,
            onStartSupport = viewModel::startSupport,
            onLater = onDone,
            modifier = modifier,
        )
    }
}

/**
 * The zone's tint behind everything; Ulap, the title, the body and the trigger line, centered;
 * then what ResponseRules offers. Only the rules decide which buttons show (technical-plan.md §5).
 */
@Composable
internal fun ResponseContent(
    state: ResponseUiState.Ready,
    showUlap: Boolean,
    onStartSupport: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UlapTheme.colors
    val response = state.response
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.zone(state.mood.zone).tint)
            .verticalScroll(rememberScrollState())
            .padding(UlapSpacing.ResponseScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(UlapSpacing.S),
    ) {
        // Decorative: the title right under Ulap says the same thing (design-spec.md §11).
        if (showUlap) {
            Ulap(
                expression = response.expression.toMascot(),
                contentDescription = null,
                size = UlapSize.Response,
                float = response.floats,
            )
        }
        Text(
            text = stringResource(response.message.titleRes),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(response.message.bodyRes, rememberMoodWord(state)),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.ink,
            textAlign = TextAlign.Center,
        )
        response.triggerLine?.responseLineRes?.let { line ->
            Text(
                text = stringResource(line),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink3,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(UlapSpacing.S))
        when (val actions = response.actions) {
            // The NCMH 1553 hotline card goes here (PHASES.md Phase 2, Hopeless 🔒).
            ResponseActions.Hotline -> Unit

            // The support card takes the buttons' place after the tap (PHASES.md Phase 2,
            // Support cards).
            is ResponseActions.Offer -> if (state.startedSupport == null) {
                OfferButtons(offer = actions, onStartSupport = onStartSupport, onLater = onLater)
            }
        }
    }
}

/**
 * The person's own mood word inside the body, e.g. "Feeling worried makes sense…" (design-spec.md
 * §10.2). Mood names are capitalized for tiles, so lowercase it; all three languages allow that.
 */
@Composable
private fun rememberMoodWord(state: ResponseUiState.Ready): String =
    stringResource(state.mood.nameRes).lowercase(LocalConfiguration.current.locales[0])

/** The zone's support, plus "Maybe later" only when stress is under 8 (design-spec.md §6.6). */
@Composable
private fun OfferButtons(offer: ResponseActions.Offer, onStartSupport: () -> Unit, onLater: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(UlapSpacing.S)) {
        offer.primary.offerRes?.let { label ->
            UlapPrimaryButton(
                text = stringResource(label),
                onClick = onStartSupport,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (offer.showSecondary) {
            UlapSecondaryButton(
                text = stringResource(R.string.resp_action_later),
                onClick = onLater,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 760)
@Composable
private fun ResponseContentPreview() {
    val worried = checkNotNull(MoodCatalog.byCode("worried"))
    UlapTheme {
        ResponseContent(
            state = ResponseUiState.Ready(
                mood = worried,
                response = ResponseRules.respond(worried, stress = 6, trigger = Trigger.WORK),
            ),
            showUlap = true,
            onStartSupport = {},
            onLater = {},
        )
    }
}
