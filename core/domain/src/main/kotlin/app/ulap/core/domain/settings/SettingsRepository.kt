package app.ulap.core.domain.settings

import app.ulap.core.model.AppLanguage
import app.ulap.core.model.UserSettings
import kotlinx.coroutines.flow.Flow

/** Settings kept on this phone only (technical-plan.md §6). They never sync. */
interface SettingsRepository {
    /** The current settings, updated on every change. */
    val settings: Flow<UserSettings>

    suspend fun setLanguage(language: AppLanguage)

    suspend fun setMascotEnabled(enabled: Boolean)

    suspend fun setOnboardingDone()

    /**
     * This install's ID: a random UUID made on first use, then never changed. Sent as
     * `X-Device-Id` (api-spec.md §2), so the server can tell one person's phones apart.
     */
    suspend fun deviceId(): String
}
