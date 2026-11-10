package app.ulap.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ulap.core.domain.settings.SettingsRepository
import app.ulap.core.model.AppLanguage
import app.ulap.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The Account tab's settings list (design-spec.md §7.8). Reads settings kept on this phone only. */
@HiltViewModel
class AccountViewModel @Inject constructor(private val settingsRepository: SettingsRepository) : ViewModel() {
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

    fun setMascotEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setMascotEnabled(enabled) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
