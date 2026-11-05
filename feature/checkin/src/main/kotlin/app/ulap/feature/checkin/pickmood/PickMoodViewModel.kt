package app.ulap.feature.checkin.pickmood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ulap.core.domain.settings.SettingsRepository
import app.ulap.core.model.UserSettings
import app.ulap.shared.rules.TimeOfDay
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the pick-mood screen shows besides the fixed mood grid (design-spec.md §7.2). */
data class PickMoodUiState(
    /** Picks the greeting: "Good morning", "Good afternoon" or "Good evening". */
    val timeOfDay: TimeOfDay,
    /** The user can turn Ulap off in Account; the greeting still shows (§8.5). */
    val showUlap: Boolean,
)

@HiltViewModel
class PickMoodViewModel @Inject constructor(settingsRepository: SettingsRepository, clock: Clock) : ViewModel() {
    // Read once when the screen opens. Guests have no name, so the greeting has none (§7.2).
    private val timeOfDay = TimeOfDay.ofHour(clock.now().toJavaInstant().atZone(ZoneId.systemDefault()).hour)

    val uiState: StateFlow<PickMoodUiState> = settingsRepository.settings
        .map { PickMoodUiState(timeOfDay = timeOfDay, showUlap = it.mascotEnabled) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            // Until DataStore answers (a few milliseconds), assume the default: Ulap on.
            initialValue = PickMoodUiState(timeOfDay = timeOfDay, showUlap = UserSettings().mascotEnabled),
        )

    private companion object {
        // Keeps the flow alive through a screen rotation, which takes well under 5 seconds.
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
