package app.ulap.feature.account.signin

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import app.ulap.core.designsystem.component.UlapFieldNotice
import app.ulap.core.designsystem.component.UlapGoogleButton
import app.ulap.core.designsystem.component.UlapPasswordField
import app.ulap.core.designsystem.component.UlapPrimaryButton
import app.ulap.core.designsystem.component.UlapQuietButton
import app.ulap.core.designsystem.component.UlapSays
import app.ulap.core.designsystem.component.UlapSecondaryButton
import app.ulap.core.designsystem.component.UlapTextField
import app.ulap.core.designsystem.component.UlapTextLink
import app.ulap.core.designsystem.mascot.UlapExpression
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.ui.R

/** The sign-in sheet (design-spec.md §7.9), shown while [SignInUiState.sheetOpen]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SignInSheet(state: SignInUiState, mascotEnabled: Boolean, actions: SignInActions) {
    ModalBottomSheet(
        onDismissRequest = actions.onDismiss,
        // Fully open: the email form and the keyboard need the room.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = UlapTheme.colors.surface,
        contentColor = UlapTheme.colors.ink,
    ) {
        SignInSheetContent(state = state, mascotEnabled = mascotEnabled, actions = actions)
    }
}

@Composable
internal fun SignInSheetContent(
    state: SignInUiState,
    mascotEnabled: Boolean,
    actions: SignInActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = UlapSpacing.ScreenPadding)
            .padding(bottom = UlapSpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(UlapSpacing.S),
    ) {
        when (state.step) {
            SignInStep.CHOOSE -> ChooseStep(state, mascotEnabled, actions)
            SignInStep.CREATE_ACCOUNT, SignInStep.SIGN_IN -> EmailStep(state, actions)
            SignInStep.FORGOT_PASSWORD -> ForgotStep(state, actions)
            SignInStep.RESET_SENT -> ResetSentStep(state, actions)
        }
    }
}

@Composable
private fun ColumnScope.ChooseStep(state: SignInUiState, mascotEnabled: Boolean, actions: SignInActions) {
    UlapSays(
        expression = UlapExpression.GENTLE,
        message = stringResource(R.string.signin_sheet_title),
        supporting = stringResource(R.string.signin_sheet_supporting),
        showUlap = mascotEnabled,
        modifier = Modifier.padding(bottom = UlapSpacing.Xs),
    )
    SignInButtons(
        googleAvailable = state.googleAvailable,
        busy = state.busy,
        googleError = state.googleError,
        onContinueWithGoogle = actions.onContinueWithGoogle,
        onUseEmail = { actions.onShowStep(SignInStep.CREATE_ACCOUNT) },
    )
}

@Composable
private fun ColumnScope.EmailStep(state: SignInUiState, actions: SignInActions) {
    val creating = state.step == SignInStep.CREATE_ACCOUNT
    StepTitle(if (creating) R.string.signin_create_title else R.string.signin_email_title)
    EmailField(state, actions, imeAction = ImeAction.Next)
    UlapPasswordField(
        value = state.password,
        onValueChange = actions.onPasswordChange,
        label = stringResource(R.string.signin_password_label),
        showLabel = stringResource(R.string.signin_show_password),
        hideLabel = stringResource(R.string.signin_hide_password),
        supporting = if (creating) stringResource(R.string.signin_password_hint) else null,
        error = state.passwordError?.let { stringResource(it.res) },
        enabled = !state.busy,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { actions.onSubmit() }),
    )
    state.formError?.let { UlapFieldNotice(stringResource(it.res)) }
    UlapPrimaryButton(
        text = stringResource(if (creating) R.string.signin_create_button else R.string.signin_sign_in_button),
        onClick = actions.onSubmit,
        enabled = !state.busy,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = UlapSpacing.Xs),
    )
    if (!creating) {
        UlapTextLink(
            text = stringResource(R.string.signin_forgot_link),
            onClick = { actions.onShowStep(SignInStep.FORGOT_PASSWORD) },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
    UlapQuietButton(
        text = stringResource(if (creating) R.string.signin_switch_to_sign_in else R.string.signin_switch_to_create),
        onClick = { actions.onShowStep(if (creating) SignInStep.SIGN_IN else SignInStep.CREATE_ACCOUNT) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ColumnScope.ForgotStep(state: SignInUiState, actions: SignInActions) {
    StepTitle(R.string.signin_forgot_title)
    StepBody(stringResource(R.string.signin_forgot_body))
    EmailField(state, actions, imeAction = ImeAction.Done)
    state.formError?.let { UlapFieldNotice(stringResource(it.res)) }
    UlapPrimaryButton(
        text = stringResource(R.string.signin_forgot_button),
        onClick = actions.onSubmit,
        enabled = !state.busy,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = UlapSpacing.Xs),
    )
    BackToSignIn(actions)
}

@Composable
private fun ColumnScope.ResetSentStep(state: SignInUiState, actions: SignInActions) {
    StepTitle(R.string.signin_forgot_sent_title)
    StepBody(stringResource(R.string.signin_forgot_sent_body, state.email.trim()))
    BackToSignIn(actions)
}

private val previewActions = SignInActions({}, {}, {}, {}, {}, {})

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun ChooseStepPreview() {
    UlapTheme {
        SignInSheetContent(SignInUiState(googleAvailable = true), mascotEnabled = true, actions = previewActions)
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun CreateAccountPreview() {
    UlapTheme {
        SignInSheetContent(
            SignInUiState(
                googleAvailable = true,
                step = SignInStep.CREATE_ACCOUNT,
                email = "ana@example.com",
                password = "short",
                passwordError = SignInMessage.PASSWORD_TOO_SHORT,
            ),
            mascotEnabled = true,
            actions = previewActions,
        )
    }
}
