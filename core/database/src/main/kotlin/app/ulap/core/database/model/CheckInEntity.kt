package app.ulap.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import kotlin.time.Instant

/**
 * One check-in: the `check_ins` table, column for column as in technical-plan.md §6.
 *
 * Times are stored as epoch milliseconds (UTC), and enums by name. Rows are never deleted
 * here: a delete sets [deletedAt] (a tombstone), so it can sync to the server.
 */
@Entity(
    tableName = "check_ins",
    indices = [
        // Lists and reports read the live check-ins (deleted_at IS NULL) by time.
        Index(value = ["deleted_at", "logged_at"]),
        // Sync finds the PENDING rows.
        Index(value = ["sync_state"]),
        Index(value = ["mood_code"]),
    ],
)
data class CheckInEntity(
    /** UUIDv7 from `Ids.newId()`, made on the phone. Never changes, even after sync. */
    @PrimaryKey val id: String,
    /** A code from `MoodCatalog`, e.g. `worried`. */
    @ColumnInfo(name = "mood_code") val moodCode: String,
    /** 1..10. */
    val energy: Int,
    /** 1..10. */
    val stress: Int,
    /** 1..10, from "How stressed do you feel now?". `null` until they answer. */
    @ColumnInfo(name = "stress_after") val stressAfter: Int?,
    val trigger: Trigger?,
    /** Their own words when [trigger] is OTHER. A journal field: never log it. */
    @ColumnInfo(name = "trigger_other") val triggerOther: String?,
    /** The journal. Never log it. */
    val note: String?,
    @ColumnInfo(name = "support_used") val supportUsed: SupportType,
    @ColumnInfo(name = "is_good_moment") val isGoodMoment: Boolean,
    @ColumnInfo(name = "logged_at") val loggedAt: Instant,
    /** The phone's UTC offset when logged, so reports group by the local day it was then. */
    @ColumnInfo(name = "tz_offset_min") val tzOffsetMin: Int,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    /** Set on every edit. The server uses it to pick the newer change (api-spec.md §8). */
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant?,
    @ColumnInfo(name = "sync_state") val syncState: SyncState,
    /**
     * Why this row stopped trying to upload: a rejection reason from `/sync/push`, or `FAILED`
     * after 10 internal errors (technical-plan.md §8). `null` while it can still sync.
     */
    @ColumnInfo(name = "sync_error") val syncError: String?,
    /** Push requests that failed with `500 INTERNAL` with this row in them. 0 after every edit. */
    @ColumnInfo(name = "sync_attempts") val syncAttempts: Int,
    /** The server's change number, once the server has it. */
    @ColumnInfo(name = "server_seq") val serverSeq: Long?,
)
