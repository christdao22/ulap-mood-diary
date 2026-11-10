package app.ulap.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.ulap.core.designsystem.mascot.MoodFace
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.shared.model.Zone
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The accessibility rules each component promises (design-spec.md §6, §11): what TalkBack reads,
 * 48dp touch areas, and growing instead of clipping at 200% text.
 */
@RunWith(RobolectricTestRunner::class)
// Real text measurement, so the font-scale tests see real line heights.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComponentsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(fontScale: Float = 1f, content: @Composable () -> Unit) = compose.setContent {
        val density = LocalDensity.current.density
        CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
            UlapTheme(content)
        }
    }

    @Test
    fun `a mood tile reads as one sentence and opens on tap`() {
        var taps = 0
        show {
            MoodTile(
                name = "Worried",
                face = MoodFace.WORRIED,
                zone = Zone.RED,
                contentDescription = TILE_DESCRIPTION,
                onClick = { taps++ },
            )
        }

        compose.onNodeWithContentDescription(TILE_DESCRIPTION)
            .assertHasClickAction()
            .assertHeightIsAtLeast(52.dp)
            .performClick()
        // The visible name is replaced by the sentence, so TalkBack doesn't read it twice, and the
        // face is decorative: the sentence is the only thing on the tile TalkBack reads.
        compose.onNodeWithText("Worried").assertDoesNotExist()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .assertCountEquals(1)
        assertEquals(1, taps)
    }

    @Test
    fun `a mood tile without a face (Ulap off) still opens on tap`() {
        var taps = 0
        show {
            MoodTile(
                name = "Worried",
                face = null,
                zone = Zone.RED,
                contentDescription = TILE_DESCRIPTION,
                onClick = { taps++ },
            )
        }

        compose.onNodeWithContentDescription(TILE_DESCRIPTION)
            .assertHeightIsAtLeast(52.dp)
            .performClick()
        compose.onNodeWithText("Worried", useUnmergedTree = true).assertExists()
        assertEquals(1, taps)
    }

    @Test
    fun `a mood tile grows taller at 200 percent text instead of clipping`() {
        show(fontScale = 2f) {
            MoodTile(
                name = "Overwhelmed",
                face = MoodFace.OVERWHELMED,
                zone = Zone.RED,
                contentDescription = TILE_DESCRIPTION,
                onClick = {},
                modifier = Modifier.width(110.dp),
            )
        }

        // The face plus the name at 200% is well past the 52dp minimum.
        compose.onNodeWithContentDescription(TILE_DESCRIPTION).assertHeightIsAtLeast(80.dp)
    }

    @Test
    fun `trigger chips are single select, clear on a second tap and keep a 48dp touch area`() {
        show {
            var selected by remember { mutableStateOf<String?>(null) }
            Column(Modifier.selectableGroup()) {
                listOf("Work", "Family").forEach { option ->
                    TriggerChip(
                        label = option,
                        selected = selected == option,
                        onClick = { selected = if (selected == option) null else option },
                    )
                }
            }
        }
        val work = compose.onNodeWithText("Work")

        work.assertIsNotSelected()
        // The pill is 40dp; Material pads its touch area to 48dp (§11).
        work.assertTouchHeightIsEqualTo(48.dp)
        work.performClick().assertIsSelected()
        compose.onNodeWithText("Family").assertIsNotSelected()
        work.performClick().assertIsNotSelected()
    }

    @Test
    fun `the slider tells TalkBack its label and level and moves in whole steps`() {
        var stress = 0
        show {
            var value by remember { mutableIntStateOf(8) }
            LevelSlider(
                label = "Stress",
                value = value,
                onValueChange = {
                    value = it
                    stress = it
                },
                levelWord = "high",
                startLabel = "Very low",
                endLabel = "Very high",
                stateDescription = "$value of 10",
                zone = Zone.RED,
            )
        }
        val slider = compose.onNodeWithContentDescription("Stress")

        slider.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "8 of 10"))
        // The visible end labels are hidden from TalkBack; the slider already says it all.
        compose.onNodeWithText("Very low").assertDoesNotExist()

        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(3.4f) }
        assertEquals(3, stress)
        slider.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "3 of 10"))
    }

    @Test
    fun `buttons keep their minimum heights and the icon button its description`() {
        show {
            Column {
                UlapPrimaryButton(text = "Breathe with me", onClick = {})
                UlapSecondaryButton(text = "Maybe later", onClick = {}, enabled = false)
                UlapIconButton(onClick = {}, contentDescription = "Back to moods") {}
            }
        }

        compose.onNodeWithText("Breathe with me").assertHeightIsAtLeast(52.dp)
        compose.onNodeWithText("Maybe later").assertHeightIsAtLeast(48.dp).assertIsNotEnabled()
        compose.onNodeWithContentDescription("Back to moods")
            .assertHasClickAction()
            .assertTouchHeightIsEqualTo(48.dp)
    }

    @Test
    fun `the overflow button is named for TalkBack, keeps a 48dp touch area and opens its menu`() {
        var picked = ""
        show {
            UlapOverflowMenu(contentDescription = "Edit or delete this check-in") { close ->
                UlapMenuItem(
                    text = "Delete",
                    onClick = {
                        close()
                        picked = "Delete"
                    },
                )
            }
        }

        compose.onNodeWithContentDescription("Edit or delete this check-in")
            .assertHasClickAction()
            .assertTouchHeightIsEqualTo(48.dp)
            .assertTouchWidthIsEqualTo(48.dp)
            .performClick()
        compose.onNodeWithText("Delete").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals("Delete", picked)
        compose.onNodeWithText("Delete").assertDoesNotExist()
    }

    @Test
    fun `a primary button wraps a long label at 200 percent text instead of cutting it off`() {
        show(fontScale = 2f) {
            UlapPrimaryButton(
                text = "Gusto ba nimo moginhawa uban nako",
                onClick = {},
                modifier = Modifier.width(200.dp),
            )
        }

        compose.onNodeWithText("Gusto ba nimo moginhawa uban nako").assertHeightIsAtLeast(80.dp)
    }

    @Test
    fun `a settings switch row toggles from anywhere on the row and stays at least 56dp tall`() {
        var checked by mutableStateOf(true)
        show {
            UlapSettingsSwitchRow(title = "Show Ulap the cloud", checked = checked, onCheckedChange = { checked = it })
        }

        compose.onNodeWithText("Show Ulap the cloud")
            .assertHeightIsAtLeast(56.dp)
            .assertIsOn()
            .performClick()
            .assertIsOff()
    }

    @Test
    fun `a settings switch row wraps its title at 200 percent text instead of cutting it off`() {
        show(fontScale = 2f) {
            UlapSettingsSwitchRow(
                title = "Ipakita si Ulap ang panganod",
                checked = false,
                onCheckedChange = {},
                modifier = Modifier.width(240.dp),
            )
        }

        compose.onNodeWithText("Ipakita si Ulap ang panganod").assertHeightIsAtLeast(80.dp)
    }

    private companion object {
        const val TILE_DESCRIPTION = "Worried. Red zone. Default stress 8."
    }
}
