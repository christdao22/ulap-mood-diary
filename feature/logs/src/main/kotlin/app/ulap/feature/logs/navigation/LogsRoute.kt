package app.ulap.feature.logs.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import app.ulap.feature.logs.LogsScreen
import kotlinx.serialization.Serializable

/** The Logs tab, with every screen opened from it, so the tab bar treats them as one tab. */
@Serializable
data object LogsGraph

/** The Logs tab's start screen (design-spec.md §7.6). */
@Serializable
data object LogsRoute

/**
 * Adds the Logs tab: the person's check-ins, grouped by day.
 *
 * @param onEditCheckIn Edit in a row's menu, with that check-in's ID (design-spec.md §7.6).
 * @param onCheckInNow "Check in now" on the empty diary: switches to the Check in tab (§7.6).
 * @param nestedScreens Screens from other features that open inside this tab, such as editing a
 *   check-in. Features never import each other, so the app root passes them in.
 */
fun NavGraphBuilder.logsGraph(
    onEditCheckIn: (String) -> Unit,
    onCheckInNow: () -> Unit,
    nestedScreens: NavGraphBuilder.() -> Unit,
) {
    navigation<LogsGraph>(startDestination = LogsRoute) {
        composable<LogsRoute> { entry ->
            LogsScreen(
                onEditCheckIn = { checkInId ->
                    // Only while Logs is on top, so a quick double tap can't open Details twice.
                    if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) onEditCheckIn(checkInId)
                },
                onCheckInNow = onCheckInNow,
            )
        }
        nestedScreens()
    }
}
