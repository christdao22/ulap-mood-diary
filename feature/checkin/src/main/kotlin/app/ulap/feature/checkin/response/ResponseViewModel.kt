package app.ulap.feature.checkin.response

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.ulap.core.domain.checkin.CheckInRepository
import app.ulap.core.domain.checkin.SaveResult
import app.ulap.core.domain.settings.SettingsRepository
import app.ulap.core.model.UserSettings
import app.ulap.feature.checkin.navigation.ResponseRoute
import app.ulap.shared.catalog.MoodCatalog
import app.ulap.shared.model.CheckIn
import app.ulap.shared.model.Mood
import app.ulap.shared.model.SupportType
import app.ulap.shared.rules.Response
import app.ulap.shared.rules.ResponseActions
import app.ulap.shared.rules.ResponseRules
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Response screen shows (design-spec.md §7.4). */
sealed interface ResponseUiState {
    /** Reading the check-in from the phone's database. */
    data object Loading : ResponseUiState

    /** The check-in is gone (deleted, for example). The screen leaves. */
    data object Missing : ResponseUiState

    data class Ready(
        val mood: Mood,
        /** What ResponseRules decided for this check-in. The screen only shows it. */
        val response: Response,
        /**
         * The support the person started with the primary button, or null before that tap. Read
         * back from the check-in, so it survives the app being closed in the background.
         */
        val startedSupport: SupportType? = null,
    ) : ResponseUiState
}

@HiltViewModel
class ResponseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    settingsRepository: SettingsRepository,
    private val checkInRepository: CheckInRepository,
) : ViewModel() {
    private val checkInId = savedStateHandle.toRoute<ResponseRoute>().checkInId

    private val _uiState = MutableStateFlow<ResponseUiState>(ResponseUiState.Loading)
    val uiState: StateFlow<ResponseUiState> = _uiState.asStateFlow()

    /** The user can turn Ulap off; every message still shows (design-spec.md §8.5). */
    val showUlap: StateFlow<Boolean> = settingsRepository.settings
        .map { it.mascotEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UserSettings().mascotEnabled)

    /** Holds journal text (the note), so it stays here and out of the UI state. Never log it. */
    private var checkIn: CheckIn? = null

    init {
        viewModelScope.launch {
            // Screens read only from Room (CLAUDE.md): the check-in Details just saved.
            val saved = checkInRepository.get(checkInId)
            val mood = saved?.let { MoodCatalog.byCode(it.moodCode) }
            checkIn = saved
            _uiState.value = if (saved == null || mood == null) {
                ResponseUiState.Missing
            } else {
                ResponseUiState.Ready(
                    mood = mood,
                    response = ResponseRules.respond(mood, saved.stress, saved.trigger),
                    startedSupport = saved.supportUsed.takeUnless { it == SupportType.NONE },
                )
            }
        }
    }

    /**
     * The primary button: records the support on the check-in (api-spec.md §5.3), so Logs and
     * Reports can show it. A good moment is also marked as one; only Yellow and Green offer it.
     */
    fun startSupport() {
        // Only once, and only when there's a button to start (Hopeless has none).
        val state = (_uiState.value as? ResponseUiState.Ready)?.takeIf { it.startedSupport == null } ?: return
        val offer = state.response.actions as? ResponseActions.Offer
        val current = checkIn
        if (offer == null || current == null) return
        _uiState.update { state.copy(startedSupport = offer.primary) }
        viewModelScope.launch {
            val input = current.toInput().copy(
                supportUsed = offer.primary,
                isGoodMoment = offer.primary == SupportType.GOOD_MOMENT,
            )
            // A failed edit only loses the record of which support was used; the screen carries on.
            val result = checkInRepository.edit(current.id, input)
            if (result is SaveResult.Saved) checkIn = result.checkIn
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
