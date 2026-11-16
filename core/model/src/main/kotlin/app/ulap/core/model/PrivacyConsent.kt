package app.ulap.core.model

import kotlin.time.Instant

/**
 * The record that this person agreed to the privacy notice, made when they tap "Start my first
 * check-in" in onboarding (design-spec.md §7.1, technical-plan.md §13). RA 10173 needs consent
 * for health-related data and the means to show it was given, so both the time and the notice
 * version are kept.
 *
 * @property acceptedAt When they agreed.
 * @property noticeVersion Which notice they agreed to: [CURRENT_NOTICE_VERSION] at that time.
 */
data class PrivacyConsent(val acceptedAt: Instant, val noticeVersion: String) {
    companion object {
        /**
         * The effective date of the privacy notice the app shows (docs/privacy-notice.md). Change
         * it whenever the notice changes in a way people should agree to again.
         */
        const val CURRENT_NOTICE_VERSION = "2026-11-16"
    }
}
