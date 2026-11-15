package app.ulap.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import app.ulap.core.database.model.CheckInEntity
import app.ulap.core.database.model.PullSeenEntity
import app.ulap.core.database.model.SyncPositionEntity
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * What sync reads and writes (technical-plan.md §8). Only core:sync uses it; screens and
 * repositories never do.
 *
 * The `mark…` and `count…` updates take the `updated_at` that was pushed and change the row only
 * if it still has it. A check-in edited while its push was on the way stays `PENDING`, so the
 * newer version goes up next.
 */
@Dao
interface SyncDao {
    // The upload queue: PENDING rows that haven't stopped trying (sync_error IS NULL).

    /** The next rows to push, oldest change first. */
    @Query(
        """
        SELECT * FROM check_ins
        WHERE sync_state = 'PENDING' AND sync_error IS NULL
        ORDER BY updated_at, id
        LIMIT :limit
        """,
    )
    suspend fun uploadBatch(limit: Int): List<CheckInEntity>

    /** How many rows wait to upload. SyncTriggers watches it. */
    @Query("SELECT COUNT(*) FROM check_ins WHERE sync_state = 'PENDING' AND sync_error IS NULL")
    fun observeWaitingCount(): Flow<Int>

    /** How many rows stopped trying (rejected, or `FAILED`). Account shows the number. */
    @Query("SELECT COUNT(*) FROM check_ins WHERE sync_state = 'PENDING' AND sync_error IS NOT NULL")
    fun observeStuckCount(): Flow<Int>

    /** The server has this version (`APPLIED` or `STALE`, api-spec.md §8.2). */
    @Query(
        """
        UPDATE check_ins SET sync_state = 'SYNCED', server_seq = :serverSeq, sync_error = NULL, sync_attempts = 0
        WHERE id = :id AND updated_at = :updatedAt AND sync_state = 'PENDING'
        """,
    )
    suspend fun markSynced(id: String, updatedAt: Instant, serverSeq: Long): Int

    /** The server refused this version: stop trying until an edit or "Try again". */
    @Query(
        """
        UPDATE check_ins SET sync_error = :reason
        WHERE id = :id AND updated_at = :updatedAt AND sync_state = 'PENDING'
        """,
    )
    suspend fun markRejected(id: String, updatedAt: Instant, reason: String): Int

    /**
     * A push with this row in it failed with `500 INTERNAL`. At [maxAttempts] the row stops
     * trying, with `sync_error = 'FAILED'` (technical-plan.md §8, When a change can't upload).
     */
    @Query(
        """
        UPDATE check_ins
        SET sync_attempts = sync_attempts + 1,
            sync_error = CASE WHEN sync_attempts + 1 >= :maxAttempts THEN 'FAILED' ELSE sync_error END
        WHERE id = :id AND updated_at = :updatedAt AND sync_state = 'PENDING' AND sync_error IS NULL
        """,
    )
    suspend fun countInternalError(id: String, updatedAt: Instant, maxAttempts: Int): Int

    /** "Try again" in Account: every stopped row goes back in the queue. */
    @Query(
        """
        UPDATE check_ins SET sync_error = NULL, sync_attempts = 0
        WHERE sync_state = 'PENDING' AND sync_error IS NOT NULL
        """,
    )
    suspend fun retryStuck(): Int

    // A first sign-in (api-spec.md §8.5).

    /** The check-ins that aren't deleted. */
    @Query("SELECT COUNT(*) FROM check_ins WHERE deleted_at IS NULL")
    suspend fun countLive(): Int

    /** Every live check-in goes up to the account, as new. */
    @Query(
        """
        UPDATE check_ins SET sync_state = 'PENDING', sync_error = NULL, sync_attempts = 0, server_seq = NULL
        WHERE deleted_at IS NULL
        """,
    )
    suspend fun queueLiveForUpload()

    /** Deletes this phone's tombstones: the account never had those check-ins, so it needs no delete. */
    @Query("DELETE FROM check_ins WHERE deleted_at IS NOT NULL")
    suspend fun deleteTombstones()

    /** "Don't add": this phone's check-ins go, and the account's come down instead. */
    @Query("DELETE FROM check_ins")
    suspend fun deleteAllCheckIns()

    // Pull.

    /** A row as merged with the server's version (api-spec.md §8.3, Phone merge). */
    @Upsert
    suspend fun saveMerged(checkIn: CheckInEntity)

    // The position, one row.

    @Query("SELECT * FROM sync_position WHERE id = 0")
    suspend fun position(): SyncPositionEntity?

    @Query("SELECT * FROM sync_position WHERE id = 0")
    fun observePosition(): Flow<SyncPositionEntity?>

    @Upsert
    suspend fun savePosition(position: SyncPositionEntity)

    // A full resync (technical-plan.md §8, Full resync).

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSeen(ids: List<PullSeenEntity>)

    @Query("DELETE FROM pull_seen")
    suspend fun clearSeen()

    /**
     * The resync's last step: a `SYNCED` row the server didn't send was deleted there, and its
     * tombstone purged. `PENDING` rows stay and push as usual.
     */
    @Query("DELETE FROM check_ins WHERE sync_state = 'SYNCED' AND id NOT IN (SELECT id FROM pull_seen)")
    suspend fun deleteSyncedNotSeen(): Int
}
