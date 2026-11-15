package app.ulap.server.sync

import app.ulap.server.common.atUtc
import app.ulap.server.db.Db
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaDuration

/**
 * Deletes tombstones (deleted check-ins) older than [retention] for good (technical-plan.md §8,
 * rule 2). Each batch raises `sync_purge.purged_through_seq` in the same transaction, so a pull
 * whose cursor is below a purged delete gets `resetRequired` (api-spec.md §8.3, D6).
 *
 * The `scheduler` service runs it daily at 03:00 Manila time (ulap-reference.md §6.6). Safe to
 * run again at any time: a second run finds nothing old enough.
 */
class TombstonePurger(
    private val db: Db,
    private val clock: Clock,
    private val retention: Duration = 90.days,
    private val batchSize: Int = 1_000,
) {
    /** Purges every tombstone that's old enough, in batches, and returns how many went. */
    suspend fun purge(): Int {
        val cutoff = (clock.instant() - retention.toJavaDuration()).atUtc()
        var total = 0
        do {
            val purged = db.tx {
                val batch = lockTombstonesBefore(cutoff, batchSize)
                deleteCheckIns(batch.map { it.first })
                batch.maxOfOrNull { it.second }?.let { raisePurgedThroughSeq(it) }
                batch.size
            }
            total += purged
        } while (purged == batchSize)
        return total
    }
}
