package app.ulap.shared.rules

import app.ulap.shared.model.CheckIn
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import app.ulap.shared.model.Zone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class LogFilterTest {
    // Filipino names, to show search matches the shown name and not the mood code.
    private val names = SearchNames(
        moods = mapOf("worried" to "Nag-aalala", "calm" to "Kalmado", "happy" to "Masaya"),
        triggers = mapOf(Trigger.WORK to "Trabaho", Trigger.OTHER to "Iba pa"),
    )

    private fun checkIn(
        id: String,
        moodCode: String = "calm",
        trigger: Trigger? = null,
        triggerOther: String? = null,
        note: String? = null,
    ) = CheckIn(
        id = id,
        moodCode = moodCode,
        energy = 5,
        stress = 5,
        stressAfter = null,
        trigger = trigger,
        triggerOther = triggerOther,
        note = note,
        supportUsed = SupportType.NONE,
        isGoodMoment = false,
        loggedAt = Instant.parse("2026-11-05T01:00:00Z"),
        tzOffsetMin = 480,
        createdAt = Instant.parse("2026-11-05T01:00:00Z"),
        updatedAt = Instant.parse("2026-11-05T01:00:00Z"),
    )

    private fun List<CheckIn>.ids(filter: LogFilter) = filterLogs(filter, names).map { it.id }

    @Test
    fun `no filter keeps every check-in in order`() {
        val all = listOf(checkIn("a"), checkIn("b", moodCode = "worried"))
        assertEquals(listOf("a", "b"), all.ids(LogFilter()))
    }

    @Test
    fun `search finds the mood name in the app's language, ignoring case`() {
        val all = listOf(checkIn("a", moodCode = "worried"), checkIn("b", moodCode = "happy"))
        assertEquals(listOf("a"), all.ids(LogFilter(query = "ALALA")))
        // The code is never shown, so it isn't searched.
        assertEquals(emptyList(), all.ids(LogFilter(query = "worried")))
    }

    @Test
    fun `search finds the trigger name, the Other text and the note`() {
        val all = listOf(
            checkIn("work", trigger = Trigger.WORK),
            checkIn("other", trigger = Trigger.OTHER, triggerOther = "Traffic sa EDSA"),
            checkIn("note", note = "Long day at the office"),
            checkIn("none"),
        )
        assertEquals(listOf("work"), all.ids(LogFilter(query = "trabaho")))
        assertEquals(listOf("other"), all.ids(LogFilter(query = "edsa")))
        assertEquals(listOf("note"), all.ids(LogFilter(query = "office")))
    }

    @Test
    fun `every word has to match, in any field and any order`() {
        val all = listOf(
            checkIn("both", trigger = Trigger.WORK, note = "So tired today"),
            checkIn("one", trigger = Trigger.WORK, note = "Fine"),
        )
        assertEquals(listOf("both"), all.ids(LogFilter(query = "  tired   trabaho ")))
    }

    @Test
    fun `a blank query keeps everything`() {
        assertEquals(listOf("a"), listOf(checkIn("a")).ids(LogFilter(query = "   ")))
    }

    @Test
    fun `a zone keeps only that zone's moods, and works with search`() {
        val all = listOf(
            checkIn("worried", moodCode = "worried", note = "exam"),
            checkIn("calm", moodCode = "calm", note = "exam"),
            checkIn("happy", moodCode = "happy"),
        )
        assertEquals(listOf("calm"), all.ids(LogFilter(zone = Zone.GREEN)))
        assertEquals(listOf("worried"), all.ids(LogFilter(query = "exam", zone = Zone.RED)))
        assertEquals(emptyList(), all.ids(LogFilter(query = "exam", zone = Zone.YELLOW)))
    }

    @Test
    fun `without names search still finds notes`() {
        val all = listOf(checkIn("a", moodCode = "worried", note = "exam"))
        assertEquals(listOf("a"), all.filterLogs(LogFilter(query = "exam"), SearchNames.None).map { it.id })
    }

    @Test
    fun `a filter is active once it has words or a zone`() {
        assertFalse(LogFilter().isActive)
        assertFalse(LogFilter(query = " ").isActive)
        assertTrue(LogFilter(query = "x").isActive)
        assertTrue(LogFilter(zone = Zone.BLUE).isActive)
    }
}
