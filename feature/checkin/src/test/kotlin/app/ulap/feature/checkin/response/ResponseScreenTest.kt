package app.ulap.feature.checkin.response

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.shared.catalog.MoodCatalog
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import app.ulap.shared.rules.ResponseRules
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2000dp")
class ResponseScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private fun show(moodCode: String, stress: Int, trigger: Trigger? = null, startedSupport: SupportType? = null) {
        val mood = checkNotNull(MoodCatalog.byCode(moodCode))
        compose.setContent {
            UlapTheme {
                ResponseContent(
                    state = ResponseUiState.Ready(
                        mood = mood,
                        response = ResponseRules.respond(mood, stress, trigger),
                        startedSupport = startedSupport,
                        stressNow = stress,
                    ),
                    showUlap = true,
                    actions = ResponseScreenActions(
                        onStartSupport = { events += "support" },
                        onFinish = { events += "finish" },
                        onStressNowChange = { events += "stress $it" },
                        onStressCheckSave = { events += "check in" },
                    ),
                )
            }
        }
    }

    @Test
    fun `Red shows the title, the body with the mood word, and the trigger line`() {
        show("worried", stress = 6, trigger = Trigger.WORK)
        compose.onNodeWithText("That's a lot to carry right now.").assertExists()
        compose.onNodeWithText(
            "Feeling worried makes sense when things pile up. Your body is on high alert, and that's okay.",
        ).assertExists()
        compose.onNodeWithText("Work can take a lot out of us.").assertExists()
    }

    @Test
    fun `under stress 8 both buttons show, and each reports its tap`() {
        show("worried", stress = 6)
        compose.onNodeWithText("Breathe with me · 1 minute").performClick()
        compose.onNodeWithText("Maybe later").performClick()
        assertEquals(listOf("support", "finish"), events)
    }

    @Test
    fun `at stress 8 or more only the primary button shows`() {
        show("worried", stress = 8)
        compose.onNodeWithText("Breathe with me · 1 minute").assertExists()
        compose.onNodeWithText("Maybe later").assertDoesNotExist()
    }

    @Test
    fun `Yellow offers to save a good moment, with no trigger line`() {
        show("excited", stress = 3, trigger = Trigger.WORK)
        compose.onNodeWithText("Love this!").assertExists()
        compose.onNodeWithText("What made you feel excited? Remembering it can help on harder days.").assertExists()
        compose.onNodeWithText("Save to good moments").assertExists()
        compose.onNodeWithText("Work can take a lot out of us.").assertDoesNotExist()
        compose.onNodeWithText("How stressed do you feel now?").assertDoesNotExist()
    }

    @Test
    fun `Blue offers something small that helps`() {
        show("sad", stress = 6, trigger = Trigger.MONEY)
        compose.onNodeWithText("Thank you for being honest.").assertExists()
        compose.onNodeWithText("Money worries are real, and tiring.").assertExists()
        compose.onNodeWithText("Something small that helps").assertExists()
    }

    @Test
    fun `once a support has started, the buttons make way for it`() {
        show("worried", stress = 6, startedSupport = SupportType.BREATHE)
        compose.onNodeWithText("Breathe with me · 1 minute").assertDoesNotExist()
        compose.onNodeWithText("Maybe later").assertDoesNotExist()
    }

    @Test
    fun `after Breathe with me, the breathing card shows its steps`() {
        show("worried", stress = 6, startedSupport = SupportType.BREATHE)
        compose.onNodeWithText("In for 4 · hold for 2 · out for 6").assertExists()
        compose.onNodeWithText("Follow the circle. Five slow breaths is enough.").assertExists()
    }

    @Test
    fun `after Something small that helps, the list shows`() {
        show("sad", stress = 6, startedSupport = SupportType.SMALL_HELPS)
        compose.onNodeWithText(
            "Want to try one?",
        ).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithText("Drink a glass of water").assertExists()
        compose.onNodeWithText("Message someone, even just to say hi").assertExists()
        compose.onNodeWithText("Even one is enough.").assertExists()
    }

    @Test
    fun `after Save to good moments, it says so and Done ends the check-in`() {
        show("excited", stress = 3, startedSupport = SupportType.GOOD_MOMENT)
        compose.onNodeWithText("Saved to your good moments").assertExists()
        compose.onNodeWithText("Save to good moments").assertDoesNotExist()
        compose.onNodeWithText("Done").performClick()
        assertEquals(listOf("finish"), events)
    }

    @Test
    fun `Hopeless shows its message and no activity buttons`() {
        show("hopeless", stress = 9)
        compose.onNodeWithText("I'm really glad you told me.").assertExists()
        compose.onNodeWithText("Something small that helps").assertDoesNotExist()
        compose.onNodeWithText("Maybe later").assertDoesNotExist()
    }

    @Test
    fun `Red asks how stressed the person feels now, starting at the logged stress`() {
        show("worried", stress = 8)
        compose.onNodeWithText("When you're ready").assertExists()
        compose.onNodeWithText("How stressed do you feel now?")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithContentDescription("Stress")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "8 of 10, high"))
    }

    @Test
    fun `the stress check reports the slider and Check in`() {
        show("sad", stress = 6)
        compose.onNodeWithContentDescription("Stress")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(4f) }
        compose.onNodeWithText("Check in").performClick()
        assertEquals(listOf("stress 4", "check in"), events)
    }

    @Test
    fun `the stress check stays under a started support`() {
        show("worried", stress = 6, startedSupport = SupportType.BREATHE)
        compose.onNodeWithText("How stressed do you feel now?").assertExists()
    }

    @Test
    fun `Hopeless still shows the stress check`() {
        show("hopeless", stress = 9)
        compose.onNodeWithText("How stressed do you feel now?").assertExists()
        compose.onNodeWithText("Check in").assertExists()
    }

    @Test
    fun `Green has no stress check`() {
        show("calm", stress = 2)
        compose.onNodeWithText("How stressed do you feel now?").assertDoesNotExist()
    }
}
