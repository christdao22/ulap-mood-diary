package app.ulap.core.ui

import androidx.annotation.StringRes
import app.ulap.core.designsystem.component.BreathPhase
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import app.ulap.shared.rules.ResponseMessage

// The Response screen's words (design-spec.md §7.4, technical-plan.md Appendix B). Like
// CheckInText.kt, each value maps to its string by hand, so R8 keeps them and the compiler sees
// every use.

/** The response's title, e.g. "That's a lot to carry right now." (`resp_*_title`). */
@get:StringRes
val ResponseMessage.titleRes: Int
    get() = when (this) {
        ResponseMessage.RED -> R.string.resp_red_title
        ResponseMessage.BLUE -> R.string.resp_blue_title
        ResponseMessage.YELLOW -> R.string.resp_yellow_title
        ResponseMessage.GREEN -> R.string.resp_green_title
        ResponseMessage.HOPELESS -> R.string.resp_hopeless_title
    }

/**
 * The response's body (`resp_*_body`). Some take the mood name as `%1$s`, so always format it
 * with the mood name; the others ignore it.
 */
@get:StringRes
val ResponseMessage.bodyRes: Int
    get() = when (this) {
        ResponseMessage.RED -> R.string.resp_red_body
        ResponseMessage.BLUE -> R.string.resp_blue_body
        ResponseMessage.YELLOW -> R.string.resp_yellow_body
        ResponseMessage.GREEN -> R.string.resp_green_body
        ResponseMessage.HOPELESS -> R.string.resp_hopeless_body
    }

/**
 * The line under the response, e.g. "Work can take a lot out of us." (`trigger_line_*`), or null
 * for a trigger without one. ResponseRules decides when it shows (technical-plan.md §5).
 */
@get:StringRes
val Trigger.responseLineRes: Int?
    get() = when (this) {
        Trigger.WORK -> R.string.trigger_line_work
        Trigger.FAMILY -> R.string.trigger_line_family
        Trigger.MONEY -> R.string.trigger_line_money
        Trigger.HEALTH -> R.string.trigger_line_health
        Trigger.SCHOOL -> R.string.trigger_line_school
        Trigger.RELATIONSHIPS, Trigger.OTHER -> null
    }

/** The primary button that offers this support, e.g. "Breathe with me · 1 minute". Null for NONE. */
@get:StringRes
val SupportType.offerRes: Int?
    get() = when (this) {
        SupportType.BREATHE -> R.string.resp_action_breathe
        SupportType.SMALL_HELPS -> R.string.resp_action_small_helps
        SupportType.GOOD_MOMENT -> R.string.resp_action_good_moment
        SupportType.NONE -> null
    }

/** The word inside the breathing circle for each part of a breath, e.g. "Inhale" (design-spec.md §6.9). */
@get:StringRes
val BreathPhase.labelRes: Int
    get() = when (this) {
        BreathPhase.IN -> R.string.help_breathe_in
        BreathPhase.HOLD -> R.string.help_breathe_hold
        BreathPhase.OUT -> R.string.help_breathe_out
    }

/**
 * The "something small that helps" list for Blue (design-spec.md §7.4, technical-plan.md
 * Appendix B, help_small_*). Each is small, quick and free; easiest first.
 */
val SmallHelpsRes: List<Int> = listOf(
    R.string.help_small_water,
    R.string.help_small_song,
    R.string.help_small_stretch,
    R.string.help_small_air,
    R.string.help_small_message,
)
