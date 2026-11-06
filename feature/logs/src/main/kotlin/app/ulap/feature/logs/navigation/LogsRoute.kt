package app.ulap.feature.logs.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.ulap.feature.logs.LogsScreen
import kotlinx.serialization.Serializable

/** The Logs tab's start screen (design-spec.md §7.6). */
@Serializable
data object LogsRoute

/** Adds the Logs tab: the person's check-ins, grouped by day. */
fun NavGraphBuilder.logsScreen() {
    composable<LogsRoute> {
        LogsScreen()
    }
}
