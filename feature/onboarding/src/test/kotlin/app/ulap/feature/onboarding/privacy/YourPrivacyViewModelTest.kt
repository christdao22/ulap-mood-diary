package app.ulap.feature.onboarding.privacy

import app.ulap.core.testing.FakeSettingsRepository
import app.ulap.core.testing.MainDispatcherRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class YourPrivacyViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `Start marks onboarding done before moving on`() = runTest {
        val settings = FakeSettingsRepository()
        val doneWhenCalled = mutableListOf<Boolean>()
        assertFalse(settings.settings.value.onboardingDone)

        YourPrivacyViewModel(settings).start { doneWhenCalled += settings.settings.value.onboardingDone }

        assertEquals(listOf(true), doneWhenCalled)
    }
}
