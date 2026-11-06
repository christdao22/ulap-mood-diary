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
import app.ulap.shared.validation.CheckInInput
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
        /**
         * The "How stressed do you feel now?" slider. It starts at the stress the person logged
         * (design-spec.md §7.4). Only used while [showsStressCheck].
         */
        val stressNow: Int,
        /** True once Check in has saved [stressNow]; the screen then moves on. */
        val stressCheckSaved: Boolean = false,
    ) : ResponseUiState {
        /**
         * Whether the "How stressed do you feel now?" card shows. At stress 8 or more it waits for
         * the support to start, so the screen opens with one button (technical-plan.md §5).
         */
        val showsStressCheck: Boolean
            get() = response.stressCheck.isShown(supportStarted = startedSupport != null)
    }
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

    /**
     * One edit at a time. Each edit starts from the check-in the last one stored, so a quick
     * Check in right after the primary tap keeps the support that tap recorded.
     */
    private val editLock = Mutex()

    /** True from the Check in tap until the save fails; it stays true after a save. Not UI state. */
    private var savingStressCheck = false

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
                    stressNow = saved.stressAfter ?: saved.stress,
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
        val offer = state.response.actions as? ResponseActions.Offer ?: return
        _uiState.update { (it as? ResponseUiState.Ready)?.copy(startedSupport = offer.primary) ?: it }
        viewModelScope.launch {
            // A failed edit only loses the record of which support was used; the screen carries on.
            editCheckIn {
                it.copy(supportUsed = offer.primary, isGoodMoment = offer.primary == SupportType.GOOD_MOMENT)
            }
        }
    }

    /** Moves the "How stressed do you feel now?" slider. Held still once Check in is tapped. */
    fun setStressNow(stress: Int) {
        if (savingStressCheck) return
        _uiState.update { (it as? ResponseUiState.Ready)?.copy(stressNow = stress) ?: it }
    }

    /**
     * Check in: saves the slider as the check-in's stress_after (technical-plan.md §6), which the
     * After screen compares with the logged stress (design-spec.md §7.5).
     */
    fun saveStressCheck() {
        val state = (_uiState.value as? ResponseUiState.Ready)
            ?.takeIf { it.showsStressCheck && !savingStressCheck }
            ?: return
        // A double tap saves once. Saving to the phone's database is too quick to need a spinner.
        savingStressCheck = true
        viewModelScope.launch {
            // The screen moves on only after the save: leaving clears this ViewModel, which
            // would cancel a save still running.
            when (editCheckIn { it.copy(stressAfter = state.stressNow) }) {
                is SaveResult.Saved ->
                    _uiState.update { (it as? ResponseUiState.Ready)?.copy(stressCheckSaved = true) ?: it }

                SaveResult.NotFound -> _uiState.value = ResponseUiState.Missing

                // Invalid can't happen (the slider stays in 1..10). Let the person try again.
                is SaveResult.Invalid, null -> savingStressCheck = false
            }
        }
    }

    /** Applies [change] to the latest stored check-in, one edit at a time (see [editLock]). */
    private suspend fun editCheckIn(change: (CheckInInput) -> CheckInInput): SaveResult? = editLock.withLock {
        val current = checkIn ?: return@withLock null
        checkInRepository.edit(current.id, change(current.toInput())).also { result ->
            if (result is SaveResult.Saved) checkIn = result.checkIn
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
