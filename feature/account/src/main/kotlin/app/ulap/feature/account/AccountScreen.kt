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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import app.ulap.core.domain.sync.SyncStatus
import app.ulap.core.model.AppLanguage
import app.ulap.core.ui.HotlineSettingsRow
import app.ulap.core.ui.R
import app.ulap.core.ui.nameRes
import app.ulap.feature.account.signin.SignInActions
import app.ulap.feature.account.signin.SignInSheet
import app.ulap.feature.account.signin.SignInStep
import app.ulap.feature.account.signin.SignInUiState
import app.ulap.feature.account.signin.SignInViewModel
import app.ulap.feature.account.signin.requestGoogleIdToken
import kotlinx.coroutines.launch

/**
 * The Account tab (design-spec.md §7.8), wired to its ViewModels.
 *
 * @param openSignInStep Opens the sign-in sheet at this step, for example after a password reset
 *   link. [onClearSignInStep] then clears the request, so it opens only once.
 */
@Composable
internal fun AccountScreen(
    onOpenLanguage: () -> Unit,
    onOpenPrivacyNotice: () -> Unit,
    modifier: Modifier = Modifier,
    openSignInStep: SignInStep? = null,
    onClearSignInStep: () -> Unit = {},
    viewModel: AccountViewModel = hiltViewModel(),
    signInViewModel: SignInViewModel = hiltViewModel(),
) {
    val language by viewModel.language.collectAsStateWithLifecycle()
    val mascotEnabled by viewModel.mascotEnabled.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val resend by viewModel.resend.collectAsStateWithLifecycle()
    val delete by viewModel.delete.collectAsStateWithLifecycle()
    val sync by viewModel.sync.collectAsStateWithLifecycle()
    val signIn by signInViewModel.state.collectAsStateWithLifecycle()
    val currentOnClearSignInStep by rememberUpdatedState(onClearSignInStep)
    LaunchedEffect(openSignInStep) {
        if (openSignInStep != null) {
            signInViewModel.openSheet(openSignInStep)
            currentOnClearSignInStep()
        }
    }

    val continueWithGoogle = rememberContinueWithGoogle(signInViewModel)

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
        delete = delete,
        onContinueWithGoogle = continueWithGoogle,
        onOpenSignIn = signInViewModel::openSheet,
        onResendVerification = viewModel::resendVerification,
        onSignOut = viewModel::signOut,
        onDeleteEverything = viewModel::deleteEverything,
        sync = sync,
        syncActions = SyncActions(viewModel::syncNow, viewModel::retryStuck, viewModel::answerAddCheckIns),
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
 * profile once signed in, with the sync row under it once the email is verified. The NCMH hotline
 * row is always there (technical-plan.md §13), and "Delete account and all data" is always last.
 * Under the list, the self-care disclaimer (technical-plan.md §13).
 *
 * @param account Null while the saved session is read: no card yet, so nothing flashes.
 * @param sync Null until read. Shown only signed in, and it asks "Add this phone's check-ins?"
 *   when [SyncStatus.askToAddCheckIns].
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
    delete: DeleteState = DeleteState.Idle,
    onContinueWithGoogle: () -> Unit = {},
    onOpenSignIn: (SignInStep) -> Unit = {},
    onResendVerification: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onDeleteEverything: (withBackup: Boolean) -> Unit = {},
    sync: SyncStatus? = null,
    syncActions: SyncActions = SyncActions(),
) {
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
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
        SyncSection(account = account, sync = sync, actions = syncActions)
        SettingsList(
            language = language,
            mascotEnabled = mascotEnabled,
            onMascotEnabledChange = onMascotEnabledChange,
            onOpenLanguage = onOpenLanguage,
            onOpenPrivacyNotice = onOpenPrivacyNotice,
            signedIn = account is AccountState.SignedIn,
            delete = delete,
            onSignOut = { confirmSignOut = true },
            onDelete = { confirmDelete = true },
        )
        DeleteFailedNotice(delete)
        DisclaimerFooter()
    }
    if (confirmSignOut) {
        SignOutDialog(
            onConfirm = {
                confirmSignOut = false
                onSignOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }
    if (confirmDelete) {
        DeleteDataDialog(
            account = account,
            onConfirm = { withBackup ->
                confirmDelete = false
                onDeleteEverything(withBackup)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/**
 * Google's picker opens over this Activity (api-spec.md §6.1). The result goes to the ViewModel
 * even if the sheet was closed meanwhile.
 */
@Composable
private fun rememberContinueWithGoogle(signInViewModel: SignInViewModel): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return {
        if (signInViewModel.onGoogleStarted()) {
            scope.launch {
                signInViewModel.onGoogleResult(requestGoogleIdToken(context, signInViewModel.googleWebClientId))
            }
        }
    }
}

/** What the sync row and the "Add this phone's check-ins?" question can ask for. */
internal data class SyncActions(
    val onSyncNow: () -> Unit = {},
    val onTryAgain: () -> Unit = {},
    val onAnswerAddCheckIns: (add: Boolean) -> Unit = {},
)

/**
 * Signed in only. The sync row waits for the verified email, which the profile card asks for.
 * The question shows until it's answered; closing it without an answer (not saved) puts it off
 * until Account opens again, and nothing syncs meanwhile.
 */
@Composable
private fun SyncSection(account: AccountState?, sync: SyncStatus?, actions: SyncActions) {
    val signedIn = account as? AccountState.SignedIn ?: return
    if (sync == null) return
    var addLater by remember { mutableStateOf(false) }
    if (signedIn.profile.emailVerified) {
        SyncCard(status = sync, onSyncNow = actions.onSyncNow, onTryAgain = actions.onTryAgain)
    }
    if (sync.askToAddCheckIns && !addLater) {
        AddCheckInsDialog(onAnswer = actions.onAnswerAddCheckIns, onLater = { addLater = true })
    }
}

/** "Ulap is a self-care diary, not a medical or emergency service." (technical-plan.md §13) */
@Composable
private fun DisclaimerFooter() {
    Text(
        text = stringResource(R.string.app_disclaimer),
        modifier = Modifier.padding(horizontal = UlapSpacing.S),
        style = MaterialTheme.typography.bodySmall,
        color = UlapTheme.colors.ink2,
    )
}

/** Check-ins stay on this phone, and the dialog says so (design-spec.md §7.8). */
@Composable
private fun SignOutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    UlapConfirmDialog(
        title = stringResource(R.string.account_sign_out_title),
        text = stringResource(R.string.account_sign_out_body),
        confirmLabel = stringResource(R.string.account_sign_out_confirm),
        dismissLabel = stringResource(R.string.account_sign_out_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/**
 * The settings list, in the design-spec.md §7.8 order. [onSignOut] and [onDelete] only open their
 * confirm dialogs.
 */
@Composable
private fun SettingsList(
    language: AppLanguage?,
    mascotEnabled: Boolean,
    onMascotEnabledChange: (Boolean) -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenPrivacyNotice: () -> Unit,
    signedIn: Boolean,
    delete: DeleteState,
    onSignOut: () -> Unit,
    onDelete: () -> Unit,
) {
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
        UlapSettingsDivider()
        HotlineSettingsRow()
        if (signedIn) {
            UlapSettingsDivider()
            UlapSettingsRow(title = stringResource(R.string.account_sign_out), onClick = onSignOut)
        }
        UlapSettingsDivider()
        DeleteDataRow(delete = delete, onClick = onDelete)
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
