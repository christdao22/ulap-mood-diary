package app.ulap.core.ui

import app.ulap.core.designsystem.component.BreathPhase
import app.ulap.shared.catalog.MoodCatalog
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import app.ulap.shared.rules.ResponseMessage
import app.ulap.shared.rules.ResponseRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Each value has its own string; lint's MissingTranslation check covers the three languages. */
class ResponseTextTest {
    @Test
    fun `every response message has its own title and body`() {
        val messages = ResponseMessage.entries
        assertEquals(messages.size, messages.map { it.titleRes }.toSet().size)
        assertEquals(messages.size, messages.map { it.bodyRes }.toSet().size)
    }

    @Test
    fun `every trigger ResponseRules shows a line for has one, and only those`() {
        val worried = checkNotNull(MoodCatalog.byCode("worried"))
        Trigger.entries.forEach { trigger ->
            val shown = ResponseRules.respond(worried, stress = 5, trigger = trigger).triggerLine
            assertEquals(shown != null, trigger.responseLineRes != null, "$trigger")
        }
    }

    @Test
    fun `every support that can be offered has its own button label`() {
        val offered = SupportType.entries - SupportType.NONE
        assertEquals(offered.size, offered.mapNotNull { it.offerRes }.toSet().size)
        assertNull(SupportType.NONE.offerRes)
    }

    @Test
    fun `every part of a breath has its own word`() {
        assertEquals(BreathPhase.entries.size, BreathPhase.entries.map { it.labelRes }.toSet().size)
    }
}
