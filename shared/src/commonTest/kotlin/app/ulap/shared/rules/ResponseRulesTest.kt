package app.ulap.shared.rules

import app.ulap.shared.catalog.MoodCatalog
import app.ulap.shared.model.Mood
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import app.ulap.shared.model.Zone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResponseRulesTest {
    private fun mood(code: String): Mood = MoodCatalog.byCode(code)!!

    private fun respond(code: String, stress: Int = mood(code).defaultStress, trigger: Trigger? = null) =
        ResponseRules.respond(mood(code), stress, trigger)

    @Test
    fun redOffersBreathingAndTheStressCheck() {
        val response = respond("nervous", stress = 6)
        assertEquals(UlapExpression.CONCERNED, response.expression)
        assertEquals(ResponseMessage.RED, response.message)
        assertEquals(ResponseActions.Offer(SupportType.BREATHE, showSecondary = true), response.actions)
        assertTrue(response.showStressCheck)
        assertTrue(response.floats)
    }

    @Test
    fun blueOffersSmallHelpsAndTheStressCheck() {
        val response = respond("sad", stress = 6)
        assertEquals(UlapExpression.GENTLE, response.expression)
        assertEquals(ResponseMessage.BLUE, response.message)
        assertEquals(ResponseActions.Offer(SupportType.SMALL_HELPS, showSecondary = true), response.actions)
        assertTrue(response.showStressCheck)
    }

    @Test
    fun yellowOffersGoodMomentsAndNoStressCheck() {
        val response = respond("happy")
        assertEquals(UlapExpression.HAPPY, response.expression)
        assertEquals(ResponseMessage.YELLOW, response.message)
        assertEquals(ResponseActions.Offer(SupportType.GOOD_MOMENT, showSecondary = true), response.actions)
        assertFalse(response.showStressCheck)
    }

    @Test
    fun greenOffersGoodMomentsAndNoStressCheck() {
        val response = respond("calm")
        assertEquals(UlapExpression.CALM, response.expression)
        assertEquals(ResponseMessage.GREEN, response.message)
        assertEquals(ResponseActions.Offer(SupportType.GOOD_MOMENT, showSecondary = true), response.actions)
        assertFalse(response.showStressCheck)
    }

    @Test
    fun hopelessShowsTheHotlineAQuietUlapAndStillTheStressCheck() {
        // Safety rule (technical-plan.md §5): at every stress level, with or without a trigger.
        for (stress in 1..10) {
            val response = respond(MoodCatalog.HOPELESS, stress = stress, trigger = Trigger.MONEY)
            assertEquals(ResponseActions.Hotline, response.actions, "stress $stress")
            assertEquals(UlapExpression.QUIET, response.expression)
            assertEquals(ResponseMessage.HOPELESS, response.message)
            assertFalse(response.floats)
            assertTrue(response.showStressCheck)
        }
    }

    @Test
    fun onlyHopelessShowsTheHotline() {
        MoodCatalog.all.filter { it.code != MoodCatalog.HOPELESS }.forEach { mood ->
            for (stress in 1..10) {
                val response = ResponseRules.respond(mood, stress, trigger = null)
                assertTrue(response.actions is ResponseActions.Offer, "${mood.code} at $stress")
                assertTrue(response.floats)
            }
        }
    }

    @Test
    fun stressOfEightOrMoreShowsOneButtonOnly() {
        assertEquals(true, (respond("worried", stress = 7).actions as ResponseActions.Offer).showSecondary)
        assertEquals(false, (respond("worried", stress = 8).actions as ResponseActions.Offer).showSecondary)
        assertEquals(false, (respond("worried", stress = 10).actions as ResponseActions.Offer).showSecondary)
        // The person's slider value counts, not the mood's default.
        assertEquals(false, (respond("calm", stress = 9).actions as ResponseActions.Offer).showSecondary)
        assertEquals(true, (respond("panicked", stress = 5).actions as ResponseActions.Offer).showSecondary)
    }

    @Test
    fun unpleasantZonesWithATriggerGetItsLine() {
        assertEquals(Trigger.WORK, respond("worried", trigger = Trigger.WORK).triggerLine)
        assertEquals(Trigger.FAMILY, respond("sad", trigger = Trigger.FAMILY).triggerLine)
        assertEquals(Trigger.MONEY, respond("anxious", trigger = Trigger.MONEY).triggerLine)
        assertEquals(Trigger.HEALTH, respond("tired", trigger = Trigger.HEALTH).triggerLine)
        assertEquals(Trigger.SCHOOL, respond("nervous", trigger = Trigger.SCHOOL).triggerLine)
    }

    @Test
    fun noTriggerLineWithoutATriggerOrALine() {
        assertNull(respond("worried").triggerLine)
        // Appendix B has no line for these two.
        assertNull(respond("worried", trigger = Trigger.RELATIONSHIPS).triggerLine)
        assertNull(respond("worried", trigger = Trigger.OTHER).triggerLine)
    }

    @Test
    fun pleasantZonesNeverGetATriggerLine() {
        Trigger.entries.forEach {
            assertNull(respond("happy", trigger = it).triggerLine)
            assertNull(respond("grateful", trigger = it).triggerLine)
        }
    }

    @Test
    fun everyMoodGetsTheResponseOfItsZone() {
        MoodCatalog.all.filter { it.code != MoodCatalog.HOPELESS }.forEach { mood ->
            val response = ResponseRules.respond(mood, mood.defaultStress, trigger = null)
            assertEquals(mood.zone.name, response.message.name, mood.code)
            assertEquals(mood.zone == Zone.RED || mood.zone == Zone.BLUE, response.showStressCheck, mood.code)
        }
    }

    @Test
    fun stressOffTheScaleIsABug() {
        assertFailsWith<IllegalArgumentException> { respond("calm", stress = 0) }
        assertFailsWith<IllegalArgumentException> { respond("calm", stress = 11) }
    }

    @Test
    fun afterCheckComparesBeforeAndNow() {
        assertEquals(AfterCheck(StressChange.LOWER, UlapExpression.CALM), ResponseRules.afterCheck(before = 8, now = 5))
        assertEquals(
            AfterCheck(StressChange.SAME, UlapExpression.GENTLE),
            ResponseRules.afterCheck(before = 8, now = 8),
        )
        assertEquals(
            AfterCheck(StressChange.HIGHER, UlapExpression.GENTLE),
            ResponseRules.afterCheck(before = 8, now = 9),
        )
        assertFailsWith<IllegalArgumentException> { ResponseRules.afterCheck(before = 8, now = 0) }
    }
}
