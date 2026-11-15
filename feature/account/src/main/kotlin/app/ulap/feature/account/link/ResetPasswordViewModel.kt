package app.ulap.feature.account.link

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ulap.core.domain.account.AccountRepository
import app.ulap.core.domain.account.AuthProblem
import app.ulap.feature.account.signin.SignInMessage
import app.ulap.feature.account.signin.toMessage
import app.ulap.shared.validation.PasswordRules
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class ResetStep {
    FORM,
    DONE,

    /** Used already, or older than 30 minutes (api-spec.md §6.7). */
    EXPIRED,
}

/**
 * The reset link's screen (api-spec.md §6.8). [password] lives only here, in memory, and is
 * dropped once the reset ends. It isn't saved with the screen's state, so it never reaches disk.
 */
internal data class ResetUiState(
    val step: ResetStep = ResetStep.FORM,
    val password: String = "",
    val passwordError: SignInMessage? = null,
    /** Not about the password: offline, the server, too many tries. */
    val formError: SignInMessage? = null,
    val busy: Boolean = false,
) {
    override fun toString(): String = "ResetUiState(step=$step, busy=$busy)"
}

/**
 * Sets a new password with the link's token. The password gets the same checks as creating an
 * account (PasswordRules), before anything is sent; a weak one keeps the link usable (§6.8).
 */
@HiltViewModel
class ResetPasswordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accounts: AccountRepository,
) : ViewModel() {
    private val token: String = savedStateHandle.get<String>(VerifyEmailViewModel.TOKEN_ARG).orEmpty()

    private val _state =
        MutableStateFlow(ResetUiState(step = if (token.isBlank()) ResetStep.EXPIRED else ResetStep.FORM))
    internal val state: StateFlow<ResetUiState> = _state.asStateFlow()

    fun onPasswordChange(password: String) {
        _state.update { it.copy(password = password, passwordError = null, formError = null) }
    }

    fun submit() {
        val current = _state.value
        if (current.busy || current.step != ResetStep.FORM) return
        PasswordRules.check(current.password)?.let { problem ->
            _state.update { it.copy(passwordError = problem.toMessage()) }
            return
        }
        _state.update { it.copy(busy = true, formError = null) }
        viewModelScope.launch {
            val problem = accounts.resetPassword(token, current.password)
            _state.update { it.after(problem) }
        }
    }
}

private fun ResetUiState.after(problem: AuthProblem?): ResetUiState = when (problem) {
    null -> ResetUiState(step = ResetStep.DONE)
    AuthProblem.LINK_USED_OR_EXPIRED -> ResetUiState(step = ResetStep.EXPIRED)
    AuthProblem.WEAK_PASSWORD -> copy(busy = false, passwordError = SignInMessage.WEAK_PASSWORD)
    else -> copy(busy = false, formError = problem.toMessage())
}
