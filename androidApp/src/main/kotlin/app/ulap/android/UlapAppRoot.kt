package app.ulap.android

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.ulap.android.navigation.TopLevelDestination
import app.ulap.android.navigation.navigateToTab
import app.ulap.core.designsystem.component.UlapTabBar
import app.ulap.core.designsystem.component.UlapTabItem
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.feature.account.navigation.accountScreen
import app.ulap.feature.checkin.navigation.CheckInGraph
import app.ulap.feature.checkin.navigation.checkInGraph
import app.ulap.feature.logs.navigation.logsScreen
import app.ulap.feature.reports.navigation.reportsScreen

/**
 * The app's root: every screen sits above the fixed tab bar (design-spec.md §7) and the app opens
 * on Check in. Each feature adds its own screens to the NavHost; features never import each
 * other, so moving between them happens here (technical-plan.md §4).
 */
@Composable
fun UlapAppRoot(modifier: Modifier = Modifier, navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        modifier = modifier,
        containerColor = UlapTheme.colors.bg,
        bottomBar = {
            UlapTabBar {
                TopLevelDestination.entries.forEach { destination ->
                    UlapTabItem(
                        selected = destination.isSelected(currentDestination),
                        onClick = { navController.navigateToTab(destination) },
                        label = stringResource(destination.labelRes),
                        icon = destination.icon,
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = CheckInGraph,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            // Details, the next screen, comes in the next task (PHASES.md Phase 2, Screens).
            checkInGraph(onMoodClick = {})
            logsScreen()
            reportsScreen()
            accountScreen()
        }
    }
}
