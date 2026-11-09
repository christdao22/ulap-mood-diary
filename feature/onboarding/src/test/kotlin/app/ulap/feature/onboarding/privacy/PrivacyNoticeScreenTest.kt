package app.ulap.feature.onboarding.privacy

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.ulap.core.designsystem.theme.UlapTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class PrivacyNoticeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `it covers what is kept, why, where, what never happens and deleting`() {
        compose.setContent { UlapTheme { PrivacyNoticeScreen(onBack = {}) } }
        listOf("What Ulap keeps", "Why", "Where it's kept", "What Ulap never does", "Deleting your data")
            .forEach { compose.onNodeWithText(it).performScrollTo().assertExists() }
    }

    @Test
    fun `Back calls back`() {
        var backs = 0
        compose.setContent { UlapTheme { PrivacyNoticeScreen(onBack = { backs++ }) } }
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
    }
}
