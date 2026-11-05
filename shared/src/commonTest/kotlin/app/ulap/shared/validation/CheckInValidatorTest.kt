package app.ulap.shared.validation

import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class CheckInValidatorTest {
    private val now = Instant.parse("2026-11-04T01:15:00Z")

    private val valid = CheckInInput(
        moodCode = "worried",
        energy = 6,
        stress = 8,
        stressAfter = 5,
        trigger = Trigger.WORK,
        triggerOther = null,
        note = "Report due tomorrow.",
        supportUsed = SupportType.BREATHE,
        isGoodMoment = false,
        loggedAt = now,
        tzOffsetMin = 480,
    )

    private fun violations(input: CheckInInput): List<Violation> =
        when (val result = CheckInValidator.validate(input, now)) {
            is ValidationResult.Valid -> emptyList()
            is ValidationResult.Invalid -> result.violations
        }

    private fun cleaned(input: CheckInInput): CheckInInput =
        assertIs<ValidationResult.Valid>(CheckInValidator.validate(input, now)).checkIn

    @Test
    fun aNormalCheckInIsValid() {
        assertEquals(valid, cleaned(valid))
    }

    @Test
    fun unknownMoodIsRejected() {
        assertEquals(listOf(Violation("moodCode", Reason.INVALID_MOOD)), violations(valid.copy(moodCode = "stressed")))
    }

    @Test
    fun levelsMustBeOneToTen() {
        for (bad in listOf(0, 11, -1)) {
            assertEquals(listOf(Violation("energy", Reason.OUT_OF_RANGE)), violations(valid.copy(energy = bad)))
            assertEquals(listOf(Violation("stress", Reason.OUT_OF_RANGE)), violations(valid.copy(stress = bad)))
            assertEquals(
                listOf(Violation("stressAfter", Reason.OUT_OF_RANGE)),
                violations(valid.copy(stressAfter = bad)),
            )
        }
        for (ok in listOf(1, 10)) {
            assertEquals(emptyList(), violations(valid.copy(energy = ok, stress = ok, stressAfter = ok)))
        }
        assertEquals(emptyList(), violations(valid.copy(stressAfter = null)))
    }

    @Test
    fun noteIsTrimmedAndEmptyBecomesNull() {
        assertEquals("Report due.", cleaned(valid.copy(note = "  Report due.\n")).note)
        assertNull(cleaned(valid.copy(note = "   \n ")).note)
        assertNull(cleaned(valid.copy(note = "")).note)
    }

    @Test
    fun noteIsAtMost5000Characters() {
        assertEquals(emptyList(), violations(valid.copy(note = "a".repeat(5_000))))
        // Spaces around the note don't count, because it's trimmed first.
        assertEquals(emptyList(), violations(valid.copy(note = "  " + "a".repeat(5_000) + "  ")))
        assertEquals(listOf(Violation("note", Reason.TOO_LONG)), violations(valid.copy(note = "a".repeat(5_001))))
    }

    @Test
    fun triggerOtherIsKeptOnlyForOther() {
        val other = valid.copy(trigger = Trigger.OTHER, triggerOther = "Traffic")
        assertEquals("Traffic", cleaned(other).triggerOther)
        assertNull(cleaned(other.copy(trigger = Trigger.WORK)).triggerOther)
        assertNull(cleaned(other.copy(trigger = null)).triggerOther)
    }

    @Test
    fun triggerOtherIsAtMost100Characters() {
        val other = valid.copy(trigger = Trigger.OTHER)
        assertEquals(emptyList(), violations(other.copy(triggerOther = "a".repeat(100))))
        assertEquals(
            listOf(Violation("triggerOther", Reason.TOO_LONG)),
            violations(other.copy(triggerOther = "a".repeat(101))),
        )
        // Ignored, so not checked, when the trigger isn't OTHER.
        assertEquals(emptyList(), violations(valid.copy(trigger = Trigger.WORK, triggerOther = "a".repeat(101))))
    }

    @Test
    fun goodMomentOnlyForYellowAndGreen() {
        assertEquals(emptyList(), violations(valid.copy(moodCode = "happy", stress = 2, isGoodMoment = true)))
        assertEquals(emptyList(), violations(valid.copy(moodCode = "calm", stress = 1, isGoodMoment = true)))
        assertEquals(
            listOf(Violation("isGoodMoment", Reason.GOOD_MOMENT_NOT_ALLOWED)),
            violations(valid.copy(moodCode = "worried", isGoodMoment = true)),
        )
        assertEquals(
            listOf(Violation("isGoodMoment", Reason.GOOD_MOMENT_NOT_ALLOWED)),
            violations(valid.copy(moodCode = "tired", isGoodMoment = true)),
        )
    }

    @Test
    fun unknownMoodIsNotAlsoAGoodMomentViolation() {
        assertEquals(
            listOf(Violation("moodCode", Reason.INVALID_MOOD)),
            violations(valid.copy(moodCode = "nope", isGoodMoment = true)),
        )
    }

    @Test
    fun loggedAtIsAtMostFiveMinutesAhead() {
        assertEquals(emptyList(), violations(valid.copy(loggedAt = now + 5.minutes)))
        assertEquals(
            listOf(Violation("loggedAt", Reason.INVALID_TIME)),
            violations(valid.copy(loggedAt = now + 5.minutes + 1.seconds)),
        )
    }

    @Test
    fun loggedAtIsNotBefore2020() {
        assertEquals(emptyList(), violations(valid.copy(loggedAt = Instant.parse("2020-01-01T00:00:00Z"))))
        assertEquals(
            listOf(Violation("loggedAt", Reason.INVALID_TIME)),
            violations(valid.copy(loggedAt = Instant.parse("2019-12-31T23:59:59Z"))),
        )
    }

    @Test
    fun tzOffsetIsMinus720ToPlus840() {
        for (ok in listOf(-720, 0, 480, 840)) assertEquals(emptyList(), violations(valid.copy(tzOffsetMin = ok)))
        for (bad in listOf(-721, 841)) {
            assertEquals(
                listOf(Violation("tzOffsetMin", Reason.OUT_OF_RANGE)),
                violations(valid.copy(tzOffsetMin = bad)),
            )
        }
    }

    @Test
    fun everyProblemIsReportedAtOnce() {
        val bad = valid.copy(moodCode = "nope", energy = 0, stress = 11, tzOffsetMin = 900)
        assertEquals(
            listOf("moodCode", "energy", "stress", "tzOffsetMin"),
            violations(bad).map { it.field },
        )
    }
}
