package app.ulap.core.domain.checkin

import app.ulap.shared.model.CheckIn
import app.ulap.shared.validation.CheckInInput
import app.ulap.shared.validation.Violation
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * The person's check-ins. Every write goes to the phone's database first and is queued for sync
 * (technical-plan.md §8). This never starts a sync itself: SyncTriggers does that (D8).
 */
interface CheckInRepository {
    /** Validates and saves a new check-in, with a new UUIDv7 ID. */
    suspend fun add(input: CheckInInput): SaveResult

    /** Validates and replaces the editable fields of a live check-in. */
    suspend fun edit(id: String, input: CheckInInput): SaveResult

    /** Deletes a check-in (a tombstone, so the delete syncs). False if it was already gone. */
    suspend fun delete(id: String): Boolean

    /** A live check-in, or `null` if it's missing or deleted. */
    suspend fun get(id: String): CheckIn?

    /** The live check-ins of one local day, newest first, updated on every change. */
    fun observeDay(day: LocalDate): Flow<List<CheckIn>>

    /** Every live check-in, newest first, updated on every change. Logs reads this. */
    fun observeAll(): Flow<List<CheckIn>>
}

sealed interface SaveResult {
    /** [checkIn] is what was stored, after clean-up (a trimmed note, for example). */
    data class Saved(val checkIn: CheckIn) : SaveResult

    /** Nothing was saved. The screen shouldn't allow these, so they point to a bug. */
    data class Invalid(val violations: List<Violation>) : SaveResult

    /** The check-in to edit is missing or deleted. */
    data object NotFound : SaveResult
}
