package app.ulap.feature.onboarding.privacy

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
class YourPrivacyScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private fun show(largeText: Boolean = false) {
        compose.setContent {
            LargestFontScale(enabled = largeText) {
                UlapTheme {
                    YourPrivacyContent(onStart = { events += "start" }, onReadPrivacyNotice = { events += "notice" })
                }
            }
        }
    }

    @Test
    fun `it says where the diary lives and what Ulap is not`() {
        show()
        compose.onNodeWithText("Your diary stays on this phone. You can back it up later.").assertExists()
        compose.onNodeWithText("Ulap is a self-care diary, not a medical or emergency service.").assertExists()
        compose.onNodeWithText("By starting, you agree to the privacy notice.").assertExists()
    }

    @Test
    fun `the notice link and Start call back`() {
        show()
        compose.onNodeWithText("Read the privacy notice").performClick()
        compose.onNodeWithText("Start my first check-in").performClick()
        assertEquals(listOf("notice", "start"), events)
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
