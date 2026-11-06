package app.ulap.core.ui

import androidx.annotation.StringRes
import app.ulap.core.designsystem.mascot.UlapExpression as MascotExpression
import app.ulap.shared.model.Trigger
import app.ulap.shared.model.Zone
import app.ulap.shared.rules.EnergyLevel
import app.ulap.shared.rules.StressLevel
import app.ulap.shared.rules.UlapExpression as RuleExpression

// The words around a check-in: triggers, level words and Ulap's per-zone lines. Like MoodText.kt,
// each value maps to its string by hand, so R8 keeps the strings and the compiler sees every use.

/** The trigger chip's label, e.g. "Work" (design-spec.md §6.5). */
@get:StringRes
val Trigger.nameRes: Int
    get() = when (this) {
        Trigger.WORK -> R.string.trigger_work
        Trigger.FAMILY -> R.string.trigger_family
        Trigger.MONEY -> R.string.trigger_money
        Trigger.HEALTH -> R.string.trigger_health
        Trigger.RELATIONSHIPS -> R.string.trigger_relationships
        Trigger.SCHOOL -> R.string.trigger_school
        Trigger.OTHER -> R.string.trigger_other
    }

/** The word after a stress value, e.g. "high" in "8 / 10 · high" (design-spec.md §6.4). */
@get:StringRes
val StressLevel.wordRes: Int
    get() = when (this) {
        StressLevel.VERY_LOW -> R.string.stress_level_very_low
        StressLevel.LOW -> R.string.stress_level_low
        StressLevel.MODERATE -> R.string.stress_level_moderate
        StressLevel.HIGH -> R.string.stress_level_high
        StressLevel.VERY_HIGH -> R.string.stress_level_very_high
    }

/** The word after an energy value, e.g. "medium" (design-spec.md §6.4). */
@get:StringRes
val EnergyLevel.wordRes: Int
    get() = when (this) {
        EnergyLevel.LOW -> R.string.energy_level_low
        EnergyLevel.MEDIUM -> R.string.energy_level_medium
        EnergyLevel.HIGH -> R.string.energy_level_high
    }

/** Ulap's soft-mirror line on the Details header, e.g. "That sounds peaceful." (Appendix B `pre_*`). */
@get:StringRes
val Zone.mirrorLineRes: Int
    get() = when (this) {
        Zone.RED -> R.string.pre_red
        Zone.BLUE -> R.string.pre_blue
        Zone.YELLOW -> R.string.pre_yellow
        Zone.GREEN -> R.string.pre_green
    }

/** The journal's placeholder, e.g. "What made this moment good?" (Appendix B `prompt_*`). */
@get:StringRes
val Zone.journalPromptRes: Int
    get() = when (this) {
        Zone.RED -> R.string.prompt_red
        Zone.BLUE -> R.string.prompt_blue
        Zone.YELLOW -> R.string.prompt_yellow
        Zone.GREEN -> R.string.prompt_green
    }

/**
 * The face ResponseRules picked, as the design system draws it. The two enums stay separate on
 * purpose: `shared` can't depend on Compose, and the design system shouldn't know the rules.
 */
fun RuleExpression.toMascot(): MascotExpression = when (this) {
    RuleExpression.NEUTRAL -> MascotExpression.NEUTRAL
    RuleExpression.CONCERNED -> MascotExpression.CONCERNED
    RuleExpression.GENTLE -> MascotExpression.GENTLE
    RuleExpression.HAPPY -> MascotExpression.HAPPY
    RuleExpression.CALM -> MascotExpression.CALM
    RuleExpression.QUIET -> MascotExpression.QUIET
}
