package app.ulap.feature.reports.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.ulap.feature.reports.ReportsScreen
import kotlinx.serialization.Serializable

/** The Reports tab's start screen (design-spec.md §7.7). */
@Serializable
data object ReportsRoute

/** Adds the Reports tab: Daily, Weekly and Monthly patterns, worked out on the phone. */
fun NavGraphBuilder.reportsScreen() {
    composable<ReportsRoute> {
        ReportsScreen()
    }
}
