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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ulap.core.designsystem.component.UlapConfirmDialog
import app.ulap.core.designsystem.component.UlapSettingsCard
import app.ulap.core.designsystem.component.UlapSettingsDivider
import app.ulap.core.designsystem.component.UlapSettingsRow
import app.ulap.core.designsystem.component.UlapSettingsSwitchRow
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.domain.account.AccountState
import app.ulap.core.model.AppLanguage
import app.ulap.core.ui.R
import app.ulap.core.ui.nameRes
import app.ulap.feature.account.signin.SignInActions
import app.ulap.feature.account.signin.SignInSheet
import app.ulap.feature.account.signin.SignInStep
import app.ulap.feature.account.signin.SignInUiState
import app.ulap.feature.account.signin.SignInViewModel
import app.ulap.feature.account.signin.requestGoogleIdToken
import kotlinx.coroutines.launch

/** The Account tab (design-spec.md §7.8), wired to its ViewModels. */
@Composable
internal fun AccountScreen(
    onOpenLanguage: () -> Unit,
    onOpenPrivacyNotice: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = hiltViewModel(),
    signInViewModel: SignInViewModel = hiltViewModel(),
) {
    val language by viewModel.language.collectAsStateWithLifecycle()
    val mascotEnabled by viewModel.mascotEnabled.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val resend by viewModel.resend.collectAsStateWithLifecycle()
    val signIn by signInViewModel.state.collectAsStateWithLifecycle()

    // Google's picker opens over this Activity (api-spec.md §6.1). The result goes to the
    // ViewModel even if the sheet was closed meanwhile.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val continueWithGoogle: () -> Unit = {
        if (signInViewModel.onGoogleStarted()) {
            scope.launch {
                signInViewModel.onGoogleResult(requestGoogleIdToken(context, signInViewModel.googleWebClientId))
            }
        }
    }

    AccountContent(
        language = language,
        mascotEnabled = mascotEnabled,
        onMascotEnabledChange = viewModel::setMascotEnabled,
        onOpenLanguage = onOpenLanguage,
        onOpenPrivacyNotice = onOpenPrivacyNotice,
        modifier = modifier,
        account = account,
        signIn = signIn,
        resend = resend,
        onContinueWithGoogle = continueWithGoogle,
        onOpenSignIn = signInViewModel::openSheet,
        onResendVerification = viewModel::resendVerification,
        onSignOut = viewModel::signOut,
    )

    if (signIn.sheetOpen) {
        SignInSheet(
            state = signIn,
            mascotEnabled = mascotEnabled,
            actions = SignInActions(
                onDismiss = signInViewModel::closeSheet,
                onContinueWithGoogle = continueWithGoogle,
                onShowStep = signInViewModel::showStep,
                onEmailChange = signInViewModel::onEmailChange,
                onPasswordChange = signInViewModel::onPasswordChange,
                onSubmit = signInViewModel::submit,
            ),
        )
    }
}

/**
 * The Account tab: the account card, then the settings list (design-spec.md §7.8). The card is
 * "Back up and sync" for a guest, "Sign in again" when the server ended the session, and the
 * profile once signed in. Sync, the hotline row and deleting data join in their own PHASES.md
 * tasks, in the §7.8 order.
 *
 * @param account Null while the saved session is read: no card yet, so nothing flashes.
 */
@Composable
internal fun AccountContent(
    language: AppLanguage?,
    mascotEnabled: Boolean,
    onMascotEnabledChange: (Boolean) -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenPrivacyNotice: () -> Unit,
    modifier: Modifier = Modifier,
    account: AccountState? = null,
    signIn: SignInUiState = SignInUiState(googleAvailable = false),
    resend: ResendState = ResendState.Idle,
    onContinueWithGoogle: () -> Unit = {},
    onOpenSignIn: (SignInStep) -> Unit = {},
    onResendVerification: () -> Unit = {},
    onSignOut: () -> Unit = {},
) {
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
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
        AccountCardFor(
            account = account,
            signIn = signIn,
            resend = resend,
            onContinueWithGoogle = onContinueWithGoogle,
            onOpenSignIn = onOpenSignIn,
            onResendVerification = onResendVerification,
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
            if (account is AccountState.SignedIn) {
                UlapSettingsDivider()
                UlapSettingsRow(title = stringResource(R.string.account_sign_out), onClick = { confirmSignOut = true })
            }
        }
    }
    if (confirmSignOut) {
        UlapConfirmDialog(
            title = stringResource(R.string.account_sign_out_title),
            text = stringResource(R.string.account_sign_out_body),
            confirmLabel = stringResource(R.string.account_sign_out_confirm),
            dismissLabel = stringResource(R.string.account_sign_out_cancel),
            onConfirm = {
                confirmSignOut = false
                onSignOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }
}

/** The card at the top of the tab, for whichever account state this phone is in (design-spec.md §7.8). */
@Composable
private fun AccountCardFor(
    account: AccountState?,
    signIn: SignInUiState,
    resend: ResendState,
    onContinueWithGoogle: () -> Unit,
    onOpenSignIn: (SignInStep) -> Unit,
    onResendVerification: () -> Unit,
) {
    when (account) {
        null -> Unit

        AccountState.Guest -> BackupCard(
            googleAvailable = signIn.googleAvailable,
            // The card's own error only while the sheet is closed; the sheet shows it otherwise.
            busy = signIn.busy,
            googleError = signIn.googleError.takeUnless { signIn.sheetOpen },
            onContinueWithGoogle = onContinueWithGoogle,
            onUseEmail = { onOpenSignIn(SignInStep.CREATE_ACCOUNT) },
        )

        AccountState.SignInAgain -> SignInAgainCard(onSignIn = { onOpenSignIn(SignInStep.CHOOSE) })

        is AccountState.SignedIn -> ProfileCard(
            profile = account.profile,
            resend = resend,
            onResendVerification = onResendVerification,
        )
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
            account = AccountState.Guest,
            signIn = SignInUiState(googleAvailable = true),
        )
    }
}
