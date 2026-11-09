package app.ulap.feature.onboarding.privacy

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.ulap.core.designsystem.theme.UlapTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class YourPrivacyScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private fun show() {
        compose.setContent {
            UlapTheme {
                YourPrivacyContent(onStart = { events += "start" }, onReadPrivacyNotice = { events += "notice" })
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
}
