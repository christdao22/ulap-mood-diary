package app.ulap.feature.onboarding.language

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

/** Onboarding step 1: choose a language (design-spec.md §7.1). */
@HiltViewModel
class ChooseLanguageViewModel @Inject constructor(private val settingsRepository: SettingsRepository) : ViewModel() {
    /** The saved choice, or null before the person picks one. */
    val chosen: StateFlow<AppLanguage?> = settingsRepository.settings
        .map { it.language }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /**
     * Saves the choice to DataStore (technical-plan.md §11). MainActivity sees it and switches the
     * app, so the screen redraws in that language straight away.
     */
    fun choose(language: AppLanguage) {
        viewModelScope.launch { settingsRepository.setLanguage(language) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
