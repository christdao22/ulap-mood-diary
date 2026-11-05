package app.ulap.core.database

import app.ulap.core.database.model.CheckInEntity
import app.ulap.core.database.model.SyncState
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import kotlin.time.Instant

// A small builder for test rows: each test states only the fields it is about.

internal val T0: Instant = Instant.parse("2026-11-04T01:15:00Z")

internal const val MANILA: Int = 480

internal fun checkIn(
    id: String,
    loggedAt: Instant = T0,
    tzOffsetMin: Int = MANILA,
    moodCode: String = "worried",
    note: String? = "Report due tomorrow.",
    trigger: Trigger? = Trigger.WORK,
    triggerOther: String? = null,
    deletedAt: Instant? = null,
) = CheckInEntity(
    id = id,
    moodCode = moodCode,
    energy = 6,
    stress = 8,
    stressAfter = null,
    trigger = trigger,
    triggerOther = triggerOther,
    note = note,
    supportUsed = SupportType.NONE,
    isGoodMoment = false,
    loggedAt = loggedAt,
    tzOffsetMin = tzOffsetMin,
    createdAt = loggedAt,
    updatedAt = loggedAt,
    deletedAt = deletedAt,
    syncState = SyncState.PENDING,
    syncError = null,
    syncAttempts = 0,
    serverSeq = null,
)
