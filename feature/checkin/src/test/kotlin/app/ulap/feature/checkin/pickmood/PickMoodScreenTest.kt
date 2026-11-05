package app.ulap.feature.checkin.pickmood

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.ui.R
import app.ulap.core.ui.nameRes
import app.ulap.shared.catalog.MoodCatalog
import app.ulap.shared.rules.TimeOfDay
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// A phone-width screen tall enough that all four zone cards are on screen at once.
@Config(qualifiers = "w360dp-h3000dp")
class PickMoodScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val picked = mutableListOf<String>()

    private fun show(timeOfDay: TimeOfDay = TimeOfDay.AFTERNOON, showUlap: Boolean = true) = compose.setContent {
        UlapTheme {
            PickMoodContent(
                uiState = PickMoodUiState(timeOfDay = timeOfDay, showUlap = showUlap),
                onMoodClick = { picked += it },
            )
        }
    }

    @Test
    fun `Ulap greets for the time of day and says any answer is okay`() {
        show(timeOfDay = TimeOfDay.MORNING)
        compose.onNodeWithText("Good morning", substring = true).assertExists()
        compose.onNodeWithText("How are you feeling right now?", substring = true).assertExists()
        compose.onNodeWithText("Any answer is okay.", substring = true).assertExists()
    }

    @Test
    fun `night uses the evening greeting`() {
        show(timeOfDay = TimeOfDay.NIGHT)
        compose.onNodeWithText("Good evening", substring = true).assertExists()
    }

    @Test
    fun `the greeting still shows when Ulap is turned off`() {
        show(showUlap = false)
        compose.onNodeWithText("How are you feeling right now?", substring = true).assertExists()
    }

    @Test
    fun `the zones come in order Red, Blue, Yellow, Green, each as a heading`() {
        show()
        val headings = compose.onAllNodes(isHeading()).fetchSemanticsNodes().map { node ->
            node.config[SemanticsProperties.Text].joinToString(" ")
        }
        assertEquals(
            listOf(
                "Red Zone tense · high energy",
                "Blue Zone heavy · low energy",
                "Yellow Zone bright · high energy",
                "Green Zone easy · low energy",
            ),
            headings,
        )
    }

    @Test
    fun `TalkBack reads each tile as mood, zone and default stress`() {
        show()
        compose.onNodeWithContentDescription("Worried. Red Zone. Default stress 8.").assertHasClickAction()
        compose.onNodeWithContentDescription("Calm. Green Zone. Default stress 1.").assertHasClickAction()
    }

    @Test
    fun `all 25 moods are on the screen, once each`() {
        show()
        MoodCatalog.all.forEach { mood ->
            val name = context.getString(mood.nameRes)
            val matches = compose.onAllNodesWithText(name, useUnmergedTree = true).fetchSemanticsNodes()
            // The tile's own text is hidden from TalkBack, so it shows up only in the unmerged tree.
            assertEquals(1, matches.size, "tiles named $name")
        }
    }

    @Test
    fun `with Ulap turned off, every mood is still there and still reads the same`() {
        show(showUlap = false)
        MoodCatalog.all.forEach { mood ->
            val name = context.getString(mood.nameRes)
            assertEquals(1, compose.onAllNodesWithText(name, useUnmergedTree = true).fetchSemanticsNodes().size)
        }
        compose.onNodeWithContentDescription("Worried. Red Zone. Default stress 8.").assertHasClickAction()
    }

    @Test
    fun `tapping a tile hands its mood code to the next screen`() {
        show()
        val name = context.getString(R.string.mood_burned_out)
        compose.onNodeWithContentDescription("$name.", substring = true).performClick()
        compose.onNodeWithContentDescription("Calm.", substring = true).performClick()
        assertEquals(listOf("burned_out", "calm"), picked)
    }
}
