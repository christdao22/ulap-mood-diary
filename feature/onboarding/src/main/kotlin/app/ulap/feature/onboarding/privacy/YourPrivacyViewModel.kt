package app.ulap.feature.onboarding.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ulap.core.domain.settings.SettingsRepository
import app.ulap.core.model.PrivacyConsent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.launch

/** Onboarding step 3: your privacy (design-spec.md §7.1). */
@HiltViewModel
class YourPrivacyViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    /**
     * "Start my first check-in" is the person agreeing to the privacy notice (design-spec.md
     * §7.1). This records that consent with the time and notice version, and marks onboarding
     * done so it never shows again (PHASES.md Phase 5), then calls [onDone]. Both are saved first:
     * if the app closes before the first check-in, onboarding stays done and the consent is kept.
     */
    fun start(onDone: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.setOnboardingDone(
                PrivacyConsent(acceptedAt = clock.now(), noticeVersion = PrivacyConsent.CURRENT_NOTICE_VERSION),
            )
            onDone()
        }
    }
}
