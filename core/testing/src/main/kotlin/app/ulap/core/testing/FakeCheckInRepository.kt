package app.ulap.core.testing

import app.ulap.core.domain.checkin.CheckInRepository
import app.ulap.core.domain.checkin.SaveResult
import app.ulap.shared.model.CheckIn
import app.ulap.shared.validation.CheckInInput
import app.ulap.shared.validation.CheckInValidator
import app.ulap.shared.validation.ValidationResult
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Check-ins held in memory. It validates like the real repository, with the shared rules, so a
 * test that saves bad input sees [SaveResult.Invalid]. IDs are "check-in-1", "check-in-2", ….
 */
class FakeCheckInRepository(private val now: () -> Instant = { Instant.parse("2026-11-04T08:49:00Z") }) :
    CheckInRepository {
    private val checkIns = MutableStateFlow<List<CheckIn>>(emptyList())

    /** Every live check-in, oldest first. */
    val saved: List<CheckIn> get() = checkIns.value

    override suspend fun add(input: CheckInInput): SaveResult = when (val result = validate(input)) {
        is ValidationResult.Invalid -> SaveResult.Invalid(result.violations)

        is ValidationResult.Valid -> {
            val checkIn = result.checkIn.toCheckIn(id = "check-in-${checkIns.value.size + 1}", createdAt = now())
            checkIns.update { it + checkIn }
            SaveResult.Saved(checkIn)
        }
    }

    override suspend fun edit(id: String, input: CheckInInput): SaveResult {
        val current = checkIns.value.firstOrNull { it.id == id } ?: return SaveResult.NotFound
        return when (val result = validate(input)) {
            is ValidationResult.Invalid -> SaveResult.Invalid(result.violations)

            is ValidationResult.Valid -> {
                val edited = result.checkIn.toCheckIn(id = id, createdAt = current.createdAt).copy(updatedAt = now())
                checkIns.update { list -> list.map { if (it.id == id) edited else it } }
                SaveResult.Saved(edited)
            }
        }
    }

    override suspend fun delete(id: String): Boolean {
        val before = checkIns.value.size
        checkIns.update { list -> list.filterNot { it.id == id } }
        return checkIns.value.size < before
    }

    override suspend fun get(id: String): CheckIn? = checkIns.value.firstOrNull { it.id == id }

    override fun observeDay(day: LocalDate): Flow<List<CheckIn>> = checkIns.map { list ->
        list.filter { it.localDate() == day }.sortedByDescending { it.loggedAt }
    }

    override fun observeAll(): Flow<List<CheckIn>> = checkIns.map { list ->
        list.sortedWith(compareByDescending<CheckIn> { it.loggedAt }.thenByDescending { it.id })
    }

    private fun validate(input: CheckInInput) = CheckInValidator.validate(input, now())

    private fun CheckIn.localDate(): LocalDate =
        loggedAt.toLocalDateTime(UtcOffset(seconds = tzOffsetMin * SECONDS_PER_MINUTE).asTimeZone()).date

    private fun CheckInInput.toCheckIn(id: String, createdAt: Instant) = CheckIn(
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
        updatedAt = createdAt,
    )

    private companion object {
        const val SECONDS_PER_MINUTE = 60
    }
}
