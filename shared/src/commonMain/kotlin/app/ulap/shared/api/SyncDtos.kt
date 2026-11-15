package app.ulap.shared.api

import kotlin.time.Instant
import kotlinx.serialization.Serializable

/*
 * Request and response bodies of the sync endpoints (api-spec.md §8). The app and the server use
 * these same classes, so the wire format can't drift between them.
 */

/** The size limits of the sync endpoints (api-spec.md §8.2, §8.3, §11). */
object SyncLimits {
    /** Changes in one `POST /sync/push`. More → `413 PAYLOAD_TOO_LARGE`. The phone sends batches of this size. */
    const val MAX_PUSH_CHANGES: Int = 200

    /** The largest request body the server reads, 1 MB (api-spec.md §11, Input limits). */
    const val MAX_BODY_BYTES: Long = 1_048_576

    /** `GET /sync/pull?limit=`: the default and the allowed range. */
    const val PULL_DEFAULT_LIMIT: Int = 500
    val PULL_LIMIT_RANGE: IntRange = 1..1_000
}

/**
 * One check-in on the wire (api-spec.md §5.3). Every field but [id] and [updatedAt] is optional,
 * because a delete (a tombstone) carries only `id`, `updatedAt`, `deletedAt` and, on pull,
 * `serverSeq`.
 *
 * [trigger] and [supportUsed] are enum names kept as strings, not Kotlin enums. An unknown name
 * then rejects only that one change (`INVALID_ENUM`) instead of failing the whole request, and an
 * older app can still read a value a newer server added (api-spec.md §2, Versioning).
 *
 * [note] and [triggerOther] are journal text: `toString()` hides them, so they can't reach a log.
 */
@Serializable
data class CheckInDto(
    val id: String,
    val moodCode: String? = null,
    val energy: Int? = null,
    val stress: Int? = null,
    val stressAfter: Int? = null,
    val trigger: String? = null,
    val triggerOther: String? = null,
    val note: String? = null,
    val supportUsed: String? = null,
    val isGoodMoment: Boolean? = null,
    @Serializable(with = ApiInstantSerializer::class)
    val loggedAt: Instant? = null,
    val tzOffsetMin: Int? = null,
    @Serializable(with = ApiInstantSerializer::class)
    val updatedAt: Instant,
    @Serializable(with = ApiInstantSerializer::class)
    val deletedAt: Instant? = null,
    /** Set by the server on pull. Ignored on push. */
    val serverSeq: Long? = null,
) {
    val isDelete: Boolean get() = deletedAt != null

    override fun toString(): String =
        "CheckInDto(id=$id, updatedAt=$updatedAt, deletedAt=$deletedAt, serverSeq=$serverSeq, text=***)"
}

/** `POST /sync/push` (api-spec.md §8.2). */
@Serializable
data class PushRequest(val changes: List<CheckInDto>)

/** What the server did with a change it accepted. */
@Serializable
enum class PushStatus {
    /** Stored. [PushResult.serverSeq] is its new number. */
    APPLIED,

    /** The server already had this version or a newer one, and kept that. The phone gets it on pull. */
    STALE,
}

@Serializable
data class PushResult(val id: String, val serverSeq: Long, val status: PushStatus)

/**
 * A change the server refused. [reason] is one of [PushRejectReason]; [field] names the field when
 * there is one. The phone stops retrying the row (technical-plan.md §8, When a change can't upload).
 */
@Serializable
data class PushRejection(val id: String, val reason: String, val field: String? = null)

/**
 * The `reason` values of api-spec.md §8.2. The first five come from
 * [app.ulap.shared.validation.Reason]; only ever add one.
 */
object PushRejectReason {
    const val INVALID_MOOD: String = "INVALID_MOOD"
    const val OUT_OF_RANGE: String = "OUT_OF_RANGE"
    const val TOO_LONG: String = "TOO_LONG"
    const val INVALID_ENUM: String = "INVALID_ENUM"
    const val INVALID_TIME: String = "INVALID_TIME"
    const val GOOD_MOMENT_NOT_ALLOWED: String = "GOOD_MOMENT_NOT_ALLOWED"
    const val FORBIDDEN: String = "FORBIDDEN"
}

@Serializable
data class PushResponse(
    val accepted: List<PushResult>,
    val rejected: List<PushRejection>,
    @Serializable(with = ApiInstantSerializer::class)
    val serverTime: Instant,
)

/**
 * `GET /sync/pull` (api-spec.md §8.3). [changes] are in ascending `serverSeq` order. Keep pulling
 * with `since = nextSince` while [hasMore]. [resetRequired] starts a full resync (technical-plan.md §8).
 */
@Serializable
data class PullResponse(
    val changes: List<CheckInDto>,
    val nextSince: Long,
    val hasMore: Boolean,
    // No default: with one, a `false` would be left out of the JSON, and the field is always sent (§8.3).
    val resetRequired: Boolean,
    @Serializable(with = ApiInstantSerializer::class)
    val serverTime: Instant,
)

/** `GET /sync/status` (api-spec.md §8.4), for the Account screen. */
@Serializable
data class SyncStatusResponse(
    val checkInCount: Long,
    val latestServerSeq: Long,
    @Serializable(with = ApiInstantSerializer::class)
    val lastPushAt: Instant? = null,
)
