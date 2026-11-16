package app.ulap.feature.onboarding.privacy

import app.ulap.core.model.PrivacyConsent
import app.ulap.core.testing.FakeSettingsRepository
import app.ulap.core.testing.MainDispatcherRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class YourPrivacyViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = Instant.parse("2026-11-16T02:30:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }

    @Test
    fun `Start marks onboarding done before moving on`() = runTest {
        val settings = FakeSettingsRepository()
        val doneWhenCalled = mutableListOf<Boolean>()
        assertFalse(settings.settings.value.onboardingDone)

        YourPrivacyViewModel(settings, clock).start { doneWhenCalled += settings.settings.value.onboardingDone }

        assertEquals(listOf(true), doneWhenCalled)
    }

    @Test
    fun `Start records consent to the current notice with the time`() = runTest {
        val settings = FakeSettingsRepository()
        assertNull(settings.settings.value.consent)

        YourPrivacyViewModel(settings, clock).start {}

        assertEquals(PrivacyConsent(now, PrivacyConsent.CURRENT_NOTICE_VERSION), settings.settings.value.consent)
    }
}
