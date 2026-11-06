package app.ulap.feature.checkin.response

import androidx.lifecycle.SavedStateHandle
import app.ulap.core.domain.checkin.SaveResult
import app.ulap.core.testing.FakeCheckInRepository
import app.ulap.core.testing.FakeSettingsRepository
import app.ulap.core.testing.MainDispatcherRule
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import app.ulap.shared.rules.ResponseActions
import app.ulap.shared.rules.ResponseMessage
import app.ulap.shared.validation.CheckInInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric, because reading the route from SavedStateHandle goes through Android's Bundle.
@RunWith(RobolectricTestRunner::class)
class ResponseViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val loggedAt = Instant.parse("2026-11-04T08:49:00Z")
    private val checkIns = FakeCheckInRepository(now = { loggedAt })

    private suspend fun save(moodCode: String, stress: Int, trigger: Trigger? = null): String {
        val input = CheckInInput(
            moodCode = moodCode,
            energy = 5,
            stress = stress,
            stressAfter = null,
            trigger = trigger,
            triggerOther = null,
            note = "a private note",
            supportUsed = SupportType.NONE,
            isGoodMoment = false,
            loggedAt = loggedAt,
            tzOffsetMin = 480,
        )
        return assertIs<SaveResult.Saved>(checkIns.add(input)).checkIn.id
    }

    private fun viewModel(checkInId: String) = ResponseViewModel(
        savedStateHandle = SavedStateHandle(mapOf("checkInId" to checkInId)),
        settingsRepository = FakeSettingsRepository(),
        checkInRepository = checkIns,
    )

    private fun ResponseViewModel.ready() = assertIs<ResponseUiState.Ready>(uiState.value)

    @Test
    fun `it reads the saved check-in and shows what ResponseRules decided`() = runTest {
        val id = save("worried", stress = 6, trigger = Trigger.WORK)
        val state = viewModel(id).ready()

        assertEquals("worried", state.mood.code)
        assertEquals(ResponseMessage.RED, state.response.message)
        assertEquals(Trigger.WORK, state.response.triggerLine)
        assertEquals(ResponseActions.Offer(primary = SupportType.BREATHE, showSecondary = true), state.response.actions)
        assertNull(state.startedSupport)
    }

    @Test
    fun `a check-in that's gone sends the screen away`() {
        assertEquals(ResponseUiState.Missing, viewModel("check-in-404").uiState.value)
    }

    @Test
    fun `the primary button records the support on the check-in`() = runTest {
        val id = save("worried", stress = 9)
        val viewModel = viewModel(id)
        viewModel.startSupport()

        assertEquals(SupportType.BREATHE, viewModel.ready().startedSupport)
        val stored = checkNotNull(checkIns.get(id))
        assertEquals(SupportType.BREATHE, stored.supportUsed)
        assertFalse(stored.isGoodMoment)
        assertEquals("a private note", stored.note)
    }

    @Test
    fun `saving a good moment marks the check-in as one`() = runTest {
        val id = save("excited", stress = 3)
        viewModel(id).startSupport()

        val stored = checkNotNull(checkIns.get(id))
        assertEquals(SupportType.GOOD_MOMENT, stored.supportUsed)
        assertTrue(stored.isGoodMoment)
    }

    @Test
    fun `a support already started comes back after the screen is rebuilt`() = runTest {
        val id = save("sad", stress = 5)
        viewModel(id).startSupport()

        assertEquals(SupportType.SMALL_HELPS, viewModel(id).ready().startedSupport)
    }

    @Test
    fun `Hopeless offers no button to start, so nothing is recorded`() = runTest {
        val id = save("hopeless", stress = 9)
        val viewModel = viewModel(id)
        assertEquals(ResponseActions.Hotline, viewModel.ready().response.actions)

        viewModel.startSupport()
        assertNull(viewModel.ready().startedSupport)
        assertEquals(SupportType.NONE, checkNotNull(checkIns.get(id)).supportUsed)
    }

    @Test
    fun `the stress check starts at the logged stress and follows the slider`() = runTest {
        val viewModel = viewModel(save("worried", stress = 7))
        assertTrue(viewModel.ready().showsStressCheck)
        assertEquals(7, viewModel.ready().stressNow)

        viewModel.setStressNow(5)
        assertEquals(5, viewModel.ready().stressNow)
    }

    @Test
    fun `at stress 8 or more the stress check waits for the support to start`() = runTest {
        val id = save("worried", stress = 8)
        val viewModel = viewModel(id)
        assertFalse(viewModel.ready().showsStressCheck)

        // A Check in that can't be seen yet saves nothing.
        viewModel.saveStressCheck()
        assertFalse(viewModel.ready().stressCheckSaved)
        assertNull(checkNotNull(checkIns.get(id)).stressAfter)

        viewModel.startSupport()
        assertTrue(viewModel.ready().showsStressCheck)
        // It also shows when the screen is rebuilt with the support already started.
        assertTrue(viewModel(id).ready().showsStressCheck)
    }

    @Test
    fun `Check in saves stress after and keeps the rest of the check-in`() = runTest {
        val id = save("worried", stress = 8)
        val viewModel = viewModel(id)
        viewModel.startSupport()
        viewModel.setStressNow(5)
        viewModel.saveStressCheck()

        assertTrue(viewModel.ready().stressCheckSaved)
        val stored = checkNotNull(checkIns.get(id))
        assertEquals(5, stored.stressAfter)
        assertEquals(8, stored.stress)
        assertEquals(SupportType.BREATHE, stored.supportUsed)
        assertEquals("a private note", stored.note)
    }

    @Test
    fun `once Check in is tapped, the slider and a second tap change nothing`() = runTest {
        val id = save("sad", stress = 6)
        val viewModel = viewModel(id)
        viewModel.setStressNow(4)
        viewModel.saveStressCheck()
        viewModel.setStressNow(9)
        viewModel.saveStressCheck()

        assertEquals(4, viewModel.ready().stressNow)
        assertEquals(4, checkNotNull(checkIns.get(id)).stressAfter)
    }

    @Test
    fun `Hopeless still has the stress check`() = runTest {
        val id = save("hopeless", stress = 9)
        val viewModel = viewModel(id)
        // No support button to wait for, so the card is there from the start.
        assertTrue(viewModel.ready().showsStressCheck)

        viewModel.setStressNow(7)
        viewModel.saveStressCheck()
        assertEquals(7, checkNotNull(checkIns.get(id)).stressAfter)
    }

    @Test
    fun `Yellow and Green have no stress check, so nothing is saved`() = runTest {
        val id = save("calm", stress = 2)
        val viewModel = viewModel(id)
        assertFalse(viewModel.ready().showsStressCheck)

        viewModel.saveStressCheck()
        assertFalse(viewModel.ready().stressCheckSaved)
        assertNull(checkNotNull(checkIns.get(id)).stressAfter)
    }

    @Test
    fun `a check-in deleted before Check in sends the screen away`() = runTest {
        val id = save("worried", stress = 7)
        val viewModel = viewModel(id)
        checkIns.delete(id)
        viewModel.saveStressCheck()

        assertEquals(ResponseUiState.Missing, viewModel.uiState.value)
    }

    @Test
    fun `a saved stress after comes back after the screen is rebuilt`() = runTest {
        val id = save("worried", stress = 8)
        viewModel(id).run {
            startSupport()
            setStressNow(3)
            saveStressCheck()
        }

        assertEquals(3, viewModel(id).ready().stressNow)
    }
}
