package app.ulap.core.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.ulap.core.composetesting.LargestFontScale
import app.ulap.core.composetesting.assertNoTextCutOff
import app.ulap.core.composetesting.assertTouchTargetsAtLeast48dp
import app.ulap.core.composetesting.assertTouchTargetsHaveNames
import app.ulap.core.designsystem.icon.UlapIcons
import app.ulap.core.designsystem.theme.UlapTheme
import kotlin.test.Test
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The tab bar's promises (design-spec.md §6.10, §11). */
@RunWith(RobolectricTestRunner::class)
class TabBarTest {
    @get:Rule
    val compose = createComposeRule()

    private val tabs = listOf("Check in" to UlapIcons.CheckIn, "Logs" to UlapIcons.Logs)

    private fun show() = compose.setContent {
        UlapTheme {
            var selected by remember { mutableIntStateOf(0) }
            UlapTabBar {
                tabs.forEachIndexed { index, (label, icon) ->
                    UlapTabItem(
                        selected = index == selected,
                        onClick = { selected = index },
                        label = label,
                        icon = icon,
                    )
                }
            }
        }
    }

    @Test
    fun `each tab reads as a tab with its label and says whether it is selected`() {
        show()
        compose.onNodeWithText("Check in")
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        compose.onNodeWithText("Logs").assertIsNotSelected()
    }

    @Test
    fun `tapping a tab selects it`() {
        show()
        compose.onNodeWithText("Logs").performClick().assertIsSelected()
        compose.onNodeWithText("Check in").assertIsNotSelected()
    }

    @Test
    fun `each tab is at least 52dp tall`() {
        show()
        compose.onNodeWithText("Logs").assertHeightIsAtLeast(52.dp)
    }

    // Accessibility (design-spec.md §11), with the longest real tab label in each slot
    // ("Listahan" is Bisaya for Logs). Native graphics measure real fonts.
    private val longestTabs = listOf(
        "Check in" to UlapIcons.CheckIn,
        "Listahan" to UlapIcons.Logs,
        "Reports" to UlapIcons.Reports,
        "Account" to UlapIcons.Account,
    )

    private fun showLongest(largeText: Boolean) = compose.setContent {
        LargestFontScale(enabled = largeText) {
            UlapTheme {
                UlapTabBar {
                    longestTabs.forEachIndexed { index, (label, icon) ->
                        UlapTabItem(selected = index == 0, onClick = {}, label = label, icon = icon)
                    }
                }
            }
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "w360dp-h800dp")
    fun `every tab is at least 48dp and has a name`() {
        showLongest(largeText = false)
        compose.assertTouchTargetsAtLeast48dp()
        compose.assertTouchTargetsHaveNames()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "w360dp-h800dp")
    fun `no tab label is cut off at 200 percent text`() {
        showLongest(largeText = true)
        compose.assertNoTextCutOff()
    }
}
