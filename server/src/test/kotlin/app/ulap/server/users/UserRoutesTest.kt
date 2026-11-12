@file:OptIn(ExperimentalUuidApi::class)

package app.ulap.server.users

import app.ulap.server.auth.AuthTestContext
import app.ulap.server.auth.DEVICE_B
import app.ulap.server.auth.GOOD_PASSWORD
import app.ulap.server.auth.auth
import app.ulap.server.auth.identitiesOf
import app.ulap.server.auth.post
import app.ulap.server.auth.uniqueEmail
import app.ulap.server.auth.withAuthServer
import app.ulap.server.crypto.NoteCipher
import app.ulap.server.db.AuthIdentitiesTable
import app.ulap.server.db.CheckInsTable
import app.ulap.server.db.Db
import app.ulap.server.db.EmailTokensTable
import app.ulap.server.db.RefreshTokensTable
import app.ulap.server.db.TestPostgres
import app.ulap.server.db.UsersTable
import app.ulap.server.error
import app.ulap.shared.api.Api
import app.ulap.shared.api.AuthResponse
import app.ulap.shared.api.DeleteMeRequest
import app.ulap.shared.api.EmailRegisterRequest
import app.ulap.shared.api.ErrorCode
import app.ulap.shared.api.ErrorDetail
import app.ulap.shared.api.GoogleSignInRequest
import app.ulap.shared.api.RefreshRequest
import app.ulap.shared.api.UpdateMeRequest
import app.ulap.shared.api.UserDto
import app.ulap.shared.api.VerifyEmailRequest
import app.ulap.shared.model.Ids
import app.ulap.shared.validation.AccountRules
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

/** `GET /me`, `PATCH /me` and `DELETE /me` (api-spec.md §7) on the real module and a real PostgreSQL. */
class UserRoutesTest {
    private val db = Db(TestPostgres.dataSource)
    private val cipher = NoteCipher.from(TestPostgres.config.noteEncryption)

    @Test
    fun getMeReturnsTheSignedInUser() = withAuthServer {
        val email = uniqueEmail()
        val session = registered(email)

        val response = getMe(session.accessToken)
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        val me = response.user()
        assertEquals(session.user, me)
        assertEquals("Ana", me.displayName)
        assertEquals("en", me.language)
        assertTrue(me.mascotEnabled)
        assertEquals(listOf("EMAIL" to email), me.identities.map { it.provider to it.email })
    }

    @Test
    fun everyMeRouteNeedsAnAccessToken() = withAuthServer {
        val responses = listOf(
            getMe(token = null),
            patchMe(token = null, UpdateMeRequest(mascotEnabled = false)),
            deleteMe(token = null),
            getMe(token = "not-a-token"),
        )
        responses.forEach {
            assertEquals(HttpStatusCode.Unauthorized, it.status)
            assertEquals(ErrorCode.UNAUTHENTICATED, it.error().code)
        }
    }

    @Test
    fun patchChangesOnlyTheFieldsThatWereSent() = withAuthServer {
        val session = registered()

        val language = patchMe(session.accessToken, UpdateMeRequest(language = "ceb"))
        assertEquals(HttpStatusCode.OK, language.status, language.bodyAsText())
        assertEquals(session.user.copy(language = "ceb"), language.user())

        val rest = patchMe(session.accessToken, UpdateMeRequest(displayName = "  Ian  ", mascotEnabled = false))
        val expected = session.user.copy(displayName = "Ian", language = "ceb", mascotEnabled = false)
        assertEquals(expected, rest.user())

        // Nothing sent, or a field sent as null: nothing changes and the User still comes back.
        assertEquals(expected, patchMe(session.accessToken, "{}").user())
        assertEquals(expected, patchMe(session.accessToken, """{"displayName":null}""").user())
        assertEquals(expected, getMe(session.accessToken).user())
    }

    @Test
    fun patchRejectsBadValuesAndChangesNothing() = withAuthServer {
        val session = registered()

        // Both fields are wrong, and the valid one next to them must not be saved either.
        val both = patchMe(
            session.accessToken,
            UpdateMeRequest(displayName = "a".repeat(51), language = "es", mascotEnabled = false),
        )
        assertEquals(HttpStatusCode.BadRequest, both.status)
        assertEquals(ErrorCode.VALIDATION_FAILED, both.error().code)
        assertEquals(
            listOf(ErrorDetail("displayName", "TOO_LONG"), ErrorDetail("language", "INVALID")),
            both.error().details,
        )

        val blank = patchMe(session.accessToken, UpdateMeRequest(displayName = "   "))
        assertEquals(listOf(ErrorDetail("displayName", "TOO_SHORT")), blank.error().details)

        val wrongType = patchMe(session.accessToken, """{"mascotEnabled":"sometimes"}""")
        assertEquals(HttpStatusCode.BadRequest, wrongType.status)
        assertEquals(ErrorCode.BAD_REQUEST, wrongType.error().code)

        assertEquals(session.user, getMe(session.accessToken).user())
    }

    @Test
    fun patchOnlyEverChangesTheCaller() = withAuthServer {
        val caller = registered()
        val other = registered()

        // A user id in the body is ignored: the access token alone says whose account it is (§11).
        val response = patchMe(caller.accessToken, """{"id":"${other.user.id}","displayName":"Mallory"}""")
        assertEquals(caller.user.copy(displayName = "Mallory"), response.user())
        assertEquals(other.user, getMe(other.accessToken).user())
    }

    @Test
    fun deleteNeedsTheConfirmationWord() = withAuthServer {
        val session = registered()

        val attempts = listOf(
            deleteMe<String>(session.accessToken, body = null),
            deleteMe(session.accessToken, "{}"),
            deleteMe(session.accessToken, DeleteMeRequest("delete")),
            deleteMe(session.accessToken, DeleteMeRequest("yes")),
        )
        attempts.forEach {
            assertEquals(HttpStatusCode.BadRequest, it.status)
            assertEquals(ErrorCode.BAD_REQUEST, it.error().code)
        }
        assertEquals(HttpStatusCode.OK, getMe(session.accessToken).status)
    }

    @Test
    fun deleteRemovesEveryRowAndLeavesOtherAccountsAlone() = withAuthServer {
        // An account with one of everything: two sign-in methods, a verification link, sessions on
        // two devices, and check-ins with and without a note.
        val email = uniqueEmail()
        val session = registered(email)
        loggedIn(email, device = DEVICE_B)
        val linked = post("/v1/auth/link/google", GoogleSignInRequest(google.account()), token = session.accessToken)
        assertEquals(HttpStatusCode.OK, linked.status, linked.bodyAsText())
        val userId = Uuid.parse(session.user.id)
        insertCheckIn(userId, note = "Kapoy kaayo karon")
        insertCheckIn(userId, note = null)
        val identityIds = db.tx { identitiesOf(userId).map { it.id } }
        assertEquals(
            RowCounts(users = 1, identities = 2, refreshTokens = 2, emailTokens = 1, checkIns = 2),
            rowsOf(userId, identityIds),
        )

        val bystander = registered()
        val bystanderId = Uuid.parse(bystander.user.id)
        insertCheckIn(bystanderId, note = "Akoa ni")
        val bystanderIdentityIds = db.tx { identitiesOf(bystanderId).map { it.id } }
        val bystanderRows = rowsOf(bystanderId, bystanderIdentityIds)

        val deleted = deleteMe(session.accessToken)
        assertEquals(HttpStatusCode.NoContent, deleted.status, deleted.bodyAsText())

        assertEquals(RowCounts(0, 0, 0, 0, 0), rowsOf(userId, identityIds))
        assertEquals(bystanderRows, rowsOf(bystanderId, bystanderIdentityIds))
        assertEquals(bystander.user, getMe(bystander.accessToken).user())
    }

    @Test
    fun afterDeleteNothingOfTheOldAccountStillWorks() = withAuthServer {
        val email = uniqueEmail()
        val session = registered(email)
        val verifyToken = mailer.lastTokenFor(email, "verify")
        val token = session.accessToken
        assertEquals(HttpStatusCode.NoContent, deleteMe(token).status)

        // The access token is still validly signed for up to 15 minutes, but the account is gone.
        val gone = listOf(
            getMe(token),
            patchMe(token, UpdateMeRequest(mascotEnabled = false)),
            patchMe(token, "{}"),
            post("/v1/auth/link/google", GoogleSignInRequest(google.account()), token = token),
            post("/v1/auth/link/email", EmailRegisterRequest(uniqueEmail(), GOOD_PASSWORD), token = token),
        )
        gone.forEach {
            assertEquals(HttpStatusCode.Forbidden, it.status, it.bodyAsText())
            assertEquals(ErrorCode.ACCOUNT_DELETED, it.error().code)
        }
        assertEquals(
            ErrorCode.REFRESH_TOKEN_INVALID,
            post("/v1/auth/refresh", RefreshRequest(session.refreshToken)).error().code,
        )
        assertEquals(ErrorCode.INVALID_CREDENTIALS, login(email).error().code)
        assertEquals(
            ErrorCode.TOKEN_USED_OR_EXPIRED,
            post("/v1/auth/email/verify", VerifyEmailRequest(verifyToken)).error().code,
        )

        // A retry of the delete (the first answer got lost) is still a 204.
        assertEquals(HttpStatusCode.NoContent, deleteMe(token).status)

        // The email is free again, and signing up with it starts a brand-new, empty account.
        val again = registered(email)
        assertNotEquals(session.user.id, again.user.id)
        assertTrue(again.isNewUser)
        assertEquals(0, rowsOf(Uuid.parse(again.user.id), emptyList()).checkIns)
    }

    private data class RowCounts(
        val users: Long,
        val identities: Long,
        val refreshTokens: Long,
        val emailTokens: Long,
        val checkIns: Long,
    )

    /** What is left of one user in every table that can hold their data (technical-plan.md §7). */
    private suspend fun rowsOf(userId: Uuid, identityIds: List<Uuid>): RowCounts = db.tx {
        RowCounts(
            users = UsersTable.selectAll().where { UsersTable.id eq userId }.count(),
            identities = AuthIdentitiesTable.selectAll().where { AuthIdentitiesTable.userId eq userId }.count(),
            refreshTokens = RefreshTokensTable.selectAll().where { RefreshTokensTable.userId eq userId }.count(),
            emailTokens = EmailTokensTable.selectAll().where { EmailTokensTable.identityId inList identityIds }.count(),
            checkIns = CheckInsTable.selectAll().where { CheckInsTable.userId eq userId }.count(),
        )
    }

    /** A check-in straight into the table. Sync, which will normally write these, is Phase 7. */
    private suspend fun insertCheckIn(userId: Uuid, note: String?) {
        val checkInId = Uuid.parse(Ids.newId())
        val sealed = cipher.seal(checkInId, note, null)
        val loggedAt = OffsetDateTime.of(2026, 11, 10, 8, 30, 0, 0, ZoneOffset.ofHours(8))
        db.tx {
            CheckInsTable.insert {
                it[id] = checkInId
                it[CheckInsTable.userId] = userId
                it[moodCode] = "CALM"
                it[energy] = 7
                it[stress] = 3
                it[CheckInsTable.note] = sealed.note
                it[triggerOther] = sealed.triggerOther
                it[keyId] = sealed.keyId
                it[supportUsed] = "NONE"
                it[isGoodMoment] = false
                it[CheckInsTable.loggedAt] = loggedAt
                it[tzOffsetMin] = 480
                it[clientUpdatedAt] = loggedAt
            }
        }
    }
}

private suspend fun AuthTestContext.getMe(token: String?): HttpResponse =
    client.get("/v1/me") { token?.let { bearerAuth(it) } }

private suspend inline fun <reified T> AuthTestContext.patchMe(token: String?, body: T): HttpResponse =
    client.patch("/v1/me") {
        token?.let { bearerAuth(it) }
        contentType(ContentType.Application.Json)
        setBody(body)
    }

/** [body] null sends no body at all, as a client that forgot the confirmation would. */
private suspend inline fun <reified T> AuthTestContext.deleteMe(token: String?, body: T?): HttpResponse =
    client.delete("/v1/me") {
        token?.let { bearerAuth(it) }
        if (body != null) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

private suspend fun AuthTestContext.deleteMe(token: String?): HttpResponse =
    deleteMe(token, DeleteMeRequest(AccountRules.DELETE_CONFIRMATION))

private suspend fun HttpResponse.user(): UserDto = Api.json.decodeFromString(bodyAsText())

@Suppress("unused")
private fun AuthResponse.userId(): Uuid = Uuid.parse(user.id)
