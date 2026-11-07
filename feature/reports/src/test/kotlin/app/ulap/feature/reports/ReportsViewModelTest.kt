package app.ulap.feature.reports

import androidx.compose.runtime.snapshots.Snapshot
import app.ulap.core.testing.FakeCheckInRepository
import app.ulap.core.testing.FakeSettingsRepository
import app.ulap.core.testing.MainDispatcherRule
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import app.ulap.shared.rules.ReportInsight
import app.ulap.shared.rules.ReportPeriodType
import app.ulap.shared.validation.CheckInInput
import java.time.LocalDate
import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeCheckInRepository(now = { Instant.parse("2027-01-01T00:00:00Z") })
    private val systemZone = TimeZone.getDefault()

    /** 2:00 PM Thursday Nov 5, 2026 in Manila. */
    private val clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-11-05T06:00:00Z")
    }

    @BeforeTest
    fun pinTimeZone() = TimeZone.setDefault(TimeZone.getTimeZone("Asia/Manila"))

    @AfterTest
    fun restoreTimeZone() = TimeZone.setDefault(systemZone)

    private fun day(iso: String) = LocalDate.parse(iso).toEpochDay()

    private suspend fun log(
        loggedAt: String,
        stress: Int,
        moodCode: String,
        trigger: Trigger? = null,
        stressAfter: Int? = null,
        support: SupportType = SupportType.NONE,
    ) = repository.add(
        CheckInInput(
            moodCode = moodCode,
            energy = 4,
            stress = stress,
            stressAfter = stressAfter,
            trigger = trigger,
            triggerOther = null,
            note = null,
            supportUsed = support,
            isGoodMoment = false,
            loggedAt = Instant.parse(loggedAt),
            tzOffsetMin = 480,
        ),
    )

    /** The hand-checked data set: three check-ins this week, one the week before. */
    private suspend fun seed() {
        log("2026-11-05T01:00:00Z", stress = 8, moodCode = "worried", trigger = Trigger.WORK) // Nov 5, 9 AM
        log("2026-11-04T06:00:00Z", stress = 4, moodCode = "calm", trigger = Trigger.WORK) // Nov 4, 2 PM
        log(
            "2026-11-03T12:00:00Z", // Nov 3, 8 PM
            stress = 6,
            moodCode = "worried",
            trigger = Trigger.FAMILY,
            stressAfter = 3,
            support = SupportType.BREATHE,
        )
        log("2026-10-28T02:00:00Z", stress = 2, moodCode = "calm") // Oct 28, the week before
    }

    private fun viewModel() =
        ReportsViewModel(repository, FakeSettingsRepository(), clock, mainDispatcherRule.testDispatcher)

    private fun ReportsViewModel.loaded() = assertIs<ReportsUiState.Loaded>(uiState.value)

    private fun TestScope.applyStateChanges() {
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    private fun TestScope.started(): ReportsViewModel {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    @Test
    fun `the weekly report matches numbers worked out by hand`() = runTest(mainDispatcherRule.testDispatcher) {
        seed()
        val loaded = started().loaded()
        val report = loaded.report

        assertEquals(day("2026-10-30"), loaded.period.startEpochDay)
        assertEquals(day("2026-11-05"), loaded.period.endEpochDay)
        assertFalse(loaded.hasNext)
        assertTrue(report.hasEnoughData)
        // (8 + 4 + 6) / 3 = 6.0; the week before had one check-in at 2, so +4.0 and +2.
        assertEquals(3, report.summary.checkInCount)
        assertEquals(6.0, report.summary.averageStress)
        assertEquals(4.0, report.summary.averageStressChange)
        assertEquals(2, report.summary.checkInCountChange)
        assertEquals("worried", report.summary.mostCommonMoodCode)
        assertEquals(Trigger.WORK, report.summary.topTrigger)
        // 7 day bars, oldest first; days without check-ins stay empty, not 0.
        assertEquals(listOf(null, null, null, null, 6.0, 4.0, 8.0), report.trend.map { it.stress })
        assertEquals(listOf("worried" to 2, "calm" to 1), report.moods.map { it.key to it.count })
        assertEquals(listOf(Trigger.WORK to 2, Trigger.FAMILY to 1), report.triggers.map { it.trigger to it.count })
        // Breathing took 6 down to 3.
        assertEquals(listOf(SupportType.BREATHE to 3.0), report.helped.map { it.support to it.averageRelief })
    }

    @Test
    fun `the arrows step back a week and stop at today`() = runTest(mainDispatcherRule.testDispatcher) {
        seed()
        val viewModel = started()

        viewModel.onPrevious()
        applyStateChanges()
        val previous = viewModel.loaded()
        assertEquals(day("2026-10-23"), previous.period.startEpochDay)
        assertEquals(day("2026-10-29"), previous.period.endEpochDay)
        assertTrue(previous.hasNext)
        // One check-in that week: not enough for patterns.
        assertFalse(previous.report.hasEnoughData)

        viewModel.onNext()
        applyStateChanges()
        assertEquals(day("2026-11-05"), viewModel.loaded().period.endEpochDay)
        assertFalse(viewModel.loaded().hasNext)

        viewModel.onNext() // Already at today: nothing changes.
        applyStateChanges()
        assertEquals(day("2026-11-05"), viewModel.loaded().period.endEpochDay)
    }

    @Test
    fun `switching to Daily keeps the last day in view`() = runTest(mainDispatcherRule.testDispatcher) {
        seed()
        val viewModel = started()
        viewModel.onPrevious()
        applyStateChanges()

        viewModel.onPeriodTypeSelect(ReportPeriodType.DAILY)
        applyStateChanges()

        val loaded = viewModel.loaded()
        assertEquals(ReportPeriodType.DAILY, loaded.period.type)
        assertEquals(day("2026-10-29"), loaded.period.startEpochDay)
    }

    @Test
    fun `Monthly covers the calendar month and notices the top trigger`() = runTest(mainDispatcherRule.testDispatcher) {
        seed()
        val viewModel = started()

        viewModel.onPeriodTypeSelect(ReportPeriodType.MONTHLY)
        applyStateChanges()

        val loaded = viewModel.loaded()
        assertEquals(day("2026-11-01"), loaded.period.startEpochDay)
        assertEquals(day("2026-11-30"), loaded.period.endEpochDay)
        assertEquals(30, loaded.report.trend.size)
        assertFalse(loaded.hasNext)
        // Work is in 2 of 3 check-ins (over 40%), so the bubble has something to say.
        assertEquals(ReportInsight.TopTriggerShare(Trigger.WORK, 2, 3), loaded.report.insight)
    }
}
