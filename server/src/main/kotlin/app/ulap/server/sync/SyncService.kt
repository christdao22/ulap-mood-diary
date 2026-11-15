@file:OptIn(ExperimentalUuidApi::class)

package app.ulap.server.sync

import app.ulap.server.auth.AuthErrors
import app.ulap.server.auth.identitiesOf
import app.ulap.server.common.ApiException
import app.ulap.server.common.toApiInstant
import app.ulap.server.crypto.NoteCipher
import app.ulap.server.db.AuthProvider
import app.ulap.server.db.Db
import app.ulap.server.users.findUser
import app.ulap.shared.api.CheckInDto
import app.ulap.shared.api.ErrorCode
import app.ulap.shared.api.PullResponse
import app.ulap.shared.api.PushRejectReason
import app.ulap.shared.api.PushRejection
import app.ulap.shared.api.PushRequest
import app.ulap.shared.api.PushResponse
import app.ulap.shared.api.PushResult
import app.ulap.shared.api.PushStatus
import app.ulap.shared.api.SyncLimits
import app.ulap.shared.api.SyncStatusResponse
import java.time.Clock
import java.time.temporal.ChronoUnit
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction

/**
 * `/sync/push`, `/sync/pull` and `/sync/status` (api-spec.md §8, technical-plan.md §8). Every
 * function takes the user id from the access token, never from the request (api-spec.md §11).
 */
class SyncService(private val db: Db, private val cipher: NoteCipher, private val clock: Clock) {
    private val planner = PushPlanner(cipher)

    /**
     * `POST /sync/push` (api-spec.md §8.2). The whole batch is one transaction: it all commits,
     * or a failure rolls all of it back and the phone sends the batch again.
     */
    suspend fun push(userId: Uuid, request: PushRequest): PushResponse {
        if (request.changes.size > SyncLimits.MAX_PUSH_CHANGES) {
            throw ApiException(ErrorCode.PAYLOAD_TOO_LARGE, "At most ${SyncLimits.MAX_PUSH_CHANGES} changes per push")
        }
        // Milliseconds, like every timestamp on the wire (api-spec.md §2), so a clamped
        // updatedAt reads back exactly as it was stored.
        val now = clock.instant().truncatedTo(ChronoUnit.MILLIS)
        // Checking and sealing happen before the transaction, so it holds its locks only for the writes.
        val plans = request.changes.mapIndexed { index, change -> planner.plan(index, change, now) }

        val accepted = mutableListOf<PushResult>()
        val rejected = mutableListOf<PushRejection>()
        db.tx {
            requireSyncAllowed(userId, lockForPush = true)
            val states = lockCheckInStates(plans.filterIsInstance<Plan.Write>().map { it.write.id }.toSet())
                .toMutableMap()
            for (plan in plans) {
                when (plan) {
                    is Plan.Reject -> rejected += plan.rejection

                    is Plan.Write -> when (val outcome = merge(userId, plan.write, states)) {
                        is Outcome.Accepted -> accepted += PushResult(plan.id, outcome.serverSeq, outcome.status)
                        Outcome.Forbidden -> rejected += PushRejection(plan.id, PushRejectReason.FORBIDDEN)
                    }
                }
            }
        }
        return PushResponse(accepted, rejected, now.toApiInstant())
    }

    /**
     * `GET /sync/pull` (api-spec.md §8.3): up to [limit] of the caller's changes after [since], in
     * ascending `serverSeq` order, or `resetRequired` when [since] can't be trusted (D6).
     */
    suspend fun pull(userId: Uuid, since: Long, limit: Int): PullResponse {
        if (since < 0) throw ApiException.invalidField("since", "OUT_OF_RANGE")
        if (limit !in SyncLimits.PULL_LIMIT_RANGE) throw ApiException.invalidField("limit", "OUT_OF_RANGE")
        val serverTime = clock.instant().toApiInstant()

        val rows = db.tx {
            requireSyncAllowed(userId, lockForPush = false)
            // One extra row says whether there's another page.
            val rows = checkInsSince(userId, since, limit + 1)
            // Read after the rows on purpose. If the purge job commits between the two reads, this
            // read sees its new purged_through_seq, so a tombstone missing from `rows` still
            // leads to a reset rather than a silently skipped delete.
            if (since > 0 && (since < purgedThroughSeq() || since > lastIssuedSeq())) null else rows
        } ?: return PullResponse(
            changes = emptyList(),
            nextSince = 0,
            hasMore = true,
            resetRequired = true,
            serverTime = serverTime,
        )

        val page = rows.take(limit)
        return PullResponse(
            changes = page.map { it.toDto() },
            nextSince = page.lastOrNull()?.serverSeq ?: since,
            hasMore = rows.size > limit,
            resetRequired = false,
            serverTime = serverTime,
        )
    }

    /**
     * `GET /sync/status` (api-spec.md §8.4). `checkInCount` leaves deletes out. `lastPushAt` is when
     * the server last stored one of the caller's changes; a push of only `STALE` ones doesn't move it.
     */
    suspend fun status(userId: Uuid): SyncStatusResponse {
        val counts = db.tx {
            requireSyncAllowed(userId, lockForPush = false)
            syncCounts(userId)
        }
        return SyncStatusResponse(
            checkInCount = counts.liveCount,
            latestServerSeq = counts.latestServerSeq ?: 0,
            lastPushAt = counts.lastWriteAt?.toInstant()?.toApiInstant(),
        )
    }

    /**
     * Sync needs an account that still exists and a verified identity: Google (and Apple, later)
     * always are, email once its link was opened (api-spec.md §8, `403 EMAIL_NOT_VERIFIED`).
     */
    private fun JdbcTransaction.requireSyncAllowed(userId: Uuid, lockForPush: Boolean) {
        val exists = if (lockForPush) lockUserForSync(userId) else findUser(userId) != null
        if (!exists) throw AuthErrors.accountDeleted()
        if (identitiesOf(userId).none { it.provider != AuthProvider.EMAIL || it.emailVerified }) {
            throw ApiException(ErrorCode.EMAIL_NOT_VERIFIED, "Verify the email address before syncing")
        }
    }

    /**
     * The merge table of api-spec.md §8.2. [states] holds what's stored now and is updated after
     * each write, so the same id twice in one batch merges against the first one.
     */
    private fun JdbcTransaction.merge(
        userId: Uuid,
        write: CheckInWrite,
        states: MutableMap<Uuid, StoredState>,
    ): Outcome {
        val stored = states[write.id]
        return when {
            stored == null -> {
                val seq = insertCheckIn(userId, write)
                states[write.id] = StoredState(userId, write.clientUpdatedAt, write.deletedAt, seq)
                Outcome.Accepted(seq, PushStatus.APPLIED)
            }

            stored.userId != userId -> Outcome.Forbidden

            // A tombstone stays one: there is no un-delete in v1. An equal updatedAt keeps the
            // stored version (technical-plan.md §8, rule 5).
            stored.deletedAt != null || !write.clientUpdatedAt.isAfter(stored.clientUpdatedAt) ->
                Outcome.Accepted(stored.serverSeq, PushStatus.STALE)

            else -> {
                val seq = updateCheckIn(write)
                states[write.id] = StoredState(userId, write.clientUpdatedAt, write.deletedAt, seq)
                Outcome.Accepted(seq, PushStatus.APPLIED)
            }
        }
    }

    private fun StoredCheckIn.toDto(): CheckInDto {
        val updatedAt = clientUpdatedAt.toInstant().toApiInstant()
        if (deletedAt != null) {
            return CheckInDto(
                id = id.toString(),
                updatedAt = updatedAt,
                deletedAt = deletedAt.toInstant().toApiInstant(),
                serverSeq = serverSeq,
            )
        }
        val notes = cipher.open(id, sealed)
        return CheckInDto(
            id = id.toString(),
            moodCode = moodCode,
            energy = energy.toInt(),
            stress = stress.toInt(),
            stressAfter = stressAfter?.toInt(),
            trigger = trigger,
            triggerOther = notes.triggerOther,
            note = notes.note,
            supportUsed = supportUsed,
            isGoodMoment = isGoodMoment,
            loggedAt = loggedAt.toInstant().toApiInstant(),
            tzOffsetMin = tzOffsetMin.toInt(),
            updatedAt = updatedAt,
            deletedAt = null,
            serverSeq = serverSeq,
        )
    }

    private sealed interface Outcome {
        class Accepted(val serverSeq: Long, val status: PushStatus) : Outcome

        data object Forbidden : Outcome
    }
}
