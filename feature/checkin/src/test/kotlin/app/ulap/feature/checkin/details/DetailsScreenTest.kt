package app.ulap.feature.checkin.details

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.shared.catalog.MoodCatalog
import app.ulap.shared.model.Trigger
import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// A phone-width screen tall enough that the whole form is on screen at once.
@Config(qualifiers = "w360dp-h2000dp")
class DetailsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val systemZone = TimeZone.getDefault()
    private val events = mutableListOf<String>()

    @BeforeTest
    fun pinTimeZone() = TimeZone.setDefault(TimeZone.getTimeZone("Asia/Manila"))

    @AfterTest
    fun restoreTimeZone() = TimeZone.setDefault(systemZone)

    /** Shows Details with a tiny in-test state holder, so taps change what's on screen. */
    private fun show(moodCode: String = "worried", showUlap: Boolean = true) {
        compose.setContent {
            var form by remember {
                mutableStateOf(
                    DetailsForm(
                        mood = checkNotNull(MoodCatalog.byCode(moodCode)),
                        // 08:49 UTC is 4:49 PM in Manila.
                        loggedAt = Instant.parse("2026-11-04T08:49:00Z"),
                    ),
                )
            }
            UlapTheme {
                DetailsContent(
                    form = form,
                    showUlap = showUlap,
                    onBack = { events += "back" },
                    actions = object : DetailsActions {
                        override fun setEnergy(energy: Int) {
                            form = form.copy(energy = energy)
                        }

                        override fun setStress(stress: Int) {
                            form = form.copy(stress = stress)
                        }

                        override fun resetToDefaults() {
                            form = form.copy(energy = form.mood.defaultEnergy, stress = form.mood.defaultStress)
                        }

                        override fun toggleTrigger(trigger: Trigger) {
                            form = form.copy(trigger = trigger.takeUnless { it == form.trigger })
                        }

                        override fun setTriggerOther(text: String) {
                            form = form.copy(triggerOther = text)
                        }

                        override fun setNote(text: String) {
                            form = form.copy(note = text)
                        }

                        override fun save() {
                            events += "save"
                        }
                    },
                )
            }
        }
    }

    @Test
    fun `the header shows the date and time, the zone, the mood and Ulap's line`() {
        show("worried")
        compose.onNodeWithText("Wednesday, Nov 4 · 4:49 PM").assertExists()
        compose.onNodeWithText("RED ZONE").assertExists()
        compose.onNodeWithText("Worried").assertExists()
        compose.onNodeWithText("That can feel intense. I'm right here.").assertExists()
    }

    @Test
    fun `TalkBack reads each slider as name, value and word`() {
        show("worried")
        compose.onNode(hasStateDescription("6 of 10, medium") and hasContentDescription("Energy")).assertExists()
        compose.onNode(hasStateDescription("8 of 10, high") and hasContentDescription("Stress")).assertExists()
    }

    @Test
    fun `the defaults line appears only after a change, and Reset hides it again`() {
        show("worried")
        compose.onNodeWithText("Reset to default").assertDoesNotExist()
        compose.onNode(hasContentDescription("Stress")).performSemanticsAction(SemanticsActions.SetProgress) { it(5f) }
        compose.onNodeWithText("Default for Worried: energy 6, stress 8").assertExists()
        compose.onNodeWithText("Reset to default").performClick()
        compose.onNodeWithText("Reset to default").assertDoesNotExist()
        compose.onNode(hasStateDescription("8 of 10, high")).assertExists()
    }

    @Test
    fun `triggers are single-select, and tapping the picked one clears it`() {
        show()
        compose.onNodeWithText("Work").performClick().assertIsSelected()
        compose.onNodeWithText("Money").performClick().assertIsSelected()
        compose.onNodeWithText("Work").assertIsNotSelected()
        compose.onNodeWithText("Money").performClick().assertIsNotSelected()
    }

    @Test
    fun `Other opens a line for the person's own words`() {
        show()
        compose.onNodeWithText("In your own words").assertDoesNotExist()
        compose.onNodeWithText("Other").performClick()
        compose.onNodeWithText("In your own words").assertExists()
    }

    @Test
    fun `the journal's placeholder is the zone prompt`() {
        show("happy")
        compose.onNodeWithText("Journal · optional").assertExists()
        compose.onNodeWithText("What made this moment good?").assertExists()
    }

    @Test
    fun `Back and Save reach the caller`() {
        show()
        compose.onNodeWithContentDescription("Back to moods").performClick()
        compose.onNodeWithText("Save").performClick()
        assertEquals(listOf("back", "save"), events)
    }

    @Test
    fun `with Ulap turned off, the header text still shows`() {
        show("calm", showUlap = false)
        compose.onNodeWithText("Calm").assertExists()
        compose.onNodeWithText("That sounds peaceful.").assertExists()
    }

    private fun hasStateDescription(value: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)
}
