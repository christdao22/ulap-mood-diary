package app.ulap.core.network.sync

import app.ulap.core.network.ApiResult
import app.ulap.core.network.apiCall
import app.ulap.core.network.di.AuthorizedClient
import app.ulap.core.network.map
import app.ulap.shared.api.PullResponse
import app.ulap.shared.api.PushRequest
import app.ulap.shared.api.PushResponse
import app.ulap.shared.api.SyncLimits
import app.ulap.shared.api.SyncStatusResponse
import app.ulap.shared.sync.CheckInChange
import app.ulap.shared.sync.PullPage
import app.ulap.shared.sync.PushOutcome
import app.ulap.shared.sync.ServerSyncStatus
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Inject

/**
 * The 🔒 sync endpoints (api-spec.md §8). They need the signed-in user's access token and a
 * verified email (`403 EMAIL_NOT_VERIFIED` otherwise). Only SyncEngine calls them; it decides the
 * order (push, then pull) and what to do with each answer.
 */
interface SyncApi {
    /**
     * `POST /sync/push` (api-spec.md §8.2) with at most [SyncLimits.MAX_PUSH_CHANGES] changes.
     * Sending the same change again is safe: the server answers `STALE` and keeps one copy.
     */
    suspend fun push(changes: List<CheckInChange>): ApiResult<PushOutcome>

    /** `GET /sync/pull` (api-spec.md §8.3): the changes after [since], at most [limit] of them. */
    suspend fun pull(since: Long, limit: Int = SyncLimits.PULL_DEFAULT_LIMIT): ApiResult<PullPage>

    /** `GET /sync/status` (api-spec.md §8.4). */
    suspend fun status(): ApiResult<ServerSyncStatus>
}

internal class KtorSyncApi @Inject constructor(@param:AuthorizedClient private val client: HttpClient) : SyncApi {
    override suspend fun push(changes: List<CheckInChange>): ApiResult<PushOutcome> {
        // The server would answer 413 PAYLOAD_TOO_LARGE. A bigger batch is a bug in the caller.
        require(changes.size <= SyncLimits.MAX_PUSH_CHANGES) {
            "push takes at most ${SyncLimits.MAX_PUSH_CHANGES} changes, got ${changes.size}"
        }
        return client
            .apiCall<PushResponse> {
                post("sync/push") {
                    contentType(ContentType.Application.Json)
                    setBody(PushRequest(changes.map { it.toDto() }))
                }
            }.map { it.toPushOutcome() }
    }

    override suspend fun pull(since: Long, limit: Int): ApiResult<PullPage> {
        require(since >= 0) { "since must be 0 or more, got $since" }
        require(limit in SyncLimits.PULL_LIMIT_RANGE) { "limit must be in ${SyncLimits.PULL_LIMIT_RANGE}, got $limit" }
        return client
            .apiCall<PullResponse> {
                get("sync/pull") {
                    parameter("since", since)
                    parameter("limit", limit)
                }
            }.map { it.toPullPage() }
    }

    override suspend fun status(): ApiResult<ServerSyncStatus> =
        client.apiCall<SyncStatusResponse> { get("sync/status") }.map { it.toServerSyncStatus() }
}
