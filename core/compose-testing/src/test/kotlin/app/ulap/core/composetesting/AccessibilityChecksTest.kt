package app.ulap.core.composetesting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The checks must catch a screen that breaks the rules, or the screen tests prove nothing. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class AccessibilityChecksTest {
    @get:Rule
    val compose = createComposeRule()

    private val body = TextStyle(fontSize = 14.sp, lineHeight = 20.sp)

    @Test
    fun `a lone 36dp button passes, because Compose stretches its touch area to 48dp`() {
        compose.setContent { BasicText("Tap", Modifier.size(36.dp).clickable {}) }
        compose.assertTouchTargetsAtLeast48dp()
    }

    @Test
    fun `two 36dp buttons side by side fail and name each other`() {
        compose.setContent {
            Row {
                BasicText("Edit", Modifier.size(36.dp).clickable {})
                BasicText("Delete", Modifier.size(36.dp).clickable {})
            }
        }
        val error = assertFailsWith<AssertionError> { compose.assertTouchTargetsAtLeast48dp() }
        assertTrue("\"Edit\"" in error.message.orEmpty() && "\"Delete\"" in error.message.orEmpty(), error.message)
    }

    @Test
    fun `two 48dp buttons side by side pass`() {
        compose.setContent {
            Row {
                BasicText("Edit", Modifier.size(48.dp).clickable {})
                BasicText("Delete", Modifier.size(48.dp).clickable {})
            }
        }
        compose.assertTouchTargetsAtLeast48dp()
    }

    @Test
    fun `a button inside a clickable row passes`() {
        compose.setContent {
            Row(Modifier.clickable {}) {
                BasicText("Row")
                BasicText("Menu", Modifier.size(36.dp).clickable {})
            }
        }
        compose.assertTouchTargetsAtLeast48dp()
    }

    @Test
    fun `an icon button with no description fails the name check`() {
        compose.setContent { Box(Modifier.size(48.dp).clickable {}) }
        assertFailsWith<AssertionError> { compose.assertTouchTargetsHaveNames() }
    }

    @Test
    fun `a button with text or a description passes the name check`() {
        compose.setContent {
            Row {
                BasicText("Save", Modifier.size(48.dp).clickable {})
                Box(Modifier.size(48.dp).semantics { contentDescription = "Back" }.clickable {})
            }
        }
        compose.assertTouchTargetsHaveNames()
    }

    @Test
    fun `text that fits at normal size but not at 200 percent fails only at 200 percent`() {
        // 20sp of line height fits a 24dp box once, not twice.
        val content = @Composable {
            Box(Modifier.height(24.dp)) { BasicText("Saved", style = body) }
        }
        compose.setContent {
            Column {
                content()
                LargestFontScale { content() }
            }
        }
        val error = assertFailsWith<AssertionError> { compose.assertNoTextCutOff() }
        assertTrue("height" in error.message.orEmpty(), error.message)
        assertTrue(error.message.orEmpty().lines().size == 2, error.message)
    }

    @Test
    fun `a long line held to one line fails at 200 percent`() {
        compose.setContent {
            LargestFontScale {
                BasicText("Saved on this phone. Will back up when you're online.", style = body, maxLines = 1)
            }
        }
        val error = assertFailsWith<AssertionError> { compose.assertNoTextCutOff() }
        assertTrue("line" in error.message.orEmpty(), error.message)
    }

    @Test
    fun `a preview the spec shortens on purpose can be named and passes`() {
        val note = "Saved on this phone. Will back up when you're online."
        compose.setContent { LargestFontScale { BasicText(note, style = body, maxLines = 1) } }
        compose.assertNoTextCutOff(cutOnPurpose = setOf(note))
    }

    @Test
    fun `wrapping text with room to grow passes at 200 percent`() {
        compose.setContent {
            LargestFontScale { BasicText("Saved on this phone. Will back up when you're online.", style = body) }
        }
        compose.assertNoTextCutOff()
    }
}
