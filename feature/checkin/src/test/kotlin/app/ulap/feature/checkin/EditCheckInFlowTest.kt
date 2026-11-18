package app.ulap.feature.checkin

import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.ulap.core.composetesting.HiltTestActivity
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.domain.checkin.CheckInRepository
import app.ulap.core.domain.checkin.SaveResult
import app.ulap.core.domain.settings.SettingsRepository
import app.ulap.core.testing.FakeCheckInRepository
import app.ulap.core.testing.FakeSettingsRepository
import app.ulap.feature.checkin.navigation.editCheckInScreen
import app.ulap.feature.checkin.navigation.navigateToEditCheckIn
import app.ulap.shared.model.CheckIn
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
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Editing a check-in from Logs (design-spec.md §7.6): Details in edit mode, opened the way the app
 * root wires Logs' Edit to it. Save and Back return to Logs, and a check-in deleted elsewhere
 * closes the screen. Features never import each other, so Logs here is a stand-in; LogsFlowTest in
 * feature:logs checks that Edit sends the row's ID.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w360dp-h3000dp")
class EditCheckInFlowTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<HiltTestActivity>()

    private val now = Instant.parse("2026-11-04T08:49:00Z")
    private val loggedAt = now - 2.hours
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

    /**
     * Stands in for feature:logs' list, which the edit screen sits on top of. Not private:
     * kotlinx.serialization reads a data object's instance by reflection.
     */
    @Serializable
    internal data object StandInLogsRoute

    /** Saves a Worried check-in (stress 8, Work) two hours ago and opens it for editing from Logs. */
    private fun editSavedCheckIn(): CheckIn {
        val saved = runBlocking {
            checkIns.add(
                CheckInInput(
                    moodCode = "worried",
                    energy = 5,
                    stress = 8,
                    stressAfter = null,
                    trigger = Trigger.WORK,
                    triggerOther = null,
                    note = null,
                    supportUsed = SupportType.NONE,
                    isGoodMoment = false,
                    loggedAt = loggedAt,
                    tzOffsetMin = MANILA,
                ),
            )
        }.let { (it as SaveResult.Saved).checkIn }
        compose.setContent {
            UlapTheme {
                navController = rememberNavController()
                NavHost(navController = navController, startDestination = StandInLogsRoute) {
                    composable<StandInLogsRoute> { Text(LOGS) }
                    editCheckInScreen(navController)
                }
            }
        }
        compose.runOnUiThread { navController.navigateToEditCheckIn(saved.id) }
        // The form fills in from the repository once the ViewModel has read the check-in.
        compose.waitUntilAtLeastOneExists(hasContentDescription("Back to logs"), TIMEOUT)
        return saved
    }

    private fun assertBackOnLogs() = compose.waitUntilAtLeastOneExists(hasText(LOGS), TIMEOUT)

    @Test
    fun `Save changes the same check-in, keeps its time, and returns to Logs`() {
        val original = editSavedCheckIn()

        compose.onNodeWithContentDescription("Stress")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(3f) }
        compose.onNodeWithText("Family").performClick()
        compose.onNodeWithText("Save").performClick()

        assertBackOnLogs()
        val edited = checkIns.saved.single()
        assertEquals(original.id, edited.id)
        assertEquals("worried", edited.moodCode)
        assertEquals(3, edited.stress)
        assertEquals(Trigger.FAMILY, edited.trigger)
        assertEquals(loggedAt, edited.loggedAt)
    }

    @Test
    fun `Back to logs leaves the check-in as it was`() {
        val original = editSavedCheckIn()

        compose.onNodeWithContentDescription("Stress")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(3f) }
        compose.onNodeWithContentDescription("Back to logs").performClick()

        assertBackOnLogs()
        assertEquals(listOf(original), checkIns.saved)
    }

    @Test
    fun `Save on a check-in deleted elsewhere closes Details and doesn't bring it back`() {
        val original = editSavedCheckIn()

        // Deleted on another phone and synced down while this one has it open. Details finds
        // out on Save, so it never sweeps the screen away mid-edit (design-spec.md §7.6).
        runBlocking { checkIns.delete(original.id) }
        compose.onNodeWithText("Save").performClick()

        assertBackOnLogs()
        assertEquals(emptyList(), checkIns.saved)
    }

    private companion object {
        const val MANILA = 480
        const val TIMEOUT = 5_000L
        const val LOGS = "Logs list"
    }
}
