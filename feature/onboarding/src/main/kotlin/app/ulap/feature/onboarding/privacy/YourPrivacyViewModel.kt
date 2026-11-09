package app.ulap.feature.onboarding.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ulap.core.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Onboarding step 3: your privacy (design-spec.md §7.1). */
@HiltViewModel
class YourPrivacyViewModel @Inject constructor(private val settingsRepository: SettingsRepository) : ViewModel() {
    /**
     * Marks onboarding done, so it never shows again (PHASES.md Phase 5), then calls [onDone].
     * The flag is saved first: if the app closes before the first check-in, onboarding stays done.
     */
    fun start(onDone: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.setOnboardingDone()
            onDone()
        }
    }
}
