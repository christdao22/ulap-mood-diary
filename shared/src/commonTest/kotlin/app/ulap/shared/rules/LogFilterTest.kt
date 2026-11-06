package app.ulap.shared.rules

import app.ulap.shared.model.CheckIn
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import app.ulap.shared.model.Zone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
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
    fun `one index answers filter after filter, like typing does`() {
        val index = LogSearchIndex(
            listOf(checkIn("a", trigger = Trigger.WORK), checkIn("b", moodCode = "worried", note = "exam")),
            names,
        )
        assertEquals(listOf("a"), index.filter(LogFilter(query = "trab")).map { it.id })
        assertEquals(listOf("b"), index.filter(LogFilter(query = "exa")).map { it.id })
        assertEquals(listOf("b"), index.filter(LogFilter(zone = Zone.RED)).map { it.id })
        assertEquals(listOf("a", "b"), index.filter(LogFilter()).map { it.id })
        assertFalse(index.isEmpty)
        assertTrue(LogSearchIndex(emptyList(), names).isEmpty)
    }

    @Test
    fun `a long history of 1,000 check-ins searches, filters and groups correctly`() {
        // 10 check-ins a day for 100 days, newest first, the way Room hands them over. Red and
        // Green take turns; every 100th note mentions an exam.
        val newest = Instant.parse("2026-11-05T13:00:00Z") // 9 PM in Manila
        val all = (0 until 1_000).map { i ->
            checkIn(
                id = "c$i",
                moodCode = if (i % 2 == 0) "worried" else "calm",
                note = if (i % 100 == 0) "Exam week, note $i" else "Note $i",
            ).copy(loggedAt = newest - (i / 10).days - (i % 10).hours)
        }
        val index = LogSearchIndex(all, names)

        val days = index.filter(LogFilter()).groupByLocalDay()
        assertEquals(100, days.size)
        assertTrue(days.all { it.count == 10 })
        assertEquals(all.map { it.id }, days.flatMap { day -> day.checkIns.map { it.id } })

        assertEquals(500, index.filter(LogFilter(zone = Zone.RED)).size)
        assertEquals((0 until 1_000 step 100).map { "c$it" }, index.filter(LogFilter(query = "exam")).map { it.id })
        assertEquals(listOf("c999"), index.filter(LogFilter(query = "note 999")).map { it.id })
    }

    @Test
    fun `a filter is active once it has words or a zone`() {
        assertFalse(LogFilter().isActive)
        assertFalse(LogFilter(query = " ").isActive)
        assertTrue(LogFilter(query = "x").isActive)
        assertTrue(LogFilter(zone = Zone.BLUE).isActive)
    }
}
