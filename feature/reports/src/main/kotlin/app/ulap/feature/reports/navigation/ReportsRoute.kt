package app.ulap.feature.reports.navigation

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.ulap.core.ui.R
import app.ulap.core.ui.TabPlaceholder
import kotlinx.serialization.Serializable

/** The Reports tab's start screen (design-spec.md §7.7). */
@Serializable
data object ReportsRoute

/** Adds the Reports tab. Until Phase 4 builds the charts, it shows the tab's title. */
fun NavGraphBuilder.reportsScreen() {
    composable<ReportsRoute> {
        TabPlaceholder(title = stringResource(R.string.tab_reports))
    }
}
