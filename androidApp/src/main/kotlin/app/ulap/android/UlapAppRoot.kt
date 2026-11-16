package app.ulap.android

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.util.Consumer
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.ulap.android.navigation.TopLevelDestination
import app.ulap.android.navigation.navigateToTab
import app.ulap.core.designsystem.component.UlapTabBar
import app.ulap.core.designsystem.component.UlapTabItem
import app.ulap.core.designsystem.motion.rememberScreenTransitions
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
 * moves to Check in itself rather than rebuilding the NavHost. When the flag goes back to false
 * ("Delete account and all data" erases the settings, api-spec.md §7.3), the root clears every
 * screen and opens onboarding, as on a fresh install.
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
    // Fade plus an 8dp slide up, or a fade only with "Remove animations" on (design-spec.md §9).
    val transitions = rememberScreenTransitions()
    BackToOnboardingWhenErased(onboardingDone, navController)
    OpenLinksWhileRunning(navController)

    Scaffold(
        modifier = modifier,
        containerColor = UlapTheme.colors.bg,
        bottomBar = { if (onboardingDone) TabBar(currentDestination, navController) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
            enterTransition = { transitions.enter() },
            exitTransition = { transitions.exit() },
            popEnterTransition = { transitions.enter() },
            popExitTransition = { transitions.exit() },
        ) {
            checkInGraph(
                navController = navController,
                transitions = transitions,
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

/** The fixed tab bar: Check in, Logs, Reports, Account (design-spec.md §6.10). */
@Composable
private fun TabBar(currentDestination: NavDestination?, navController: NavHostController) {
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

/**
 * "Delete account and all data" erases the settings, so [onboardingDone] goes from true back to
 * false (api-spec.md §7.3). Then every screen is cleared and onboarding opens, as on a fresh
 * install. A first run, which starts on onboarding, isn't a change and does nothing.
 */
@Composable
private fun BackToOnboardingWhenErased(onboardingDone: Boolean, navController: NavHostController) {
    var wasOnboardingDone by remember { mutableStateOf(onboardingDone) }
    LaunchedEffect(onboardingDone) {
        if (wasOnboardingDone && !onboardingDone) {
            navController.navigate(OnboardingGraph) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
        wasOnboardingDone = onboardingDone
    }
}

/**
 * The email links (`/verify`, `/reset`, api-spec.md §6.5, §6.8) open the app. On a cold start the
 * NavHost reads the Activity's intent itself. When the app is already open, the link arrives as a
 * new intent (MainActivity is `singleTop`) and is handed to navigation here.
 */
@Composable
private fun OpenLinksWhileRunning(navController: NavHostController) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    DisposableEffect(activity, navController) {
        val listener = Consumer<Intent> { intent -> navController.handleDeepLink(intent) }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
    }
}
