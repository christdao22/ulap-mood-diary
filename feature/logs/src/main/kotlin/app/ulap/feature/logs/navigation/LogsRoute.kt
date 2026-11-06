package app.ulap.feature.logs.navigation

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.ulap.core.ui.R
import app.ulap.core.ui.TabPlaceholder
import kotlinx.serialization.Serializable

/** The Logs tab's start screen (design-spec.md §7.6). */
@Serializable
data object LogsRoute

/** Adds the Logs tab. Until Phase 3 builds the list, it shows the tab's title. */
fun NavGraphBuilder.logsScreen() {
    composable<LogsRoute> {
        TabPlaceholder(title = stringResource(R.string.tab_logs))
    }
}
