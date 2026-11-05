package app.ulap.shared.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TimeOfDayTest {
    @Test
    fun `each block starts and ends where technical-plan section 10 says`() {
        val expected = mapOf(
            4 to TimeOfDay.NIGHT,
            5 to TimeOfDay.MORNING,
            11 to TimeOfDay.MORNING,
            12 to TimeOfDay.AFTERNOON,
            17 to TimeOfDay.AFTERNOON,
            18 to TimeOfDay.EVENING,
            21 to TimeOfDay.EVENING,
            22 to TimeOfDay.NIGHT,
            0 to TimeOfDay.NIGHT,
        )
        expected.forEach { (hour, block) -> assertEquals(block, TimeOfDay.ofHour(hour), "hour $hour") }
    }

    @Test
    fun `every hour of the day has exactly one block`() {
        val counts = (0..23).groupingBy { TimeOfDay.ofHour(it) }.eachCount()
        val expected = mapOf(
            TimeOfDay.NIGHT to 7,
            TimeOfDay.MORNING to 7,
            TimeOfDay.AFTERNOON to 6,
            TimeOfDay.EVENING to 4,
        )
        assertEquals(expected, counts)
    }

    @Test
    fun `an hour outside the clock is a bug`() {
        assertFailsWith<IllegalArgumentException> { TimeOfDay.ofHour(24) }
        assertFailsWith<IllegalArgumentException> { TimeOfDay.ofHour(-1) }
    }
}
