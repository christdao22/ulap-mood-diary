package app.ulap.core.ui

import app.ulap.shared.catalog.MoodCatalog
import app.ulap.shared.model.Zone
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every mood and zone has its own string. Lint's MissingTranslation check then makes sure each of
 * those strings exists in English, Filipino and Bisaya.
 */
class MoodTextTest {
    @Test
    fun `every mood in the catalog has its own name string`() {
        val names = MoodCatalog.all.map { it.nameRes }
        assertEquals(MoodCatalog.all.size, names.toSet().size, "two moods share a name string")
    }

    @Test
    fun `every zone has its own name and descriptor`() {
        assertEquals(Zone.entries.size, Zone.entries.map { it.nameRes }.toSet().size)
        assertEquals(Zone.entries.size, Zone.entries.map { it.descriptorRes }.toSet().size)
    }
}
