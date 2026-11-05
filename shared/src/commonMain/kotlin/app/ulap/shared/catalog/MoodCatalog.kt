package app.ulap.shared.catalog

import app.ulap.shared.model.Mood
import app.ulap.shared.model.Zone

/**
 * The 25 moods, their zones and their default energy and stress (technical-plan.md §5).
 *
 * The app and the server both validate against this list, so the server rejects any code that
 * isn't here. To add a mood: add it below, add its name to all three strings.xml files, and
 * extend the catalog test (README §11). Never change or remove a code that has shipped: stored
 * check-ins refer to it.
 */
object MoodCatalog {
    /** The one mood that shows the NCMH 1553 hotline instead of activity buttons. */
    const val HOPELESS: String = "hopeless"

    /** Every mood, grouped by zone (Red → Green) and, inside a zone, from most to least stress. */
    val all: List<Mood> = listOf(
        // Red: tense, high energy
        mood("panicked", Zone.RED, energy = 9, stress = 10),
        mood("overwhelmed", Zone.RED, energy = 8, stress = 9),
        mood("anxious", Zone.RED, energy = 8, stress = 9),
        mood("angry", Zone.RED, energy = 9, stress = 8),
        mood("worried", Zone.RED, energy = 6, stress = 8),
        mood("frustrated", Zone.RED, energy = 7, stress = 7),
        mood("nervous", Zone.RED, energy = 7, stress = 6),
        mood("irritated", Zone.RED, energy = 6, stress = 6),
        mood("restless", Zone.RED, energy = 7, stress = 5),
        // Blue: heavy, low energy
        mood(HOPELESS, Zone.BLUE, energy = 2, stress = 8),
        mood("burned_out", Zone.BLUE, energy = 2, stress = 8),
        mood("sad", Zone.BLUE, energy = 3, stress = 6),
        mood("lonely", Zone.BLUE, energy = 3, stress = 6),
        mood("guilty", Zone.BLUE, energy = 4, stress = 6),
        mood("disappointed", Zone.BLUE, energy = 4, stress = 5),
        mood("tired", Zone.BLUE, energy = 2, stress = 4),
        mood("bored", Zone.BLUE, energy = 3, stress = 3),
        // Yellow: bright, high energy
        mood("excited", Zone.YELLOW, energy = 9, stress = 3),
        mood("motivated", Zone.YELLOW, energy = 8, stress = 2),
        mood("proud", Zone.YELLOW, energy = 7, stress = 2),
        mood("happy", Zone.YELLOW, energy = 7, stress = 2),
        // Green: easy, low energy
        mood("content", Zone.GREEN, energy = 4, stress = 1),
        mood("grateful", Zone.GREEN, energy = 4, stress = 1),
        mood("relaxed", Zone.GREEN, energy = 3, stress = 1),
        mood("calm", Zone.GREEN, energy = 3, stress = 1),
    )

    private val byCode: Map<String, Mood> = all.associateBy { it.code }

    /** The mood with this code, or `null` if the code is unknown. */
    fun byCode(code: String): Mood? = byCode[code]

    /** The moods of one zone, in display order (most to least stress). */
    fun inZone(zone: Zone): List<Mood> = all.filter { it.zone == zone }

    private fun mood(code: String, zone: Zone, energy: Int, stress: Int) =
        Mood(code = code, zone = zone, defaultEnergy = energy, defaultStress = stress)
}
