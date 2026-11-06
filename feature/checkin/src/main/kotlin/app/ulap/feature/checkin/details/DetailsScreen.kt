package app.ulap.feature.checkin.details

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ulap.core.designsystem.component.LevelSlider
import app.ulap.core.designsystem.component.TriggerChip
import app.ulap.core.designsystem.component.UlapIconButton
import app.ulap.core.designsystem.component.UlapPrimaryButton
import app.ulap.core.designsystem.component.UlapTextLink
import app.ulap.core.designsystem.icon.UlapIcons
import app.ulap.core.designsystem.mascot.Ulap
import app.ulap.core.designsystem.mascot.UlapSize
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.ui.R
import app.ulap.core.ui.journalPromptRes
import app.ulap.core.ui.mirrorLineRes
import app.ulap.core.ui.nameRes
import app.ulap.core.ui.toMascot
import app.ulap.core.ui.wordRes
import app.ulap.shared.catalog.MoodCatalog
import app.ulap.shared.model.Trigger
import app.ulap.shared.rules.EnergyLabels
import app.ulap.shared.rules.LEVEL_RANGE
import app.ulap.shared.rules.ResponseRules
import app.ulap.shared.rules.StressLabels
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/** Check in · Details (design-spec.md §7.3), wired to its ViewModel. */
@Composable
internal fun DetailsScreen(
    onBack: () -> Unit,
    onCheckInSave: (checkInId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailsViewModel = hiltViewModel(),
) {
    val showUlap by viewModel.showUlap.collectAsStateWithLifecycle()
    val form = viewModel.form
    val currentOnCheckInSave by rememberUpdatedState(onCheckInSave)
    LaunchedEffect(form.savedId) { form.savedId?.let { currentOnCheckInSave(it) } }
    // The content sees only the actions, never the whole ViewModel.
    val actions: DetailsActions = viewModel

    DetailsContent(
        form = form,
        showUlap = showUlap,
        onBack = onBack,
        actions = actions,
        modifier = modifier,
    )
}

/**
 * Back and the time, the mood header, the two sliders, the trigger chips, the journal and Save.
 * The whole screen scrolls, and lifts above the keyboard while the journal is open.
 */
@Composable
internal fun DetailsContent(
    form: DetailsForm,
    showUlap: Boolean,
    onBack: () -> Unit,
    actions: DetailsActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(UlapSpacing.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(UlapSpacing.CardGap),
    ) {
        TopRow(loggedAt = form.loggedAt, onBack = onBack)
        MoodHeader(form = form, showUlap = showUlap)
        Levels(form = form, actions = actions)
        Triggers(form = form, actions = actions)
        Journal(form = form, onNoteChange = actions::setNote)
        UlapPrimaryButton(
            text = stringResource(R.string.details_save),
            onClick = actions::save,
            enabled = !form.isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TopRow(loggedAt: Instant, onBack: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(UlapSpacing.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UlapIconButton(onClick = onBack, contentDescription = stringResource(R.string.details_back)) {
            Icon(imageVector = UlapIcons.Back, contentDescription = null)
        }
        Text(
            text = rememberLoggedAtText(loggedAt),
            style = MaterialTheme.typography.bodyMedium,
            color = UlapTheme.colors.ink2,
        )
    }
}

/**
 * "Wednesday, Nov 4 · 4:49 PM" in the app's language, on the phone's clock: 12- or 24-hour as the
 * person set it. The patterns come from the locale, so Filipino and Bisaya get their own order.
 */
@Composable
private fun rememberLoggedAtText(loggedAt: Instant): String {
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val (date, time) = remember(loggedAt, locale, is24Hour) {
        val local = loggedAt.toJavaInstant().atZone(ZoneId.systemDefault())
        val datePattern = DateFormat.getBestDateTimePattern(locale, "EEEEMMMd")
        val timePattern = DateFormat.getBestDateTimePattern(locale, if (is24Hour) "Hm" else "hma")
        DateTimeFormatter.ofPattern(datePattern, locale).format(local) to
            DateTimeFormatter.ofPattern(timePattern, locale).format(local)
    }
    return stringResource(R.string.details_logged_at, date, time)
}

/** The zone-tinted card: Ulap, the zone name, the mood name and Ulap's soft-mirror line. */
@Composable
private fun MoodHeader(form: DetailsForm, showUlap: Boolean) {
    val colors = UlapTheme.colors
    val zone = form.mood.zone
    val locale = LocalConfiguration.current.locales[0]
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.zone(zone).tint,
        contentColor = colors.ink,
    ) {
        Row(
            modifier = Modifier.padding(UlapSpacing.CardPadding),
            horizontalArrangement = Arrangement.spacedBy(UlapSpacing.S),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Decorative: the text beside Ulap already says it (design-spec.md §11).
            if (showUlap) {
                Ulap(
                    expression = ResponseRules.expression(form.mood).toMascot(),
                    contentDescription = null,
                    size = UlapSize.DetailsHeader,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(UlapSpacing.Xxs)) {
                Text(
                    // Uppercased here, not by the style, so TalkBack reads the word (Type.kt).
                    text = stringResource(zone.nameRes).uppercase(locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.zone(zone).text,
                )
                Text(
                    text = stringResource(form.mood.nameRes),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(zone.mirrorLineRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.mutedOnTint,
                )
            }
        }
    }
}

/** Energy, then stress, then "Default for Worried: energy 6, stress 8 · Reset to default" if changed. */
@Composable
private fun Levels(form: DetailsForm, actions: DetailsActions) {
    DetailsCard {
        val zone = form.mood.zone
        val max = LEVEL_RANGE.last
        val energyWord = stringResource(EnergyLabels.of(form.energy).wordRes)
        LevelSlider(
            label = stringResource(R.string.slider_energy),
            value = form.energy,
            onValueChange = actions::setEnergy,
            levelWord = energyWord,
            startLabel = stringResource(R.string.slider_energy_start),
            endLabel = stringResource(R.string.slider_energy_end),
            stateDescription = stringResource(R.string.slider_state, form.energy, max, energyWord),
            zone = zone,
        )
        val stressWord = stringResource(StressLabels.of(form.stress).wordRes)
        LevelSlider(
            label = stringResource(R.string.slider_stress),
            value = form.stress,
            onValueChange = actions::setStress,
            levelWord = stressWord,
            startLabel = stringResource(R.string.slider_stress_start),
            endLabel = stringResource(R.string.slider_stress_end),
            stateDescription = stringResource(R.string.slider_state, form.stress, max, stressWord),
            zone = zone,
        )
        if (form.changedFromDefaults) {
            FlowRow(itemVerticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        R.string.details_defaults,
                        stringResource(form.mood.nameRes),
                        form.mood.defaultEnergy,
                        form.mood.defaultStress,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = UlapTheme.colors.muted,
                )
                UlapTextLink(text = stringResource(R.string.details_reset), onClick = actions::resetToDefaults)
            }
        }
    }
}

/** "What triggered it?": single-select chips, plus a short line in the person's words for Other. */
@Composable
private fun Triggers(form: DetailsForm, actions: DetailsActions) {
    DetailsCard {
        Text(
            text = stringResource(R.string.details_trigger_question),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(UlapSpacing.Xs),
            verticalArrangement = Arrangement.spacedBy(UlapSpacing.Xs),
        ) {
            Trigger.entries.forEach { trigger ->
                TriggerChip(
                    label = stringResource(trigger.nameRes),
                    selected = form.trigger == trigger,
                    onClick = { actions.toggleTrigger(trigger) },
                )
            }
        }
        if (form.trigger == Trigger.OTHER) {
            OutlinedTextField(
                value = form.triggerOther,
                onValueChange = actions::setTriggerOther,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.details_trigger_other_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = MaterialTheme.shapes.medium,
                colors = ulapTextFieldColors(),
            )
        }
    }
}

/** "Journal · optional": four lines to start, growing as the person writes. */
@Composable
private fun Journal(form: DetailsForm, onNoteChange: (String) -> Unit) {
    val label = stringResource(R.string.details_journal_label)
    DetailsCard {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = form.note,
            onValueChange = onNoteChange,
            modifier = Modifier
                .fillMaxWidth()
                // The visible label is outside the field, so give TalkBack the field's name.
                .semantics { this.contentDescription = label },
            placeholder = { Text(stringResource(form.mood.zone.journalPromptRes)) },
            minLines = JOURNAL_MIN_LINES,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = MaterialTheme.shapes.medium,
            colors = ulapTextFieldColors(),
        )
    }
}

private const val JOURNAL_MIN_LINES = 4

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F8, widthDp = 360, heightDp = 1100)
@Composable
private fun DetailsContentPreview() {
    UlapTheme {
        DetailsContent(
            form = DetailsForm(
                mood = checkNotNull(MoodCatalog.byCode("worried")),
                loggedAt = Instant.parse("2026-11-04T08:49:00Z"),
                stress = 9,
                trigger = Trigger.WORK,
            ),
            showUlap = true,
            onBack = {},
            actions = NoDetailsActions,
        )
    }
}

private object NoDetailsActions : DetailsActions {
    override fun setEnergy(energy: Int) = Unit

    override fun setStress(stress: Int) = Unit

    override fun resetToDefaults() = Unit

    override fun toggleTrigger(trigger: Trigger) = Unit

    override fun setTriggerOther(text: String) = Unit

    override fun setNote(text: String) = Unit

    override fun save() = Unit
}
