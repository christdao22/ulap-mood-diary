package app.ulap.feature.logs

import androidx.compose.runtime.snapshots.Snapshot
import app.ulap.core.model.UserSettings
import app.ulap.core.testing.FakeCheckInRepository
import app.ulap.core.testing.FakeSettingsRepository
import app.ulap.core.testing.MainDispatcherRule
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import app.ulap.shared.model.Zone
import app.ulap.shared.rules.SearchNames
import app.ulap.shared.validation.CheckInInput
import java.time.LocalDate
import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
    private val settings = FakeSettingsRepository()
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

    private suspend fun log(
        loggedAt: String,
        stress: Int = 5,
        tzOffsetMin: Int = 480,
        moodCode: String = "calm",
        trigger: Trigger? = null,
        note: String? = null,
    ) {
        repository.add(
            CheckInInput(
                moodCode = moodCode,
                energy = 4,
                stress = stress,
                stressAfter = null,
                trigger = trigger,
                triggerOther = null,
                note = note,
                supportUsed = SupportType.NONE,
                isGoodMoment = false,
                loggedAt = Instant.parse(loggedAt),
                tzOffsetMin = tzOffsetMin,
            ),
        )
    }

    // The list's work runs on the test's dispatcher, so virtual time and runCurrent cover it.
    private fun viewModel() = LogsViewModel(repository, settings, clock, mainDispatcherRule.testDispatcher)

    private fun LogsViewModel.loaded() = assertIs<LogsUiState.Loaded>(uiState.value)

    private fun LogsViewModel.days() = loaded().days

    private fun LogsViewModel.moods() = days().flatMap { day -> day.day.checkIns.map { it.moodCode } }

    /**
     * The search and zone live in Compose state. In the app Compose tells snapshotFlow about a
     * change on the next frame; a unit test has no frames, so it sends that notice itself.
     */
    private fun TestScope.applyStateChanges() {
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    @Test
    fun `days are newest first and named Today, Yesterday, then by date`() =
        runTest(mainDispatcherRule.testDispatcher) {
            log("2026-11-05T01:00:00Z") // 9 AM Nov 5 in Manila
            log("2026-11-04T15:59:00Z") // 11:59 PM Nov 4
            log("2026-11-02T02:00:00Z") // Nov 2
            log("2025-12-31T02:00:00Z") // last year
            val viewModel = viewModel()
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
            val viewModel = viewModel()
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
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        assertEquals(DayLabel.Yesterday, viewModel.days().single().label)
    }

    @Test
    fun `Today becomes Yesterday at midnight while the screen is open`() = runTest(mainDispatcherRule.testDispatcher) {
        currentTime = Instant.parse("2026-11-05T15:58:00Z") // 11:58 PM Nov 5 in Manila
        log("2026-11-05T15:00:00Z")
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        assertEquals(DayLabel.Today, viewModel.days().single().label)

        currentTime = Instant.parse("2026-11-05T16:00:30Z") // 12:00:30 AM Nov 6
        advanceTimeBy(3.minutes)

        assertEquals(DayLabel.Yesterday, viewModel.days().single().label)
    }

    @Test
    fun `a new check-in shows up without reopening the screen`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        assertEquals(emptyList(), viewModel.days())

        log("2026-11-05T01:00:00Z")
        runCurrent()

        assertEquals(1, viewModel.days().single().day.count)
    }

    @Test
    fun `search narrows the list using the names on screen`() = runTest(mainDispatcherRule.testDispatcher) {
        log("2026-11-05T01:00:00Z", moodCode = "worried", trigger = Trigger.WORK)
        log("2026-11-05T02:00:00Z", moodCode = "calm", note = "Walked by the sea")
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.onSearchNames(
            SearchNames(moods = mapOf("worried" to "Nag-aalala"), triggers = mapOf(Trigger.WORK to "Trabaho")),
        )

        viewModel.onQueryChange("trabaho")
        applyStateChanges()
        assertEquals(listOf("worried"), viewModel.moods())

        viewModel.onQueryChange("SEA")
        applyStateChanges()
        assertEquals(listOf("calm"), viewModel.moods())
        // The day header sums up only what's shown.
        assertEquals(1, viewModel.days().single().day.count)
    }

    @Test
    fun `a zone chip keeps that zone, and tapping it again shows all`() = runTest(mainDispatcherRule.testDispatcher) {
        log("2026-11-05T01:00:00Z", moodCode = "worried")
        log("2026-11-05T02:00:00Z", moodCode = "calm")
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onZoneClick(Zone.RED)
        applyStateChanges()
        assertEquals(Zone.RED, viewModel.zone)
        assertEquals(listOf("worried"), viewModel.moods())

        viewModel.onZoneClick(Zone.RED)
        applyStateChanges()
        assertNull(viewModel.zone)
        assertEquals(listOf("calm", "worried"), viewModel.moods())
    }

    @Test
    fun `a long history lists all 1,000 check-ins and search reaches the oldest`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // 10 a day for 100 days, ending 9 PM Nov 5 in Manila; the oldest note is different.
            val newest = Instant.parse("2026-11-05T13:00:00Z")
            repeat(1_000) { i ->
                log(
                    loggedAt = (newest - (i / 10).days - (i % 10).hours).toString(),
                    note = if (i == 999) "First week with the diary" else "Note $i",
                )
            }
            val viewModel = viewModel()
            backgroundScope.launch { viewModel.uiState.collect {} }
            runCurrent()

            assertEquals(100, viewModel.days().size)
            assertEquals(1_000, viewModel.days().sumOf { it.day.count })
            assertEquals(DayLabel.Today, viewModel.days().first().label)

            viewModel.onQueryChange("first week")
            applyStateChanges()
            val oldest = viewModel.days().single()
            assertEquals(DayLabel.Date(LocalDate.of(2026, 7, 29), showYear = false), oldest.label)
            assertEquals(1, oldest.day.count)
        }

    @Test
    fun `no match is told apart from an empty diary`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        assertTrue(viewModel.loaded().isDiaryEmpty)
        assertFalse(viewModel.loaded().isNoMatch)

        log("2026-11-05T01:00:00Z", moodCode = "calm")
        viewModel.onZoneClick(Zone.BLUE)
        applyStateChanges()

        assertFalse(viewModel.loaded().isDiaryEmpty)
        assertTrue(viewModel.loaded().isNoMatch)
    }

    @Test
    fun `Delete asks first and deletes nothing on its own`() = runTest(mainDispatcherRule.testDispatcher) {
        log("2026-11-05T01:00:00Z")
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        val id = viewModel.days().single().day.checkIns.single().id

        viewModel.onDeleteClick(id)
        runCurrent()

        assertEquals(id, viewModel.pendingDeleteId)
        assertEquals(1, viewModel.days().single().day.count)
    }

    @Test
    fun `confirming the delete takes the check-in off the list`() = runTest(mainDispatcherRule.testDispatcher) {
        log("2026-11-05T01:00:00Z", moodCode = "worried")
        log("2026-11-05T02:00:00Z", moodCode = "calm")
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        val calm = viewModel.days().single().day.checkIns.first { it.moodCode == "calm" }

        viewModel.onDeleteClick(calm.id)
        viewModel.onDeleteConfirm()
        runCurrent()

        assertNull(viewModel.pendingDeleteId)
        assertNull(repository.get(calm.id))
        assertEquals(listOf("worried"), viewModel.moods())
    }

    @Test
    fun `Keep closes the question and keeps the check-in`() = runTest(mainDispatcherRule.testDispatcher) {
        log("2026-11-05T01:00:00Z")
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        val id = viewModel.days().single().day.checkIns.single().id

        viewModel.onDeleteClick(id)
        viewModel.onDeleteDismiss()
        // A stray confirm after Keep (a double tap) must not delete anything.
        viewModel.onDeleteConfirm()
        runCurrent()

        assertNull(viewModel.pendingDeleteId)
        assertEquals(1, viewModel.days().single().day.count)
    }

    @Test
    fun `Ulap shows on the empty diary unless the user turned Ulap off`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.showUlap.collect {} }
        runCurrent()
        assertTrue(viewModel.showUlap.value)

        settings.settings.value = UserSettings(mascotEnabled = false)
        runCurrent()

        assertFalse(viewModel.showUlap.value)
    }

    @Test
    fun `nothing is listed until the check-ins are read`() {
        val viewModel = viewModel()
        assertEquals(LogsUiState.Loading, viewModel.uiState.value)
    }
}
