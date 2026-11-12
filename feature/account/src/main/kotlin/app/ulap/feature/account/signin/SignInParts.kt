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

// Pieces the sign-in sheet's steps and the Account card share (design-spec.md §7.8, §7.9).

/**
 * Continue with Google above Use email instead, as on the sheet and the Account card
 * (design-spec.md §7.8, §7.9). Builds without a Web client ID hide the Google button (README §6.6).
 */
@Composable
internal fun SignInButtons(
    googleAvailable: Boolean,
    busy: Boolean,
    googleError: SignInMessage?,
    onContinueWithGoogle: () -> Unit,
    onUseEmail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(UlapSpacing.Xs)) {
        if (googleAvailable) {
            UlapGoogleButton(
                text = stringResource(R.string.signin_google),
                onClick = onContinueWithGoogle,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        UlapSecondaryButton(
            text = stringResource(R.string.signin_use_email),
            onClick = onUseEmail,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        googleError?.let { UlapFieldNotice(stringResource(it.res)) }
    }
}

@Composable
internal fun BackToSignIn(actions: SignInActions) {
    UlapQuietButton(
        text = stringResource(R.string.signin_back_to_sign_in),
        onClick = { actions.onShowStep(SignInStep.SIGN_IN) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun EmailField(state: SignInUiState, actions: SignInActions, imeAction: ImeAction) {
    UlapTextField(
        value = state.email,
        onValueChange = actions.onEmailChange,
        label = stringResource(R.string.signin_email_label),
        error = state.emailError?.let { stringResource(it.res) },
        enabled = !state.busy,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            autoCorrectEnabled = false,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = { actions.onSubmit() }),
    )
}

@Composable
internal fun StepTitle(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
        color = UlapTheme.colors.ink,
    )
}

@Composable
internal fun StepBody(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = UlapTheme.colors.ink3)
}

/** The text for each message, in the app's language (design-spec.md §10). */
@get:StringRes
internal val SignInMessage.res: Int
    get() = when (this) {
        SignInMessage.EMAIL_INVALID -> R.string.signin_error_email
        SignInMessage.PASSWORD_EMPTY -> R.string.signin_error_password_empty
        SignInMessage.PASSWORD_TOO_SHORT -> R.string.signin_error_password_short
        SignInMessage.PASSWORD_TOO_LONG -> R.string.signin_error_password_long
        SignInMessage.PASSWORD_SAME_AS_EMAIL -> R.string.signin_error_password_same_as_email
        SignInMessage.WRONG_EMAIL_OR_PASSWORD -> R.string.signin_error_wrong_credentials
        SignInMessage.EMAIL_TAKEN -> R.string.signin_error_email_taken
        SignInMessage.WEAK_PASSWORD -> R.string.signin_error_weak_password
        SignInMessage.GOOGLE_FAILED -> R.string.signin_error_google
        SignInMessage.NO_GOOGLE_ACCOUNT -> R.string.signin_error_no_google_account
        SignInMessage.OFFLINE -> R.string.signin_error_offline
        SignInMessage.TOO_MANY_TRIES -> R.string.signin_error_too_many
        SignInMessage.UPDATE_REQUIRED -> R.string.signin_error_update
        SignInMessage.SERVER -> R.string.signin_error_server
    }
