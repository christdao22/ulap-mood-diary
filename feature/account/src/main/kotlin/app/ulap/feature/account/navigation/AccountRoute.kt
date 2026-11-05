package app.ulap.feature.account.navigation

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.ulap.core.ui.R
import app.ulap.core.ui.TabPlaceholder
import kotlinx.serialization.Serializable

/** The Account tab's start screen (design-spec.md §7.8). */
@Serializable
data object AccountRoute

/** Adds the Account tab. Until Phase 6 builds it, it shows the tab's title. */
fun NavGraphBuilder.accountScreen() {
    composable<AccountRoute> {
        TabPlaceholder(title = stringResource(R.string.tab_account))
    }
}
