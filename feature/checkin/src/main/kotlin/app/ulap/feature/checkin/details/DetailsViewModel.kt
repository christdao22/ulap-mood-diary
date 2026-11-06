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
import app.ulap.shared.catalog.MoodCatalog
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
 * What the person has entered on Details so far (design-spec.md §7.3). Energy and stress start at
 * the mood's defaults (technical-plan.md §5).
 */
data class DetailsForm(
    val mood: Mood,
    /** When the check-in happened: the moment Details opened, shown in the header. */
    val loggedAt: Instant,
    val energy: Int = mood.defaultEnergy,
    val stress: Int = mood.defaultStress,
    val trigger: Trigger? = null,
    /** Journal text: never log it. Kept while another chip is picked, saved only with OTHER. */
    val triggerOther: String = "",
    /** Journal text: never log it. */
    val note: String = "",
    val isSaving: Boolean = false,
    /** The new check-in's ID once it's stored; the screen then moves on. */
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
    private val moodCode = savedStateHandle.toRoute<DetailsRoute>().moodCode

    // Compose state, not a StateFlow: a text field must see each keystroke at once, or fast
    // typing can drop letters. The form isn't put in SavedStateHandle on purpose: that bundle is
    // written outside the encrypted database (technical-plan.md §6), and the note is journal text.
    var form by mutableStateOf(
        DetailsForm(
            mood = checkNotNull(MoodCatalog.byCode(moodCode)) { "Details opened for an unknown mood" },
            loggedAt = clock.now(),
        ),
    )
        private set

    /** The user can turn Ulap off; the header text still shows (design-spec.md §8.5). */
    val showUlap: StateFlow<Boolean> = settingsRepository.settings
        .map { it.mascotEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UserSettings().mascotEnabled)

    override fun setEnergy(energy: Int) {
        form = form.copy(energy = energy)
    }

    override fun setStress(stress: Int) {
        form = form.copy(stress = stress)
    }

    override fun resetToDefaults() {
        form = form.copy(energy = form.mood.defaultEnergy, stress = form.mood.defaultStress)
    }

    override fun toggleTrigger(trigger: Trigger) {
        form = form.copy(trigger = trigger.takeUnless { it == form.trigger })
    }

    // Both fields stop at the validator's limits (api-spec.md §5.3), so Save never fails on length.
    override fun setTriggerOther(text: String) {
        form = form.copy(triggerOther = text.take(CheckInValidator.TRIGGER_OTHER_MAX_LENGTH))
    }

    override fun setNote(text: String) {
        form = form.copy(note = text.take(CheckInValidator.NOTE_MAX_LENGTH))
    }

    override fun save() {
        val current = form
        if (current.isSaving || current.savedId != null) return
        form = current.copy(isSaving = true)
        viewModelScope.launch {
            val result = checkInRepository.add(current.toInput())
            form = when (result) {
                is SaveResult.Saved -> form.copy(isSaving = false, savedId = result.checkIn.id)

                // The form keeps every value in range, so these point to a bug. Stay on the
                // screen with the person's entry intact rather than lose it.
                is SaveResult.Invalid, SaveResult.NotFound -> form.copy(isSaving = false)
            }
        }
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
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val SECONDS_PER_MINUTE = 60
    }
}
