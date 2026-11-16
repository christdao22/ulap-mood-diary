package app.ulap.feature.checkin.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import app.ulap.core.designsystem.motion.ScreenTransitions
import app.ulap.feature.checkin.after.AfterScreen
import app.ulap.feature.checkin.details.DetailsScreen
import app.ulap.feature.checkin.pickmood.PickMoodScreen
import app.ulap.feature.checkin.response.ResponseScreen
import app.ulap.shared.catalog.MoodCatalog
import app.ulap.shared.model.Zone
import kotlinx.serialization.Serializable

/** The Check in tab: Pick mood, then Details, Response and the after-check (design-spec.md §7.2–§7.5). */
@Serializable
data object CheckInGraph

/** Check in · Pick a mood, the tab's first screen. */
@Serializable
data object PickMoodRoute

/** Check in · Details for the mood with this code (design-spec.md §7.3). */
@Serializable
data class DetailsRoute(val moodCode: String)

/**
 * Details again, filled in from the saved check-in with this ID, to change it (design-spec.md
 * §7.6). It sits in the Logs tab, so the app root places it with [editCheckInScreen].
 */
@Serializable
data class EditCheckInRoute(val checkInId: String)

/**
 * Check in · Response to the saved check-in with this ID (design-spec.md §7.4). [moodCode] only
 * picks the transition before the check-in is read: Red and Blue come in softer (§9).
 */
@Serializable
data class ResponseRoute(val checkInId: String, val moodCode: String)

/** Check in · After, comparing the logged stress with "How stressed do you feel now?" (design-spec.md §7.5). */
@Serializable
data class AfterRoute(val checkInId: String, val moodCode: String)

/** Red and Blue responses use the slower, softer transition (design-spec.md §9). */
private fun isSoft(moodCode: String): Boolean = MoodCatalog.byCode(moodCode)?.zone.let {
    it == Zone.RED ||
        it == Zone.BLUE
}

/**
 * Adds the check-in screens as one nested graph, so the tab bar can treat them as one tab. The
 * screens all belong to this feature, so it moves between them itself. [onSeeMyWeek] opens the
 * Reports tab, which belongs to another feature, so the app root does it. Response and After use
 * [transitions] softly for Red and Blue moods; the rest use the NavHost's.
 */
fun NavGraphBuilder.checkInGraph(
    navController: NavController,
    transitions: ScreenTransitions,
    onSeeMyWeek: () -> Unit,
) {
    navigation<CheckInGraph>(startDestination = PickMoodRoute) {
        composable<PickMoodRoute> { entry ->
            PickMoodScreen(
                onMoodClick = { moodCode -> entry.ifResumed { navController.navigate(DetailsRoute(moodCode)) } },
            )
        }
        composable<DetailsRoute> { entry ->
            DetailsScreen(
                onBack = { entry.ifResumed { navController.popBackStack() } },
                // Details leaves the back stack: the check-in is saved, so Back from Response
                // goes to Pick mood instead of reopening the form.
                onCheckInSave = { checkInId ->
                    entry.ifResumed {
                        navController.navigate(ResponseRoute(checkInId, entry.toRoute<DetailsRoute>().moodCode)) {
                            popUpTo<DetailsRoute> { inclusive = true }
                        }
                    }
                },
            )
        }
        composable<ResponseRoute>(
            enterTransition = { transitions.enter(soft = isSoft(targetRoute<ResponseRoute>().moodCode)) },
            exitTransition = { transitions.exit(soft = isSoft(initialRoute<ResponseRoute>().moodCode)) },
        ) { entry ->
            val finish = { entry.ifResumed { navController.popBackStack(PickMoodRoute, inclusive = false) } }
            ResponseScreen(
                onDone = finish,
                // Response leaves the back stack: the stress check is saved, so Back from After
                // goes to Pick mood instead of asking again.
                onStressCheckSave = {
                    entry.ifResumed {
                        val route = entry.toRoute<ResponseRoute>()
                        navController.navigate(AfterRoute(route.checkInId, route.moodCode)) {
                            popUpTo<ResponseRoute> { inclusive = true }
                        }
                    }
                },
            )
        }
        composable<AfterRoute>(
            enterTransition = { transitions.enter(soft = isSoft(targetRoute<AfterRoute>().moodCode)) },
            exitTransition = { transitions.exit(soft = isSoft(initialRoute<AfterRoute>().moodCode)) },
        ) { entry ->
            val finish = { entry.ifResumed { navController.popBackStack(PickMoodRoute, inclusive = false) } }
            AfterScreen(
                onDone = finish,
                onSeeMyWeek = {
                    entry.ifResumed {
                        // End the check-in first, so coming back to the Check in tab opens Pick
                        // mood, not this screen.
                        navController.popBackStack(PickMoodRoute, inclusive = false)
                        onSeeMyWeek()
                    }
                },
            )
        }
    }
}

/**
 * Adds Details in edit mode. Save and Back both return to the screen that opened it. If the
 * check-in is gone (deleted on another phone and synced), it closes on its own.
 */
fun NavGraphBuilder.editCheckInScreen(navController: NavController) {
    composable<EditCheckInRoute> { entry ->
        val close = { entry.ifResumed { navController.popBackStack() } }
        DetailsScreen(
            onBack = close,
            onCheckInSave = { close() },
            // Not tied to a tap, so it may arrive before the screen is resumed. Popping this
            // route by type is safe to repeat: once it's gone there is nothing left to pop.
            onCheckInMissing = { navController.popBackStack<EditCheckInRoute>(inclusive = true) },
        )
    }
}

/** Opens Details filled in from the check-in with this ID. */
fun NavController.navigateToEditCheckIn(checkInId: String) = navigate(EditCheckInRoute(checkInId))

/**
 * Runs [action] only while this screen is the one on top. A quick double tap would otherwise
 * open Details twice, or pop past Pick mood and leave the tab empty.
 */
private inline fun NavBackStackEntry.ifResumed(action: () -> Unit) {
    if (lifecycle.currentState == Lifecycle.State.RESUMED) action()
}

private inline fun <reified T : Any> AnimatedContentTransitionScope<NavBackStackEntry>.targetRoute(): T =
    targetState.toRoute<T>()

private inline fun <reified T : Any> AnimatedContentTransitionScope<NavBackStackEntry>.initialRoute(): T =
    initialState.toRoute<T>()
