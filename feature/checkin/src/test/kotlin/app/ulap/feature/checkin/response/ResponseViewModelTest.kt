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
}
