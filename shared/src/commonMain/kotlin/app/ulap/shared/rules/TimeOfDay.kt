package app.ulap.shared.rules

/**
 * The four blocks of a local day (technical-plan.md §10): Morning 5–11, Afternoon 12–17,
 * Evening 18–21, Night 22–4. Reports average stress by block, and the pick-mood greeting
 * (design-spec.md §7.2) uses the same blocks so the two never disagree.
 */
enum class TimeOfDay(private val hours: IntRange) {
    MORNING(5..11),
    AFTERNOON(12..17),
    EVENING(18..21),

    /** 22:00 to 04:59. It wraps past midnight, so it is the block none of the others claim. */
    NIGHT(IntRange.EMPTY),
    ;

    companion object {
        private const val HOURS_PER_DAY = 24

        /** The block for a local clock [hour] (0..23). */
        fun ofHour(hour: Int): TimeOfDay {
            require(hour in 0 until HOURS_PER_DAY) { "hour must be in 0..23, was $hour" }
            return entries.firstOrNull { hour in it.hours } ?: NIGHT
        }
    }
}
