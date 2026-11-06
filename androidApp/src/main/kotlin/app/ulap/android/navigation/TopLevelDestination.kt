package app.ulap.android.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import app.ulap.core.designsystem.icon.UlapIcons
import app.ulap.core.ui.R
import app.ulap.feature.account.navigation.AccountRoute
import app.ulap.feature.checkin.navigation.CheckInGraph
import app.ulap.feature.checkin.navigation.PickMoodRoute
import app.ulap.feature.logs.navigation.LogsRoute
import app.ulap.feature.reports.navigation.ReportsRoute
import kotlin.reflect.KClass

/**
 * The four tabs, in tab bar order (design-spec.md §6.10). [startRoute] is the tab's first screen,
 * which differs from [route] only for a tab with several screens.
 */
enum class TopLevelDestination(
    val route: Any,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
    val startRoute: Any = route,
) {
    CHECK_IN(CheckInGraph, R.string.tab_check_in, UlapIcons.CheckIn, startRoute = PickMoodRoute),
    LOGS(LogsRoute, R.string.tab_logs, UlapIcons.Logs),
    REPORTS(ReportsRoute, R.string.tab_reports, UlapIcons.Reports),
    ACCOUNT(AccountRoute, R.string.tab_account, UlapIcons.Account),
    ;

    private val routeClass: KClass<*> get() = route::class

    /** True when [destination] is this tab's screen or any screen inside it (e.g. Details). */
    fun isSelected(destination: NavDestination?): Boolean =
        destination?.hierarchy?.any { it.hasRoute(routeClass) } == true
}

/**
 * Switches tabs the standard way: one copy of each tab on the back stack, each tab keeps its
 * own screens and scroll position, and Back from any tab returns to Check in before leaving.
 * Tapping the tab that's already open goes back to its first screen, e.g. Details → Pick mood.
 */
fun NavController.navigateToTab(destination: TopLevelDestination) {
    if (destination.isSelected(currentDestination)) {
        popBackStack(destination.startRoute, inclusive = false)
        return
    }
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
