package app.ulap.shared.rules

import app.ulap.shared.catalog.MoodCatalog
import app.ulap.shared.model.Mood
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import app.ulap.shared.model.Zone

/** Ulap's six faces (design-spec.md §8.3). */
enum class UlapExpression { NEUTRAL, CONCERNED, GENTLE, HAPPY, CALM, QUIET }

/**
 * Which title and body Ulap says on the Response screen. Each value maps to the
 * `resp_<value>_title` and `resp_<value>_body` strings (technical-plan.md Appendix B).
 */
enum class ResponseMessage { RED, BLUE, YELLOW, GREEN, HOPELESS }

/** What the Response screen offers below the message (design-spec.md §7.4). */
sealed interface ResponseActions {
    /**
     * The NCMH 1553 hotline card, in place of activity buttons (Hopeless). It must keep working
     * offline: it only opens the dialer.
     */
    data object Hotline : ResponseActions

    /**
     * One primary support button ([primary]), plus the secondary button when [showSecondary]
     * is true. Stress ≥ 8 means one button only, so the person has no choice to make.
     */
    data class Offer(val primary: SupportType, val showSecondary: Boolean) : ResponseActions
}

/** When the Response screen shows the "How stressed do you feel now?" card (design-spec.md §7.4). */
enum class StressCheck {
    /** Yellow and Green: no card. */
    NONE,

    /** From the start: Hopeless only, which has no support button to wait for. */
    NOW,

    /**
     * Red and Blue at every stress: only once the support has started, so the card sits under the
     * breathing circle or the small helps it asks about (technical-plan.md §5).
     */
    AFTER_SUPPORT,
    ;

    /** Whether the card shows, given whether the person has started the support yet. */
    fun isShown(supportStarted: Boolean): Boolean = when (this) {
        NONE -> false
        NOW -> true
        AFTER_SUPPORT -> supportStarted
    }
}

/** Everything the Response screen needs to know, decided in one place. */
data class Response(
    val expression: UlapExpression,
    /** False for Hopeless: Ulap stays still (design-spec.md §8.3). */
    val floats: Boolean,
    val message: ResponseMessage,
    /** Maps to `trigger_line_<trigger>`. `null` when no line applies. */
    val triggerLine: Trigger?,
    val actions: ResponseActions,
    /** The "How stressed do you feel now?" card. Red and Blue zones only (Hopeless included). */
    val stressCheck: StressCheck,
)

/** How stress changed between the check-in and "How stressed do you feel now?". */
enum class StressChange { LOWER, SAME, HIGHER }

/** The After screen's face, message and hotline card (design-spec.md §7.5 and §10.4). */
data class AfterCheck(
    val change: StressChange,
    val expression: UlapExpression,
    /** False for Hopeless: Ulap stays as still as it was on Response. */
    val floats: Boolean,
    /** The NCMH 1553 card. Hopeless only, whichever way stress went. */
    val showHotline: Boolean,
)

/**
 * Ulap's response to a check-in (technical-plan.md §5 "Response rules"). Screens show what this
 * returns and never decide these rules themselves. Hopeless is a safety rule (🔒 in PHASES.md):
 * a change to it needs two reviewers.
 */
object ResponseRules {
    /**
     * At or above this stress, the Response screen opens with one button only: no "Maybe later",
     * and the stress check waits until the support has started (design-spec.md §6.6).
     */
    const val ONE_BUTTON_STRESS: Int = 8

    /**
     * Triggers that have a `trigger_line_*` string in technical-plan.md Appendix B.
     * RELATIONSHIPS has no line yet, and OTHER is the person's own words, so neither shows one.
     */
    private val triggersWithLine: Set<Trigger> =
        setOf(Trigger.WORK, Trigger.FAMILY, Trigger.MONEY, Trigger.HEALTH, Trigger.SCHOOL)

    /**
     * Ulap's face for [mood], on Details and Response alike (design-spec.md §7.3, §8.3): the
     * zone's face, or the quiet one for Hopeless, which never floats.
     */
    fun expression(mood: Mood): UlapExpression =
        if (mood.code == MoodCatalog.HOPELESS) UlapExpression.QUIET else mood.zone.expression()

    /** The response for [mood] logged at [stress] (1..10) with an optional [trigger]. */
    fun respond(mood: Mood, stress: Int, trigger: Trigger?): Response {
        require(stress in LEVEL_RANGE) { "stress must be in $LEVEL_RANGE, was $stress" }
        val zone = mood.zone
        val isHopeless = mood.code == MoodCatalog.HOPELESS
        return Response(
            expression = expression(mood),
            floats = !isHopeless,
            message = if (isHopeless) ResponseMessage.HOPELESS else zone.message(),
            triggerLine = trigger?.takeIf { zone.isUnpleasant && it in triggersWithLine },
            actions = if (isHopeless) {
                ResponseActions.Hotline
            } else {
                ResponseActions.Offer(primary = zone.support(), showSecondary = stress < ONE_BUTTON_STRESS)
            },
            stressCheck = stressCheck(zone, isHopeless),
        )
    }

    /**
     * Hopeless has no support button to wait for, so its card shows from the start at any stress
     * (🔒 safety rule, technical-plan.md §5). Red and Blue ask only after the support, at any
     * stress, so the question follows the breathing or small helps it's asking about.
     */
    private fun stressCheck(zone: Zone, isHopeless: Boolean): StressCheck = when {
        !zone.isUnpleasant -> StressCheck.NONE
        isHopeless -> StressCheck.NOW
        else -> StressCheck.AFTER_SUPPORT
    }

    /**
     * The After screen for [mood] logged at a stress of [before] that is now [now] (both 1..10).
     * Ulap is calm when stress went down and gentle otherwise (design-spec.md §7.5). Hopeless keeps
     * the quiet, still face from Response and shows the hotline card again, even when stress went
     * down: help stays one tap away for the whole check-in.
     */
    fun afterCheck(mood: Mood, before: Int, now: Int): AfterCheck {
        require(before in LEVEL_RANGE) { "before must be in $LEVEL_RANGE, was $before" }
        require(now in LEVEL_RANGE) { "now must be in $LEVEL_RANGE, was $now" }
        val change = when {
            now < before -> StressChange.LOWER
            now == before -> StressChange.SAME
            else -> StressChange.HIGHER
        }
        val isHopeless = mood.code == MoodCatalog.HOPELESS
        val expression = when {
            isHopeless -> UlapExpression.QUIET
            change == StressChange.LOWER -> UlapExpression.CALM
            else -> UlapExpression.GENTLE
        }
        return AfterCheck(change, expression, floats = !isHopeless, showHotline = isHopeless)
    }

    private fun Zone.expression(): UlapExpression = when (this) {
        Zone.RED -> UlapExpression.CONCERNED
        Zone.BLUE -> UlapExpression.GENTLE
        Zone.YELLOW -> UlapExpression.HAPPY
        Zone.GREEN -> UlapExpression.CALM
    }

    private fun Zone.message(): ResponseMessage = when (this) {
        Zone.RED -> ResponseMessage.RED
        Zone.BLUE -> ResponseMessage.BLUE
        Zone.YELLOW -> ResponseMessage.YELLOW
        Zone.GREEN -> ResponseMessage.GREEN
    }

    private fun Zone.support(): SupportType = when (this) {
        Zone.RED -> SupportType.BREATHE
        Zone.BLUE -> SupportType.SMALL_HELPS
        Zone.YELLOW, Zone.GREEN -> SupportType.GOOD_MOMENT
    }
}
