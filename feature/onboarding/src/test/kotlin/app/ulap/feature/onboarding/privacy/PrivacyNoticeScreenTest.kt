package app.ulap.feature.onboarding.privacy

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
class PrivacyNoticeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `it covers what is kept, why, where, never, how long, deleting and contact`() {
        compose.setContent { UlapTheme { PrivacyNoticeScreen(onBack = {}) } }
        listOf(
            "What Ulap keeps",
            "Why",
            "Where it's kept",
            "What Ulap never does",
            "How long it's kept",
            "Deleting your data",
            "Questions and your rights",
        ).forEach { compose.onNodeWithText(it).performScrollTo().assertExists() }
    }

    @Test
    fun `the contact section shows the privacy email`() {
        compose.setContent { UlapTheme { PrivacyNoticeScreen(onBack = {}) } }
        compose.onNodeWithText("privacy@ulap.app", substring = true).performScrollTo().assertExists()
    }

    @Test
    fun `Back calls back`() {
        var backs = 0
        compose.setContent { UlapTheme { PrivacyNoticeScreen(onBack = { backs++ }) } }
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
    }

    private fun show(largeText: Boolean = false) = compose.setContent {
        LargestFontScale(enabled = largeText) { UlapTheme { PrivacyNoticeScreen(onBack = {}) } }
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
