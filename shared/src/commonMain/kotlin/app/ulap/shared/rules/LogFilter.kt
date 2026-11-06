package app.ulap.shared.rules

import app.ulap.shared.catalog.MoodCatalog
import app.ulap.shared.model.CheckIn
import app.ulap.shared.model.Trigger
import app.ulap.shared.model.Zone

/**
 * What the Logs list is narrowed to: the search box and one zone chip (design-spec.md §7.6).
 * [zone] null is the "All" chip.
 *
 * [query] is what the person typed, which can be words from their journal: never log it.
 */
data class LogFilter(val query: String = "", val zone: Zone? = null) {
    /** False when nothing narrows the list, so an empty result means an empty diary. */
    val isActive: Boolean get() = query.isNotBlank() || zone != null
}

/**
 * Mood and trigger names in the app's language, so search finds the words the person sees on
 * screen ("Masaya", not the code `happy`). The Android side fills them from strings.xml.
 */
data class SearchNames(val moods: Map<String, String>, val triggers: Map<Trigger, String>) {
    companion object {
        /** Before the names are known: search still finds notes and "Other" text. */
        val None = SearchNames(moods = emptyMap(), triggers = emptyMap())
    }
}

/**
 * The check-ins that match [filter], in the order given.
 *
 * A check-in matches when its mood is in the chosen zone, and every word of the query appears
 * somewhere in its mood name, trigger name, "Other" trigger text or note. Case doesn't matter, and
 * neither does word order: "work tired" finds a Work check-in whose note says "so tired".
 * This runs on the decrypted rows in memory; nothing is searched on disk (PHASES.md Phase 3).
 */
fun List<CheckIn>.filterLogs(filter: LogFilter, names: SearchNames): List<CheckIn> {
    val words = filter.query.lowercase().split(Whitespace).filter { it.isNotEmpty() }
    return filter { checkIn ->
        val inZone = filter.zone == null || MoodCatalog.byCode(checkIn.moodCode)?.zone == filter.zone
        inZone && (words.isEmpty() || checkIn.searchText(names).let { text -> words.all { it in text } })
    }
}

/** Every searchable field of one check-in, lowercased and joined by line breaks. */
private fun CheckIn.searchText(names: SearchNames): String = listOfNotNull(
    names.moods[moodCode],
    trigger?.let { names.triggers[it] },
    triggerOther,
    note,
).joinToString(separator = "\n").lowercase()

private val Whitespace = Regex("\\s+")
