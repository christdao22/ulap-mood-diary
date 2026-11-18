package app.ulap.feature.checkin

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import app.ulap.core.composetesting.HiltTestActivity
import app.ulap.core.designsystem.motion.rememberScreenTransitions
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.domain.checkin.CheckInRepository
import app.ulap.core.domain.settings.SettingsRepository
import app.ulap.core.testing.FakeCheckInRepository
import app.ulap.core.testing.FakeSettingsRepository
import app.ulap.feature.checkin.navigation.CheckInGraph
import app.ulap.feature.checkin.navigation.PickMoodRoute
import app.ulap.feature.checkin.navigation.checkInGraph
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The whole check-in, screen to screen: Pick mood → Details → Response → After (design-spec.md
 * §7.2–§7.5, technical-plan.md §15 "UI"). Unlike the screen tests, this runs the real screens, their
 * real ViewModels and the real navigation graph. Only the repositories and the clock are fakes,
 * so nothing here needs a network: the check-in works offline.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
// A phone-width screen tall enough that every screen fits without scrolling.
@Config(application = HiltTestApplication::class, qualifiers = "w360dp-h3000dp")
class CheckInFlowTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<HiltTestActivity>()

    private val now = Instant.parse("2026-11-04T08:49:00Z")
    private val checkIns = FakeCheckInRepository(now = { now })
    private val settings = FakeSettingsRepository()

    // @BindValue puts these into Hilt's graph in place of Room, DataStore and the system clock.
    @BindValue
    @JvmField
    val checkInRepository: CheckInRepository = checkIns

    @BindValue
    @JvmField
    val settingsRepository: SettingsRepository = settings

    @BindValue
    @JvmField
    val clock: Clock = object : Clock {
        override fun now(): Instant = now
    }

    private lateinit var navController: NavHostController
    private var seeMyWeekClicks = 0

    private fun startCheckIn() = compose.setContent {
        UlapTheme {
            navController = rememberNavController()
            val transitions = rememberScreenTransitions()
            NavHost(navController = navController, startDestination = CheckInGraph) {
                checkInGraph(
                    navController = navController,
                    transitions = transitions,
                    onSeeMyWeek = { seeMyWeekClicks++ },
                )
            }
        }
    }

    private fun pickMood(name: String) = compose.onNodeWithContentDescription("$name.", substring = true).performClick()

    private fun pressBack() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    private fun assertOnPickMood() {
        compose.onNodeWithText("How are you feeling right now?", substring = true).assertExists()
        assertEquals(true, navController.currentDestination?.hasRoute(PickMoodRoute::class))
    }

    @Test
    fun `a Red check-in goes from the mood to a calmer stress check and back to Pick mood`() {
        startCheckIn()
        pickMood("Worried")

        // Details opens with Worried's defaults (stress 8) and its pre-message.
        compose.onNodeWithText("That can feel intense. I'm right here.").assertExists()
        compose.onNodeWithText("Save").performClick()

        // Response: at stress 8 the stress check waits until a support has started.
        compose.onNodeWithText("That's a lot to carry right now.").assertExists()
        compose.onNodeWithText("How stressed do you feel now?").assertDoesNotExist()
        compose.onNodeWithText("Breathe with me · 1 minute").performClick()
        compose.onNodeWithContentDescription("Stress")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(5f) }
        compose.onNodeWithText("Check in").performClick()

        // After compares the two numbers, and both are saved on the one check-in.
        compose.onNodeWithText("Your stress went from 8 to 5.").assertExists()
        val saved = checkIns.saved.single()
        assertEquals("worried", saved.moodCode)
        assertEquals(8, saved.stress)
        assertEquals(5, saved.stressAfter)

        compose.onNodeWithText("Done").performClick()
        assertOnPickMood()
    }

    @Test
    fun `Back from Response goes to Pick mood, not to the saved form`() {
        startCheckIn()
        pickMood("Worried")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("That's a lot to carry right now.").assertExists()

        pressBack()

        assertOnPickMood()
        assertEquals(1, checkIns.saved.size)
    }

    @Test
    fun `Back from Details returns to Pick mood and saves nothing`() {
        startCheckIn()
        pickMood("Calm")
        compose.onNodeWithText("That sounds peaceful.").assertExists()

        compose.onNodeWithContentDescription("Back to moods").performClick()

        assertOnPickMood()
        assertTrue(checkIns.saved.isEmpty())
    }

    @Test
    fun `a Yellow check-in can be saved as a good moment, and Done ends it`() {
        startCheckIn()
        pickMood("Excited")
        compose.onNodeWithText("Save").performClick()

        compose.onNodeWithText("Love this!").assertExists()
        compose.onNodeWithText("Save to good moments").performClick()
        compose.onNodeWithText("Saved to your good moments").assertExists()
        assertTrue(checkIns.saved.single().isGoodMoment)

        compose.onNodeWithText("Done").performClick()
        assertOnPickMood()
    }

    @Test
    fun `a Blue check-in can end with Maybe later`() {
        startCheckIn()
        pickMood("Sad")
        compose.onNodeWithText("Save").performClick()

        compose.onNodeWithText("Thank you for being honest.").assertExists()
        compose.onNodeWithText("Maybe later").performClick()

        assertOnPickMood()
        assertEquals("sad", checkIns.saved.single().moodCode)
    }

    @Test
    fun `Hopeless reaches the NCMH 1553 card and the dialer with no network at all`() {
        startCheckIn()
        pickMood("Hopeless")
        compose.onNodeWithText("Save").performClick()

        compose.onNodeWithText("I'm really glad you told me.").assertExists()
        compose.onNodeWithText("NCMH Crisis Hotline · available 24/7").assertExists()
        compose.onNodeWithText("Call 1553").performClick()

        val intent = checkNotNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals("tel:1553", intent.dataString)

        // A quiet Done is the way out (design-spec.md §7.4).
        compose.onNodeWithText("Done").performClick()
        assertOnPickMood()
    }

    @Test
    fun `See my week ends the check-in before the Reports tab opens`() {
        startCheckIn()
        pickMood("Worried")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Breathe with me · 1 minute").performClick()
        compose.onNodeWithText("Check in").performClick()

        compose.onNodeWithText("See my week").performClick()

        assertEquals(1, seeMyWeekClicks)
        // Coming back to the Check in tab must open Pick mood, not the After screen.
        assertOnPickMood()
    }
}
