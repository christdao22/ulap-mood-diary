package app.ulap.core.designsystem.mascot

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.core.app.ApplicationProvider
import app.ulap.core.designsystem.motion.rememberReducedMotion
import app.ulap.core.designsystem.theme.UlapTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UlapTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `every expression draws, with its description for TalkBack`() {
        compose.setContent {
            UlapTheme {
                Column {
                    UlapExpression.entries.forEach { Ulap(it, contentDescription = "Ulap ${it.name}", float = false) }
                }
            }
        }

        // Drawing runs every path through the parser, so a typo in a spec path fails here.
        compose.onRoot().captureToImage()
        UlapExpression.entries.forEach { compose.onNodeWithContentDescription("Ulap ${it.name}").assertExists() }
    }

    @Test
    fun `Ulap is sized by width with the 130 by 110 shape`() {
        compose.setContent {
            UlapTheme { Ulap(UlapExpression.CALM, contentDescription = "Ulap", size = UlapSize.Response) }
        }
        val ulap = compose.onNodeWithContentDescription("Ulap")

        ulap.assertWidthIsEqualTo(168.dp)
        assertEquals(168f * 110f / 130f, ulap.getUnclippedBoundsInRoot().height.value, absoluteTolerance = 0.5f)
    }

    @Test
    fun `a decorative Ulap is skipped by TalkBack`() {
        compose.setContent {
            UlapTheme {
                Column {
                    Ulap(UlapExpression.NEUTRAL, contentDescription = "Ulap")
                    Ulap(UlapExpression.NEUTRAL, contentDescription = null)
                }
            }
        }

        compose.onAllNodesWithContentDescription("Ulap").assertCountEquals(1)
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Image)).assertCountEquals(1)
    }

    @Test
    fun `reduced motion follows the system Remove animations setting`() {
        var reduced: Boolean? = null
        compose.setContent { reduced = rememberReducedMotion() }
        compose.waitForIdle()
        assertFalse(reduced!!)
    }

    @Test
    fun `reduced motion is on when the animator scale is 0`() {
        val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        var reduced: Boolean? = null
        compose.setContent { reduced = rememberReducedMotion() }
        compose.waitForIdle()
        assertTrue(reduced!!)
    }
}
