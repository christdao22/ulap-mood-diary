package app.ulap.android

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import app.ulap.feature.account.navigation.accountGraph
import app.ulap.feature.checkin.navigation.CheckInGraph
import app.ulap.feature.checkin.navigation.checkInGraph
import app.ulap.feature.checkin.navigation.editCheckInScreen
import app.ulap.feature.checkin.navigation.navigateToEditCheckIn
import app.ulap.feature.logs.navigation.logsGraph
import app.ulap.feature.onboarding.navigation.OnboardingGraph
import app.ulap.feature.onboarding.navigation.accountPrivacyNoticeScreen
import app.ulap.feature.onboarding.navigation.navigateToAccountPrivacyNotice
import app.ulap.feature.onboarding.navigation.onboardingGraph
import app.ulap.feature.reports.navigation.reportsScreen

/**
 * The app's root: every screen sits above the fixed tab bar (design-spec.md §7) and the app opens
 * on Check in. Each feature adds its own screens to the NavHost; features never import each
 * other, so moving between them happens here (technical-plan.md §4).
 *
 * Until [onboardingDone], the app opens on onboarding instead, with no tab bar, so it shows only
 * once (design-spec.md §7.1). The start is picked once: when step 3 sets the flag, the root
 * moves to Check in itself rather than rebuilding the NavHost.
 */
@Composable
fun UlapAppRoot(
    onboardingDone: Boolean,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val startDestination: Any = remember { if (onboardingDone) CheckInGraph else OnboardingGraph }

    Scaffold(
        modifier = modifier,
        containerColor = UlapTheme.colors.bg,
        bottomBar = {
            if (onboardingDone) {
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
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            checkInGraph(
                navController = navController,
                onSeeMyWeek = { navController.navigateToTab(TopLevelDestination.REPORTS) },
            )
            // Edit opens Check in's Details screen, filled in, inside the Logs tab (design-spec.md §7.6).
            logsGraph(
                onEditCheckIn = navController::navigateToEditCheckIn,
                onCheckInNow = { navController.navigateToTab(TopLevelDestination.CHECK_IN) },
            ) {
                editCheckInScreen(navController)
            }
            reportsScreen()
            // The privacy notice belongs to onboarding but opens inside the Account tab too (design-spec.md §7.8).
            accountGraph(
                navController = navController,
                onOpenPrivacyNotice = navController::navigateToAccountPrivacyNotice,
            ) {
                accountPrivacyNoticeScreen(navController)
            }
            onboardingGraph(
                navController = navController,
                onFinish = {
                    navController.navigate(CheckInGraph) {
                        popUpTo(OnboardingGraph) { inclusive = true }
                    }
                },
            )
        }
    }
}
