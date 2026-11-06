package app.ulap.core.ui

import androidx.annotation.StringRes
import app.ulap.shared.model.Mood
import app.ulap.shared.model.Zone

// Moods and zones are stored as stable codes; only their names are translated
// (technical-plan.md §11). These map a code to its string, written out by hand instead of looked
// up by name, so R8 keeps the strings and the compiler sees every use.

/**
 * The mood's name in the user's language, e.g. "Worried". Every Mood comes from MoodCatalog, and
 * MoodTextTest checks each one has a name, so the `else` branch only fires on a new, unnamed mood.
 */
@get:StringRes
val Mood.nameRes: Int
    get() = when (code) {
        "panicked" -> R.string.mood_panicked
        "overwhelmed" -> R.string.mood_overwhelmed
        "anxious" -> R.string.mood_anxious
        "angry" -> R.string.mood_angry
        "worried" -> R.string.mood_worried
        "frustrated" -> R.string.mood_frustrated
        "nervous" -> R.string.mood_nervous
        "irritated" -> R.string.mood_irritated
        "restless" -> R.string.mood_restless
        "hopeless" -> R.string.mood_hopeless
        "burned_out" -> R.string.mood_burned_out
        "sad" -> R.string.mood_sad
        "lonely" -> R.string.mood_lonely
        "guilty" -> R.string.mood_guilty
        "disappointed" -> R.string.mood_disappointed
        "tired" -> R.string.mood_tired
        "bored" -> R.string.mood_bored
        "excited" -> R.string.mood_excited
        "motivated" -> R.string.mood_motivated
        "proud" -> R.string.mood_proud
        "happy" -> R.string.mood_happy
        "content" -> R.string.mood_content
        "grateful" -> R.string.mood_grateful
        "relaxed" -> R.string.mood_relaxed
        "calm" -> R.string.mood_calm
        else -> error("No name string for mood '$code'. Add it here and to all three strings.xml files.")
    }

/** The zone's name, e.g. "Red Zone". Section headers and TalkBack both use it. */
@get:StringRes
val Zone.nameRes: Int
    get() = when (this) {
        Zone.RED -> R.string.zone_red_name
        Zone.BLUE -> R.string.zone_blue_name
        Zone.YELLOW -> R.string.zone_yellow_name
        Zone.GREEN -> R.string.zone_green_name
    }

/** The zone's short name, e.g. "Red", for the Logs zone filter chips (design-spec.md §6.12). */
@get:StringRes
val Zone.shortNameRes: Int
    get() = when (this) {
        Zone.RED -> R.string.zone_red_short
        Zone.BLUE -> R.string.zone_blue_short
        Zone.YELLOW -> R.string.zone_yellow_short
        Zone.GREEN -> R.string.zone_green_short
    }

/** How the zone feels, e.g. "tense · high energy" (design-spec.md §6.2). */
@get:StringRes
val Zone.descriptorRes: Int
    get() = when (this) {
        Zone.RED -> R.string.zone_red_descriptor
        Zone.BLUE -> R.string.zone_blue_descriptor
        Zone.YELLOW -> R.string.zone_yellow_descriptor
        Zone.GREEN -> R.string.zone_green_descriptor
    }
