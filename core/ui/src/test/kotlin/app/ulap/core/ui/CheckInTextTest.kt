package app.ulap.core.ui

import app.ulap.shared.model.Trigger
import app.ulap.shared.model.Zone
import app.ulap.shared.rules.EnergyLevel
import app.ulap.shared.rules.StressLevel
import app.ulap.shared.rules.UlapExpression
import kotlin.test.Test
import kotlin.test.assertEquals

/** Each value has its own string; lint's MissingTranslation check covers the three languages. */
class CheckInTextTest {
    @Test
    fun `every trigger has its own label`() {
        assertEquals(Trigger.entries.size, Trigger.entries.map { it.nameRes }.toSet().size)
    }

    @Test
    fun `every stress and energy level has its own word`() {
        assertEquals(StressLevel.entries.size, StressLevel.entries.map { it.wordRes }.toSet().size)
        assertEquals(EnergyLevel.entries.size, EnergyLevel.entries.map { it.wordRes }.toSet().size)
    }

    @Test
    fun `every zone has its own mirror line and journal prompt`() {
        assertEquals(Zone.entries.size, Zone.entries.map { it.mirrorLineRes }.toSet().size)
        assertEquals(Zone.entries.size, Zone.entries.map { it.journalPromptRes }.toSet().size)
    }

    @Test
    fun `each rule face maps to the design system face of the same name`() {
        UlapExpression.entries.forEach { assertEquals(it.name, it.toMascot().name) }
    }
}
