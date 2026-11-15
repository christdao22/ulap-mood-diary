package app.ulap.core.sync

import app.ulap.core.database.model.CheckInEntity
import app.ulap.core.database.model.SyncState
import app.ulap.shared.sync.CheckInChange
import app.ulap.shared.sync.PulledChange
import app.ulap.shared.validation.CheckInInput
import kotlin.time.Instant

/*
 * Room rows ↔ the :shared sync types. Rows never leave core:database and this module.
 */

/** The change to push for a PENDING row. A tombstone sends only its times (api-spec.md §8.2). */
internal fun CheckInEntity.toChange(): CheckInChange = when (val deletedAt = deletedAt) {
    null -> CheckInChange.Saved(id, updatedAt, toInput())
    else -> CheckInChange.Deleted(id, updatedAt, deletedAt)
}

/**
 * The row to save after merging a pulled change into [local] (api-spec.md §8.3, Phone merge), or
 * null to leave the row as it is.
 *
 * - No local row: insert it as `SYNCED`. A tombstone for a row this phone never had is skipped.
 * - A server tombstone always wins, even over a newer local edit: the server never brings a
 *   deleted check-in back (api-spec.md §8.2, merge table), so the edit would never go up.
 * - Local `PENDING` and newer: keep it. It goes up on the next push.
 * - Otherwise the server's version replaces the row and it's `SYNCED`.
 *
 * [now] is the new row's `created_at`: the server doesn't keep one, and this is when this phone
 * first had it.
 */
internal fun mergePulled(local: CheckInEntity?, pulled: PulledChange, now: Instant): CheckInEntity? =
    when (val change = pulled.change) {
        is CheckInChange.Deleted -> local?.asServerTombstone(change, pulled.serverSeq)

        is CheckInChange.Saved -> when {
            local == null -> change.toNewEntity(pulled.serverSeq, now)
            local.syncState == SyncState.PENDING && local.updatedAt > change.updatedAt -> null
            else -> local.withServerVersion(change, pulled.serverSeq)
        }
    }

private fun CheckInEntity.toInput() = CheckInInput(
    moodCode = moodCode,
    energy = energy,
    stress = stress,
    stressAfter = stressAfter,
    trigger = trigger,
    triggerOther = triggerOther,
    note = note,
    supportUsed = supportUsed,
    isGoodMoment = isGoodMoment,
    loggedAt = loggedAt,
    tzOffsetMin = tzOffsetMin,
)

/** Deleted on the server: deleted here too, journal text cleared, as the server does (api-spec.md §5.3). */
private fun CheckInEntity.asServerTombstone(change: CheckInChange.Deleted, serverSeq: Long) = copy(
    note = null,
    triggerOther = null,
    updatedAt = change.updatedAt,
    deletedAt = change.deletedAt,
    syncState = SyncState.SYNCED,
    syncError = null,
    syncAttempts = 0,
    serverSeq = serverSeq,
)

/** Keeps the row's ID and `created_at`; everything else is the server's. */
private fun CheckInEntity.withServerVersion(change: CheckInChange.Saved, serverSeq: Long) =
    change.toNewEntity(serverSeq, createdAt = createdAt)

private fun CheckInChange.Saved.toNewEntity(serverSeq: Long, createdAt: Instant) = CheckInEntity(
    id = id,
    moodCode = checkIn.moodCode,
    energy = checkIn.energy,
    stress = checkIn.stress,
    stressAfter = checkIn.stressAfter,
    trigger = checkIn.trigger,
    triggerOther = checkIn.triggerOther,
    note = checkIn.note,
    supportUsed = checkIn.supportUsed,
    isGoodMoment = checkIn.isGoodMoment,
    loggedAt = checkIn.loggedAt,
    tzOffsetMin = checkIn.tzOffsetMin,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = null,
    syncState = SyncState.SYNCED,
    syncError = null,
    syncAttempts = 0,
    serverSeq = serverSeq,
)
