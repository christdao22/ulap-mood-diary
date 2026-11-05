package app.ulap.core.data.checkin

import androidx.room.withTransaction
import app.ulap.core.database.UlapDatabase
import app.ulap.core.database.model.CheckInEntity
import app.ulap.core.database.model.SyncState
import app.ulap.core.domain.checkin.CheckInRepository
import app.ulap.core.domain.checkin.SaveResult
import app.ulap.shared.model.CheckIn
import app.ulap.shared.model.Ids
import app.ulap.shared.validation.CheckInInput
import app.ulap.shared.validation.CheckInValidator
import app.ulap.shared.validation.ValidationResult
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/**
 * Check-ins in Room, the source of truth for every screen (technical-plan.md §3).
 *
 * Every save validates with the shared rules, then writes the row as `PENDING` with a fresh
 * `updated_at`, and clears `sync_error` and `sync_attempts` so it tries to upload again
 * (technical-plan.md §8). Nothing here starts a sync: SyncTriggers watches the PENDING rows (D8).
 */
internal class OfflineFirstCheckInRepository @Inject constructor(
    private val database: UlapDatabase,
    private val clock: Clock,
) : CheckInRepository {
    private val dao = database.checkInDao()

    override suspend fun add(input: CheckInInput): SaveResult {
        val now = clock.now()
        return when (val result = CheckInValidator.validate(input, now)) {
            is ValidationResult.Invalid -> SaveResult.Invalid(result.violations)

            is ValidationResult.Valid -> {
                val entity = result.checkIn.toNewEntity(id = Ids.newId(), now = now)
                dao.insert(entity)
                SaveResult.Saved(entity.toCheckIn())
            }
        }
    }

    override suspend fun edit(id: String, input: CheckInInput): SaveResult {
        val now = clock.now()
        val checkIn = when (val result = CheckInValidator.validate(input, now)) {
            is ValidationResult.Invalid -> return SaveResult.Invalid(result.violations)
            is ValidationResult.Valid -> result.checkIn
        }
        // Read and write in one transaction, so a delete can't land in between.
        return database.withTransaction {
            val current = dao.get(id)?.takeIf { it.deletedAt == null }
            if (current == null) {
                SaveResult.NotFound
            } else {
                val edited = current.withInput(checkIn, now)
                dao.update(edited)
                SaveResult.Saved(edited.toCheckIn())
            }
        }
    }

    override suspend fun delete(id: String): Boolean = dao.softDelete(id, clock.now()) > 0

    override suspend fun get(id: String): CheckIn? = dao.get(id)?.takeIf { it.deletedAt == null }?.toCheckIn()

    override fun observeDay(day: LocalDate): Flow<List<CheckIn>> =
        dao.observeDay(day.toEpochDays()).map { rows -> rows.map { it.toCheckIn() } }
}

private fun CheckInInput.toNewEntity(id: String, now: Instant) = CheckInEntity(
    id = id,
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
    createdAt = now,
    updatedAt = now,
    deletedAt = null,
    syncState = SyncState.PENDING,
    syncError = null,
    syncAttempts = 0,
    serverSeq = null,
)

/** The edit of a row: new field values, queued for sync. Keeps its ID, creation time and server_seq. */
private fun CheckInEntity.withInput(input: CheckInInput, now: Instant) = copy(
    moodCode = input.moodCode,
    energy = input.energy,
    stress = input.stress,
    stressAfter = input.stressAfter,
    trigger = input.trigger,
    triggerOther = input.triggerOther,
    note = input.note,
    supportUsed = input.supportUsed,
    isGoodMoment = input.isGoodMoment,
    loggedAt = input.loggedAt,
    tzOffsetMin = input.tzOffsetMin,
    updatedAt = now,
    syncState = SyncState.PENDING,
    syncError = null,
    syncAttempts = 0,
)

private fun CheckInEntity.toCheckIn() = CheckIn(
    id = id,
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
    createdAt = createdAt,
    updatedAt = updatedAt,
)
