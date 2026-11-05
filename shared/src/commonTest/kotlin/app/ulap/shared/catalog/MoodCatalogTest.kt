package app.ulap.shared.catalog

import app.ulap.shared.model.Zone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoodCatalogTest {
    @Test
    fun hasAll25MoodsWithUniqueCodes() {
        assertEquals(25, MoodCatalog.all.size)
        assertEquals(25, MoodCatalog.all.map { it.code }.toSet().size)
    }

    @Test
    fun zonesHoldTheMoodsFromTheTechnicalPlan() {
        assertEquals(
            listOf(
                "panicked", "overwhelmed", "anxious", "angry", "worried",
                "frustrated", "nervous", "irritated", "restless",
            ),
            MoodCatalog.inZone(Zone.RED).map { it.code },
        )
        assertEquals(
            listOf("hopeless", "burned_out", "sad", "lonely", "guilty", "disappointed", "tired", "bored"),
            MoodCatalog.inZone(Zone.BLUE).map { it.code },
        )
        assertEquals(
            listOf("excited", "motivated", "proud", "happy"),
            MoodCatalog.inZone(Zone.YELLOW).map { it.code },
        )
        assertEquals(
            listOf("content", "grateful", "relaxed", "calm"),
            MoodCatalog.inZone(Zone.GREEN).map { it.code },
        )
    }

    @Test
    fun defaultsMatchTheTechnicalPlan() {
        // Spot checks across every zone (technical-plan.md §5).
        val expected = mapOf(
            "panicked" to (9 to 10),
            "worried" to (6 to 8),
            "restless" to (7 to 5),
            "hopeless" to (2 to 8),
            "burned_out" to (2 to 8),
            "bored" to (3 to 3),
            "excited" to (9 to 3),
            "happy" to (7 to 2),
            "content" to (4 to 1),
            "calm" to (3 to 1),
        )
        expected.forEach { (code, defaults) ->
            val mood = MoodCatalog.byCode(code)!!
            assertEquals(defaults, mood.defaultEnergy to mood.defaultStress, code)
        }
    }

    @Test
    fun everyDefaultIsOnTheOneToTenScale() {
        MoodCatalog.all.forEach {
            assertTrue(it.defaultEnergy in 1..10, it.code)
            assertTrue(it.defaultStress in 1..10, it.code)
        }
    }

    @Test
    fun zonesAreListedFromMostToLeastStress() {
        assertEquals(Zone.entries.sortedBy { it.tier }, MoodCatalog.all.map { it.zone }.distinct())
        Zone.entries.forEach { zone ->
            val stress = MoodCatalog.inZone(zone).map { it.defaultStress }
            assertEquals(stress.sortedDescending(), stress, zone.name)
        }
    }

    @Test
    fun codesAreStableLowercaseSnakeCase() {
        MoodCatalog.all.forEach { assertTrue(Regex("[a-z]+(_[a-z]+)*").matches(it.code), it.code) }
    }

    @Test
    fun unknownCodeIsNull() {
        assertNull(MoodCatalog.byCode("Worried"))
        assertNull(MoodCatalog.byCode("stressed"))
    }

    @Test
    fun onlyRedAndBlueAreUnpleasant() {
        assertEquals(setOf(Zone.RED, Zone.BLUE), Zone.entries.filter { it.isUnpleasant }.toSet())
    }
}
