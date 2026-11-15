package app.ulap.feature.account.link

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ulap.core.domain.account.AccountRepository
import app.ulap.core.domain.account.AuthProblem
import app.ulap.feature.account.signin.SignInMessage
import app.ulap.feature.account.signin.toMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The verification link's screen (api-spec.md §6.5). */
internal sealed interface VerifyState {
    data object Checking : VerifyState

    data object Verified : VerifyState

    /** Used already, or older than 24 hours. */
    data object Expired : VerifyState

    /** Offline, or the server couldn't answer. The link still works, so it can try again. */
    data class Failed(val message: SignInMessage) : VerifyState
}

/**
 * Sends the link's token as soon as the screen opens: opening the link is the person's "yes".
 * The token comes from the link (VerifyEmailRoute) and is never shown or logged.
 */
@HiltViewModel
class VerifyEmailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val accounts: AccountRepository,
) : ViewModel() {
    private val token: String = savedStateHandle.get<String>(TOKEN_ARG).orEmpty()

    // Remembered across process death: asking again would spend nothing but answer "expired".
    private val _state = MutableStateFlow<VerifyState>(
        if (savedStateHandle.get<Boolean>(VERIFIED_KEY) == true) VerifyState.Verified else VerifyState.Checking,
    )
    internal val state: StateFlow<VerifyState> = _state.asStateFlow()

    init {
        if (_state.value == VerifyState.Checking) verify()
    }

    fun retry() {
        if (_state.value is VerifyState.Failed) verify()
    }

    private fun verify() {
        if (token.isBlank()) {
            _state.value = VerifyState.Expired
            return
        }
        _state.value = VerifyState.Checking
        viewModelScope.launch {
            _state.value = when (val problem = accounts.verifyEmail(token)) {
                null -> {
                    savedStateHandle[VERIFIED_KEY] = true
                    VerifyState.Verified
                }

                AuthProblem.LINK_USED_OR_EXPIRED -> VerifyState.Expired

                else -> VerifyState.Failed(problem.toMessage())
            }
        }
    }

    internal companion object {
        /** The `token` property of VerifyEmailRoute and ResetPasswordRoute, which navigation keeps under its name. */
        const val TOKEN_ARG = "token"
        private const val VERIFIED_KEY = "verified"
    }
}
