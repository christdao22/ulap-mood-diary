package app.ulap.feature.account.link

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ulap.core.designsystem.component.UlapFieldNotice
import app.ulap.core.designsystem.component.UlapIconButton
import app.ulap.core.designsystem.component.UlapPasswordField
import app.ulap.core.designsystem.component.UlapPrimaryButton
import app.ulap.core.designsystem.component.UlapQuietButton
import app.ulap.core.designsystem.icon.UlapIcons
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.ui.R
import app.ulap.feature.account.signin.StepBody
import app.ulap.feature.account.signin.StepTitle
import app.ulap.feature.account.signin.res

/** What the verification link opens (api-spec.md §6.5), wired to its ViewModel. */
@Composable
internal fun VerifyEmailScreen(
    onBack: () -> Unit,
    onGoToAccount: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VerifyEmailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    VerifyEmailContent(
        state = state,
        onRetry = viewModel::retry,
        onGoToAccount = onGoToAccount,
        onBack = onBack,
        modifier = modifier,
    )
}

/** Checking, then one plain answer. Every answer has a way on. */
@Composable
internal fun VerifyEmailContent(
    state: VerifyState,
    onRetry: () -> Unit,
    onGoToAccount: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LinkScreen(onBack = onBack, modifier = modifier) {
        when (state) {
            VerifyState.Checking -> StepTitle(R.string.link_checking)

            VerifyState.Verified -> {
                StepTitle(R.string.verify_done_title)
                StepBody(stringResource(R.string.verify_done_body))
                WideButton(R.string.link_go_to_account, onGoToAccount)
            }

            VerifyState.Expired -> {
                StepTitle(R.string.link_expired_title)
                StepBody(stringResource(R.string.verify_expired_body))
                WideButton(R.string.link_go_to_account, onGoToAccount)
            }

            is VerifyState.Failed -> {
                StepTitle(R.string.verify_failed_title)
                UlapFieldNotice(stringResource(state.message.res))
                WideButton(R.string.link_try_again, onRetry)
                UlapQuietButton(
                    text = stringResource(R.string.link_not_now),
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** What the reset link opens (api-spec.md §6.8), wired to its ViewModel. */
@Composable
internal fun ResetPasswordScreen(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onAskForNewLink: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ResetPasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ResetPasswordContent(
        state = state,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::submit,
        onSignIn = onSignIn,
        onAskForNewLink = onAskForNewLink,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * One password field, with the same hint and errors as creating an account (design-spec.md
 * §7.9). It says up front that saving signs out every phone, so that doesn't come as a surprise.
 */
@Composable
internal fun ResetPasswordContent(
    state: ResetUiState,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSignIn: () -> Unit,
    onAskForNewLink: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LinkScreen(onBack = onBack, modifier = modifier) {
        when (state.step) {
            ResetStep.FORM -> {
                StepTitle(R.string.reset_title)
                StepBody(stringResource(R.string.reset_body))
                UlapPasswordField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = stringResource(R.string.reset_password_label),
                    showLabel = stringResource(R.string.signin_show_password),
                    hideLabel = stringResource(R.string.signin_hide_password),
                    supporting = stringResource(R.string.signin_password_hint),
                    error = state.passwordError?.let { stringResource(it.res) },
                    enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                )
                state.formError?.let { UlapFieldNotice(stringResource(it.res)) }
                WideButton(R.string.reset_button, onSubmit, enabled = !state.busy)
            }

            ResetStep.DONE -> {
                StepTitle(R.string.reset_done_title)
                StepBody(stringResource(R.string.reset_done_body))
                WideButton(R.string.signin_sign_in_button, onSignIn)
            }

            ResetStep.EXPIRED -> {
                StepTitle(R.string.link_expired_title)
                StepBody(stringResource(R.string.reset_expired_body))
                WideButton(R.string.reset_new_link, onAskForNewLink)
            }
        }
    }
}

/** A back button, then the screen's own content, scrolling at large font sizes. */
@Composable
private fun LinkScreen(onBack: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(UlapSpacing.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(UlapSpacing.M),
    ) {
        UlapIconButton(onClick = onBack, contentDescription = stringResource(R.string.back)) {
            Icon(imageVector = UlapIcons.Back, contentDescription = null)
        }
        content()
    }
}

@Composable
private fun WideButton(@StringRes title: Int, onClick: () -> Unit, enabled: Boolean = true) {
    UlapPrimaryButton(
        text = stringResource(title),
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = UlapSpacing.Xs),
    )
}

@Preview(showBackground = true)
@Composable
private fun ResetPasswordPreview() {
    UlapTheme {
        ResetPasswordContent(
            state = ResetUiState(password = "kalma"),
            onPasswordChange = {},
            onSubmit = {},
            onSignIn = {},
            onAskForNewLink = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VerifyEmailPreview() {
    UlapTheme {
        VerifyEmailContent(state = VerifyState.Verified, onRetry = {}, onGoToAccount = {}, onBack = {})
    }
}
