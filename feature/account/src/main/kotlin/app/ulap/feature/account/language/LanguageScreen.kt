package app.ulap.feature.account.language

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ulap.core.designsystem.component.UlapIconButton
import app.ulap.core.designsystem.component.UlapOptionCard
import app.ulap.core.designsystem.icon.UlapIcons
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.model.AppLanguage
import app.ulap.core.ui.R
import app.ulap.core.ui.greetingRes
import app.ulap.core.ui.nameRes

/** Account · Language (design-spec.md §7.8), wired to its ViewModel. */
@Composable
internal fun LanguageScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LanguageViewModel = hiltViewModel(),
) {
    val chosen by viewModel.chosen.collectAsStateWithLifecycle()
    LanguageContent(chosen = chosen, onChoose = viewModel::choose, onBack = onBack, modifier = modifier)
}

/**
 * The same three options as onboarding step 1 (design-spec.md §7.1). A tap applies the language
 * straight away, with no Save button: the screen redraws in it, which shows the choice took, and
 * Back returns to Account.
 */
@Composable
internal fun LanguageContent(
    chosen: AppLanguage?,
    onChoose: (AppLanguage) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(UlapSpacing.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(UlapSpacing.M),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(UlapSpacing.S),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UlapIconButton(onClick = onBack, contentDescription = stringResource(R.string.back)) {
                Icon(imageVector = UlapIcons.Back, contentDescription = null)
            }
            Text(
                text = stringResource(R.string.account_language),
                style = MaterialTheme.typography.headlineSmall,
                color = UlapTheme.colors.ink,
                modifier = Modifier.semantics { heading() },
            )
        }
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
private fun LanguagePreview() {
    UlapTheme {
        LanguageContent(chosen = AppLanguage.FILIPINO, onChoose = {}, onBack = {})
    }
}
