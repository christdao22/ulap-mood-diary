package app.ulap.core.network.sync

import app.ulap.shared.api.CheckInDto
import app.ulap.shared.api.PullResponse
import app.ulap.shared.api.PushResponse
import app.ulap.shared.api.SyncStatusResponse
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import app.ulap.shared.sync.AcceptedChange
import app.ulap.shared.sync.CheckInChange
import app.ulap.shared.sync.PullPage
import app.ulap.shared.sync.PulledChange
import app.ulap.shared.sync.PushOutcome
import app.ulap.shared.sync.RejectedChange
import app.ulap.shared.sync.ServerSyncStatus
import app.ulap.shared.validation.CheckInInput

/*
 * Sync DTOs ↔ the :shared sync types. Internal, so the DTOs stay in this module (README §5).
 * Error messages name the check-in id and the field, never a value: values can be journal text.
 */

/** A delete sends only `id`, `updatedAt` and `deletedAt` (api-spec.md §8.2). */
internal fun CheckInChange.toDto(): CheckInDto = when (this) {
    is CheckInChange.Saved -> CheckInDto(
        id = id,
        moodCode = checkIn.moodCode,
        energy = checkIn.energy,
        stress = checkIn.stress,
        stressAfter = checkIn.stressAfter,
        trigger = checkIn.trigger?.name,
        triggerOther = checkIn.triggerOther,
        note = checkIn.note,
        supportUsed = checkIn.supportUsed.name,
        isGoodMoment = checkIn.isGoodMoment,
        loggedAt = checkIn.loggedAt,
        tzOffsetMin = checkIn.tzOffsetMin,
        updatedAt = updatedAt,
    )

    is CheckInChange.Deleted -> CheckInDto(id = id, updatedAt = updatedAt, deletedAt = deletedAt)
}

internal fun PushResponse.toPushOutcome(): PushOutcome = PushOutcome(
    // APPLIED and STALE lead to the same thing on the phone (api-spec.md §8.2, Phone actions).
    accepted = accepted.map { AcceptedChange(it.id, it.serverSeq) },
    rejected = rejected.map { RejectedChange(it.id, it.reason, it.field) },
    serverTime = serverTime,
)

internal fun PullResponse.toPullPage(): PullPage = PullPage(
    changes = changes.map { it.toPulledChange() },
    nextSince = nextSince,
    hasMore = hasMore,
    resetRequired = resetRequired,
    serverTime = serverTime,
)

internal fun SyncStatusResponse.toServerSyncStatus(): ServerSyncStatus =
    ServerSyncStatus(checkInCount = checkInCount, latestServerSeq = latestServerSeq, lastPushAt = lastPushAt)

/**
 * The server always sends `serverSeq` on pull, and every field of §5.3 on a check-in that isn't a
 * tombstone. A missing one is a server bug, so it throws, like a body that doesn't parse.
 */
private fun CheckInDto.toPulledChange(): PulledChange {
    val seq = checkNotNull(serverSeq) { "pulled change $id has no serverSeq" }
    val deletedAt = deletedAt
    val change = if (deletedAt != null) {
        CheckInChange.Deleted(id, updatedAt, deletedAt)
    } else {
        CheckInChange.Saved(id, updatedAt, toCheckInInput())
    }
    return PulledChange(seq, change)
}

private fun CheckInDto.toCheckInInput(): CheckInInput = CheckInInput(
    moodCode = required("moodCode", moodCode),
    energy = required("energy", energy),
    stress = required("stress", stress),
    stressAfter = stressAfter,
    trigger = trigger?.let(::triggerOrOther),
    triggerOther = triggerOther,
    note = note,
    supportUsed = supportOrNone(required("supportUsed", supportUsed)),
    isGoodMoment = required("isGoodMoment", isGoodMoment),
    loggedAt = required("loggedAt", loggedAt),
    tzOffsetMin = required("tzOffsetMin", tzOffsetMin),
)

private fun <T : Any> CheckInDto.required(field: String, value: T?): T =
    checkNotNull(value) { "pulled check-in $id has no $field" }

/*
 * A name this app doesn't know comes from a newer server (only ever added, api-spec.md §2,
 * Versioning). It reads as the closest value this app has, the way an unknown mood code shows as
 * "Other feeling" (api-spec.md §5.4), so one new value can't stop the whole pull.
 */
private fun triggerOrOther(name: String): Trigger = Trigger.entries.firstOrNull { it.name == name } ?: Trigger.OTHER

private fun supportOrNone(name: String): SupportType =
    SupportType.entries.firstOrNull { it.name == name } ?: SupportType.NONE
