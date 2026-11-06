package app.ulap.feature.checkin.details

import androidx.lifecycle.SavedStateHandle
import app.ulap.core.domain.checkin.SaveResult
import app.ulap.core.testing.FakeCheckInRepository
import app.ulap.core.testing.FakeSettingsRepository
import app.ulap.core.testing.MainDispatcherRule
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import app.ulap.shared.validation.CheckInInput
import app.ulap.shared.validation.CheckInValidator
import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric, because reading the route from SavedStateHandle goes through Android's Bundle.
@RunWith(RobolectricTestRunner::class)
class DetailsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val openedAt = Instant.parse("2026-11-04T08:49:00Z")

    /** The time on both clocks. An edit test moves it on to see updatedAt change. */
    private var now = openedAt
    private val clock = object : Clock {
        override fun now(): Instant = now
    }
    private val checkIns = FakeCheckInRepository(now = { now })
    private val systemZone = TimeZone.getDefault()

    private fun viewModel(moodCode: String = "worried") = viewModel(SavedStateHandle(mapOf("moodCode" to moodCode)))

    /** Details opened from Logs' Edit (EditCheckInRoute). */
    private fun editViewModel(checkInId: String) = viewModel(SavedStateHandle(mapOf("checkInId" to checkInId)))

    private fun viewModel(savedStateHandle: SavedStateHandle) = DetailsViewModel(
        savedStateHandle = savedStateHandle,
        settingsRepository = FakeSettingsRepository(),
        checkInRepository = checkIns,
        clock = clock,
    )

    /** The form, which is always there for a new check-in and once an edit has loaded. */
    private val DetailsViewModel.loaded: DetailsForm get() = checkNotNull(form)

    /** A check-in saved earlier, the way Check in leaves it after Response and After. */
    private suspend fun saveEarlier(): String {
        val input = CheckInInput(
            moodCode = "worried",
            energy = 4,
            stress = 9,
            stressAfter = 6,
            trigger = Trigger.OTHER,
            triggerOther = "The commute",
            note = "Late again.",
            supportUsed = SupportType.BREATHE,
            isGoodMoment = false,
            loggedAt = openedAt - 3.hours,
            tzOffsetMin = 480,
        )
        return assertIs<SaveResult.Saved>(checkIns.add(input)).checkIn.id
    }

    @BeforeTest
    fun pinTimeZone() = TimeZone.setDefault(TimeZone.getTimeZone("Asia/Manila"))

    @AfterTest
    fun restoreTimeZone() = TimeZone.setDefault(systemZone)

    @Test
    fun `energy and stress start at the mood's defaults`() {
        val form = viewModel("worried").loaded
        assertEquals(6, form.energy)
        assertEquals(8, form.stress)
        assertFalse(form.changedFromDefaults)
        assertEquals(openedAt, form.loggedAt)
    }

    @Test
    fun `moving a slider shows the defaults line, and Reset brings both back`() {
        val viewModel = viewModel("worried")
        viewModel.setStress(5)
        viewModel.setEnergy(3)
        assertTrue(viewModel.loaded.changedFromDefaults)

        viewModel.resetToDefaults()
        assertEquals(6, viewModel.loaded.energy)
        assertEquals(8, viewModel.loaded.stress)
        assertFalse(viewModel.loaded.changedFromDefaults)
    }

    @Test
    fun `tapping the picked trigger again clears it`() {
        val viewModel = viewModel()
        viewModel.toggleTrigger(Trigger.WORK)
        assertEquals(Trigger.WORK, viewModel.loaded.trigger)
        viewModel.toggleTrigger(Trigger.MONEY)
        assertEquals(Trigger.MONEY, viewModel.loaded.trigger)
        viewModel.toggleTrigger(Trigger.MONEY)
        assertNull(viewModel.loaded.trigger)
    }

    @Test
    fun `the text fields stop at the validator's limits`() {
        val viewModel = viewModel()
        viewModel.setNote("a".repeat(CheckInValidator.NOTE_MAX_LENGTH + 10))
        viewModel.setTriggerOther("b".repeat(CheckInValidator.TRIGGER_OTHER_MAX_LENGTH + 10))
        assertEquals(CheckInValidator.NOTE_MAX_LENGTH, viewModel.loaded.note.length)
        assertEquals(CheckInValidator.TRIGGER_OTHER_MAX_LENGTH, viewModel.loaded.triggerOther.length)
    }

    @Test
    fun `Save stores what the person entered, at the time Details opened, in local time`() {
        val viewModel = viewModel("sad")
        viewModel.setStress(7)
        viewModel.toggleTrigger(Trigger.FAMILY)
        viewModel.setNote("  A long day.  ")
        viewModel.save()

        val saved = checkIns.saved.single()
        assertEquals("sad", saved.moodCode)
        assertEquals(3, saved.energy)
        assertEquals(7, saved.stress)
        assertEquals(Trigger.FAMILY, saved.trigger)
        assertEquals("A long day.", saved.note)
        assertNull(saved.stressAfter)
        assertEquals(SupportType.NONE, saved.supportUsed)
        assertFalse(saved.isGoodMoment)
        assertEquals(openedAt, saved.loggedAt)
        assertEquals(480, saved.tzOffsetMin)
        assertEquals(saved.id, viewModel.loaded.savedId)
    }

    @Test
    fun `Other keeps its words only while Other is picked`() {
        val viewModel = viewModel()
        viewModel.toggleTrigger(Trigger.OTHER)
        viewModel.setTriggerOther("  The commute  ")
        viewModel.save()
        assertEquals("The commute", checkIns.saved.single().triggerOther)

        val second = viewModel()
        second.toggleTrigger(Trigger.OTHER)
        second.setTriggerOther("The commute")
        second.toggleTrigger(Trigger.WORK)
        second.save()
        assertNull(checkIns.saved.last().triggerOther)
    }

    @Test
    fun `an empty journal is saved as no note`() {
        val viewModel = viewModel()
        viewModel.setNote("   ")
        viewModel.save()
        assertNull(checkIns.saved.single().note)
    }

    @Test
    fun `a second tap on Save doesn't save twice`() {
        val viewModel = viewModel()
        viewModel.save()
        viewModel.save()
        assertEquals(1, checkIns.saved.size)
    }

    @Test
    fun `Edit opens Details filled in, at the time it was logged`() = runTest {
        val id = saveEarlier()
        val form = editViewModel(id).loaded

        assertTrue(form.isEditing)
        assertEquals("worried", form.mood.code)
        assertEquals(openedAt - 3.hours, form.loggedAt)
        assertEquals(4, form.energy)
        assertEquals(9, form.stress)
        assertEquals(Trigger.OTHER, form.trigger)
        assertEquals("The commute", form.triggerOther)
        assertEquals("Late again.", form.note)
        assertTrue(form.changedFromDefaults)
    }

    @Test
    fun `saving an edit changes the same check-in and updates updatedAt`() = runTest {
        val id = saveEarlier()
        val viewModel = editViewModel(id)
        now = openedAt + 1.hours
        viewModel.setStress(5)
        viewModel.toggleTrigger(Trigger.WORK)
        viewModel.setNote("  Better after lunch.  ")
        viewModel.save()

        val edited = checkIns.saved.single()
        assertEquals(id, edited.id)
        assertEquals(id, viewModel.loaded.savedId)
        assertEquals(5, edited.stress)
        assertEquals(4, edited.energy)
        assertEquals(Trigger.WORK, edited.trigger)
        assertNull(edited.triggerOther)
        assertEquals("Better after lunch.", edited.note)
        assertEquals(openedAt, edited.createdAt)
        assertEquals(openedAt + 1.hours, edited.updatedAt)
    }

    @Test
    fun `an edit keeps what Details doesn't show`() = runTest {
        val id = saveEarlier()
        val viewModel = editViewModel(id)
        viewModel.setEnergy(7)
        viewModel.save()

        val edited = checkIns.saved.single()
        assertEquals(openedAt - 3.hours, edited.loggedAt)
        assertEquals(480, edited.tzOffsetMin)
        assertEquals(6, edited.stressAfter)
        assertEquals(SupportType.BREATHE, edited.supportUsed)
    }

    @Test
    fun `Edit of a check-in that's gone closes Details`() = runTest {
        val viewModel = editViewModel("check-in-404")
        assertNull(viewModel.form)
        assertTrue(viewModel.isMissing)
    }

    @Test
    fun `a check-in deleted while Details is open closes it on Save`() = runTest {
        val id = saveEarlier()
        val viewModel = editViewModel(id)
        checkIns.delete(id)
        viewModel.save()
        assertTrue(viewModel.isMissing)
        assertTrue(checkIns.saved.isEmpty())
    }
}
