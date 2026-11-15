package app.ulap.core.sync

import app.ulap.core.network.ApiResult
import javax.inject.Inject
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

/**
 * The short retries for one call inside a sync run: 2s, 4s, 8s and 16s, each plus up to a second
 * of jitter (api-spec.md §4, Retry policy). After those, the run ends and WorkManager's backoff
 * takes over.
 *
 * Only outages are retried here: no answer, 429, 502, 503 and 504. A `500 INTERNAL` ends the run
 * straight away, because each one counts against the rows in the push (technical-plan.md §8, When
 * a change can't upload). Retrying it four times per run would stop a row after three runs
 * instead of about ten.
 */
internal class ApiRetry(private val delays: List<Duration>, private val jitter: () -> Duration) {
    @Inject constructor() : this(DELAYS, { Random.nextLong(JITTER_MILLIS).milliseconds })

    suspend fun <T> call(request: suspend () -> ApiResult<T>): ApiResult<T> {
        for (wait in delays) {
            val result = request()
            if (!result.isOutage()) return result
            delay(wait + jitter())
        }
        return request()
    }

    private companion object {
        val DELAYS = listOf(2.seconds, 4.seconds, 8.seconds, 16.seconds)
        const val JITTER_MILLIS = 1_000L
    }
}

private val OUTAGE_STATUSES = setOf(429, 502, 503, 504)

private fun ApiResult<*>.isOutage(): Boolean = when (this) {
    is ApiResult.NetworkError -> true
    is ApiResult.Failure -> status in OUTAGE_STATUSES
    is ApiResult.Success -> false
}
