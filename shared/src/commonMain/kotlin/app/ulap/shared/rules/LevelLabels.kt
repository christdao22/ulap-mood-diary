package app.ulap.shared.rules

/** Energy, stress and stress-after are all on a 1..10 scale (api-spec.md §5.3). */
val LEVEL_RANGE: IntRange = 1..10

/**
 * The word shown next to a stress value, e.g. "8 / 10 · high" (design-spec.md §6.4).
 * Each level covers the values up to and including [upTo]: 1–2 very low · 3–4 low ·
 * 5–6 moderate · 7–8 high · 9–10 very high.
 */
enum class StressLevel(val upTo: Int) {
    VERY_LOW(2),
    LOW(4),
    MODERATE(6),
    HIGH(8),
    VERY_HIGH(10),
}

/**
 * The word shown next to an energy value (design-spec.md §6.4). Energy has three words, not five:
 * 1–3 low · 4–6 medium · 7–10 high.
 */
enum class EnergyLevel(val upTo: Int) {
    LOW(3),
    MEDIUM(6),
    HIGH(10),
}

object StressLabels {
    /** The level for [stress], which must be 1..10 like every stored stress value. */
    fun of(stress: Int): StressLevel {
        require(stress in LEVEL_RANGE) { "stress must be in $LEVEL_RANGE, was $stress" }
        return StressLevel.entries.first { stress <= it.upTo }
    }
}

object EnergyLabels {
    /** The level for [energy], which must be 1..10 like every stored energy value. */
    fun of(energy: Int): EnergyLevel {
        require(energy in LEVEL_RANGE) { "energy must be in $LEVEL_RANGE, was $energy" }
        return EnergyLevel.entries.first { energy <= it.upTo }
    }
}
