package app.ulap.feature.onboarding.language

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
class ChooseLanguageScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var continued = 0

    private fun show(initial: AppLanguage? = null) {
        compose.setContent {
            var chosen by remember { mutableStateOf(initial) }
            UlapTheme {
                ChooseLanguageContent(chosen = chosen, onChoose = { chosen = it }, onContinue = { continued++ })
            }
        }
    }

    @Test
    fun `each language is named in itself with its own welcome`() {
        show()
        compose.onNodeWithText("Choose your language").assertExists()
        listOf("English", "Welcome", "Filipino", "Maligayang pagdating", "Bisaya", "Maayong pag-abot")
            .forEach { compose.onNodeWithText(it).assertExists() }
    }

    @Test
    fun `Continue waits for a choice`() {
        show()
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.onNodeWithText("Bisaya").performClick()
        compose.onNodeWithText("Continue").assertIsEnabled().performClick()
        assertEquals(1, continued)
    }

    @Test
    fun `the chosen language shows as selected`() {
        show(AppLanguage.FILIPINO)
        compose.onNode(hasText("Filipino") and isSelectable()).assertIsSelected()
        compose.onNode(hasText("English") and isSelectable()).assertIsNotSelected()
    }

    @Test
    @Config(qualifiers = "b+ceb-w360dp-h800dp")
    fun `the screen speaks Bisaya once Bisaya is the app language`() {
        show(AppLanguage.BISAYA)
        compose.onNodeWithText("Pilia ang imong pinulongan").assertExists()
        compose.onNodeWithText("Padayon").assertExists()
    }
}
