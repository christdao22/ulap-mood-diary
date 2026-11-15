@file:OptIn(ExperimentalUuidApi::class)

package app.ulap.server.sync

import app.ulap.server.common.ApiException
import app.ulap.server.common.atUtc
import app.ulap.server.common.toApiInstant
import app.ulap.server.common.toJavaTime
import app.ulap.server.crypto.NoteCipher
import app.ulap.server.crypto.SealedNotes
import app.ulap.shared.api.CheckInDto
import app.ulap.shared.api.ErrorCode
import app.ulap.shared.api.ErrorDetail
import app.ulap.shared.api.PushRejectReason
import app.ulap.shared.api.PushRejection
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import app.ulap.shared.validation.CheckInInput
import app.ulap.shared.validation.CheckInValidator
import app.ulap.shared.validation.ValidationResult
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.time.toJavaDuration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** What a push does with one change: write this row, or reject it. */
sealed interface Plan {
    /** [id] is the id as the phone sent it, so the phone can match the answer to its row. */
    class Write(val id: String, val write: CheckInWrite) : Plan

    class Reject(val rejection: PushRejection) : Plan
}

/**
 * The checking half of `POST /sync/push` (api-spec.md §8.2): reads each incoming change, applies
 * the shared [CheckInValidator] and the clock guard, and seals the journal text. It touches no
 * database, so [SyncService] runs it before opening the transaction.
 */
class PushPlanner(private val cipher: NoteCipher) {
    /**
     * Checks one change and turns it into the row to write, or a rejection. A change the request
     * can't even be read without (a bad id, a missing required field) fails the whole request
     * with `400`, like malformed JSON (api-spec.md §4).
     */
    fun plan(index: Int, change: CheckInDto, now: Instant): Plan {
        val id = runCatching { Uuid.parse(change.id) }.getOrNull()
            ?: throw ApiException.invalidField("changes[$index].id")
        val updatedAt = clockGuard(change.updatedAt.toJavaTime(), now)
        val deletedAt = change.deletedAt ?: return live(index, id, change, updatedAt, now)
        return Plan.Write(change.id, tombstone(id, updatedAt, clockGuard(deletedAt.toJavaTime(), now)))
    }

    private fun live(index: Int, id: Uuid, change: CheckInDto, updatedAt: OffsetDateTime, now: Instant): Plan {
        invalidEnumField(index, change)?.let { field -> return reject(change, PushRejectReason.INVALID_ENUM, field) }
        val input = CheckInInput(
            moodCode = required(index, "moodCode", change.moodCode),
            energy = required(index, "energy", change.energy),
            stress = required(index, "stress", change.stress),
            stressAfter = change.stressAfter,
            // Both names were checked just above.
            trigger = change.trigger?.let(Trigger::valueOf),
            triggerOther = change.triggerOther,
            note = change.note,
            supportUsed = SupportType.valueOf(required(index, "supportUsed", change.supportUsed)),
            isGoodMoment = required(index, "isGoodMoment", change.isGoodMoment),
            loggedAt = required(index, "loggedAt", change.loggedAt),
            tzOffsetMin = required(index, "tzOffsetMin", change.tzOffsetMin),
        )
        return when (val result = CheckInValidator.validate(input, now.toApiInstant())) {
            // The phone validates with the same rules, so this is a bug. One reason is enough to find it.
            is ValidationResult.Invalid -> result.violations.first().let { reject(change, it.reason.name, it.field) }

            is ValidationResult.Valid -> Plan.Write(change.id, result.checkIn.toWrite(id, updatedAt))
        }
    }

    /** The enum field whose name this server doesn't know (`INVALID_ENUM`), or null. */
    private fun invalidEnumField(index: Int, change: CheckInDto): String? {
        val supportUsed = required(index, "supportUsed", change.supportUsed)
        return when {
            change.trigger != null && Trigger.entries.none { it.name == change.trigger } -> "trigger"
            SupportType.entries.none { it.name == supportUsed } -> "supportUsed"
            else -> null
        }
    }

    private fun CheckInInput.toWrite(id: Uuid, updatedAt: OffsetDateTime) = CheckInWrite(
        id = id,
        moodCode = moodCode,
        energy = energy.toShort(),
        stress = stress.toShort(),
        stressAfter = stressAfter?.toShort(),
        trigger = trigger?.name,
        // Sealed with the current key on every write, which also re-seals rows from an older key.
        sealed = cipher.seal(id, note, triggerOther),
        supportUsed = supportUsed.name,
        isGoodMoment = isGoodMoment,
        loggedAt = loggedAt.toJavaTime().atUtc(),
        tzOffsetMin = tzOffsetMin.toShort(),
        clientUpdatedAt = updatedAt,
        deletedAt = null,
    )

    /**
     * A tombstone keeps only `id`, `updatedAt`, `deletedAt` and `serverSeq` (api-spec.md §5.3).
     * The journal text and every other answer are dropped right away; the NOT NULL columns get
     * these neutral values, which pull never sends.
     */
    private fun tombstone(id: Uuid, updatedAt: OffsetDateTime, deletedAt: OffsetDateTime) = CheckInWrite(
        id = id,
        moodCode = "",
        energy = TOMBSTONE_LEVEL,
        stress = TOMBSTONE_LEVEL,
        stressAfter = null,
        trigger = null,
        sealed = SealedNotes(keyId = null, note = null, triggerOther = null),
        supportUsed = SupportType.NONE.name,
        isGoodMoment = false,
        loggedAt = deletedAt,
        tzOffsetMin = 0,
        clientUpdatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    private companion object {
        const val TOMBSTONE_LEVEL: Short = 1

        fun reject(change: CheckInDto, reason: String, field: String?): Plan =
            Plan.Reject(PushRejection(change.id, reason, field))

        fun <T : Any> required(index: Int, field: String, value: T?): T = value
            ?: throw ApiException(
                ErrorCode.BAD_REQUEST,
                "changes[$index].$field is required",
                listOf(ErrorDetail("changes[$index].$field", "REQUIRED")),
            )

        /**
         * The clock guard (api-spec.md §8.2): a time more than 5 minutes ahead of the server is a
         * phone clock that's wrong, so the server's own time is stored instead. Otherwise that
         * change would win every later conflict. It applies to `deletedAt` as well, so a
         * tombstone from a fast clock still gets purged after 90 days.
         */
        fun clockGuard(time: Instant, now: Instant): OffsetDateTime =
            (if (time > now + CheckInValidator.MAX_CLOCK_SKEW.toJavaDuration()) now else time).atUtc()
    }
}
