package app.ulap.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ulap.core.domain.account.AccountRepository
import app.ulap.core.domain.account.AccountState
import app.ulap.core.domain.account.AuthProblem
import app.ulap.core.domain.settings.SettingsRepository
import app.ulap.core.model.AppLanguage
import app.ulap.core.model.UserSettings
import app.ulap.feature.account.signin.SignInMessage
import app.ulap.feature.account.signin.toMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Sending the verification email again (api-spec.md §6.6). */
internal sealed interface ResendState {
    data object Idle : ResendState

    data object Sending : ResendState

    data object Sent : ResendState

    data class Failed(val message: SignInMessage) : ResendState
}

/** "Delete account and all data" (api-spec.md §7.3). After it works, the app leaves this tab. */
internal sealed interface DeleteState {
    data object Idle : DeleteState

    data object Deleting : DeleteState

    data class Failed(val message: DeleteMessage) : DeleteState
}

/** Why nothing was deleted. Each one says so, so the person never wonders what's left. */
internal enum class DeleteMessage { OFFLINE, SIGN_IN_AGAIN, TOO_MANY_TRIES, UPDATE_REQUIRED, SERVER }

/**
 * The Account tab (design-spec.md §7.8): the account card and the settings list. Everything here
 * is read from this phone (the saved session and settings), so the tab works offline.
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val accounts: AccountRepository,
) : ViewModel() {
    /** The chosen language, shown on the Language row. Null if none was ever chosen. */
    val language: StateFlow<AppLanguage?> = settingsRepository.settings
        .map { it.language }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Whether Ulap the cloud shows. Off hides only the character, never its messages (design-spec.md §8.5). */
    val mascotEnabled: StateFlow<Boolean> = settingsRepository.settings
        .map { it.mascotEnabled }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            UserSettings().mascotEnabled,
        )

    /** Null until the saved session has been read, so a signed-in person never sees the guest card flash. */
    val account: StateFlow<AccountState?> = accounts.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private val _resend = MutableStateFlow<ResendState>(ResendState.Idle)
    internal val resend: StateFlow<ResendState> = _resend.asStateFlow()

    private val _delete = MutableStateFlow<DeleteState>(DeleteState.Idle)
    internal val delete: StateFlow<DeleteState> = _delete.asStateFlow()

    fun setMascotEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setMascotEnabled(enabled) }
    }

    fun resendVerification() {
        if (_resend.value == ResendState.Sending) return
        _resend.value = ResendState.Sending
        viewModelScope.launch {
            _resend.value =
                accounts.resendVerification()?.let { ResendState.Failed(it.toMessage()) } ?: ResendState.Sent
        }
    }

    /** Check-ins stay on this phone (design-spec.md §7.8). The repository finishes even if the tab closes. */
    fun signOut() {
        _resend.value = ResendState.Idle
        viewModelScope.launch { accounts.signOut() }
    }

    /**
     * Deletes what the person confirmed. [withBackup] is the signed-in dialog: the server deletes
     * first, and the phone is erased only after it did. Without it (a guest, or "Sign in again"),
     * only this phone is erased. The choice comes from the dialog, not the current state, so a
     * session that ends meanwhile can't turn "delete my backup too" into "only this phone".
     * Either way the repository finishes even if the tab closes.
     */
    fun deleteEverything(withBackup: Boolean) {
        if (_delete.value == DeleteState.Deleting) return
        _delete.value = DeleteState.Deleting
        viewModelScope.launch {
            val problem = if (withBackup) {
                accounts.deleteAccount()
            } else {
                accounts.eraseThisPhone()
                null
            }
            _delete.value = problem?.let { DeleteState.Failed(it.toDeleteMessage()) } ?: DeleteState.Idle
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private fun AuthProblem.toDeleteMessage(): DeleteMessage = when (this) {
    AuthProblem.OFFLINE -> DeleteMessage.OFFLINE
    AuthProblem.SIGNED_OUT -> DeleteMessage.SIGN_IN_AGAIN
    AuthProblem.TOO_MANY_TRIES -> DeleteMessage.TOO_MANY_TRIES
    AuthProblem.UPDATE_REQUIRED -> DeleteMessage.UPDATE_REQUIRED
    else -> DeleteMessage.SERVER
}
