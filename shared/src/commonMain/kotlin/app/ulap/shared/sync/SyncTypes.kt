package app.ulap.shared.sync

import app.ulap.shared.validation.CheckInInput
import kotlin.time.Instant

/*
 * What sync moves between the phone and the server (api-spec.md §8), without the wire format.
 * The app's SyncApi turns these into the DTOs in app.ulap.shared.api and back, so the DTOs never
 * leave core:network (README §5, Module rules).
 */

/** One change to one check-in: a save or a delete. */
sealed interface CheckInChange {
    val id: String

    /** When the change was made. The newer of two changes wins (technical-plan.md §8, Rules). */
    val updatedAt: Instant

    /** A new or edited check-in. `toString()` hides [checkIn], which holds journal text. */
    data class Saved(override val id: String, override val updatedAt: Instant, val checkIn: CheckInInput) :
        CheckInChange {
        override fun toString(): String = "CheckInChange.Saved(id=$id, updatedAt=$updatedAt, checkIn=***)"
    }

    /** A delete (a tombstone). It carries no content: the server clears the journal text. */
    data class Deleted(override val id: String, override val updatedAt: Instant, val deletedAt: Instant) :
        CheckInChange
}

/**
 * The server took the change (api-spec.md §8.2). It stored it (`APPLIED`) or already had this
 * version or a newer one (`STALE`). The phone does the same for both: mark the row `SYNCED` and
 * keep [serverSeq]. A newer version, if there is one, comes on the next pull.
 */
data class AcceptedChange(val id: String, val serverSeq: Long)

/**
 * The server refused the change. [reason] is a `PushRejectReason` value and [field] names the
 * field when there is one. The phone stops retrying the row (technical-plan.md §8, When a change
 * can't upload). Log [id] and [reason] only, never the change.
 */
data class RejectedChange(val id: String, val reason: String, val field: String?)

/** The answer to one `POST /sync/push`. */
data class PushOutcome(val accepted: List<AcceptedChange>, val rejected: List<RejectedChange>, val serverTime: Instant)

/** A change from `GET /sync/pull`, with the number the server gave it. */
data class PulledChange(val serverSeq: Long, val change: CheckInChange)

/**
 * One page of `GET /sync/pull` (api-spec.md §8.3). [changes] are in ascending [PulledChange.serverSeq]
 * order. Pull again with `since = nextSince` while [hasMore]. [resetRequired] means start a full
 * resync from `since = 0` (technical-plan.md §8, Full resync).
 */
data class PullPage(
    val changes: List<PulledChange>,
    val nextSince: Long,
    val hasMore: Boolean,
    val resetRequired: Boolean,
    val serverTime: Instant,
)

/** `GET /sync/status` (api-spec.md §8.4), for the Account screen. */
data class ServerSyncStatus(val checkInCount: Long, val latestServerSeq: Long, val lastPushAt: Instant?)
