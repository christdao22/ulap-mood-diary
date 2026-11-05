package app.ulap.shared.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LevelLabelsTest {
    @Test
    fun stressWordsMatchTheDesignSpec() {
        val expected = listOf(
            StressLevel.VERY_LOW, StressLevel.VERY_LOW,
            StressLevel.LOW, StressLevel.LOW,
            StressLevel.MODERATE, StressLevel.MODERATE,
            StressLevel.HIGH, StressLevel.HIGH,
            StressLevel.VERY_HIGH, StressLevel.VERY_HIGH,
        )
        assertEquals(expected, (1..10).map(StressLabels::of))
    }

    @Test
    fun energyWordsMatchTheDesignSpec() {
        val expected = listOf(
            EnergyLevel.LOW, EnergyLevel.LOW, EnergyLevel.LOW,
            EnergyLevel.MEDIUM, EnergyLevel.MEDIUM, EnergyLevel.MEDIUM,
            EnergyLevel.HIGH, EnergyLevel.HIGH, EnergyLevel.HIGH, EnergyLevel.HIGH,
        )
        assertEquals(expected, (1..10).map(EnergyLabels::of))
    }

    @Test
    fun valuesOffTheScaleAreABug() {
        assertFailsWith<IllegalArgumentException> { StressLabels.of(0) }
        assertFailsWith<IllegalArgumentException> { StressLabels.of(11) }
        assertFailsWith<IllegalArgumentException> { EnergyLabels.of(0) }
        assertFailsWith<IllegalArgumentException> { EnergyLabels.of(11) }
    }
}
