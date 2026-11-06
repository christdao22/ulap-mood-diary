package app.ulap.feature.checkin.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import app.ulap.feature.checkin.details.DetailsScreen
import app.ulap.feature.checkin.pickmood.PickMoodScreen
import app.ulap.feature.checkin.response.ResponseScreen
import kotlinx.serialization.Serializable

/** The Check in tab: Pick mood, then Details, Response and the after-check (design-spec.md §7.2–§7.5). */
@Serializable
data object CheckInGraph

/** Check in · Pick a mood, the tab's first screen. */
@Serializable
data object PickMoodRoute

/** Check in · Details for the mood with this code (design-spec.md §7.3). */
@Serializable
data class DetailsRoute(val moodCode: String)

/** Check in · Response to the saved check-in with this ID (design-spec.md §7.4). */
@Serializable
data class ResponseRoute(val checkInId: String)

/**
 * Adds the check-in screens as one nested graph, so the tab bar can treat them as one tab. The
 * screens all belong to this feature, so it moves between them itself.
 */
fun NavGraphBuilder.checkInGraph(navController: NavController) {
    navigation<CheckInGraph>(startDestination = PickMoodRoute) {
        composable<PickMoodRoute> { entry ->
            PickMoodScreen(
                onMoodClick = { moodCode -> entry.ifResumed { navController.navigate(DetailsRoute(moodCode)) } },
            )
        }
        composable<DetailsRoute> { entry ->
            DetailsScreen(
                onBack = { entry.ifResumed { navController.popBackStack() } },
                // Details leaves the back stack: the check-in is saved, so Back from Response
                // goes to Pick mood instead of reopening the form.
                onCheckInSave = { checkInId ->
                    entry.ifResumed {
                        navController.navigate(ResponseRoute(checkInId)) {
                            popUpTo<DetailsRoute> { inclusive = true }
                        }
                    }
                },
            )
        }
        composable<ResponseRoute> { entry ->
            ResponseScreen(
                onDone = { entry.ifResumed { navController.popBackStack(PickMoodRoute, inclusive = false) } },
            )
        }
    }
}

/**
 * Runs [action] only while this screen is the one on top. A quick double tap would otherwise
 * open Details twice, or pop past Pick mood and leave the tab empty.
 */
private inline fun NavBackStackEntry.ifResumed(action: () -> Unit) {
    if (lifecycle.currentState == Lifecycle.State.RESUMED) action()
}
