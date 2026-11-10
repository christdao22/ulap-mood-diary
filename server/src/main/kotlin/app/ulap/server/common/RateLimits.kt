package app.ulap.server.common

import app.ulap.server.auth.AccessTokenResult
import app.ulap.server.auth.AccessTokens
import app.ulap.server.auth.bearerToken
import app.ulap.shared.api.Api
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** How many requests a key may make per [period]. The bucket refills completely each period. */
data class RateLimitRule(val limit: Int, val period: Duration)

/**
 * The limits from api-spec.md §10. Tests pass smaller ones through `AppDeps`.
 * [default] and [authToken] are Ktor `RateLimit` limiters, keyed from headers. The email-keyed
 * ones ([emailSignIn], [emailSignInDaily], [emailSend]) need the email from the request body, so
 * the auth services apply them with a [WindowLimiter] (see `AuthLimits`). `/sync/push` (60/min)
 * and `/sync/pull` (120/min) by user arrive in Phase 7.
 */
data class RateLimitSettings(
    /** Everything else: 120 per minute by user or IP. */
    val default: RateLimitRule = RateLimitRule(limit = 120, period = 1.minutes),
    /** `/auth/google` and `/auth/refresh`: 30 per minute by IP + device. */
    val authToken: RateLimitRule = RateLimitRule(limit = 30, period = 1.minutes),
    /** `/auth/email/login` and `/auth/email/register`: 10 per minute by IP + email… */
    val emailSignIn: RateLimitRule = RateLimitRule(limit = 10, period = 1.minutes),
    /** …and 50 per day. */
    val emailSignInDaily: RateLimitRule = RateLimitRule(limit = 50, period = 1.days),
    /** `/auth/password/forgot` and `/auth/email/resend-verification`: 3 per hour by email. */
    val emailSend: RateLimitRule = RateLimitRule(limit = 3, period = 1.hours),
)

object RateLimits {
    /** "Everything else": 120 per minute by user or IP (api-spec.md §10). Wraps every /v1 route. */
    val DEFAULT: RateLimitName = RateLimitName("default")

    /** `/auth/google` and `/auth/refresh`: 30 per minute by IP + device (api-spec.md §10). */
    val AUTH_TOKEN: RateLimitName = RateLimitName("auth-token")

    /**
     * Cloudflare's tunnel is the only way in to production (no open ports, ulap-reference.md
     * §8.4) and it sets `CF-Connecting-IP` to the real client, replacing anything the client sent.
     * Locally there's no tunnel, so the socket address is used.
     */
    const val HEADER_CLIENT_IP: String = "CF-Connecting-IP"

    fun clientIp(call: ApplicationCall): String =
        call.request.headers[HEADER_CLIENT_IP]?.trim()?.takeIf { it.isNotEmpty() }
            ?: call.request.local.remoteAddress

    /** `user:<id>` for a call with a valid access token, else `ip:<address>`. */
    fun userOrIp(call: ApplicationCall, accessTokens: AccessTokens): String {
        val result = call.bearerToken()?.let(accessTokens::verify)
        return if (result is AccessTokenResult.Valid) "user:${result.principal.userId}" else "ip:${clientIp(call)}"
    }
}

/**
 * Over a limit, the plugin answers `429` with `Retry-After: <seconds>`, and StatusPages adds the
 * `RATE_LIMITED` body. Counters live in memory, which is enough for the single server of D14.
 */
fun Application.installRateLimits(settings: RateLimitSettings, accessTokens: AccessTokens) {
    install(RateLimit) {
        register(RateLimits.DEFAULT) {
            rateLimiter(limit = settings.default.limit, refillPeriod = settings.default.period)
            // Signed-in calls share one bucket per user across devices; the rest go by IP.
            requestKey { call -> RateLimits.userOrIp(call, accessTokens) }
        }
        register(RateLimits.AUTH_TOKEN) {
            rateLimiter(limit = settings.authToken.limit, refillPeriod = settings.authToken.period)
            requestKey { call ->
                RateLimits.clientIp(call) + "|" + call.request.headers[Api.HEADER_DEVICE_ID].orEmpty()
            }
        }
    }
}
