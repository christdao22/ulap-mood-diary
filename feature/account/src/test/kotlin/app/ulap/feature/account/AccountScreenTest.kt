package app.ulap.feature.account

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
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
class AccountScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var languageOpens = 0
    private var privacyOpens = 0

    private fun show(language: AppLanguage? = AppLanguage.ENGLISH) {
        compose.setContent {
            UlapTheme {
                AccountContent(
                    language = language,
                    onOpenLanguage = { languageOpens++ },
                    onOpenPrivacyNotice = { privacyOpens++ },
                )
            }
        }
    }

    @Test
    fun `the Language row names the chosen language in itself`() {
        show(AppLanguage.BISAYA)
        // One button that TalkBack reads as "Language, Bisaya".
        compose.onNode(hasText("Language") and hasText("Bisaya") and hasClickAction()).assertExists()
    }

    @Test
    fun `the Language row opens the language screen`() {
        show()
        compose.onNodeWithText("Language").performClick()
        assertEquals(1, languageOpens)
    }

    @Test
    fun `the Privacy notice row opens the privacy notice`() {
        show()
        compose.onNodeWithText("Privacy notice").performClick()
        assertEquals(1, privacyOpens)
    }

    @Test
    @Config(qualifiers = "b+ceb-w360dp-h800dp")
    fun `the list speaks Bisaya once Bisaya is the app language`() {
        show(AppLanguage.BISAYA)
        compose.onNodeWithText("Pinulongan").assertExists()
    }
}
