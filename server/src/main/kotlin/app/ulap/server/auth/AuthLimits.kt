package app.ulap.server.auth

import app.ulap.server.common.ApiException
import app.ulap.server.common.RateLimitSettings
import app.ulap.server.common.WindowLimiter
import java.time.Clock

/**
 * The email-keyed rate limits of api-spec.md §10, applied by the services because the email is
 * in the request body. Over a limit → `429 RATE_LIMITED` with `Retry-After`. Every attempt counts,
 * including failed ones: that's what slows a password guesser down.
 */
class AuthLimits(settings: RateLimitSettings, clock: Clock) {
    private val signIn = WindowLimiter(settings.emailSignIn, clock)
    private val signInDaily = WindowLimiter(settings.emailSignInDaily, clock)
    private val emailSend = WindowLimiter(settings.emailSend, clock)

    /** `/auth/email/login` and `/auth/email/register`: 10 per minute and 50 per day by IP + email. */
    fun checkSignIn(ip: String, email: String) {
        val key = "$ip|$email"
        val retryAfter = signIn.hit(key) ?: signInDaily.hit(key) ?: return
        throw ApiException.rateLimited(retryAfter)
    }

    /** `/auth/password/forgot` and `/auth/email/resend-verification`: 3 per hour by email. */
    fun checkEmailSend(email: String) {
        val retryAfter = emailSend.hit(email) ?: return
        throw ApiException.rateLimited(retryAfter)
    }
}
