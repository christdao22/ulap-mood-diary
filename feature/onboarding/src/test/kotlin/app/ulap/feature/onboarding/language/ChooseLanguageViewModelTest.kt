package app.ulap.feature.onboarding.language

import app.ulap.core.model.AppLanguage
import app.ulap.core.model.UserSettings
import app.ulap.core.testing.FakeSettingsRepository
import app.ulap.core.testing.MainDispatcherRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class ChooseLanguageViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `nothing is chosen for a new person`() = runTest {
        val viewModel = ChooseLanguageViewModel(FakeSettingsRepository())
        assertNull(viewModel.chosen.value)
    }

    @Test
    fun `choosing saves the language to settings`() = runTest {
        val settings = FakeSettingsRepository()
        val viewModel = ChooseLanguageViewModel(settings)

        viewModel.choose(AppLanguage.BISAYA)

        assertEquals(AppLanguage.BISAYA, settings.settings.value.language)
        assertEquals(AppLanguage.BISAYA, viewModel.chosen.first { it != null })
    }

    @Test
    fun `a saved choice shows as chosen`() = runTest {
        val viewModel = ChooseLanguageViewModel(FakeSettingsRepository(UserSettings(language = AppLanguage.FILIPINO)))
        assertEquals(AppLanguage.FILIPINO, viewModel.chosen.first { it != null })
    }
}
