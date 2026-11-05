package app.ulap.feature.checkin.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import app.ulap.feature.checkin.pickmood.PickMoodScreen
import kotlinx.serialization.Serializable

/** The Check in tab: Pick mood, then Details, Response and the after-check (design-spec.md §7.2–§7.5). */
@Serializable
data object CheckInGraph

/** Check in · Pick a mood, the tab's first screen. */
@Serializable
data object PickMoodRoute

/**
 * Adds the check-in screens as one nested graph, so the tab bar can treat them as one tab.
 *
 * @param onMoodClick Called with the mood's code when the user taps a tile.
 */
fun NavGraphBuilder.checkInGraph(onMoodClick: (moodCode: String) -> Unit) {
    navigation<CheckInGraph>(startDestination = PickMoodRoute) {
        composable<PickMoodRoute> {
            PickMoodScreen(onMoodClick = onMoodClick)
        }
    }
}
