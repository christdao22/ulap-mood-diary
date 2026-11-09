package app.ulap.feature.onboarding.meet

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
}
