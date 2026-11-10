package app.ulap.feature.account.language

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ulap.core.domain.settings.SettingsRepository
import app.ulap.core.model.AppLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Account · Language: change the language chosen in onboarding (technical-plan.md §11). */
@HiltViewModel
class LanguageViewModel @Inject constructor(private val settingsRepository: SettingsRepository) : ViewModel() {
    /** The saved choice, or null if none was ever chosen. */
    val chosen: StateFlow<AppLanguage?> = settingsRepository.settings
        .map { it.language }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /**
     * Saves the choice to DataStore, as onboarding does. MainActivity hands it to the per-app
     * language API, which redraws the app, this screen included, in that language.
     */
    fun choose(language: AppLanguage) {
        viewModelScope.launch { settingsRepository.setLanguage(language) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
