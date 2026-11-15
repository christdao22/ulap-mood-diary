package app.ulap.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Instant

/**
 * `pull_seen` (technical-plan.md §6): the IDs of the live check-ins the server sent during a full
 * resync. Empty at every other time.
 */
@Entity(tableName = "pull_seen")
data class PullSeenEntity(@PrimaryKey val id: String)

/**
 * `sync_position`: where sync is, in one row (technical-plan.md §6). It sits in Room, next to the
 * check-ins, so each sync step changes the rows and the position in one transaction, and a new
 * database (a lost Keystore key, or "Delete account and all data") starts again from nothing.
 * No row yet means every field's default.
 */
@Entity(tableName = "sync_position")
data class SyncPositionEntity(
    /** Always [ONLY_ROW]. */
    @PrimaryKey val id: Int = ONLY_ROW,
    /**
     * The account these check-ins belong to: set when someone signs in (api-spec.md §8.5). Signing
     * in again to the same account just carries on; a different one is a first sign-in.
     */
    @ColumnInfo(name = "owner_user_id") val ownerUserId: String? = null,
    /** The last `serverSeq` pulled. 0 pulls everything. */
    @ColumnInfo(name = "last_pull_seq") val lastPullSeq: Long = 0,
    /** True while a full resync runs (technical-plan.md §8, Full resync). Survives the app being killed. */
    @ColumnInfo(name = "sync_resetting") val resetting: Boolean = false,
    /** When the last sync finished. Null until the first one. */
    @ColumnInfo(name = "last_sync_at") val lastSyncAt: Instant? = null,
    /**
     * Waiting for the answer to "Add this phone's check-ins to your account?" (api-spec.md §8.5).
     * Nothing syncs until then.
     */
    @ColumnInfo(name = "asking_to_add") val askingToAdd: Boolean = false,
) {
    companion object {
        const val ONLY_ROW: Int = 0
    }
}
