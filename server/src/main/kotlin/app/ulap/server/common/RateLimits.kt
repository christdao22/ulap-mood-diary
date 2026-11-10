package app.ulap.server.common

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** How many requests a key may make per [period]. The bucket refills completely each period. */
data class RateLimitRule(val limit: Int, val period: Duration)

/**
 * The limits from api-spec.md §10. Tests pass smaller ones through `AppDeps`. The other rows
 * arrive with their routes: `/auth/google` and `/auth/refresh` (30/min by IP + device), email
 * login and register (10/min and 50/day by IP + email), forgot and resend (3/hour by email) in
 * the auth tasks, and `/sync/push` (60/min) and `/sync/pull` (120/min) by user in Phase 7.
 */
data class RateLimitSettings(val default: RateLimitRule = RateLimitRule(limit = 120, period = 1.minutes))

object RateLimits {
    /** "Everything else": 120 per minute by user or IP (api-spec.md §10). Wraps every /v1 route. */
    val DEFAULT: RateLimitName = RateLimitName("default")

    /**
     * Cloudflare's tunnel is the only way in to production (no open ports, ulap-reference.md
     * §8.4) and it sets `CF-Connecting-IP` to the real client, replacing anything the client sent.
     * Locally there's no tunnel, so the socket address is used.
     */
    const val HEADER_CLIENT_IP: String = "CF-Connecting-IP"

    fun clientIp(call: ApplicationCall): String =
        call.request.headers[HEADER_CLIENT_IP]?.trim()?.takeIf { it.isNotEmpty() }
            ?: call.request.local.remoteAddress
}

/**
 * Over a limit, the plugin answers `429` with `Retry-After: <seconds>`, and StatusPages adds the
 * `RATE_LIMITED` body. Counters live in memory, which is enough for the single server of D14.
 */
fun Application.installRateLimits(settings: RateLimitSettings) {
    install(RateLimit) {
        register(RateLimits.DEFAULT) {
            rateLimiter(limit = settings.default.limit, refillPeriod = settings.default.period)
            // By IP until sign-in exists. The JWT task keys signed-in calls by user id instead.
            requestKey { call -> RateLimits.clientIp(call) }
        }
    }
}
