package app.ulap.feature.logs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.shared.model.CheckIn
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import app.ulap.shared.model.Zone
import app.ulap.shared.rules.CheckInDay
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2000dp")
class LogsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun checkIn(
        id: String,
        moodCode: String = "worried",
        stress: Int = 8,
        trigger: Trigger? = Trigger.WORK,
        triggerOther: String? = null,
        note: String? = null,
    ) = CheckIn(
        id = id,
        moodCode = moodCode,
        energy = 6,
        stress = stress,
        stressAfter = null,
        trigger = trigger,
        triggerOther = triggerOther,
        note = note,
        supportUsed = SupportType.NONE,
        isGoodMoment = false,
        loggedAt = Instant.parse("2026-11-05T01:15:00Z"),
        tzOffsetMin = 480,
        createdAt = Instant.parse("2026-11-05T01:15:00Z"),
        updatedAt = Instant.parse("2026-11-05T01:15:00Z"),
    )

    private fun show(vararg days: LogDay) = compose.setContent {
        UlapTheme { LogsContent(uiState = LogsUiState.Loaded(days.toList())) }
    }

    @Test
    fun `a day header names the day and sums it up`() {
        show(
            LogDay(DayLabel.Today, CheckInDay(1, listOf(checkIn("a", stress = 8), checkIn("b", stress = 3)))),
            LogDay(DayLabel.Yesterday, CheckInDay(0, listOf(checkIn("c", stress = 4)))),
        )

        compose.onNodeWithText("Today").assertExists()
        compose.onNodeWithText("2 check-ins · avg stress 5.5").assertExists()
        compose.onNodeWithText("Yesterday").assertExists()
        compose.onNodeWithText("1 check-in · avg stress 4.0").assertExists()
    }

    @Test
    fun `day headers are headings, so TalkBack can jump between days`() {
        show(LogDay(DayLabel.Today, CheckInDay(1, listOf(checkIn("a")))))
        compose.onAllNodesWithText("Today", substring = true).filter(isHeading()).assertCountEquals(1)
    }

    @Test
    fun `an older day shows its date, and the year only when it isn't this year`() {
        show(
            LogDay(DayLabel.Date(LocalDate.of(2026, 11, 2), showYear = false), CheckInDay(1, listOf(checkIn("a")))),
            LogDay(DayLabel.Date(LocalDate.of(2025, 12, 31), showYear = true), CheckInDay(0, listOf(checkIn("b")))),
        )

        compose.onNodeWithText("Mon, Nov 2").assertExists()
        compose.onNodeWithText("Wed, Dec 31, 2025").assertExists()
    }

    @Test
    fun `a row shows the mood, its stress and trigger, and the note`() {
        show(LogDay(DayLabel.Today, CheckInDay(1, listOf(checkIn("a", note = "Report due tomorrow.")))))

        compose.onNodeWithText("Worried", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Stress 8 · Work", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Report due tomorrow.", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the time is on the clock it was logged on`() {
        // 01:15 UTC, logged in Manila (+8): 9:15 AM. The space before AM varies by ICU version.
        show(LogDay(DayLabel.Today, CheckInDay(1, listOf(checkIn("a")))))
        compose.onNodeWithText("9:15", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `Other shows the person's own words, and no trigger shows none`() {
        show(
            LogDay(
                DayLabel.Today,
                CheckInDay(
                    1,
                    listOf(
                        checkIn("a", moodCode = "calm", stress = 3, trigger = Trigger.OTHER, triggerOther = "Traffic"),
                        checkIn("b", moodCode = "happy", stress = 2, trigger = null),
                    ),
                ),
            ),
        )

        compose.onNodeWithText("Stress 3 · Traffic", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Stress 2", substring = true, useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText("Stress 2 ·", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `search and zone chips show once there are check-ins`() {
        show(LogDay(DayLabel.Today, CheckInDay(1, listOf(checkIn("a")))))

        compose.onNodeWithContentDescription("Search mood, trigger or journal").assertExists()
        listOf("All", "Red", "Blue", "Yellow", "Green").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("All").assertIsSelected()
    }

    @Test
    fun `an empty diary has nothing to search`() {
        show()

        compose.onNodeWithContentDescription("Search mood, trigger or journal").assertDoesNotExist()
        compose.onNodeWithText("All").assertDoesNotExist()
        compose.onNodeWithText("No check-ins match. Try another word or zone.").assertDoesNotExist()
    }

    @Test
    fun `no match says so and offers another try`() {
        compose.setContent {
            UlapTheme {
                LogsContent(uiState = LogsUiState.Loaded(days = emptyList(), isDiaryEmpty = false), zone = Zone.BLUE)
            }
        }

        compose.onNodeWithText("No check-ins match. Try another word or zone.").assertExists()
        compose.onNodeWithText("Blue").assertIsSelected()
    }

    @Test
    fun `typing, clearing and tapping a chip reach the screen's callbacks`() {
        var query by mutableStateOf("")
        var zoneClicked: Zone? = null
        compose.setContent {
            UlapTheme {
                LogsContent(
                    uiState = LogsUiState.Loaded(listOf(LogDay(DayLabel.Today, CheckInDay(1, listOf(checkIn("a")))))),
                    query = query,
                    onQueryChange = { query = it },
                    onZoneClick = { zoneClicked = it },
                )
            }
        }

        compose.onNodeWithContentDescription("Search mood, trigger or journal").performTextInput("work")
        assertEquals("work", query)
        compose.onNodeWithContentDescription("Clear search").performClick()
        assertEquals("", query)
        compose.onNodeWithText("Yellow").performClick()
        assertEquals(Zone.YELLOW, zoneClicked)
    }
}
