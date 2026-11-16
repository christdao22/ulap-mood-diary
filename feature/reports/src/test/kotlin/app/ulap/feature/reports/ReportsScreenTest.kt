package app.ulap.feature.reports

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.ulap.core.composetesting.LargestFontScale
import app.ulap.core.composetesting.assertNoTextCutOff
import app.ulap.core.composetesting.assertTouchTargetsAtLeast48dp
import app.ulap.core.composetesting.assertTouchTargetsHaveNames
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.shared.model.CheckIn
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import app.ulap.shared.rules.ReportPeriod
import app.ulap.shared.rules.ReportPeriodType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h3000dp")
class ReportsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.parse("2026-11-04").toEpochDay()

    /** 9:00 AM Manila on [daysAgo] days before Nov 4, 2026. */
    private fun checkIn(daysAgo: Int, stress: Int, moodCode: String = "worried", trigger: Trigger? = Trigger.WORK) =
        Instant.parse("2026-11-04T01:00:00Z").minus(daysAgo.days).let { at ->
            CheckIn(
                id = "$daysAgo-$stress",
                moodCode = moodCode,
                energy = 5,
                stress = stress,
                stressAfter = null,
                trigger = trigger,
                triggerOther = null,
                note = null,
                supportUsed = SupportType.NONE,
                isGoodMoment = false,
                loggedAt = at,
                tzOffsetMin = 480,
                createdAt = at,
                updatedAt = at,
            )
        }

    private val weekOfCheckIns = listOf(
        checkIn(0, 8),
        checkIn(1, 4, moodCode = "calm"),
        checkIn(2, 6, trigger = Trigger.FAMILY),
        checkIn(3, 2, moodCode = "excited", trigger = null),
    )

    private fun show(checkIns: List<CheckIn>, largeText: Boolean = false, onSelect: (ReportPeriodType) -> Unit = {}) {
        val period = ReportPeriod.of(ReportPeriodType.WEEKLY, today)
        compose.setContent {
            LargestFontScale(enabled = largeText) {
                UlapTheme {
                    ReportsContent(
                        uiState = ReportsUiState.Loaded(
                            period,
                            today,
                            hasNext = false,
                            PeriodReport.of(checkIns, period),
                        ),
                        periodType = ReportPeriodType.WEEKLY,
                        onPeriodTypeSelect = onSelect,
                    )
                }
            }
        }
    }

    @Test
    fun `under 3 check-ins Ulap asks for a few more instead of drawing charts`() {
        show(listOf(checkIn(0, 5), checkIn(1, 6)))

        compose.onNodeWithText("A few more check-ins and I'll start seeing patterns with you.").assertExists()
        compose.onNodeWithText("Stress trend").assertDoesNotExist()
    }

    @Test
    fun `tiles, the chart summary and the bar lists show the week`() {
        show(listOf(checkIn(0, 8), checkIn(1, 4, moodCode = "calm"), checkIn(2, 6, trigger = Trigger.FAMILY)))

        compose.onNodeWithText("Oct 29 – Nov 4").assertExists()
        compose.onNodeWithText("6.0").assertExists() // (8 + 4 + 6) / 3
        compose.onNodeWithText("Ulap noticed:").assertExists()
        compose.onNodeWithContentDescription(
            "Daily average stress. Highest Wednesday, 8.0. Lowest Tuesday, 4.0.",
        ).assertExists()
        compose.onNodeWithContentDescription("Work, 2 check-ins").assertExists()
        compose.onNodeWithText("High 7–10").assertExists()
    }

    @Test
    fun `tapping a segment selects that period type`() {
        var picked: ReportPeriodType? = null
        show(emptyList()) { picked = it }

        compose.onNodeWithText("Weekly").assertIsSelected()
        compose.onNodeWithText("Monthly").performClick()

        assertEquals(ReportPeriodType.MONTHLY, picked)
    }

    // Accessibility (design-spec.md §11). Native graphics measure real fonts.

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+h3000dp")
    fun `every touch target is at least 48dp and has a name`() {
        show(weekOfCheckIns)
        compose.assertTouchTargetsAtLeast48dp()
        compose.assertTouchTargetsHaveNames()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+en")
    fun `nothing is cut off at 200 percent text in English`() {
        show(weekOfCheckIns, largeText = true)
        compose.assertNoTextCutOff()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+fil")
    fun `nothing is cut off at 200 percent text in Filipino`() {
        show(weekOfCheckIns, largeText = true)
        compose.assertNoTextCutOff()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+b+ceb")
    fun `nothing is cut off at 200 percent text in Bisaya`() {
        show(weekOfCheckIns, largeText = true)
        compose.assertNoTextCutOff()
    }
}
