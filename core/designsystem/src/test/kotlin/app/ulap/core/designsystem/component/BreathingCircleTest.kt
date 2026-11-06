package app.ulap.core.designsystem.component

import android.provider.Settings
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import app.ulap.core.designsystem.theme.UlapTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BreathingCircleTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `one breath is in for 4, hold for 2, out for 6`() {
        val steps = (0 until BREATH_CYCLE_SECONDS).map(::breathStepAt)
        assertEquals(
            listOf(
                BreathStep(BreathPhase.IN, 4), BreathStep(BreathPhase.IN, 3),
                BreathStep(BreathPhase.IN, 2), BreathStep(BreathPhase.IN, 1),
                BreathStep(BreathPhase.HOLD, 2), BreathStep(BreathPhase.HOLD, 1),
                BreathStep(BreathPhase.OUT, 6), BreathStep(BreathPhase.OUT, 5),
                BreathStep(BreathPhase.OUT, 4), BreathStep(BreathPhase.OUT, 3),
                BreathStep(BreathPhase.OUT, 2), BreathStep(BreathPhase.OUT, 1),
            ),
            steps,
        )
    }

    @Test
    fun `the breaths repeat every 12 seconds`() {
        assertEquals(12, BREATH_CYCLE_SECONDS)
        assertEquals(breathStepAt(5), breathStepAt(5 + 4 * BREATH_CYCLE_SECONDS))
    }

    @Test
    fun `with animations on there is no counter, only the moving circle`() {
        show()
        compose.onNodeWithText("IN · 4").assertDoesNotExist()
    }

    @Test
    fun `with animations off a counter paces the breath, a second at a time`() {
        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        compose.mainClock.autoAdvance = false
        show()
        compose.onNodeWithText("IN · 4").assertExists()
        advanceSeconds(1)
        compose.onNodeWithText("IN · 3").assertExists()
        advanceSeconds(3)
        compose.onNodeWithText("HOLD · 2").assertExists()
    }

    /** The counter ticks after its delay; one more frame then draws the new number. */
    private fun advanceSeconds(seconds: Int) {
        compose.mainClock.advanceTimeBy(ONE_SECOND_MILLIS * seconds)
        compose.mainClock.advanceTimeByFrame()
    }

    private fun show() {
        compose.setContent {
            UlapTheme { BreathingCircle(counterText = { "${it.phase} · ${it.secondsLeft}" }) }
        }
    }

    private companion object {
        const val ONE_SECOND_MILLIS = 1_000L
    }
}
