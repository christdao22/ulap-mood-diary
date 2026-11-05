package app.ulap.shared.validation

import app.ulap.shared.catalog.MoodCatalog
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import app.ulap.shared.rules.LEVEL_RANGE
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The editable fields of a check-in, as the person entered them. Field names match the API
 * (api-spec.md §5.3), so a [Violation.field] can go straight into a push rejection.
 */
data class CheckInInput(
    val moodCode: String,
    val energy: Int,
    val stress: Int,
    val stressAfter: Int?,
    val trigger: Trigger?,
    val triggerOther: String?,
    val note: String?,
    val supportUsed: SupportType,
    val isGoodMoment: Boolean,
    val loggedAt: Instant,
    val tzOffsetMin: Int,
)

/**
 * Why a field was rejected. Each [name] is a `reason` value from api-spec.md §8.2. The server
 * adds `INVALID_ENUM` (an unknown enum name while reading JSON) and `FORBIDDEN` itself, because
 * neither can happen to a [CheckInInput].
 */
enum class Reason { INVALID_MOOD, OUT_OF_RANGE, TOO_LONG, INVALID_TIME, GOOD_MOMENT_NOT_ALLOWED }

data class Violation(val field: String, val reason: Reason)

sealed interface ValidationResult {
    /** [checkIn] is the cleaned-up input to store (see [CheckInValidator.validate]). */
    data class Valid(val checkIn: CheckInInput) : ValidationResult

    data class Invalid(val violations: List<Violation>) : ValidationResult
}

/**
 * The check-in rules from api-spec.md §5.3. The phone checks before saving and the server checks
 * every pushed change, with this same code, so both reject the same inputs.
 *
 * Lengths are counted in UTF-16 code units (Kotlin's `String.length`), so an emoji counts as 2.
 */
object CheckInValidator {
    const val TRIGGER_OTHER_MAX_LENGTH: Int = 100
    const val NOTE_MAX_LENGTH: Int = 5_000

    /** UTC−12:00 to UTC+14:00. The Philippines is +480. */
    val TZ_OFFSET_RANGE: IntRange = -720..840

    /** No check-in is older than the app. */
    val EARLIEST_LOGGED_AT: Instant = Instant.parse("2020-01-01T00:00:00Z")

    /** Room for a phone clock that runs a little fast. */
    val MAX_CLOCK_SKEW: Duration = 5.minutes

    /**
     * Cleans up [input], then checks it against the rules at time [now].
     *
     * Cleaning up: `note` is trimmed and an empty note becomes `null`; `triggerOther` is dropped
     * unless `trigger` is OTHER (api-spec.md §5.3). Lengths are checked after cleaning up.
     */
    fun validate(input: CheckInInput, now: Instant): ValidationResult {
        val checkIn = normalize(input)
        val violations = buildList {
            checkMoodAndLevels(checkIn)
            checkText(checkIn)
            checkTime(checkIn, now)
        }
        return if (violations.isEmpty()) ValidationResult.Valid(checkIn) else ValidationResult.Invalid(violations)
    }

    private fun MutableList<Violation>.checkMoodAndLevels(checkIn: CheckInInput) {
        val mood = MoodCatalog.byCode(checkIn.moodCode)
        if (mood == null) add(Violation("moodCode", Reason.INVALID_MOOD))
        if (checkIn.energy !in LEVEL_RANGE) add(Violation("energy", Reason.OUT_OF_RANGE))
        if (checkIn.stress !in LEVEL_RANGE) add(Violation("stress", Reason.OUT_OF_RANGE))
        if (checkIn.stressAfter != null && checkIn.stressAfter !in LEVEL_RANGE) {
            add(Violation("stressAfter", Reason.OUT_OF_RANGE))
        }
        // Good moments are for pleasant moods only. An unknown mood is already rejected above.
        if (checkIn.isGoodMoment && mood != null && mood.zone.isUnpleasant) {
            add(Violation("isGoodMoment", Reason.GOOD_MOMENT_NOT_ALLOWED))
        }
    }

    private fun MutableList<Violation>.checkText(checkIn: CheckInInput) {
        if ((checkIn.triggerOther?.length ?: 0) > TRIGGER_OTHER_MAX_LENGTH) {
            add(Violation("triggerOther", Reason.TOO_LONG))
        }
        if ((checkIn.note?.length ?: 0) > NOTE_MAX_LENGTH) add(Violation("note", Reason.TOO_LONG))
    }

    private fun MutableList<Violation>.checkTime(checkIn: CheckInInput, now: Instant) {
        if (checkIn.loggedAt < EARLIEST_LOGGED_AT || checkIn.loggedAt > now + MAX_CLOCK_SKEW) {
            add(Violation("loggedAt", Reason.INVALID_TIME))
        }
        if (checkIn.tzOffsetMin !in TZ_OFFSET_RANGE) add(Violation("tzOffsetMin", Reason.OUT_OF_RANGE))
    }

    private fun normalize(input: CheckInInput): CheckInInput = input.copy(
        note = input.note?.trim()?.ifEmpty { null },
        triggerOther = input.triggerOther.takeIf { input.trigger == Trigger.OTHER },
    )
}
