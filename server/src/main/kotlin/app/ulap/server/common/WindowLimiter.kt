package app.ulap.server.common

import java.time.Clock
import java.time.Instant

/**
 * A fixed-window counter per key, kept in memory, for the limits Ktor's `RateLimit` plugin can't
 * key: the ones by email (api-spec.md §10), because the email is in the request body, which the
 * plugin never reads. Same model as the plugin: the count resets completely each [RateLimitRule.period].
 * In memory is enough for the single server of D14.
 */
class WindowLimiter(private val rule: RateLimitRule, private val clock: Clock) {
    private class Window(val startsAt: Instant, var count: Int)

    private val period = java.time.Duration.ofMillis(rule.period.inWholeMilliseconds)
    private val windows = HashMap<String, Window>()

    /** Counts one request for [key]. Null when it's allowed, else the seconds until the window resets. */
    fun hit(key: String): Long? = synchronized(windows) {
        val now = clock.instant()
        if (windows.size > PRUNE_ABOVE) windows.values.removeIf { it.startsAt + period <= now }
        val window = windows[key]?.takeIf { it.startsAt + period > now } ?: Window(now, 0).also { windows[key] = it }
        if (window.count >= rule.limit) {
            return secondsUntil(window.startsAt + period, now)
        }
        window.count++
        null
    }

    private fun secondsUntil(resetAt: Instant, now: Instant): Long =
        java.time.Duration.between(now, resetAt).toSeconds().coerceAtLeast(1)

    private companion object {
        /** Expired windows are dropped once the map grows past this, so it can't grow forever. */
        const val PRUNE_ABOVE = 10_000
    }
}
