package app.ulap.feature.checkin.after

import androidx.lifecycle.SavedStateHandle
import app.ulap.core.domain.checkin.SaveResult
import app.ulap.core.testing.FakeCheckInRepository
import app.ulap.core.testing.FakeSettingsRepository
import app.ulap.core.testing.MainDispatcherRule
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Zone
import app.ulap.shared.rules.StressChange
import app.ulap.shared.rules.UlapExpression
import app.ulap.shared.validation.CheckInInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric, because reading the route from SavedStateHandle goes through Android's Bundle.
@RunWith(RobolectricTestRunner::class)
class AfterViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val loggedAt = Instant.parse("2026-11-04T08:49:00Z")
    private val checkIns = FakeCheckInRepository(now = { loggedAt })

    private suspend fun save(moodCode: String, stress: Int, stressAfter: Int?): String {
        val input = CheckInInput(
            moodCode = moodCode,
            energy = 5,
            stress = stress,
            stressAfter = stressAfter,
            trigger = null,
            triggerOther = null,
            note = "a private note",
            supportUsed = SupportType.BREATHE,
            isGoodMoment = false,
            loggedAt = loggedAt,
            tzOffsetMin = 480,
        )
        return assertIs<SaveResult.Saved>(checkIns.add(input)).checkIn.id
    }

    private fun viewModel(checkInId: String) = AfterViewModel(
        savedStateHandle = SavedStateHandle(mapOf("checkInId" to checkInId)),
        settingsRepository = FakeSettingsRepository(),
        checkInRepository = checkIns,
    )

    @Test
    fun `it compares the logged stress with the stress now`() = runTest {
        val state = assertIs<AfterUiState.Ready>(viewModel(save("worried", stress = 8, stressAfter = 5)).uiState.value)

        assertEquals(Zone.RED, state.zone)
        assertEquals(8, state.before)
        assertEquals(5, state.now)
        assertEquals(StressChange.LOWER, state.result.change)
        assertEquals(UlapExpression.CALM, state.result.expression)
    }

    @Test
    fun `stress that stayed or went up gets the gentle face`() = runTest {
        val same = assertIs<AfterUiState.Ready>(viewModel(save("sad", stress = 6, stressAfter = 6)).uiState.value)
        val higher = assertIs<AfterUiState.Ready>(viewModel(save("sad", stress = 6, stressAfter = 9)).uiState.value)

        assertEquals(StressChange.SAME, same.result.change)
        assertEquals(UlapExpression.GENTLE, same.result.expression)
        assertEquals(StressChange.HIGHER, higher.result.change)
        assertEquals(UlapExpression.GENTLE, higher.result.expression)
    }

    @Test
    fun `a check-in without a stress check sends the screen away`() = runTest {
        assertEquals(AfterUiState.Missing, viewModel(save("worried", stress = 8, stressAfter = null)).uiState.value)
    }

    @Test
    fun `a check-in that's gone sends the screen away`() {
        assertEquals(AfterUiState.Missing, viewModel("check-in-404").uiState.value)
    }
}
