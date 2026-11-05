package app.ulap.shared.model

/**
 * The four mood zones, ranked by stress: [tier] 1 is the most stressful (technical-plan.md §5).
 *
 * Red and Blue are the unpleasant zones. They get a support offer plus the "How stressed do you
 * feel now?" check, and a trigger line when a trigger is set.
 */
enum class Zone(val tier: Int) {
    /** Tense, high energy. */
    RED(1),

    /** Heavy, low energy. */
    BLUE(2),

    /** Bright, high energy. */
    YELLOW(3),

    /** Easy, low energy. */
    GREEN(4),
    ;

    val isUnpleasant: Boolean get() = this == RED || this == BLUE
}
