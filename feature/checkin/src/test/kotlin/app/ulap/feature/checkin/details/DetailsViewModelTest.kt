package app.ulap.feature.checkin.details

import androidx.lifecycle.SavedStateHandle
import app.ulap.core.testing.FakeCheckInRepository
import app.ulap.core.testing.FakeSettingsRepository
import app.ulap.core.testing.MainDispatcherRule
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import app.ulap.shared.validation.CheckInValidator
import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric, because reading the route from SavedStateHandle goes through Android's Bundle.
@RunWith(RobolectricTestRunner::class)
class DetailsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val openedAt = Instant.parse("2026-11-04T08:49:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = openedAt
    }
    private val checkIns = FakeCheckInRepository(now = { openedAt })
    private val systemZone = TimeZone.getDefault()

    private fun viewModel(moodCode: String = "worried") = DetailsViewModel(
        savedStateHandle = SavedStateHandle(mapOf("moodCode" to moodCode)),
        settingsRepository = FakeSettingsRepository(),
        checkInRepository = checkIns,
        clock = clock,
    )

    @BeforeTest
    fun pinTimeZone() = TimeZone.setDefault(TimeZone.getTimeZone("Asia/Manila"))

    @AfterTest
    fun restoreTimeZone() = TimeZone.setDefault(systemZone)

    @Test
    fun `energy and stress start at the mood's defaults`() {
        val form = viewModel("worried").form
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
        assertTrue(viewModel.form.changedFromDefaults)

        viewModel.resetToDefaults()
        assertEquals(6, viewModel.form.energy)
        assertEquals(8, viewModel.form.stress)
        assertFalse(viewModel.form.changedFromDefaults)
    }

    @Test
    fun `tapping the picked trigger again clears it`() {
        val viewModel = viewModel()
        viewModel.toggleTrigger(Trigger.WORK)
        assertEquals(Trigger.WORK, viewModel.form.trigger)
        viewModel.toggleTrigger(Trigger.MONEY)
        assertEquals(Trigger.MONEY, viewModel.form.trigger)
        viewModel.toggleTrigger(Trigger.MONEY)
        assertNull(viewModel.form.trigger)
    }

    @Test
    fun `the text fields stop at the validator's limits`() {
        val viewModel = viewModel()
        viewModel.setNote("a".repeat(CheckInValidator.NOTE_MAX_LENGTH + 10))
        viewModel.setTriggerOther("b".repeat(CheckInValidator.TRIGGER_OTHER_MAX_LENGTH + 10))
        assertEquals(CheckInValidator.NOTE_MAX_LENGTH, viewModel.form.note.length)
        assertEquals(CheckInValidator.TRIGGER_OTHER_MAX_LENGTH, viewModel.form.triggerOther.length)
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
        assertEquals(saved.id, viewModel.form.savedId)
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
}
