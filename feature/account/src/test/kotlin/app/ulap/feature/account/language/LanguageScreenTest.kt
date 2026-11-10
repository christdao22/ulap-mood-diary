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
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.model.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class LanguageScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var backs = 0

    private fun show(initial: AppLanguage?) {
        compose.setContent {
            var chosen by remember { mutableStateOf(initial) }
            UlapTheme {
                LanguageContent(chosen = chosen, onChoose = { chosen = it }, onBack = { backs++ })
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
}
