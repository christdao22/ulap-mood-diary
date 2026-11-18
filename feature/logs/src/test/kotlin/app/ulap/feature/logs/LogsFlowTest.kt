package app.ulap.feature.logs

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.ulap.core.composetesting.HiltTestActivity
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.domain.checkin.CheckInRepository
import app.ulap.core.domain.checkin.SaveResult
import app.ulap.core.domain.settings.SettingsRepository
import app.ulap.core.testing.FakeCheckInRepository
import app.ulap.core.testing.FakeSettingsRepository
import app.ulap.feature.logs.navigation.LogsGraph
import app.ulap.feature.logs.navigation.logsGraph
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import app.ulap.shared.validation.CheckInInput
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Logs tab end to end (design-spec.md §7.6, technical-plan.md §15 "UI"): the real screen,
 * ViewModel and tab graph, reading the check-ins a repository holds, the way it reads Room. Search
 * and the zone chips narrow the list, Delete asks first, and Edit opens inside the tab.
 *
 * Features never import each other, so the edit screen here is a stand-in that shows the ID it
 * was given. EditCheckInFlowTest in feature:checkin runs the real one.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w360dp-h3000dp")
class LogsFlowTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<HiltTestActivity>()

    // 4:49 PM in Manila and still Nov 4 in UTC, so "Today" is the same day on any test machine.
    private val now = Instant.parse("2026-11-04T08:49:00Z")
    private val checkIns = FakeCheckInRepository(now = { now })

    @BindValue
    @JvmField
    val checkInRepository: CheckInRepository = checkIns

    @BindValue
    @JvmField
    val settingsRepository: SettingsRepository = FakeSettingsRepository()

    @BindValue
    @JvmField
    val clock: Clock = object : Clock {
        override fun now(): Instant = now
    }

    private lateinit var navController: NavHostController
    private var checkInNowClicks = 0

    /** Stands in for feature:checkin's edit screen, which the app root nests in this tab. */
    @Serializable
    private data class StandInEditRoute(val checkInId: String)

    private fun openLogs() = compose.setContent {
        UlapTheme {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = LogsGraph) {
                logsGraph(
                    onEditCheckIn = { navController.navigate(StandInEditRoute(it)) },
                    onCheckInNow = { checkInNowClicks++ },
                    nestedScreens = {
                        composable<StandInEditRoute> { entry ->
                            Text("Editing ${entry.toRoute<StandInEditRoute>().checkInId}")
                        }
                    },
                )
            }
        }
    }

    /** Saves a check-in the way the check-in flow does, and returns its ID. */
    private fun saveCheckIn(
        moodCode: String,
        stress: Int,
        loggedAt: Instant = now,
        trigger: Trigger? = Trigger.WORK,
        note: String? = null,
    ): String = runBlocking {
        val result = checkIns.add(
            CheckInInput(
                moodCode = moodCode,
                energy = 5,
                stress = stress,
                stressAfter = null,
                trigger = trigger,
                triggerOther = null,
                note = note,
                supportUsed = SupportType.NONE,
                isGoodMoment = false,
                loggedAt = loggedAt,
                tzOffsetMin = MANILA,
            ),
        )
        (result as SaveResult.Saved).checkIn.id
    }

    // The list is worked out on Dispatchers.Default, which the test clock doesn't wait for, so
    // each check waits for the screen to catch up.
    private fun waitForRow(mood: String) = compose.waitUntilAtLeastOneExists(row(mood), TIMEOUT)

    private fun waitForNoRow(mood: String) = compose.waitUntilDoesNotExist(row(mood), TIMEOUT)

    private fun waitForText(text: String) = compose.waitUntilAtLeastOneExists(hasText(text), TIMEOUT)

    /** A log row reads as one sentence that starts with its mood (design-spec.md §11). */
    private fun row(mood: String) = hasContentDescription("$mood. ", substring = true)

    private fun openRowMenu(mood: String) =
        compose.onNodeWithContentDescription("Edit or delete check-in: $mood", substring = true).performClick()

    private fun pressBack() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun `saved check-ins show under their day, and search and zone chips narrow them`() {
        saveCheckIn("calm", stress = 2, loggedAt = now - 1.days, trigger = null)
        saveCheckIn("worried", stress = 8, loggedAt = now - 2.hours, note = "big exam tomorrow")
        saveCheckIn("sad", stress = 6, trigger = Trigger.FAMILY)
        openLogs()

        waitForRow("Worried")
        // Each day header reads as one sentence for TalkBack.
        compose.onNodeWithContentDescription("Today. 2 check-ins, average stress 7.0.").assertExists()
        compose.onNodeWithContentDescription("Yesterday. 1 check-in, average stress 2.0.").assertExists()

        // A journal word finds its check-in and hides the rest.
        compose.onNodeWithContentDescription("Search mood, trigger or journal").performTextInput("exam")
        waitForNoRow("Sad")
        compose.onNode(row("Worried")).assertExists()
        compose.onNode(row("Calm")).assertDoesNotExist()

        // Nothing in the Green zone matches "exam", so Ulap says so instead of an empty screen.
        compose.onNodeWithText("Green").performClick()
        waitForText("No check-ins match. Try another word or zone.")

        compose.onNodeWithContentDescription("Clear search").performClick()
        waitForRow("Calm")
        compose.onNode(row("Worried")).assertDoesNotExist()

        // Tapping the selected chip again goes back to All.
        compose.onNodeWithText("Green").performClick()
        waitForRow("Sad")
        compose.onNode(row("Worried")).assertExists()
    }

    @Test
    fun `Delete asks first, Keep changes nothing, and Delete removes the check-in`() {
        saveCheckIn("worried", stress = 8)
        saveCheckIn("calm", stress = 2, loggedAt = now - 1.hours, trigger = null)
        openLogs()
        waitForRow("Worried")

        openRowMenu("Worried")
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete this check-in?").assertExists()
        compose.onNodeWithText("Keep").performClick()
        compose.onNodeWithText("Delete this check-in?").assertDoesNotExist()
        assertEquals(2, checkIns.saved.size)

        openRowMenu("Worried")
        compose.onNodeWithText("Delete").performClick()
        // The dialog's own Delete button, now the only "Delete" on screen.
        compose.onNodeWithText("Delete").performClick()

        waitForNoRow("Worried")
        compose.onNode(row("Calm")).assertExists()
        assertEquals(listOf("calm"), checkIns.saved.map { it.moodCode })
    }

    @Test
    fun `Edit opens that check-in inside the Logs tab, and Back returns to the list`() {
        val id = saveCheckIn("worried", stress = 8)
        openLogs()
        waitForRow("Worried")

        openRowMenu("Worried")
        compose.onNodeWithText("Edit").performClick()

        compose.onNodeWithText("Editing $id").assertExists()
        pressBack()
        waitForRow("Worried")
    }

    @Test
    fun `an empty diary offers Check in now, and a new check-in shows up while Logs is open`() {
        openLogs()
        waitForText("Your check-ins will show up here.")

        compose.onNodeWithText("Check in now").performClick()
        assertEquals(1, checkInNowClicks)

        // Saved from the Check in tab: Logs hears about it from the repository, not from a refresh.
        saveCheckIn("excited", stress = 3, trigger = null)
        waitForRow("Excited")
        compose.onNodeWithText("Your check-ins will show up here.").assertDoesNotExist()
    }

    private companion object {
        const val MANILA = 480
        const val TIMEOUT = 5_000L
    }
}
