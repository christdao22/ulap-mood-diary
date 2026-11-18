package app.ulap.feature.reports

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import app.ulap.core.composetesting.HiltTestActivity
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.domain.checkin.CheckInRepository
import app.ulap.core.domain.settings.SettingsRepository
import app.ulap.core.testing.FakeCheckInRepository
import app.ulap.core.testing.FakeSettingsRepository
import app.ulap.feature.reports.navigation.ReportsRoute
import app.ulap.feature.reports.navigation.reportsScreen
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import app.ulap.shared.validation.CheckInInput
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Reports tab end to end (design-spec.md §7.7, technical-plan.md §15 "UI"): the real screen,
 * ViewModel and route, with every number worked out on the phone from the check-ins a repository
 * holds, the way it reads Room. Only the repositories and the clock are fakes.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w360dp-h3000dp")
class ReportsFlowTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<HiltTestActivity>()

    // Wednesday, Nov 4, 2026: 4:49 PM in Manila and still Nov 4 in UTC, so "today" is the same
    // day on any test machine. The weekly report is the 7 days up to today: Oct 29 – Nov 4.
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

    private fun openReports() = compose.setContent {
        UlapTheme {
            NavHost(navController = rememberNavController(), startDestination = ReportsRoute) {
                reportsScreen()
            }
        }
    }

    /** Saves a check-in the way the check-in flow does, so it lands in the repository like Room. */
    private fun saveCheckIn(moodCode: String, stress: Int, loggedAt: Instant, trigger: Trigger? = Trigger.WORK) =
        runBlocking {
            checkIns.add(
                CheckInInput(
                    moodCode = moodCode,
                    energy = 5,
                    stress = stress,
                    stressAfter = null,
                    trigger = trigger,
                    triggerOther = null,
                    note = null,
                    supportUsed = SupportType.NONE,
                    isGoodMoment = false,
                    loggedAt = loggedAt,
                    tzOffsetMin = MANILA,
                ),
            )
        }

    /** Three check-ins this week, on Wednesday, Tuesday and Monday: average stress 6.0. */
    private fun saveThisWeek() {
        saveCheckIn("worried", stress = 8, loggedAt = now - 1.hours)
        saveCheckIn("calm", stress = 4, loggedAt = now - 1.days)
        saveCheckIn("worried", stress = 6, loggedAt = now - 2.days, trigger = Trigger.FAMILY)
    }

    // The report is worked out on Dispatchers.Default, which the test clock doesn't wait for, so
    // each check waits for the screen to catch up.
    private fun waitForText(text: String) = compose.waitUntilAtLeastOneExists(hasText(text), TIMEOUT)

    private fun waitForDescription(text: String) =
        compose.waitUntilAtLeastOneExists(hasContentDescription(text), TIMEOUT)

    @Test
    fun `Reports opens on this week, with the numbers from this week's check-ins`() {
        saveThisWeek()
        openReports()

        waitForText("Oct 29 – Nov 4")
        compose.onNodeWithText("Weekly").assertIsSelected()
        compose.onNodeWithText("6.0").assertExists() // (8 + 4 + 6) / 3
        compose.onNodeWithText("Ulap noticed:").assertExists()
        compose.onNodeWithContentDescription("Work, 2 check-ins").assertExists()
        compose.onNodeWithContentDescription("Family, 1 check-in").assertExists()
        // There is nothing after today to step forward to.
        compose.onNodeWithContentDescription("Next period").assertDoesNotExist()
    }

    @Test
    fun `stepping back shows last week, and forward returns to this week`() {
        saveThisWeek()
        openReports()
        waitForText("Oct 29 – Nov 4")

        compose.onNodeWithContentDescription("Previous period").performClick()

        // No check-ins last week: Ulap asks for a few more instead of drawing empty charts.
        waitForText("Oct 22 – Oct 28")
        compose.onNodeWithText("A few more check-ins and I'll start seeing patterns with you.").assertExists()
        compose.onNodeWithText("Stress trend").assertDoesNotExist()

        compose.onNodeWithContentDescription("Next period").performClick()
        waitForText("Oct 29 – Nov 4")
        compose.onNodeWithText("Stress trend").assertExists()
        compose.onNodeWithContentDescription("Next period").assertDoesNotExist()
    }

    @Test
    fun `Daily and Monthly show today and this month, and a segment keeps the day in view`() {
        saveThisWeek()
        openReports()
        waitForText("Oct 29 – Nov 4")

        compose.onNodeWithText("Daily").performClick()
        waitForText("Today")
        compose.onNodeWithText("Daily").assertIsSelected()

        compose.onNodeWithText("Monthly").performClick()
        waitForText("November 2026")

        // Weekly, back one week, then Daily: the last day of that week, not today.
        compose.onNodeWithText("Weekly").performClick()
        waitForText("Oct 29 – Nov 4")
        compose.onNodeWithContentDescription("Previous period").performClick()
        waitForText("Oct 22 – Oct 28")
        compose.onNodeWithText("Daily").performClick()
        waitForText("Wed, Oct 28")
    }

    @Test
    fun `a check-in saved while Reports is open updates the report`() {
        saveCheckIn("worried", stress = 8, loggedAt = now - 1.hours)
        saveCheckIn("calm", stress = 4, loggedAt = now - 1.days)
        openReports()
        waitForText("A few more check-ins and I'll start seeing patterns with you.")

        // Saved from the Check in tab: Reports hears about it from the repository, not from a refresh.
        saveCheckIn("worried", stress = 6, loggedAt = now - 2.days, trigger = Trigger.FAMILY)

        waitForText("Stress trend")
        compose.onNodeWithText("6.0").assertExists()
        waitForDescription("Family, 1 check-in")
    }

    private companion object {
        const val MANILA = 480
        const val TIMEOUT = 5_000L
    }
}
