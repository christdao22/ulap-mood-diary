package app.ulap.feature.checkin.after

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.ulap.core.domain.checkin.CheckInRepository
import app.ulap.core.domain.settings.SettingsRepository
import app.ulap.core.model.UserSettings
import app.ulap.feature.checkin.navigation.AfterRoute
import app.ulap.shared.catalog.MoodCatalog
import app.ulap.shared.model.Zone
import app.ulap.shared.rules.AfterCheck
import app.ulap.shared.rules.ResponseRules
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the After screen shows (design-spec.md §7.5). */
sealed interface AfterUiState {
    /** Reading the check-in from the phone's database. */
    data object Loading : AfterUiState

    /** The check-in is gone, or has no stress check to compare. The screen leaves. */
    data object Missing : AfterUiState

    data class Ready(
        /** The check-in's zone, for the background tint it shared with Response. */
        val zone: Zone,
        /** The stress the person logged. */
        val before: Int,
        /** Their answer to "How stressed do you feel now?" (stress_after). */
        val now: Int,
        /** What ResponseRules decided: lower, same or higher, and Ulap's face. */
        val result: AfterCheck,
    ) : AfterUiState
}

@HiltViewModel
class AfterViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    settingsRepository: SettingsRepository,
    checkInRepository: CheckInRepository,
) : ViewModel() {
    private val checkInId = savedStateHandle.toRoute<AfterRoute>().checkInId

    private val _uiState = MutableStateFlow<AfterUiState>(AfterUiState.Loading)
    val uiState: StateFlow<AfterUiState> = _uiState.asStateFlow()

    /** The user can turn Ulap off; every message still shows (design-spec.md §8.5). */
    val showUlap: StateFlow<Boolean> = settingsRepository.settings
        .map { it.mascotEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UserSettings().mascotEnabled)

    init {
        viewModelScope.launch {
            // Screens read only from Room (CLAUDE.md): the stress_after Response just saved. Only
            // the numbers and the zone go into the UI state; the note stays out of it.
            val saved = checkInRepository.get(checkInId)
            val zone = saved?.let { MoodCatalog.byCode(it.moodCode) }?.zone
            val now = saved?.stressAfter
            _uiState.value = if (saved == null || zone == null || now == null) {
                AfterUiState.Missing
            } else {
                AfterUiState.Ready(
                    zone = zone,
                    before = saved.stress,
                    now = now,
                    result = ResponseRules.afterCheck(before = saved.stress, now = now),
                )
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
