package app.ulap.feature.account.language

import app.ulap.core.model.AppLanguage
import app.ulap.core.model.UserSettings
import app.ulap.core.testing.FakeSettingsRepository
import app.ulap.core.testing.MainDispatcherRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class LanguageViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `the language chosen in onboarding shows as chosen`() = runTest {
        val viewModel = LanguageViewModel(FakeSettingsRepository(UserSettings(language = AppLanguage.FILIPINO)))
        assertEquals(AppLanguage.FILIPINO, viewModel.chosen.first { it != null })
    }

    @Test
    fun `choosing another language saves it to settings`() = runTest {
        val settings = FakeSettingsRepository(UserSettings(language = AppLanguage.ENGLISH))
        val viewModel = LanguageViewModel(settings)

        viewModel.choose(AppLanguage.BISAYA)

        assertEquals(AppLanguage.BISAYA, settings.settings.value.language)
        assertEquals(AppLanguage.BISAYA, viewModel.chosen.first { it == AppLanguage.BISAYA })
    }
}
