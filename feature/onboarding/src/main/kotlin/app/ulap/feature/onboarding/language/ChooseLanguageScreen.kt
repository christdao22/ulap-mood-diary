package app.ulap.feature.onboarding.language

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ulap.core.designsystem.component.UlapOptionCard
import app.ulap.core.designsystem.component.UlapPrimaryButton
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.model.AppLanguage
import app.ulap.core.ui.R
import app.ulap.core.ui.greetingRes
import app.ulap.core.ui.nameRes
import app.ulap.feature.onboarding.OnboardingStepLayout
import app.ulap.feature.onboarding.OnboardingTitle

/** Onboarding step 1 (design-spec.md §7.1), wired to its ViewModel. */
@Composable
internal fun ChooseLanguageScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChooseLanguageViewModel = hiltViewModel(),
) {
    val chosen by viewModel.chosen.collectAsStateWithLifecycle()
    ChooseLanguageContent(chosen = chosen, onChoose = viewModel::choose, onContinue = onContinue, modifier = modifier)
}

/**
 * Three large options, each a language named in itself with a greeting in it, so a person who
 * reads only Bisaya can still find theirs. Continue waits for a choice, since nothing is picked
 * for them.
 */
@Composable
internal fun ChooseLanguageContent(
    chosen: AppLanguage?,
    onChoose: (AppLanguage) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingStepLayout(
        modifier = modifier,
        actions = {
            UlapPrimaryButton(
                text = stringResource(R.string.onboarding_continue),
                onClick = onContinue,
                enabled = chosen != null,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        OnboardingTitle(stringResource(R.string.onboarding_language_title))
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(UlapSpacing.S),
        ) {
            AppLanguage.entries.forEach { language ->
                UlapOptionCard(
                    title = stringResource(language.nameRes),
                    subtitle = stringResource(language.greetingRes),
                    selected = chosen == language,
                    onClick = { onChoose(language) },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChooseLanguagePreview() {
    UlapTheme {
        ChooseLanguageContent(chosen = AppLanguage.BISAYA, onChoose = {}, onContinue = {})
    }
}
