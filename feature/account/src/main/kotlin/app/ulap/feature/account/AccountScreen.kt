package app.ulap.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ulap.core.designsystem.component.UlapSettingsCard
import app.ulap.core.designsystem.component.UlapSettingsDivider
import app.ulap.core.designsystem.component.UlapSettingsRow
import app.ulap.core.designsystem.component.UlapSettingsSwitchRow
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.model.AppLanguage
import app.ulap.core.ui.R
import app.ulap.core.ui.nameRes

/** The Account tab (design-spec.md §7.8), wired to its ViewModel. */
@Composable
internal fun AccountScreen(
    onOpenLanguage: () -> Unit,
    onOpenPrivacyNotice: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val language by viewModel.language.collectAsStateWithLifecycle()
    val mascotEnabled by viewModel.mascotEnabled.collectAsStateWithLifecycle()
    AccountContent(
        language = language,
        mascotEnabled = mascotEnabled,
        onMascotEnabledChange = viewModel::setMascotEnabled,
        onOpenLanguage = onOpenLanguage,
        onOpenPrivacyNotice = onOpenPrivacyNotice,
        modifier = modifier,
    )
}

/**
 * The settings list, for now only the rows that work without an account: the Ulap switch, Language
 * and the privacy notice. The profile card, sync, hotline row and deleting data join it in their
 * own PHASES.md tasks, in the §7.8 order.
 */
@Composable
internal fun AccountContent(
    language: AppLanguage?,
    mascotEnabled: Boolean,
    onMascotEnabledChange: (Boolean) -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenPrivacyNotice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(UlapSpacing.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(UlapSpacing.CardGap),
    ) {
        Text(
            text = stringResource(R.string.tab_account),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMedium,
            color = UlapTheme.colors.ink,
        )
        UlapSettingsCard {
            UlapSettingsSwitchRow(
                title = stringResource(R.string.account_show_ulap),
                checked = mascotEnabled,
                onCheckedChange = onMascotEnabledChange,
            )
            UlapSettingsDivider()
            UlapSettingsRow(
                title = stringResource(R.string.account_language),
                value = language?.let { stringResource(it.nameRes) },
                onClick = onOpenLanguage,
            )
            UlapSettingsDivider()
            UlapSettingsRow(title = stringResource(R.string.privacy_title), onClick = onOpenPrivacyNotice)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AccountPreview() {
    UlapTheme {
        AccountContent(
            language = AppLanguage.BISAYA,
            mascotEnabled = true,
            onMascotEnabledChange = {},
            onOpenLanguage = {},
            onOpenPrivacyNotice = {},
        )
    }
}
