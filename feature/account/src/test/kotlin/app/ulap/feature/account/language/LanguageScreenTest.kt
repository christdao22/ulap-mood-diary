package app.ulap.feature.account.language

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.ulap.core.composetesting.LargestFontScale
import app.ulap.core.composetesting.assertNoTextCutOff
import app.ulap.core.composetesting.assertTouchTargetsAtLeast48dp
import app.ulap.core.composetesting.assertTouchTargetsHaveNames
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.model.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class LanguageScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var backs = 0

    private fun show(initial: AppLanguage?, largeText: Boolean = false) {
        compose.setContent {
            var chosen by remember { mutableStateOf(initial) }
            LargestFontScale(enabled = largeText) {
                UlapTheme {
                    LanguageContent(chosen = chosen, onChoose = { chosen = it }, onBack = { backs++ })
                }
            }
        }
    }

    @Test
    fun `it offers the three languages with the saved one selected`() {
        show(AppLanguage.FILIPINO)
        compose.onNode(hasText("Filipino") and isSelectable()).assertIsSelected()
        compose.onNode(hasText("English") and isSelectable()).assertIsNotSelected()
        compose.onNode(hasText("Bisaya") and isSelectable()).assertIsNotSelected()
    }

    @Test
    fun `a tap picks that language`() {
        show(AppLanguage.ENGLISH)
        compose.onNodeWithText("Bisaya").performClick()
        compose.onNode(hasText("Bisaya") and isSelectable()).assertIsSelected()
        compose.onNode(hasText("English") and isSelectable()).assertIsNotSelected()
    }

    @Test
    fun `Back calls back`() {
        show(AppLanguage.ENGLISH)
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
    }

    @Test
    @Config(qualifiers = "fil-w360dp-h800dp")
    fun `the screen speaks Filipino once Filipino is the app language`() {
        show(AppLanguage.FILIPINO)
        compose.onNodeWithText("Wika").assertExists()
        compose.onNodeWithContentDescription("Bumalik").assertExists()
    }

    // Accessibility (design-spec.md §11). Native graphics measure real fonts.

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+h3000dp")
    fun `every touch target is at least 48dp and has a name`() {
        show(AppLanguage.FILIPINO)
        compose.assertTouchTargetsAtLeast48dp()
        compose.assertTouchTargetsHaveNames()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+en")
    fun `nothing is cut off at 200 percent text in English`() {
        show(AppLanguage.FILIPINO, largeText = true)
        compose.assertNoTextCutOff()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+fil")
    fun `nothing is cut off at 200 percent text in Filipino`() {
        show(AppLanguage.FILIPINO, largeText = true)
        compose.assertNoTextCutOff()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+b+ceb")
    fun `nothing is cut off at 200 percent text in Bisaya`() {
        show(AppLanguage.FILIPINO, largeText = true)
        compose.assertNoTextCutOff()
    }
}
