package app.ulap.feature.account

import app.ulap.core.model.AppLanguage
import app.ulap.core.model.UserSettings
import app.ulap.core.testing.FakeSettingsRepository
import app.ulap.core.testing.MainDispatcherRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class AccountViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `the Language row follows the saved language`() = runTest {
        val settings = FakeSettingsRepository(UserSettings(language = AppLanguage.ENGLISH))
        val viewModel = AccountViewModel(settings)
        assertEquals(AppLanguage.ENGLISH, viewModel.language.first { it != null })

        settings.settings.update { it.copy(language = AppLanguage.BISAYA) }

        assertEquals(AppLanguage.BISAYA, viewModel.language.first { it == AppLanguage.BISAYA })
    }

    @Test
    fun `the Ulap switch follows the saved setting`() = runTest {
        val settings = FakeSettingsRepository(UserSettings(mascotEnabled = false))
        val viewModel = AccountViewModel(settings)
        assertFalse(viewModel.mascotEnabled.first { !it })
    }

    @Test
    fun `turning Ulap off saves it, and turning it back on saves that too`() = runTest {
        val settings = FakeSettingsRepository(UserSettings(mascotEnabled = true))
        val viewModel = AccountViewModel(settings)

        viewModel.setMascotEnabled(false)
        assertFalse(settings.settings.first { !it.mascotEnabled }.mascotEnabled)

        viewModel.setMascotEnabled(true)
        assertTrue(settings.settings.first { it.mascotEnabled }.mascotEnabled)
    }
}
