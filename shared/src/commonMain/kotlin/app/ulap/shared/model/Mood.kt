package app.ulap.shared.model

/**
 * One of the 25 moods in [app.ulap.shared.catalog.MoodCatalog].
 *
 * [code] is stable and is what gets stored and synced (e.g. `worried`). It is never a translated
 * word: the app looks up the name in the user's language (technical-plan.md §5).
 * [defaultEnergy] and [defaultStress] are 1..10 and pre-fill the Details sliders.
 */
data class Mood(val code: String, val zone: Zone, val defaultEnergy: Int, val defaultStress: Int)
