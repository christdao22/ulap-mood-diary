package app.ulap.feature.checkin.pickmood

import app.ulap.core.model.UserSettings
import app.ulap.core.testing.FakeSettingsRepository
import app.ulap.core.testing.MainDispatcherRule
import app.ulap.shared.rules.TimeOfDay
import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule

@OptIn(ExperimentalCoroutinesApi::class)
class PickMoodViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settings = FakeSettingsRepository()
    private val systemZone = TimeZone.getDefault()

    private fun clockAt(isoInstant: String) = object : Clock {
        override fun now(): Instant = Instant.parse(isoInstant)
    }

    // The greeting follows the phone's own time zone, so pin one: Manila, UTC+8.
    @BeforeTest
    fun pinTimeZone() = TimeZone.setDefault(TimeZone.getTimeZone("Asia/Manila"))

    @AfterTest
    fun restoreTimeZone() = TimeZone.setDefault(systemZone)

    @Test
    fun `the greeting uses the local hour, not UTC`() {
        // 22:30 UTC is 06:30 the next morning in Manila.
        val viewModel = PickMoodViewModel(settings, clockAt("2026-11-05T22:30:00Z"))
        assertEquals(TimeOfDay.MORNING, viewModel.uiState.value.timeOfDay)
    }

    @Test
    fun `Ulap shows by default and hides once the user turns it off`() = runTest {
        val viewModel = PickMoodViewModel(settings, clockAt("2026-11-05T06:00:00Z"))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertTrue(viewModel.uiState.value.showUlap)
        settings.setMascotEnabled(false)
        assertFalse(viewModel.uiState.value.showUlap)
    }

    @Test
    fun `a user who already turned Ulap off never sees it once settings load`() = runTest {
        settings.settings.value = UserSettings(mascotEnabled = false)
        val viewModel = PickMoodViewModel(settings, clockAt("2026-11-05T06:00:00Z"))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertFalse(viewModel.uiState.value.showUlap)
    }
}
