package app.ulap.shared.sync

import app.ulap.shared.model.SupportType
import app.ulap.shared.validation.CheckInInput
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.time.Instant

class SyncTypesTest {
    @Test
    fun aSavedChangeHidesItsJournalTextWhenPrinted() {
        // CLAUDE.md rules: journal notes never reach a log, even through a stray toString().
        val at = Instant.parse("2026-11-04T01:15:00Z")
        val change = CheckInChange.Saved(
            id = "a",
            updatedAt = at,
            checkIn = CheckInInput(
                moodCode = "worried",
                energy = 6,
                stress = 8,
                stressAfter = null,
                trigger = null,
                triggerOther = "my sister",
                note = "secret note",
                supportUsed = SupportType.NONE,
                isGoodMoment = false,
                loggedAt = at,
                tzOffsetMin = 480,
            ),
        )

        val printed = change.toString()

        assertFalse(printed.contains("secret note"))
        assertFalse(printed.contains("my sister"))
    }
}
