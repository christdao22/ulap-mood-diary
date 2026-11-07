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
import app.ulap.core.designsystem.component.UlapQuietButton
import app.ulap.core.designsystem.component.UlapSecondaryButton
import app.ulap.core.designsystem.mascot.Ulap
import app.ulap.core.designsystem.mascot.UlapSize
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.ui.HotlineCard
import app.ulap.core.ui.R
import app.ulap.core.ui.bodyRes
import app.ulap.core.ui.nameRes
import app.ulap.core.ui.offerRes
import app.ulap.core.ui.responseLineRes
import app.ulap.core.ui.titleRes
import app.ulap.core.ui.toMascot
import app.ulap.shared.catalog.MoodCatalog
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import app.ulap.shared.rules.ResponseActions
import app.ulap.shared.rules.ResponseRules

/** Check in · Response (design-spec.md §7.4), wired to its ViewModel. */
@Composable
internal fun ResponseScreen(
    onDone: () -> Unit,
    onStressCheckSave: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ResponseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val showUlap by viewModel.showUlap.collectAsStateWithLifecycle()
    val currentOnDone by rememberUpdatedState(onDone)
    val currentOnStressCheckSave by rememberUpdatedState(onStressCheckSave)
    LaunchedEffect(state) { if (state == ResponseUiState.Missing) currentOnDone() }
    val stressCheckSaved = (state as? ResponseUiState.Ready)?.stressCheckSaved == true
    LaunchedEffect(stressCheckSaved) { if (stressCheckSaved) currentOnStressCheckSave() }

    // Loading is a single read from the phone's database, too quick to need a spinner.
    (state as? ResponseUiState.Ready)?.let { ready ->
        ResponseContent(
            state = ready,
            showUlap = showUlap,
            actions = ResponseScreenActions(
                onStartSupport = viewModel::startSupport,
                onFinish = onDone,
                onStressNowChange = viewModel::setStressNow,
                onStressCheckSave = viewModel::saveStressCheck,
            ),
            modifier = modifier,
        )
    }
}

/** What the person can do on the Response screen. One class, so the content's signature stays short. */
internal data class ResponseScreenActions(
    val onStartSupport: () -> Unit,
    /** "Maybe later" and Done: the check-in ends. */
    val onFinish: () -> Unit,
    val onStressNowChange: (Int) -> Unit,
    val onStressCheckSave: () -> Unit,
)

/**
 * The zone's tint behind everything; Ulap, the title, the body and the trigger line, centered;
 * then what ResponseRules offers. Only the rules decide which buttons show (technical-plan.md §5).
 */
@Composable
internal fun ResponseContent(
    state: ResponseUiState.Ready,
    showUlap: Boolean,
    actions: ResponseScreenActions,
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
        ResponseActionsArea(state = state, actions = actions)
    }
}

/** What ResponseRules offers, then the stress check for Red and Blue (design-spec.md §7.4). */
@Composable
private fun ResponseActionsArea(state: ResponseUiState.Ready, actions: ResponseScreenActions) {
    when (val offered = state.response.actions) {
        // Hopeless (🔒): the NCMH 1553 card in place of activity buttons. It only opens the dialer,
        // so it works offline (technical-plan.md §13).
        ResponseActions.Hotline -> HotlineCard()

        // After the tap, the support card takes the buttons' place (design-spec.md §7.4).
        is ResponseActions.Offer -> when (val support = state.startedSupport) {
            null -> OfferButtons(
                offer = offered,
                onStartSupport = actions.onStartSupport,
                onLater = actions.onFinish,
            )

            else -> SupportCard(support = support, onFinish = actions.onFinish)
        }
    }
    // Red and Blue, Hopeless included (§7.4). At stress 8 or more it waits for the support to start.
    if (state.showsStressCheck) {
        StressCheckCard(
            zone = state.mood.zone,
            stress = state.stressNow,
            onStressChange = actions.onStressNowChange,
            onCheckIn = actions.onStressCheckSave,
        )
    }
    // Hopeless has no "Maybe later", so a quiet Done gives a way out that isn't Back: nobody should
    // feel stuck on this screen, and the stress check stays optional (design-spec.md §7.4).
    if (state.response.actions == ResponseActions.Hotline) {
        UlapSecondaryButton(
            text = stringResource(R.string.resp_action_done),
            onClick = actions.onFinish,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The person's own mood word inside the body, e.g. "Feeling worried makes sense…" (design-spec.md
 * §10.2). Mood names are capitalized for tiles, so lowercase it; all three languages allow that.
 */
@Composable
private fun rememberMoodWord(state: ResponseUiState.Ready): String =
    stringResource(state.mood.nameRes).lowercase(LocalConfiguration.current.locales[0])

/** The zone's support, then a quiet "Maybe later" under Breathe at any stress, or an outlined one under stress 8 (§6.6, §7.4). */
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
        if (offer.primary == SupportType.BREATHE) {
            // A quiet, borderless way out for anyone who doesn't want the breathing right now.
            // It shows at any stress, borderless instead of outlined (design-spec.md §7.4).
            UlapQuietButton(
                text = stringResource(R.string.resp_action_later),
                onClick = onLater,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (offer.showSecondary) {
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
                stressNow = 6,
            ),
            showUlap = true,
            actions = ResponseScreenActions(onStartSupport = {
            }, onFinish = {}, onStressNowChange = {}, onStressCheckSave = {}),
        )
    }
}

@Preview(widthDp = 360, heightDp = 1000)
@Composable
private fun ResponseContentHopelessPreview() {
    val hopeless = checkNotNull(MoodCatalog.byCode(MoodCatalog.HOPELESS))
    UlapTheme {
        ResponseContent(
            state = ResponseUiState.Ready(
                mood = hopeless,
                response = ResponseRules.respond(hopeless, stress = 8, trigger = null),
                stressNow = 8,
            ),
            showUlap = true,
            actions = ResponseScreenActions(onStartSupport = {
            }, onFinish = {}, onStressNowChange = {}, onStressCheckSave = {}),
        )
    }
}
