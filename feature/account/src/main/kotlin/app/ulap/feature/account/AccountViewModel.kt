package app.ulap.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ulap.core.domain.account.AccountRepository
import app.ulap.core.domain.account.AccountState
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

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
