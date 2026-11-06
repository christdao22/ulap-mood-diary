package app.ulap.feature.logs

import app.ulap.core.testing.FakeCheckInRepository
import app.ulap.core.testing.MainDispatcherRule
import app.ulap.shared.model.SupportType
import app.ulap.shared.validation.CheckInInput
import java.time.LocalDate
import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule

@OptIn(ExperimentalCoroutinesApi::class)
class LogsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Saves anything logged up to 2027, so each test picks its own "now" on the clock below.
    private val repository = FakeCheckInRepository(now = { Instant.parse("2027-01-01T00:00:00Z") })
    private val systemZone = TimeZone.getDefault()

    /** 2:00 PM on Nov 5 in Manila. */
    private var currentTime = Instant.parse("2026-11-05T06:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = currentTime
    }

    // "Today" follows the phone's own time zone, so pin one: Manila, UTC+8.
    @BeforeTest
    fun pinTimeZone() = TimeZone.setDefault(TimeZone.getTimeZone("Asia/Manila"))

    @AfterTest
    fun restoreTimeZone() = TimeZone.setDefault(systemZone)

    private suspend fun log(loggedAt: String, stress: Int = 5, tzOffsetMin: Int = 480) {
        repository.add(
            CheckInInput(
                moodCode = "calm",
                energy = 4,
                stress = stress,
                stressAfter = null,
                trigger = null,
                triggerOther = null,
                note = null,
                supportUsed = SupportType.NONE,
                isGoodMoment = false,
                loggedAt = Instant.parse(loggedAt),
                tzOffsetMin = tzOffsetMin,
            ),
        )
    }

    private fun LogsViewModel.days() = assertIs<LogsUiState.Loaded>(uiState.value).days

    @Test
    fun `days are newest first and named Today, Yesterday, then by date`() =
        runTest(mainDispatcherRule.testDispatcher) {
            log("2026-11-05T01:00:00Z") // 9 AM Nov 5 in Manila
            log("2026-11-04T15:59:00Z") // 11:59 PM Nov 4
            log("2026-11-02T02:00:00Z") // Nov 2
            log("2025-12-31T02:00:00Z") // last year
            val viewModel = LogsViewModel(repository, clock)
            backgroundScope.launch { viewModel.uiState.collect {} }
            runCurrent()

            assertEquals(
                listOf(
                    DayLabel.Today,
                    DayLabel.Yesterday,
                    DayLabel.Date(LocalDate.of(2026, 11, 2), showYear = false),
                    DayLabel.Date(LocalDate.of(2025, 12, 31), showYear = true),
                ),
                viewModel.days().map { it.label },
            )
        }

    @Test
    fun `a day header counts the day's check-ins and averages their stress`() =
        runTest(mainDispatcherRule.testDispatcher) {
            log("2026-11-05T01:00:00Z", stress = 8)
            log("2026-11-05T02:00:00Z", stress = 3)
            val viewModel = LogsViewModel(repository, clock)
            backgroundScope.launch { viewModel.uiState.collect {} }
            runCurrent()

            val today = viewModel.days().single().day
            assertEquals(2, today.count)
            assertEquals(5.5, today.averageStress)
        }

    @Test
    fun `a check-in stays on the day of the clock it was logged on`() = runTest(mainDispatcherRule.testDispatcher) {
        // 11 PM Nov 4 in New York is already Nov 5 in Manila, but it was logged in New York.
        log("2026-11-05T04:00:00Z", tzOffsetMin = -300)
        val viewModel = LogsViewModel(repository, clock)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        assertEquals(DayLabel.Yesterday, viewModel.days().single().label)
    }

    @Test
    fun `Today becomes Yesterday at midnight while the screen is open`() = runTest(mainDispatcherRule.testDispatcher) {
        currentTime = Instant.parse("2026-11-05T15:58:00Z") // 11:58 PM Nov 5 in Manila
        log("2026-11-05T15:00:00Z")
        val viewModel = LogsViewModel(repository, clock)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        assertEquals(DayLabel.Today, viewModel.days().single().label)

        currentTime = Instant.parse("2026-11-05T16:00:30Z") // 12:00:30 AM Nov 6
        advanceTimeBy(3.minutes)

        assertEquals(DayLabel.Yesterday, viewModel.days().single().label)
    }

    @Test
    fun `a new check-in shows up without reopening the screen`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = LogsViewModel(repository, clock)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        assertEquals(emptyList(), viewModel.days())

        log("2026-11-05T01:00:00Z")
        runCurrent()

        assertEquals(1, viewModel.days().single().day.count)
    }

    @Test
    fun `nothing is listed until the check-ins are read`() {
        val viewModel = LogsViewModel(repository, clock)
        assertEquals(LogsUiState.Loading, viewModel.uiState.value)
    }
}
