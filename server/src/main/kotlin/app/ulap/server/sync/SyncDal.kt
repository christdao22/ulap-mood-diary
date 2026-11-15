@file:OptIn(ExperimentalUuidApi::class)

package app.ulap.server.sync

import app.ulap.server.crypto.SealedNotes
import app.ulap.server.db.CheckInsTable
import app.ulap.server.db.SyncPurgeTable
import app.ulap.server.db.UsersTable
import java.time.OffsetDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Sequence
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.nextLongVal
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.updateReturning

/*
 * Queries for the sync endpoints (technical-plan.md §7, api-spec.md §8). The purge has TombstoneDal.kt. Exposed
 * only, no rules (ulap-reference.md §6.1). Each function runs in the transaction the caller opened
 * with `Db.tx`. `note` and `trigger_other` go in and come out sealed; SyncService opens them.
 */

private val checkInSeq = Sequence("check_in_seq")

/** What a push needs to know about a stored check-in to merge an incoming change (api-spec.md §8.2). */
class StoredState(
    val userId: Uuid,
    val clientUpdatedAt: OffsetDateTime,
    val deletedAt: OffsetDateTime?,
    val serverSeq: Long,
)

/**
 * Every column of a check-in as written by a push. For a tombstone the service fills the content
 * columns with neutral values, because the table's NOT NULL columns still need something.
 */
// One parameter per column of check_ins.
@Suppress("LongParameterList")
class CheckInWrite(
    val id: Uuid,
    val moodCode: String,
    val energy: Short,
    val stress: Short,
    val stressAfter: Short?,
    val trigger: String?,
    val sealed: SealedNotes,
    val supportUsed: String,
    val isGoodMoment: Boolean,
    val loggedAt: OffsetDateTime,
    val tzOffsetMin: Short,
    val clientUpdatedAt: OffsetDateTime,
    val deletedAt: OffsetDateTime?,
)

/** A stored check-in as a pull reads it, notes still sealed. */
// One parameter per column of check_ins.
@Suppress("LongParameterList")
class StoredCheckIn(
    val id: Uuid,
    val moodCode: String,
    val energy: Short,
    val stress: Short,
    val stressAfter: Short?,
    val trigger: String?,
    val sealed: SealedNotes,
    val supportUsed: String,
    val isGoodMoment: Boolean,
    val loggedAt: OffsetDateTime,
    val tzOffsetMin: Short,
    val clientUpdatedAt: OffsetDateTime,
    val deletedAt: OffsetDateTime?,
    val serverSeq: Long,
) {
    override fun toString(): String = "StoredCheckIn(id=$id, serverSeq=$serverSeq)"
}

/**
 * Locks the user row until the transaction ends, so one user's pushes run one at a time, and
 * returns false when the account is gone.
 *
 * Why the lock: `server_seq` comes from one global sequence. If two pushes for the same user ran at
 * once, the one holding the lower number could commit second, and a pull in between would move
 * the phone's cursor past a change it never saw. One push at a time per user means that user's
 * numbers always commit in order. It also waits for a `DELETE /me` that's running, so a push
 * can't write rows into an account that's being deleted.
 *
 * FOR NO KEY UPDATE doesn't block the KEY SHARE lock that inserting a check-in takes on `users`.
 */
fun JdbcTransaction.lockUserForSync(userId: Uuid): Boolean = UsersTable
    .select(UsersTable.id)
    .where { UsersTable.id eq userId }
    .forUpdate(ForUpdateOption.PostgreSQL.ForNoKeyUpdate())
    .any()

/** The stored state of each of [ids] that exists, locked until the transaction ends. Any owner. */
fun JdbcTransaction.lockCheckInStates(ids: Collection<Uuid>): Map<Uuid, StoredState> {
    if (ids.isEmpty()) return emptyMap()
    return CheckInsTable
        .select(
            CheckInsTable.id,
            CheckInsTable.userId,
            CheckInsTable.clientUpdatedAt,
            CheckInsTable.deletedAt,
            CheckInsTable.serverSeq,
        )
        .where { CheckInsTable.id inList ids }
        .forUpdate()
        .associate { row ->
            row[CheckInsTable.id] to StoredState(
                userId = row[CheckInsTable.userId],
                clientUpdatedAt = row[CheckInsTable.clientUpdatedAt],
                deletedAt = row[CheckInsTable.deletedAt],
                serverSeq = row[CheckInsTable.serverSeq],
            )
        }
}

/** Inserts a new check-in for [userId] and returns the `server_seq` the database gave it. */
fun JdbcTransaction.insertCheckIn(userId: Uuid, write: CheckInWrite): Long = CheckInsTable
    .insertReturning(listOf(CheckInsTable.serverSeq)) {
        it[CheckInsTable.id] = write.id
        it[CheckInsTable.userId] = userId
        it.setContent(write)
    }
    .single()[CheckInsTable.serverSeq]

/**
 * Overwrites a stored check-in with [write] and gives it a new `server_seq` from the sequence, so
 * pulls see it again (technical-plan.md §8, rule 4). Returns the new number.
 */
fun JdbcTransaction.updateCheckIn(write: CheckInWrite): Long = CheckInsTable
    .updateReturning(listOf(CheckInsTable.serverSeq), where = { CheckInsTable.id eq write.id }) {
        it.setContent(write)
        it[CheckInsTable.serverSeq] = checkInSeq.nextLongVal()
        it[CheckInsTable.serverUpdatedAt] = CurrentTimestampWithTimeZone
    }
    .single()[CheckInsTable.serverSeq]

private fun UpdateBuilder<*>.setContent(write: CheckInWrite) {
    this[CheckInsTable.moodCode] = write.moodCode
    this[CheckInsTable.energy] = write.energy
    this[CheckInsTable.stress] = write.stress
    this[CheckInsTable.stressAfter] = write.stressAfter
    this[CheckInsTable.trigger] = write.trigger
    this[CheckInsTable.note] = write.sealed.note
    this[CheckInsTable.triggerOther] = write.sealed.triggerOther
    this[CheckInsTable.keyId] = write.sealed.keyId
    this[CheckInsTable.supportUsed] = write.supportUsed
    this[CheckInsTable.isGoodMoment] = write.isGoodMoment
    this[CheckInsTable.loggedAt] = write.loggedAt
    this[CheckInsTable.tzOffsetMin] = write.tzOffsetMin
    this[CheckInsTable.clientUpdatedAt] = write.clientUpdatedAt
    this[CheckInsTable.deletedAt] = write.deletedAt
}

/** Up to [limit] of [userId]'s check-ins with `server_seq > since`, tombstones included, in ascending order. */
fun JdbcTransaction.checkInsSince(userId: Uuid, since: Long, limit: Int): List<StoredCheckIn> = CheckInsTable
    .selectAll()
    .where { (CheckInsTable.userId eq userId) and (CheckInsTable.serverSeq greater since) }
    .orderBy(CheckInsTable.serverSeq, SortOrder.ASC)
    .limit(limit)
    .map { it.toStoredCheckIn() }

/** The highest `server_seq` the purge job has deleted, or 0 (technical-plan.md §7, `sync_purge`). */
fun JdbcTransaction.purgedThroughSeq(): Long = SyncPurgeTable
    .select(SyncPurgeTable.purgedThroughSeq)
    .where { SyncPurgeTable.id eq SYNC_PURGE_ROW }
    .single()[SyncPurgeTable.purgedThroughSeq]

/**
 * The highest `server_seq` the database has handed out, or 0 before the first one. Read from the
 * sequence itself, so a number that belonged to a purged or deleted row still counts.
 */
fun JdbcTransaction.lastIssuedSeq(): Long =
    exec("SELECT CASE WHEN is_called THEN last_value ELSE 0 END FROM check_in_seq") { rs ->
        rs.next()
        rs.getLong(1)
    } ?: 0L

/** The numbers behind `GET /sync/status` for [userId]. */
class SyncCounts(val liveCount: Long, val latestServerSeq: Long?, val lastWriteAt: OffsetDateTime?)

fun JdbcTransaction.syncCounts(userId: Uuid): SyncCounts {
    val liveCount = CheckInsTable
        .select(CheckInsTable.id.count())
        .where { (CheckInsTable.userId eq userId) and CheckInsTable.deletedAt.isNull() }
        .single()[CheckInsTable.id.count()]
    val maxSeq = CheckInsTable.serverSeq.max()
    val maxWrite = CheckInsTable.serverUpdatedAt.max()
    val latest = CheckInsTable
        .select(maxSeq, maxWrite)
        .where { CheckInsTable.userId eq userId }
        .single()
    return SyncCounts(liveCount, latest[maxSeq], latest[maxWrite])
}

/** `sync_purge` has exactly one row, with id 1 (V2__init_schema.sql). */
internal const val SYNC_PURGE_ROW: Short = 1

private fun ResultRow.toStoredCheckIn() = StoredCheckIn(
    id = this[CheckInsTable.id],
    moodCode = this[CheckInsTable.moodCode],
    energy = this[CheckInsTable.energy],
    stress = this[CheckInsTable.stress],
    stressAfter = this[CheckInsTable.stressAfter],
    trigger = this[CheckInsTable.trigger],
    sealed = SealedNotes(
        keyId = this[CheckInsTable.keyId],
        note = this[CheckInsTable.note],
        triggerOther = this[CheckInsTable.triggerOther],
    ),
    supportUsed = this[CheckInsTable.supportUsed],
    isGoodMoment = this[CheckInsTable.isGoodMoment],
    loggedAt = this[CheckInsTable.loggedAt],
    tzOffsetMin = this[CheckInsTable.tzOffsetMin],
    clientUpdatedAt = this[CheckInsTable.clientUpdatedAt],
    deletedAt = this[CheckInsTable.deletedAt],
    serverSeq = this[CheckInsTable.serverSeq],
)
