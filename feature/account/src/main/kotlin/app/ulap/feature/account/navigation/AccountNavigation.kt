package app.ulap.feature.account.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import app.ulap.feature.account.AccountScreen
import app.ulap.feature.account.language.LanguageScreen
import kotlinx.serialization.Serializable

/** The Account tab, with every screen opened from it, so the tab bar treats them as one tab. */
@Serializable
data object AccountGraph

/** The Account tab's start screen (design-spec.md §7.8). */
@Serializable
data object AccountRoute

/** Account · Language: change the language chosen in onboarding (technical-plan.md §11). */
@Serializable
data object LanguageRoute

/**
 * Adds the Account tab: for now its settings list, with Language and the privacy notice. Phase 6
 * adds sign-in, sync and deleting data (design-spec.md §7.8).
 *
 * @param onOpenPrivacyNotice The Privacy notice row. Onboarding owns that screen.
 * @param nestedScreens Screens from other features that open inside this tab, such as the privacy
 *   notice. Features never import each other, so the app root passes them in.
 */
fun NavGraphBuilder.accountGraph(
    navController: NavController,
    onOpenPrivacyNotice: () -> Unit,
    nestedScreens: NavGraphBuilder.() -> Unit,
) {
    navigation<AccountGraph>(startDestination = AccountRoute) {
        composable<AccountRoute> { entry ->
            AccountScreen(
                onOpenLanguage = {
                    entry.ifResumed { navController.navigate(LanguageRoute) { launchSingleTop = true } }
                },
                onOpenPrivacyNotice = { entry.ifResumed(onOpenPrivacyNotice) },
            )
        }
        composable<LanguageRoute> { entry ->
            LanguageScreen(onBack = { entry.ifResumed { navController.popBackStack() } })
        }
        nestedScreens()
    }
}

/** Runs [action] only while this screen is on top, so a quick double tap can't act twice. */
private inline fun NavBackStackEntry.ifResumed(action: () -> Unit) {
    if (lifecycle.currentState == Lifecycle.State.RESUMED) action()
}
