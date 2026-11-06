package app.ulap.feature.checkin.details

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.ulap.core.domain.checkin.CheckInRepository
import app.ulap.core.domain.checkin.SaveResult
import app.ulap.core.domain.settings.SettingsRepository
import app.ulap.core.model.UserSettings
import app.ulap.feature.checkin.navigation.DetailsRoute
import app.ulap.feature.checkin.navigation.EditCheckInRoute
import app.ulap.shared.catalog.MoodCatalog
import app.ulap.shared.model.CheckIn
import app.ulap.shared.model.Mood
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import app.ulap.shared.validation.CheckInInput
import app.ulap.shared.validation.CheckInValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the person has entered on Details so far (design-spec.md §7.3). For a new check-in, energy
 * and stress start at the mood's defaults (technical-plan.md §5). For an edit, every field starts
 * as it was saved (design-spec.md §7.6).
 */
data class DetailsForm(
    val mood: Mood,
    /**
     * When the check-in happened, shown in the header: the moment Details opened, or for an edit
     * the time it was first logged. An edit never moves a check-in to another day.
     */
    val loggedAt: Instant,
    val energy: Int = mood.defaultEnergy,
    val stress: Int = mood.defaultStress,
    val trigger: Trigger? = null,
    /** Journal text: never log it. Kept while another chip is picked, saved only with OTHER. */
    val triggerOther: String = "",
    /** Journal text: never log it. */
    val note: String = "",
    /** True when this changes a saved check-in rather than adding one. */
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    /** The check-in's ID once it's stored; the screen then moves on. */
    val savedId: String? = null,
) {
    /** Shows "Default for Worried: energy 6, stress 8 · Reset to default". */
    val changedFromDefaults: Boolean
        get() = energy != mood.defaultEnergy || stress != mood.defaultStress
}

/** Everything the person can change on Details. Back is separate: navigation handles it. */
interface DetailsActions {
    fun setEnergy(energy: Int)

    fun setStress(stress: Int)

    fun resetToDefaults()

    fun toggleTrigger(trigger: Trigger)

    fun setTriggerOther(text: String)

    fun setNote(text: String)

    fun save()
}

@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    settingsRepository: SettingsRepository,
    private val checkInRepository: CheckInRepository,
    private val clock: Clock,
) : ViewModel(),
    DetailsActions {
    /** The check-in being changed, from [EditCheckInRoute]; `null` when adding one ([DetailsRoute]). */
    private val editingId: String? = savedStateHandle[EDIT_ID_KEY]

    /** The check-in as it was saved, once loaded. An edit changes only what Details shows. */
    private var original: CheckIn? = null

    // Compose state, not a StateFlow: a text field must see each keystroke at once, or fast
    // typing can drop letters. The form isn't put in SavedStateHandle on purpose: that bundle is
    // written outside the encrypted database (technical-plan.md §6), and the note is journal text.

    /** `null` only while the check-in to edit loads from the database. */
    var form by mutableStateOf(
        if (editingId == null) newForm(savedStateHandle.toRoute<DetailsRoute>().moodCode) else null,
    )
        private set

    /** True when the check-in to edit is missing or deleted. The screen then closes. */
    var isMissing by mutableStateOf(false)
        private set

    /** The user can turn Ulap off; the header text still shows (design-spec.md §8.5). */
    val showUlap: StateFlow<Boolean> = settingsRepository.settings
        .map { it.mascotEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UserSettings().mascotEnabled)

    init {
        editingId?.let { id -> viewModelScope.launch { load(id) } }
    }

    private fun newForm(moodCode: String) = DetailsForm(
        mood = checkNotNull(MoodCatalog.byCode(moodCode)) { "Details opened for an unknown mood" },
        loggedAt = clock.now(),
    )

    private suspend fun load(id: String) {
        val checkIn = checkInRepository.get(id)
        val mood = checkIn?.let { MoodCatalog.byCode(it.moodCode) }
        if (checkIn == null || mood == null) {
            isMissing = true
            return
        }
        original = checkIn
        form = DetailsForm(
            mood = mood,
            loggedAt = checkIn.loggedAt,
            energy = checkIn.energy,
            stress = checkIn.stress,
            trigger = checkIn.trigger,
            triggerOther = checkIn.triggerOther.orEmpty(),
            note = checkIn.note.orEmpty(),
            isEditing = true,
        )
    }

    /** Changes the form if it's loaded. Before that there is nothing on screen to change. */
    private inline fun update(change: DetailsForm.() -> DetailsForm) {
        form = form?.change()
    }

    override fun setEnergy(energy: Int) = update { copy(energy = energy) }

    override fun setStress(stress: Int) = update { copy(stress = stress) }

    override fun resetToDefaults() = update { copy(energy = mood.defaultEnergy, stress = mood.defaultStress) }

    override fun toggleTrigger(trigger: Trigger) = update {
        copy(trigger = trigger.takeUnless { it == this.trigger })
    }

    // Both fields stop at the validator's limits (api-spec.md §5.3), so Save never fails on length.
    override fun setTriggerOther(text: String) = update {
        copy(triggerOther = text.take(CheckInValidator.TRIGGER_OTHER_MAX_LENGTH))
    }

    override fun setNote(text: String) = update { copy(note = text.take(CheckInValidator.NOTE_MAX_LENGTH)) }

    override fun save() {
        val current = form ?: return
        if (current.isSaving || current.savedId != null) return
        form = current.copy(isSaving = true)
        val saved = original
        viewModelScope.launch {
            // An edit goes through the repository, which sets updatedAt so the change syncs.
            val result = if (saved == null) {
                checkInRepository.add(current.toInput())
            } else {
                checkInRepository.edit(saved.id, current.applyTo(saved.toInput()))
            }
            when (result) {
                is SaveResult.Saved -> update { copy(isSaving = false, savedId = result.checkIn.id) }

                // Deleted since Details opened (on another phone, then synced): nothing to save to.
                SaveResult.NotFound -> isMissing = true

                // The form keeps every value in range, so this points to a bug. Stay on the
                // screen with the person's entry intact rather than lose it.
                is SaveResult.Invalid -> update { copy(isSaving = false) }
            }
        }
    }

    /**
     * Puts the form's fields into a saved check-in's [input]. The time, the after-check and the
     * support used stay as they were: Details doesn't show them.
     */
    private fun DetailsForm.applyTo(input: CheckInInput): CheckInInput {
        val entered = toInput()
        return input.copy(
            energy = entered.energy,
            stress = entered.stress,
            trigger = entered.trigger,
            triggerOther = entered.triggerOther,
            note = entered.note,
        )
    }

    private fun DetailsForm.toInput() = CheckInInput(
        moodCode = mood.code,
        energy = energy,
        stress = stress,
        stressAfter = null,
        trigger = trigger,
        triggerOther = triggerOther.trim().ifEmpty { null },
        note = note,
        // The Response screen records what support was used (design-spec.md §7.4).
        supportUsed = SupportType.NONE,
        isGoodMoment = false,
        loggedAt = loggedAt,
        tzOffsetMin = ZoneId.systemDefault().rules.getOffset(
            loggedAt.toJavaInstant(),
        ).totalSeconds / SECONDS_PER_MINUTE,
    )

    private companion object {
        /** The name of [EditCheckInRoute.checkInId], which navigation stores in SavedStateHandle. */
        const val EDIT_ID_KEY = "checkInId"
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val SECONDS_PER_MINUTE = 60
    }
}
