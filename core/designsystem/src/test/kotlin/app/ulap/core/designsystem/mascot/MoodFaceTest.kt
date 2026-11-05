package app.ulap.core.designsystem.mascot

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.shared.catalog.MoodCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** The mood faces on the pick-mood tiles (design-spec.md §8.7). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MoodFaceTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `every mood in the catalog has exactly one face, and every face has a mood`() {
        assertEquals(MoodCatalog.all.map { it.code }.sorted(), MoodFace.entries.map { it.moodCode }.sorted())
        MoodCatalog.all.forEach { assertEquals(it.code, MoodFace.forMood(it.code)?.moodCode) }
    }

    @Test
    fun `an unknown mood code has no face`() {
        assertEquals(null, MoodFace.forMood("stressed"))
    }

    @Test
    fun `Hopeless keeps the quiet face and has no extra drawing`() {
        // Safety-sensitive (§8.5, §8.7): the same still face as the Hopeless response, nothing added.
        assertEquals(emptyList(), MoodFace.HOPELESS.extras)
        assertEquals(emptyList(), MoodFace.HOPELESS.fills)
        assertEquals(listOf("M62 74 L70 74"), MoodFace.HOPELESS.lines)
        assertEquals(listOf(Eye(55f, 62f, 3f), Eye(77f, 62f, 3f)), MoodFace.HOPELESS.eyes)
    }

    @Test
    fun `every face draws, and TalkBack skips it`() {
        compose.setContent {
            UlapTheme { Column { MoodFace.entries.forEach { MoodFaceIcon(it) } } }
        }

        // Drawing runs every path through the parser, so a typo in a path fails here.
        compose.onRoot().captureToImage()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .assertCountEquals(0)
    }

    @Test
    fun `a mood face is 46dp wide`() {
        compose.setContent {
            UlapTheme { MoodFaceIcon(MoodFace.CALM, Modifier.testTag("face")) }
        }
        compose.onNodeWithTag("face").assertWidthIsEqualTo(46.dp)
    }
}
