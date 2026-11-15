package app.ulap.core.sync

import app.ulap.core.database.model.CheckInEntity
import app.ulap.core.database.model.SyncState
import app.ulap.core.network.ApiResult
import app.ulap.core.network.sync.SyncApi
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import app.ulap.shared.sync.AcceptedChange
import app.ulap.shared.sync.CheckInChange
import app.ulap.shared.sync.PullPage
import app.ulap.shared.sync.PulledChange
import app.ulap.shared.sync.PushOutcome
import app.ulap.shared.sync.RejectedChange
import app.ulap.shared.sync.ServerSyncStatus
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The sync endpoints in memory, with the server's rules (api-spec.md §8.2, §8.3): last write wins
 * by `updatedAt`, a tombstone is never brought back, every stored change gets a new `serverSeq`,
 * and a cursor below the purge line gets `resetRequired`.
 *
 * Tests queue answers in [pushAnswers] and [pullAnswers]: a failure is returned instead of the
 * real answer, and null means "answer normally". [beforePushAnswer] runs while a push is "on its
 * way", to edit a row at that moment.
 */
internal class FakeSyncServer(private val time: Instant = T0) : SyncApi {
    private var seq = 0L
    private val stored = mutableMapOf<String, PulledChange>()
    private var purgedThroughSeq = 0L

    val pushes = mutableListOf<List<CheckInChange>>()
    var pulls = 0

    /** Whether each pull said `full`. */
    val fullPulls = mutableListOf<Boolean>()
    val pushAnswers = ArrayDeque<ApiResult<Nothing>?>()
    val pullAnswers = ArrayDeque<ApiResult<Nothing>?>()
    var pageLimit = 500
    var reject: (CheckInChange) -> String? = { null }
    var beforePushAnswer: suspend () -> Unit = {}

    /** The server's current version of each check-in, deletes included. */
    val changes: Map<String, CheckInChange> get() = stored.mapValues { it.value.change }

    override suspend fun push(changes: List<CheckInChange>): ApiResult<PushOutcome> {
        pushAnswers.removeFirstOrNull()?.let { return it }
        pushes += changes
        val accepted = mutableListOf<AcceptedChange>()
        val rejected = mutableListOf<RejectedChange>()
        for (change in changes) {
            val reason = reject(change)
            if (reason != null) {
                rejected += RejectedChange(change.id, reason, null)
                continue
            }
            val current = stored[change.id]
            val wins = current == null ||
                (change.updatedAt > current.change.updatedAt && current.change !is CheckInChange.Deleted)
            if (wins) stored[change.id] = PulledChange(++seq, change)
            accepted += AcceptedChange(change.id, stored.getValue(change.id).serverSeq)
        }
        beforePushAnswer()
        return ApiResult.Success(PushOutcome(accepted, rejected, time))
    }

    override suspend fun pull(since: Long, limit: Int, full: Boolean): ApiResult<PullPage> {
        pulls++
        fullPulls += full
        return pullAnswers.removeFirstOrNull() ?: if (needsReset(since, full)) {
            ApiResult.Success(PullPage(emptyList(), 0, hasMore = true, resetRequired = true, serverTime = time))
        } else {
            page(since, limit)
        }
    }

    private fun needsReset(since: Long, full: Boolean): Boolean {
        val belowPurgeLine = !full && since < purgedThroughSeq
        return since > 0 && (belowPurgeLine || since > seq)
    }

    private fun page(since: Long, limit: Int): ApiResult<PullPage> {
        val after = stored.values.filter { it.serverSeq > since }.sortedBy { it.serverSeq }
        val page = after.take(minOf(limit, pageLimit))
        val nextSince = page.lastOrNull()?.serverSeq ?: since
        return ApiResult.Success(
            PullPage(page, nextSince, after.size > page.size, resetRequired = false, serverTime = time),
        )
    }

    override suspend fun status(): ApiResult<ServerSyncStatus> = ApiResult.Success(
        ServerSyncStatus(
            checkInCount = stored.values.count { it.change is CheckInChange.Saved }.toLong(),
            latestServerSeq = seq,
            lastPushAt = null,
        ),
    )

    /** Stores a change as if another phone had pushed it. */
    fun store(change: CheckInChange) {
        stored[change.id] = PulledChange(++seq, change)
    }

    /** The 90-day purge job: tombstones go, and the purge line moves up (technical-plan.md §7). */
    fun purgeTombstones() {
        stored.values.removeAll { it.change is CheckInChange.Deleted }
        purgedThroughSeq = seq
    }
}

internal val T0: Instant = Instant.parse("2026-11-04T01:15:00Z")

/** A test row: each test states only the fields it is about. */
internal fun row(
    id: String,
    updatedAt: Instant = T0,
    syncState: SyncState = SyncState.PENDING,
    note: String? = "Report due tomorrow.",
    stress: Int = 8,
    deletedAt: Instant? = null,
    serverSeq: Long? = null,
) = CheckInEntity(
    id = id,
    moodCode = "worried",
    energy = 6,
    stress = stress,
    stressAfter = null,
    trigger = Trigger.WORK,
    triggerOther = null,
    note = note,
    supportUsed = SupportType.NONE,
    isGoodMoment = false,
    loggedAt = T0,
    tzOffsetMin = 480,
    createdAt = T0,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncState = syncState,
    syncError = null,
    syncAttempts = 0,
    serverSeq = serverSeq,
)

/** No waiting between call retries: the tests count calls, not seconds. */
internal fun noWaitRetry() = ApiRetry(List(4) { Duration.ZERO }) { Duration.ZERO }
