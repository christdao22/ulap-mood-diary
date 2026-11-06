package app.ulap.feature.checkin.after

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.shared.catalog.MoodCatalog
import app.ulap.shared.rules.ResponseRules
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2000dp")
class AfterScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private fun show(before: Int, now: Int, moodCode: String = "worried") {
        val mood = checkNotNull(MoodCatalog.byCode(moodCode))
        compose.setContent {
            UlapTheme {
                AfterContent(
                    state = AfterUiState.Ready(
                        zone = mood.zone,
                        before = before,
                        now = now,
                        result = ResponseRules.afterCheck(mood, before, now),
                    ),
                    showUlap = true,
                    onDone = { events += "done" },
                    onSeeMyWeek = { events += "week" },
                )
            }
        }
    }

    @Test
    fun `lower stress shows before and now, and how many points lower`() {
        show(before = 8, now = 5)
        compose.onNodeWithText("Before 8").assertExists()
        compose.onNodeWithText("Now 5").assertExists()
        compose.onNodeWithText("Your stress went from 8 to 5.").assertExists()
        compose.onNodeWithText("That's 3 points lower. Remember what helped, so you can use it again.").assertExists()
    }

    @Test
    fun `one point lower says point, not points`() {
        show(before = 6, now = 5)
        compose.onNodeWithText("That's 1 point lower. Remember what helped, so you can use it again.").assertExists()
    }

    @Test
    fun `the same stress is okay`() {
        show(before = 8, now = 8)
        compose.onNodeWithText("Still at 8, and that's okay.").assertExists()
        compose.onNodeWithText("Feelings take time to shift. Be gentle with yourself today.").assertExists()
    }

    @Test
    fun `higher stress thanks the person and names the hotline`() {
        show(before = 8, now = 9)
        compose.onNodeWithText("It went up to 9.").assertExists()
        compose.onNodeWithText(
            "Thank you for checking honestly. If it keeps rising, reach out to someone you trust or call NCMH at 1553.",
        ).assertExists()
    }

    @Test
    fun `Done and See my week call back`() {
        show(before = 8, now = 5)
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("See my week").performClick()
        assertEquals(listOf("done", "week"), events)
    }

    @Test
    fun `other moods have no hotline card after the check`() {
        show(before = 8, now = 9)
        compose.onNodeWithText("NCMH Crisis Hotline · available 24/7").assertDoesNotExist()
        compose.onNodeWithText("Call 1553").assertDoesNotExist()
    }

    @Test
    fun `Hopeless shows the hotline card again, whichever way stress went`() {
        // Safety (🔒): lower is the case most likely to drop it by mistake.
        show(before = 8, now = 5, moodCode = MoodCatalog.HOPELESS)
        compose.onNodeWithText("NCMH Crisis Hotline · available 24/7").assertExists()
        compose.onNodeWithText("Call 1553").assertExists()
        compose.onNodeWithText("Done").assertExists()
    }

    @Test
    fun `Hopeless with lower stress doesn't sound solved`() {
        show(before = 8, now = 5, moodCode = MoodCatalog.HOPELESS)
        compose.onNodeWithText("I'm glad something eased a little. Help is still here whenever you need it.")
            .assertExists()
        compose.onNodeWithText("That's 3 points lower", substring = true).assertDoesNotExist()
    }

    @Test
    fun `Hopeless with higher stress doesn't say to wait`() {
        show(before = 8, now = 9, moodCode = MoodCatalog.HOPELESS)
        compose.onNodeWithText(
            "Thank you for checking honestly. This sounds really heavy. Talking to someone can help, even right now.",
        ).assertExists()
        compose.onNodeWithText("If it keeps rising", substring = true).assertDoesNotExist()
    }
}
