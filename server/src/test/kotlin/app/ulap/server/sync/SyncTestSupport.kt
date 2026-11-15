package app.ulap.server.sync

import app.ulap.server.auth.AuthTestContext
import app.ulap.server.auth.auth
import app.ulap.server.auth.post
import app.ulap.shared.api.Api
import app.ulap.shared.api.CheckInDto
import app.ulap.shared.api.GoogleSignInRequest
import app.ulap.shared.api.PullResponse
import app.ulap.shared.api.PushRequest
import app.ulap.shared.api.PushResponse
import app.ulap.shared.api.SyncStatusResponse
import app.ulap.shared.model.Ids
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** The test clock's "now" as the API's Instant. */
fun AuthTestContext.now(): Instant = Instant.fromEpochMilliseconds(clock.instant().toEpochMilli())

/** Signs in a new Google user (always verified, so sync is allowed) and returns the access token. */
suspend fun AuthTestContext.syncUser(): String =
    post("/v1/auth/google", GoogleSignInRequest(google.account())).auth().accessToken

/** A valid live check-in. [updatedAt] defaults to an hour before the test clock. */
fun AuthTestContext.checkIn(
    id: String = Ids.newId(),
    updatedAt: Instant = now() - 1.hours,
    moodCode: String = "worried",
    note: String? = "Report due tomorrow.",
): CheckInDto = CheckInDto(
    id = id,
    moodCode = moodCode,
    energy = 6,
    stress = 8,
    stressAfter = 5,
    trigger = "WORK",
    triggerOther = null,
    note = note,
    supportUsed = "BREATHE",
    isGoodMoment = false,
    loggedAt = now() - 2.hours,
    tzOffsetMin = 480,
    updatedAt = updatedAt,
)

/** A delete: only `id`, `updatedAt` and `deletedAt` (api-spec.md §8.2). */
fun AuthTestContext.deleteOf(id: String, at: Instant = now() - 30.minutes): CheckInDto =
    CheckInDto(id = id, updatedAt = at, deletedAt = at)

suspend fun AuthTestContext.push(token: String?, vararg changes: CheckInDto): HttpResponse =
    post("/v1/sync/push", PushRequest(changes.toList()), token = token)

/** Pushes and returns the body, failing the test unless it was a 200. */
suspend fun AuthTestContext.pushed(token: String, vararg changes: CheckInDto): PushResponse {
    val response = push(token, *changes)
    assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
    return Api.json.decodeFromString(response.bodyAsText())
}

suspend fun AuthTestContext.pull(token: String?, query: String = ""): HttpResponse =
    client.get("/v1/sync/pull$query") { token?.let { bearerAuth(it) } }

/** Pulls one page and returns the body, failing the test unless it was a 200. */
suspend fun AuthTestContext.pulled(
    token: String,
    since: Long = 0,
    limit: Int? = null,
    full: Boolean? = null,
): PullResponse {
    val response =
        pull(token, "?since=$since" + (limit?.let { "&limit=$it" } ?: "") + (full?.let { "&full=$it" } ?: ""))
    assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
    return Api.json.decodeFromString(response.bodyAsText())
}

/** Every change after [since], following `nextSince` while `hasMore` (api-spec.md §8.3). */
suspend fun AuthTestContext.pulledAll(token: String, since: Long = 0, limit: Int? = null): List<CheckInDto> {
    val all = mutableListOf<CheckInDto>()
    var cursor = since
    do {
        val page = pulled(token, cursor, limit)
        all += page.changes
        cursor = page.nextSince
    } while (page.hasMore)
    return all
}

suspend fun AuthTestContext.status(token: String?): HttpResponse =
    client.get("/v1/sync/status") { token?.let { bearerAuth(it) } }

suspend fun HttpResponse.syncStatus(): SyncStatusResponse = Api.json.decodeFromString(bodyAsText())
