package app.ulap.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import app.ulap.core.database.model.CheckInEntity
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes `check_ins`. Screens never call this directly: they go through
 * `CheckInRepository`, which sets `sync_state` and `updated_at` on every save.
 */
@Dao
interface CheckInDao {
    /** Adds a new check-in. An ID that already exists is a bug, so it throws. */
    @Insert
    suspend fun insert(checkIn: CheckInEntity)

    /** Replaces the row with the same ID. Returns 1, or 0 if there is no such row. */
    @Update
    suspend fun update(checkIn: CheckInEntity): Int

    /** The row with this ID, deleted or not, or `null`. */
    @Query("SELECT * FROM check_ins WHERE id = :id")
    suspend fun get(id: String): CheckInEntity?

    /**
     * Marks a check-in deleted at [at] and queues the delete for sync, in one statement.
     *
     * The row stays as a tombstone until the server has it (technical-plan.md §6). Its journal
     * text (`note`, `trigger_other`) is cleared right away, as the server does for its tombstones
     * (api-spec.md §5.3). Returns 1, or 0 if the row is missing or already deleted.
     */
    @Query(
        """
        UPDATE check_ins
        SET deleted_at = :at, updated_at = :at, note = NULL, trigger_other = NULL,
            sync_state = 'PENDING', sync_error = NULL, sync_attempts = 0
        WHERE id = :id AND deleted_at IS NULL
        """,
    )
    suspend fun softDelete(id: String, at: Instant): Int

    /**
     * The live check-ins of one local day, newest first, re-emitted on every change.
     *
     * [epochDay] counts days from 1970-01-01 (kotlinx-datetime's `LocalDate.toEpochDays()`).
     * Each row's day is the one on the phone's clock when it was logged: `logged_at` shifted by
     * `tz_offset_min`. So 11:30 PM in Manila stays on that day even if the phone later moves to
     * another time zone. Integer division is safe here: no check-in is before 2020.
     */
    @Query(
        """
        SELECT * FROM check_ins
        WHERE deleted_at IS NULL
            AND (logged_at + tz_offset_min * 60000) / 86400000 = :epochDay
        ORDER BY logged_at DESC, id DESC
        """,
    )
    fun observeDay(epochDay: Long): Flow<List<CheckInEntity>>

    /**
     * Every live check-in, newest first, re-emitted on every change. Logs groups them by local day
     * in memory (`groupByLocalDay` in :shared). The `(deleted_at, logged_at)` index serves both
     * the filter and the order.
     */
    @Query("SELECT * FROM check_ins WHERE deleted_at IS NULL ORDER BY logged_at DESC, id DESC")
    fun observeAll(): Flow<List<CheckInEntity>>
}
