package app.ulap.core.testing

import app.ulap.core.domain.settings.SettingsRepository
import app.ulap.core.model.AppLanguage
import app.ulap.core.model.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Settings held in memory. Tests set [settings] directly or through the interface. */
class FakeSettingsRepository(initial: UserSettings = UserSettings()) : SettingsRepository {
    override val settings: MutableStateFlow<UserSettings> = MutableStateFlow(initial)

    /** The same ID every time, like the real one after first use. */
    var deviceId: String = "00000000-0000-4000-8000-000000000000"

    override suspend fun setLanguage(language: AppLanguage) = settings.update { it.copy(language = language) }

    override suspend fun setMascotEnabled(enabled: Boolean) = settings.update { it.copy(mascotEnabled = enabled) }

    override suspend fun setOnboardingDone() = settings.update { it.copy(onboardingDone = true) }

    override suspend fun deviceId(): String = deviceId
}
