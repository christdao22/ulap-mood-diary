@file:OptIn(ExperimentalUuidApi::class)

package app.ulap.server.sync

import app.ulap.server.auth.post
import app.ulap.server.auth.uniqueEmail
import app.ulap.server.auth.withAuthServer
import app.ulap.server.common.RateLimitRule
import app.ulap.server.common.RateLimitSettings
import app.ulap.server.db.CheckInsTable
import app.ulap.server.db.Db
import app.ulap.server.db.TestPostgres
import app.ulap.server.error
import app.ulap.shared.api.ErrorCode
import app.ulap.shared.api.PushRejectReason
import app.ulap.shared.api.PushRejection
import app.ulap.shared.api.PushStatus
import app.ulap.shared.api.SyncLimits
import app.ulap.shared.api.VerifyEmailRequest
import app.ulap.shared.model.Ids
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

/** `POST /sync/push` (api-spec.md §8.2) on the real module and a real PostgreSQL. */
class SyncPushTest {
    private val db = Db(TestPostgres.dataSource)

    @Test
    fun aNewCheckInIsInsertedAndItsNoteIsStoredEncrypted() = withAuthServer {
        val token = syncUser()
        val change = checkIn(note = "Report due tomorrow.")

        val response = pushed(token, change)

        val result = response.accepted.single()
        assertEquals(change.id, result.id)
        assertEquals(PushStatus.APPLIED, result.status)
        assertTrue(response.rejected.isEmpty())
        assertEquals(now(), response.serverTime)
        val stored = storedRow(change.id)
        assertEquals(result.serverSeq, stored[CheckInsTable.serverSeq])
        val note = assertNotNull(stored[CheckInsTable.note])
        assertFalse(note.decodeToString().contains("Report"), "the note must be stored sealed, not as text")
        assertNotNull(stored[CheckInsTable.keyId])
    }

    @Test
    fun aNewerUpdateIsAppliedWithANewServerSeq() = withAuthServer {
        val token = syncUser()
        val first = checkIn()
        val firstSeq = pushed(token, first).accepted.single().serverSeq

        val newer = first.copy(stress = 3, note = "Done now.", updatedAt = first.updatedAt + 1.minutes)
        val result = pushed(token, newer).accepted.single()

        assertEquals(PushStatus.APPLIED, result.status)
        assertTrue(result.serverSeq > firstSeq)
        val pulled = pulledAll(token).single()
        assertEquals(3, pulled.stress)
        assertEquals("Done now.", pulled.note)
    }

    @Test
    fun anOlderOrEqualUpdateIsStaleAndKeepsTheStoredVersion() = withAuthServer {
        val token = syncUser()
        val stored = checkIn()
        val seq = pushed(token, stored).accepted.single().serverSeq

        val older = stored.copy(stress = 1, updatedAt = stored.updatedAt - 1.minutes)
        val equal = stored.copy(stress = 2)
        val response = pushed(token, older, equal)

        assertEquals(listOf(PushStatus.STALE, PushStatus.STALE), response.accepted.map { it.status })
        assertEquals(listOf(seq, seq), response.accepted.map { it.serverSeq })
        assertEquals(8, pulledAll(token).single().stress)
    }

    @Test
    fun aDeleteLeavesATombstoneWithNoJournalText() = withAuthServer {
        val token = syncUser()
        val change = checkIn(note = "Private words.").copy(trigger = "OTHER", triggerOther = "My own reason")
        pushed(token, change)

        val delete = deleteOf(change.id)
        val result = pushed(token, delete).accepted.single()

        assertEquals(PushStatus.APPLIED, result.status)
        val row = storedRow(change.id)
        assertNull(row[CheckInsTable.note])
        assertNull(row[CheckInsTable.triggerOther])
        assertNull(row[CheckInsTable.keyId])
        assertEquals("", row[CheckInsTable.moodCode])
        val tombstone = pulledAll(token).single()
        assertEquals(deleteOf(change.id).copy(serverSeq = result.serverSeq), tombstone)
    }

    @Test
    fun aDeleteOfAnIdTheServerHasNeverSeenIsStoredAsATombstone() = withAuthServer {
        val token = syncUser()
        val delete = deleteOf(Ids.newId())

        val result = pushed(token, delete).accepted.single()

        assertEquals(PushStatus.APPLIED, result.status)
        assertEquals(delete.copy(serverSeq = result.serverSeq), pulledAll(token).single())
    }

    @Test
    fun anEditAfterADeleteIsStaleBecauseThereIsNoUndelete() = withAuthServer {
        val token = syncUser()
        val change = checkIn()
        pushed(token, change)
        val deleteSeq = pushed(token, deleteOf(change.id)).accepted.single().serverSeq

        val edit = change.copy(stress = 2, updatedAt = now())
        val result = pushed(token, edit).accepted.single()

        assertEquals(PushStatus.STALE, result.status)
        assertEquals(deleteSeq, result.serverSeq)
        assertNotNull(pulledAll(token).single().deletedAt)
    }

    @Test
    fun anotherUsersCheckInIsForbiddenAndLeftAlone() = withAuthServer {
        val owner = syncUser()
        val change = checkIn()
        pushed(owner, change)

        val response = pushed(syncUser(), change.copy(stress = 1, updatedAt = now()))

        assertTrue(response.accepted.isEmpty())
        assertEquals(listOf(PushRejection(change.id, PushRejectReason.FORBIDDEN)), response.rejected)
        assertEquals(8, pulledAll(owner).single().stress)
    }

    @Test
    fun invalidValuesRejectOnlyThatChange() = withAuthServer {
        val token = syncUser()
        val good = checkIn()
        val badMood = checkIn(moodCode = "meh")
        val badTrigger = checkIn().copy(trigger = "WEATHER")
        val badSupport = checkIn().copy(supportUsed = "NAP")
        val goodMomentWhileWorried = checkIn().copy(isGoodMoment = true)
        val longNote = checkIn(note = "x".repeat(5_001))
        val futureLoggedAt = checkIn().copy(loggedAt = now() + 1.hours)

        val response =
            pushed(token, good, badMood, badTrigger, badSupport, goodMomentWhileWorried, longNote, futureLoggedAt)

        assertEquals(listOf(good.id), response.accepted.map { it.id })
        assertEquals(
            listOf(
                PushRejection(badMood.id, PushRejectReason.INVALID_MOOD, "moodCode"),
                PushRejection(badTrigger.id, PushRejectReason.INVALID_ENUM, "trigger"),
                PushRejection(badSupport.id, PushRejectReason.INVALID_ENUM, "supportUsed"),
                PushRejection(goodMomentWhileWorried.id, PushRejectReason.GOOD_MOMENT_NOT_ALLOWED, "isGoodMoment"),
                PushRejection(longNote.id, PushRejectReason.TOO_LONG, "note"),
                PushRejection(futureLoggedAt.id, PushRejectReason.INVALID_TIME, "loggedAt"),
            ),
            response.rejected,
        )
        assertEquals(listOf(good.id), pulledAll(token).map { it.id })
    }

    @Test
    fun aChangeTheServerCantReadFailsTheWholeRequest() = withAuthServer {
        val token = syncUser()

        val missingMood = push(token, checkIn(), checkIn().copy(moodCode = null))
        assertEquals(HttpStatusCode.BadRequest, missingMood.status)
        assertEquals(ErrorCode.BAD_REQUEST, missingMood.error().code)
        assertEquals("changes[1].moodCode", missingMood.error().details?.single()?.field)

        val badId = push(token, checkIn(id = "not-a-uuid"))
        assertEquals(HttpStatusCode.BadRequest, badId.status)
        assertEquals(ErrorCode.VALIDATION_FAILED, badId.error().code)

        assertTrue(pulledAll(token).isEmpty(), "nothing from a failed request is stored")
    }

    @Test
    fun theClockGuardReplacesAFutureUpdatedAtWithServerTime() = withAuthServer {
        val token = syncUser()
        val change = checkIn(updatedAt = now() + 2.hours)
        val nearFuture = checkIn(updatedAt = now() + 4.minutes)

        pushed(token, change, nearFuture)

        val pulled = pulledAll(token).associateBy { it.id }
        assertEquals(now(), pulled.getValue(change.id).updatedAt)
        assertEquals(nearFuture.updatedAt, pulled.getValue(nearFuture.id).updatedAt)
        // The phone's real later edit still wins over the guarded one.
        val later = change.copy(stress = 1, updatedAt = now() + 1.minutes)
        assertEquals(PushStatus.APPLIED, pushed(token, later).accepted.single().status)
    }

    @Test
    fun theSameIdTwiceInOneBatchMergesInOrder() = withAuthServer {
        val token = syncUser()
        val first = checkIn()
        val second = first.copy(stress = 2, updatedAt = first.updatedAt + 1.minutes)

        val response = pushed(token, first, second)

        assertEquals(listOf(PushStatus.APPLIED, PushStatus.APPLIED), response.accepted.map { it.status })
        assertEquals(2, pulledAll(token).single().stress)
    }

    @Test
    fun twoHundredChangesAreAcceptedAndTwoHundredOneAreTooMany() = withAuthServer {
        val token = syncUser()
        val batch = List(SyncLimits.MAX_PUSH_CHANGES) { checkIn() }

        assertEquals(SyncLimits.MAX_PUSH_CHANGES, pushed(token, *batch.toTypedArray()).accepted.size)

        val tooMany = push(token, *(batch + checkIn()).toTypedArray())
        assertEquals(HttpStatusCode.PayloadTooLarge, tooMany.status)
        assertEquals(ErrorCode.PAYLOAD_TOO_LARGE, tooMany.error().code)
    }

    @Test
    fun aBodyOverOneMegabyteIsTurnedAway() = withAuthServer {
        val token = syncUser()
        val padding = "x".repeat(SyncLimits.MAX_BODY_BYTES.toInt())

        val response = client.post("/v1/sync/push") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"changes":[],"padding":"$padding"}""")
        }

        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        assertEquals(ErrorCode.PAYLOAD_TOO_LARGE, response.error().code)
    }

    @Test
    fun syncNeedsAVerifiedEmail() = withAuthServer {
        val email = uniqueEmail()
        val session = registered(email)

        for (response in listOf(
            push(session.accessToken, checkIn()),
            pull(session.accessToken),
            status(session.accessToken),
        )) {
            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertEquals(ErrorCode.EMAIL_NOT_VERIFIED, response.error().code)
        }

        val verified = post("/v1/auth/email/verify", VerifyEmailRequest(mailer.lastTokenFor(email, "verify")))
        assertEquals(HttpStatusCode.NoContent, verified.status)
        assertEquals(PushStatus.APPLIED, pushed(session.accessToken, checkIn()).accepted.single().status)
    }

    @Test
    fun syncNeedsAnAccessToken() = withAuthServer {
        for (response in listOf(push(null, checkIn()), pull(null), status(null))) {
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals(ErrorCode.UNAUTHENTICATED, response.error().code)
        }
    }

    @Test
    fun pushHasItsOwnRateLimitPerUser() = withAuthServer(RateLimitSettings(syncPush = RateLimitRule(2, 1.minutes))) {
        val token = syncUser()
        repeat(2) { pushed(token, checkIn()) }

        val limited = push(token, checkIn())
        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        assertEquals(ErrorCode.RATE_LIMITED, limited.error().code)
        assertNotNull(limited.headers["Retry-After"])
        // Someone else isn't held back.
        pushed(syncUser(), checkIn())
    }

    private fun storedRow(id: String) = runBlocking {
        db.tx { CheckInsTable.selectAll().where { CheckInsTable.id eq Uuid.parse(id) }.single() }
    }
}
