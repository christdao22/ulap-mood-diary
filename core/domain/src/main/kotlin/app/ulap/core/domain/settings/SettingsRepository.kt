package app.ulap.core.domain.settings

import app.ulap.core.model.AppLanguage
import app.ulap.core.model.PrivacyConsent
import app.ulap.core.model.UserSettings
import kotlinx.coroutines.flow.Flow

/** Settings kept on this phone only (technical-plan.md §6). They never sync. */
interface SettingsRepository {
    /** The current settings, updated on every change. */
    val settings: Flow<UserSettings>

    suspend fun setLanguage(language: AppLanguage)

    suspend fun setMascotEnabled(enabled: Boolean)

    /**
     * Marks onboarding done and records [consent] to the privacy notice, in one write, so
     * onboarding is never done without a consent record (technical-plan.md §13).
     */
    suspend fun setOnboardingDone(consent: PrivacyConsent)

    /**
     * This install's ID: a random UUID made on first use, then never changed. Sent as
     * `X-Device-Id` (api-spec.md §2), so the server can tell one person's phones apart.
     */
    suspend fun deviceId(): String

    /**
     * Saves the hotline number from `GET /meta`, or `null` to go back to the built-in NCMH 1553
     * (api-spec.md §9.3). Only `HotlineRules.savedAfter` decides what to pass.
     */
    suspend fun setHotlineNumber(number: String?)

    /**
     * Back to a fresh install: no language, onboarding not done, no consent (so it's asked again),
     * a new device ID on next use. For "Delete account and all data" (api-spec.md §7.3). The
     * hotline number from `/meta` stays: it isn't personal data, and a person who just erased
     * their diary should still see the current number.
     */
    suspend fun clear()
}
