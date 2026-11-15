@file:OptIn(ExperimentalUuidApi::class)

package app.ulap.server.sync

import app.ulap.server.db.CheckInsTable
import app.ulap.server.db.SyncPurgeTable
import java.time.OffsetDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update

/*
 * Queries for the tombstone purge (technical-plan.md §7 and §8, rule 2). Exposed only, no rules.
 * Each function runs in the transaction TombstonePurger opened with `Db.tx`.
 */

/** The id and `server_seq` of up to [limit] tombstones deleted before [cutoff], locked. */
fun JdbcTransaction.lockTombstonesBefore(cutoff: OffsetDateTime, limit: Int): List<Pair<Uuid, Long>> = CheckInsTable
    .select(CheckInsTable.id, CheckInsTable.serverSeq)
    .where { CheckInsTable.deletedAt.isNotNull() and (CheckInsTable.deletedAt less cutoff) }
    .orderBy(CheckInsTable.serverSeq, SortOrder.ASC)
    .limit(limit)
    .forUpdate()
    .map { it[CheckInsTable.id] to it[CheckInsTable.serverSeq] }

fun JdbcTransaction.deleteCheckIns(ids: Collection<Uuid>): Int =
    if (ids.isEmpty()) 0 else CheckInsTable.deleteWhere { CheckInsTable.id inList ids }

/** Raises `purged_through_seq` to [seq]. It never goes down, even if an older batch commits late. */
fun JdbcTransaction.raisePurgedThroughSeq(seq: Long) {
    SyncPurgeTable.update({ (SyncPurgeTable.id eq SYNC_PURGE_ROW) and (SyncPurgeTable.purgedThroughSeq less seq) }) {
        it[purgedThroughSeq] = seq
        it[updatedAt] = CurrentTimestampWithTimeZone
    }
}
