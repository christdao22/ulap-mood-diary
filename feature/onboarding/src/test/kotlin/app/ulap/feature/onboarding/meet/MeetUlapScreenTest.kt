package app.ulap.feature.onboarding.meet

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.ulap.core.composetesting.LargestFontScale
import app.ulap.core.composetesting.assertNoTextCutOff
import app.ulap.core.composetesting.assertTouchTargetsAtLeast48dp
import app.ulap.core.composetesting.assertTouchTargetsHaveNames
import app.ulap.core.designsystem.theme.UlapTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class MeetUlapScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `Ulap says hi and Continue moves on`() {
        var continued = 0
        compose.setContent { UlapTheme { MeetUlapScreen(onContinue = { continued++ }) } }

        compose.onNodeWithText("Hi, I'm Ulap.").assertExists()
        compose.onNodeWithText(
            "I'll keep you company while you check in with your feelings. Any feeling is okay here.",
        ).assertExists()
        compose.onNodeWithText("Continue").performClick()
        assertEquals(1, continued)
    }

    private fun show(largeText: Boolean = false) = compose.setContent {
        LargestFontScale(enabled = largeText) { UlapTheme { MeetUlapScreen(onContinue = {}) } }
    }

    // Accessibility (design-spec.md §11). Native graphics measure real fonts.

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+h3000dp")
    fun `every touch target is at least 48dp and has a name`() {
        show()
        compose.assertTouchTargetsAtLeast48dp()
        compose.assertTouchTargetsHaveNames()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+en")
    fun `nothing is cut off at 200 percent text in English`() {
        show(largeText = true)
        compose.assertNoTextCutOff()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+fil")
    fun `nothing is cut off at 200 percent text in Filipino`() {
        show(largeText = true)
        compose.assertNoTextCutOff()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+b+ceb")
    fun `nothing is cut off at 200 percent text in Bisaya`() {
        show(largeText = true)
        compose.assertNoTextCutOff()
    }
}
