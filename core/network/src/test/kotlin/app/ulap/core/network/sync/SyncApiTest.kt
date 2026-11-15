package app.ulap.core.network.sync

import app.ulap.core.network.ApiResult
import app.ulap.core.network.respondError
import app.ulap.core.network.respondJson
import app.ulap.core.network.testClient
import app.ulap.shared.api.ErrorCode
import app.ulap.shared.api.SyncLimits
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import app.ulap.shared.sync.AcceptedChange
import app.ulap.shared.sync.CheckInChange
import app.ulap.shared.sync.PullPage
import app.ulap.shared.sync.PulledChange
import app.ulap.shared.sync.PushOutcome
import app.ulap.shared.sync.RejectedChange
import app.ulap.shared.sync.ServerSyncStatus
import app.ulap.shared.validation.CheckInInput
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class SyncApiTest {
    private val loggedAt = Instant.parse("2026-11-04T01:15:00Z")
    private val updatedAt = Instant.parse("2026-11-04T01:18:42.120Z")
    private val deletedAt = Instant.parse("2026-11-04T02:00:00Z")
    private val serverTime = Instant.parse("2026-11-04T02:00:01.250Z")

    private val worried = CheckInInput(
        moodCode = "worried",
        energy = 6,
        stress = 8,
        stressAfter = 5,
        trigger = Trigger.WORK,
        triggerOther = null,
        note = "Report due tomorrow.",
        supportUsed = SupportType.BREATHE,
        isGoodMoment = false,
        loggedAt = loggedAt,
        tzOffsetMin = 480,
    )

    @Test
    fun `push posts a save with every field and a delete with only its times`() = runTest {
        var seen: HttpRequestData? = null
        val api = KtorSyncApi(
            testClient { request ->
                seen = request
                respondJson(
                    """
                    {"accepted":[{"id":"a","serverSeq":1042,"status":"APPLIED"},
                                 {"id":"b","serverSeq":900,"status":"STALE"}],
                     "rejected":[{"id":"c","reason":"INVALID_MOOD","field":"moodCode"}],
                     "serverTime":"2026-11-04T02:00:01.250Z"}
                    """.trimIndent(),
                )
            },
        )

        val result = api.push(
            listOf(
                CheckInChange.Saved("a", updatedAt, worried),
                CheckInChange.Deleted("b", deletedAt, deletedAt),
            ),
        )

        val request = checkNotNull(seen)
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/v1/sync/push", request.url.encodedPath)
        // api-spec.md §8.2: nulls are left out, and a delete is only id, updatedAt and deletedAt.
        assertEquals(
            """{"changes":[{"id":"a","moodCode":"worried","energy":6,"stress":8,"stressAfter":5,""" +
                """"trigger":"WORK","note":"Report due tomorrow.","supportUsed":"BREATHE","isGoodMoment":false,""" +
                """"loggedAt":"2026-11-04T01:15:00.000Z","tzOffsetMin":480,"updatedAt":"2026-11-04T01:18:42.120Z"},""" +
                """{"id":"b","updatedAt":"2026-11-04T02:00:00.000Z","deletedAt":"2026-11-04T02:00:00.000Z"}]}""",
            (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString(),
        )
        // APPLIED and STALE both come back as accepted, with their serverSeq.
        assertEquals(
            ApiResult.Success(
                PushOutcome(
                    accepted = listOf(AcceptedChange("a", 1042), AcceptedChange("b", 900)),
                    rejected = listOf(RejectedChange("c", "INVALID_MOOD", "moodCode")),
                    serverTime = serverTime,
                ),
            ),
            result,
        )
    }

    @Test
    fun `push refuses more than one batch without calling the server`() = runTest {
        var calls = 0
        val api = KtorSyncApi(
            testClient {
                calls++
                respondJson("{}")
            },
        )
        val tooMany = List(SyncLimits.MAX_PUSH_CHANGES + 1) { CheckInChange.Deleted("id-$it", deletedAt, deletedAt) }

        assertFailsWith<IllegalArgumentException> { api.push(tooMany) }
        assertEquals(0, calls)
    }

    @Test
    fun `a 500 from push comes back as a failure the engine can count`() = runTest {
        val api = KtorSyncApi(testClient { respondError(HttpStatusCode.InternalServerError, ErrorCode.INTERNAL) })

        assertEquals(
            ApiResult.Failure(500, ErrorCode.INTERNAL),
            api.push(listOf(CheckInChange.Saved("a", updatedAt, worried))),
        )
    }

    @Test
    fun `pull asks for changes after since and reads saves and tombstones in order`() = runTest {
        var seen: HttpRequestData? = null
        val api = KtorSyncApi(
            testClient { request ->
                seen = request
                respondJson(
                    """
                    {"changes":[
                      {"id":"a","moodCode":"worried","energy":6,"stress":8,"stressAfter":5,"trigger":"WORK",
                       "triggerOther":null,"note":"Report due tomorrow.","supportUsed":"BREATHE","isGoodMoment":false,
                       "loggedAt":"2026-11-04T01:15:00.000Z","tzOffsetMin":480,
                       "updatedAt":"2026-11-04T01:18:42.120Z","deletedAt":null,"serverSeq":1042},
                      {"id":"b","updatedAt":"2026-11-04T02:00:00.000Z","deletedAt":"2026-11-04T02:00:00.000Z",
                       "serverSeq":1043}],
                     "nextSince":1043,"hasMore":true,"resetRequired":false,
                     "serverTime":"2026-11-04T02:00:01.250Z"}
                    """.trimIndent(),
                )
            },
        )

        val result = api.pull(since = 1000, limit = 2)

        val request = checkNotNull(seen)
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/v1/sync/pull", request.url.encodedPath)
        assertEquals("1000", request.url.parameters["since"])
        assertEquals("2", request.url.parameters["limit"])
        assertEquals(
            ApiResult.Success(
                PullPage(
                    changes = listOf(
                        PulledChange(1042, CheckInChange.Saved("a", updatedAt, worried)),
                        PulledChange(1043, CheckInChange.Deleted("b", deletedAt, deletedAt)),
                    ),
                    nextSince = 1043,
                    hasMore = true,
                    resetRequired = false,
                    serverTime = serverTime,
                ),
            ),
            result,
        )
    }

    @Test
    fun `pull sends the default limit when none is given`() = runTest {
        var seen: HttpRequestData? = null
        val api = KtorSyncApi(
            testClient { request ->
                seen = request
                respondJson(emptyPage(resetRequired = false))
            },
        )

        api.pull(since = 0)

        assertEquals(SyncLimits.PULL_DEFAULT_LIMIT.toString(), checkNotNull(seen).url.parameters["limit"])
    }

    @Test
    fun `a reset comes through as resetRequired`() = runTest {
        val api = KtorSyncApi(testClient { respondJson(emptyPage(resetRequired = true)) })

        val page = assertIs<ApiResult.Success<PullPage>>(api.pull(since = 5)).value

        assertEquals(true, page.resetRequired)
        assertEquals(0L, page.nextSince)
    }

    @Test
    fun `names a newer server added read as OTHER and NONE`() = runTest {
        // api-spec.md §8.3: one new enum value must not stop the pull.
        val api = KtorSyncApi(
            testClient {
                respondJson(
                    """
                    {"changes":[{"id":"a","moodCode":"calm","energy":5,"stress":2,"trigger":"WEATHER",
                       "supportUsed":"NAP","isGoodMoment":false,"loggedAt":"2026-11-04T01:15:00.000Z",
                       "tzOffsetMin":480,"updatedAt":"2026-11-04T01:18:42.120Z","serverSeq":7}],
                     "nextSince":7,"hasMore":false,"resetRequired":false,"serverTime":"2026-11-04T02:00:01.250Z"}
                    """.trimIndent(),
                )
            },
        )

        val page = assertIs<ApiResult.Success<PullPage>>(api.pull(since = 0)).value
        val saved = assertIs<CheckInChange.Saved>(page.changes.single().change)

        assertEquals(Trigger.OTHER, saved.checkIn.trigger)
        assertEquals(SupportType.NONE, saved.checkIn.supportUsed)
    }

    @Test
    fun `pull refuses a limit outside 1 to 1000 and a negative since`() = runTest {
        val api = KtorSyncApi(testClient { respondJson(emptyPage(resetRequired = false)) })

        assertFailsWith<IllegalArgumentException> { api.pull(since = 0, limit = 0) }
        assertFailsWith<IllegalArgumentException> { api.pull(since = 0, limit = 1_001) }
        assertFailsWith<IllegalArgumentException> { api.pull(since = -1) }
    }

    @Test
    fun `status reads the counts and a missing lastPushAt as null`() = runTest {
        var seen: HttpRequestData? = null
        val api = KtorSyncApi(
            testClient { request ->
                seen = request
                respondJson("""{"checkInCount":0,"latestServerSeq":0}""")
            },
        )

        val status = assertIs<ApiResult.Success<ServerSyncStatus>>(api.status()).value

        assertEquals("/v1/sync/status", checkNotNull(seen).url.encodedPath)
        assertEquals(0L, status.checkInCount)
        assertNull(status.lastPushAt)
    }

    @Test
    fun `an unverified email comes back with its code`() = runTest {
        val api = KtorSyncApi(testClient { respondError(HttpStatusCode.Forbidden, ErrorCode.EMAIL_NOT_VERIFIED) })

        assertEquals(ApiResult.Failure(403, ErrorCode.EMAIL_NOT_VERIFIED), api.pull(since = 0))
    }

    @Test
    fun `offline comes back as a network error`() = runTest {
        val api = KtorSyncApi(testClient { throw IOException("offline") })

        assertIs<ApiResult.NetworkError>(api.status())
    }

    private fun emptyPage(resetRequired: Boolean) =
        """{"changes":[],"nextSince":0,"hasMore":$resetRequired,"resetRequired":$resetRequired,""" +
            """"serverTime":"2026-11-04T02:00:01.250Z"}"""
}
