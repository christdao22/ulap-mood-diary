package app.ulap.server.sync

import app.ulap.server.auth.withAuthServer
import app.ulap.server.db.Db
import app.ulap.server.db.TestPostgres
import app.ulap.server.error
import app.ulap.shared.api.ErrorCode
import app.ulap.shared.api.PullResponse
import app.ulap.shared.api.SyncStatusResponse
import app.ulap.shared.model.Ids
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/**
 * `GET /sync/pull` and `GET /sync/status` (api-spec.md §8.3, §8.4), `resetRequired` (D6) and the
 * tombstone purge, on the real module and a real PostgreSQL.
 */
class SyncPullTest {
    private val db = Db(TestPostgres.dataSource)

    @Test
    fun pullReturnsEveryFieldAsPushed() = withAuthServer {
        val token = syncUser()
        val change = checkIn().copy(trigger = "OTHER", triggerOther = "My own reason", isGoodMoment = false)

        val seq = pushed(token, change).accepted.single().serverSeq

        val page = pulled(token)
        assertEquals(listOf(change.copy(serverSeq = seq)), page.changes)
        assertEquals(seq, page.nextSince)
        assertFalse(page.hasMore)
        assertFalse(page.resetRequired)
        assertEquals(now(), page.serverTime)
    }

    @Test
    fun pullPagesInServerSeqOrder() = withAuthServer {
        val token = syncUser()
        val changes = List(5) { checkIn() }
        val seqs = pushed(token, *changes.toTypedArray()).accepted.map { it.serverSeq }

        val first = pulled(token, since = 0, limit = 2)
        assertEquals(changes.take(2).map { it.id }, first.changes.map { it.id })
        assertEquals(seqs[1], first.nextSince)
        assertTrue(first.hasMore)

        val all = pulledAll(token, limit = 2)
        assertEquals(changes.map { it.id }, all.map { it.id })
        assertEquals(seqs, all.map { it.serverSeq })

        val caughtUp = pulled(token, since = seqs.last())
        assertTrue(caughtUp.changes.isEmpty())
        assertEquals(seqs.last(), caughtUp.nextSince)
        assertFalse(caughtUp.hasMore)
    }

    @Test
    fun pullIncludesTombstonesAndOnlyTheCallersOwnCheckIns() = withAuthServer {
        val mine = syncUser()
        val theirs = syncUser()
        val kept = checkIn()
        val deleted = checkIn()
        pushed(mine, kept, deleted)
        pushed(mine, deleteOf(deleted.id))
        pushed(theirs, checkIn())

        val pulled = pulledAll(mine)

        assertEquals(listOf(kept.id, deleted.id), pulled.map { it.id })
        assertNotNull(pulled.last().deletedAt)
        assertEquals(null, pulled.last().moodCode)
    }

    @Test
    fun pullChecksItsQuery() = withAuthServer {
        val token = syncUser()
        for (query in listOf("?limit=0", "?limit=1001", "?limit=99999999999", "?since=-1", "?since=abc")) {
            val response = pull(token, query)
            assertEquals(HttpStatusCode.BadRequest, response.status, query)
            assertEquals(ErrorCode.VALIDATION_FAILED, response.error().code, query)
        }
    }

    @Test
    fun aCursorFromAnotherDatabaseNeedsAReset() = withAuthServer {
        val token = syncUser()
        pushed(token, checkIn())
        val lastIssued = db.tx { lastIssuedSeq() }

        val reset = pulled(token, since = lastIssued + 1_000_000)

        assertEquals(
            PullResponse(emptyList(), nextSince = 0, hasMore = true, resetRequired = true, serverTime = now()),
            reset,
        )
        assertFalse(pulled(token, since = 0).resetRequired, "since=0 never needs a reset")
        assertTrue(pull(token, "?since=0").bodyAsText().contains("\"resetRequired\":false"), "the field is always sent")
    }

    @Test
    fun thePurgeRemovesOldTombstonesAndOlderCursorsNeedAReset() = withAuthServer {
        val token = syncUser()
        val live = checkIn()
        val oldDelete = deleteOf(Ids.newId(), at = now() - 100.days)
        val recentDelete = deleteOf(Ids.newId())
        pushed(token, live)
        val oldSeq = pushed(token, oldDelete).accepted.single().serverSeq
        pushed(token, recentDelete)

        val purged = TombstonePurger(db, clock).purge()

        assertTrue(purged >= 1)
        assertEquals(listOf(live.id, recentDelete.id), pulledAll(token).map { it.id })
        val purgedThrough = db.tx { purgedThroughSeq() }
        assertTrue(purgedThrough >= oldSeq)
        // A phone that last pulled before the purged delete would never hear about it.
        val reset = pulled(token, since = oldSeq - 1)
        assertTrue(reset.resetRequired)
        assertTrue(reset.changes.isEmpty())
        // A phone that already has it carries on as usual.
        assertFalse(pulled(token, since = purgedThrough).resetRequired)
        assertEquals(0, TombstonePurger(db, clock).purge(), "nothing old enough is left")
    }

    @Test
    fun aFullDownloadPagesPastThePurgeLine() = withAuthServer {
        val token = syncUser()
        val older = List(3) { checkIn() }
        pushed(token, *older.toTypedArray())
        pushed(token, deleteOf(Ids.newId(), at = now() - 100.days))
        TombstonePurger(db, clock).purge()
        val newer = checkIn()
        pushed(token, newer)

        // Page 1 ends below the purge line. Without `full`, page 2 would start the reset again.
        val first = pulled(token, since = 0, limit = 2, full = true)
        assertTrue(first.nextSince < db.tx { purgedThroughSeq() })
        assertTrue(pulled(token, since = first.nextSince, limit = 2).resetRequired)

        val second = pulled(token, since = first.nextSince, limit = 2, full = true)
        assertFalse(second.resetRequired)
        assertFalse(second.hasMore)
        assertEquals((older + newer).map { it.id }, (first.changes + second.changes).map { it.id })

        // A cursor from another database still needs a reset, full or not.
        assertTrue(pulled(token, since = db.tx { lastIssuedSeq() } + 1_000, full = true).resetRequired)
        assertEquals(HttpStatusCode.BadRequest, pull(token, "?since=1&full=yes").status)
    }

    @Test
    fun statusCountsLiveCheckIns() = withAuthServer {
        val token = syncUser()
        val empty = status(token)
        assertEquals(HttpStatusCode.OK, empty.status, empty.bodyAsText())
        assertEquals(SyncStatusResponse(checkInCount = 0, latestServerSeq = 0, lastPushAt = null), empty.syncStatus())

        val kept = checkIn()
        val deleted = checkIn()
        pushed(token, kept, deleted)
        val deleteSeq = pushed(token, deleteOf(deleted.id)).accepted.single().serverSeq

        val after = status(token).syncStatus()
        assertEquals(1, after.checkInCount)
        assertEquals(deleteSeq, after.latestServerSeq)
        assertNotNull(after.lastPushAt)
    }
}
