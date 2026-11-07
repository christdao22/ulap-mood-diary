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
    fun detailsShowsTheSameFaceAsTheResponseForEveryMood() {
        MoodCatalog.all.forEach { mood ->
            assertEquals(respond(mood.code).expression, ResponseRules.expression(mood), mood.code)
        }
        assertEquals(UlapExpression.QUIET, ResponseRules.expression(mood(MoodCatalog.HOPELESS)))
        assertEquals(UlapExpression.GENTLE, ResponseRules.expression(mood("burned_out")))
    }

    @Test
    fun redOffersBreathingAndTheStressCheck() {
        val response = respond("nervous", stress = 6)
        assertEquals(UlapExpression.CONCERNED, response.expression)
        assertEquals(ResponseMessage.RED, response.message)
        assertEquals(ResponseActions.Offer(SupportType.BREATHE, showSecondary = true), response.actions)
        assertEquals(StressCheck.AFTER_SUPPORT, response.stressCheck)
        assertTrue(response.floats)
    }

    @Test
    fun blueOffersSmallHelpsAndTheStressCheck() {
        val response = respond("sad", stress = 6)
        assertEquals(UlapExpression.GENTLE, response.expression)
        assertEquals(ResponseMessage.BLUE, response.message)
        assertEquals(ResponseActions.Offer(SupportType.SMALL_HELPS, showSecondary = true), response.actions)
        assertEquals(StressCheck.AFTER_SUPPORT, response.stressCheck)
    }

    @Test
    fun yellowOffersGoodMomentsAndNoStressCheck() {
        val response = respond("happy")
        assertEquals(UlapExpression.HAPPY, response.expression)
        assertEquals(ResponseMessage.YELLOW, response.message)
        assertEquals(ResponseActions.Offer(SupportType.GOOD_MOMENT, showSecondary = true), response.actions)
        assertEquals(StressCheck.NONE, response.stressCheck)
    }

    @Test
    fun greenOffersGoodMomentsAndNoStressCheck() {
        val response = respond("calm")
        assertEquals(UlapExpression.CALM, response.expression)
        assertEquals(ResponseMessage.GREEN, response.message)
        assertEquals(ResponseActions.Offer(SupportType.GOOD_MOMENT, showSecondary = true), response.actions)
        assertEquals(StressCheck.NONE, response.stressCheck)
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
            // No support button to wait for, so the card shows from the start, even at 8 or more.
            assertEquals(StressCheck.NOW, response.stressCheck, "stress $stress")
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
    fun redAndBlueHoldTheStressCheckUntilTheSupportStarts() {
        // At any stress, the card follows the support it asks about (technical-plan.md §5).
        assertEquals(StressCheck.AFTER_SUPPORT, respond("worried", stress = 2).stressCheck)
        assertEquals(StressCheck.AFTER_SUPPORT, respond("worried", stress = 7).stressCheck)
        assertEquals(StressCheck.AFTER_SUPPORT, respond("worried", stress = 8).stressCheck)
        assertEquals(StressCheck.AFTER_SUPPORT, respond("sad", stress = 10).stressCheck)
        // Pleasant zones have no card, at any stress.
        assertEquals(StressCheck.NONE, respond("calm", stress = 9).stressCheck)
    }

    @Test
    fun theStressCheckShowsWhenItsRuleSaysSo() {
        assertFalse(StressCheck.NONE.isShown(supportStarted = false))
        assertFalse(StressCheck.NONE.isShown(supportStarted = true))
        assertTrue(StressCheck.NOW.isShown(supportStarted = false))
        assertTrue(StressCheck.NOW.isShown(supportStarted = true))
        assertFalse(StressCheck.AFTER_SUPPORT.isShown(supportStarted = false))
        assertTrue(StressCheck.AFTER_SUPPORT.isShown(supportStarted = true))
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
            val unpleasant = mood.zone == Zone.RED || mood.zone == Zone.BLUE
            assertEquals(unpleasant, response.stressCheck != StressCheck.NONE, mood.code)
        }
    }

    @Test
    fun stressOffTheScaleIsABug() {
        assertFailsWith<IllegalArgumentException> { respond("calm", stress = 0) }
        assertFailsWith<IllegalArgumentException> { respond("calm", stress = 11) }
    }

    private fun afterCheck(code: String, before: Int, now: Int) = ResponseRules.afterCheck(mood(code), before, now)

    @Test
    fun afterCheckComparesBeforeAndNow() {
        val worried = "worried"
        assertEquals(
            AfterCheck(StressChange.LOWER, UlapExpression.CALM, floats = true, showHotline = false),
            afterCheck(worried, before = 8, now = 5),
        )
        assertEquals(
            AfterCheck(StressChange.SAME, UlapExpression.GENTLE, floats = true, showHotline = false),
            afterCheck(worried, before = 8, now = 8),
        )
        assertEquals(
            AfterCheck(StressChange.HIGHER, UlapExpression.GENTLE, floats = true, showHotline = false),
            afterCheck(worried, before = 8, now = 9),
        )
        assertFailsWith<IllegalArgumentException> { afterCheck(worried, before = 8, now = 0) }
        assertFailsWith<IllegalArgumentException> { afterCheck(worried, before = 11, now = 5) }
    }

    @Test
    fun hopelessAfterCheckStaysQuietAndKeepsTheHotlineWhicheverWayStressWent() {
        // Safety rule (🔒): the same still Ulap and the 1553 card as on Response, even when lower.
        listOf(5, 8, 9).forEach { now ->
            val after = afterCheck(MoodCatalog.HOPELESS, before = 8, now = now)
            assertEquals(UlapExpression.QUIET, after.expression, "now=$now")
            assertFalse(after.floats, "now=$now")
            assertTrue(after.showHotline, "now=$now")
        }
    }

    @Test
    fun onlyHopelessShowsTheHotlineAfterTheCheck() {
        MoodCatalog.all.filter { it.code != MoodCatalog.HOPELESS }.forEach { mood ->
            val after = ResponseRules.afterCheck(mood, before = 8, now = 9)
            assertFalse(after.showHotline, mood.code)
            assertTrue(after.floats, mood.code)
        }
    }
}
