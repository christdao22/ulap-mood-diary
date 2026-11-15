package app.ulap.feature.account.navigation

import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navDeepLink
import app.ulap.feature.account.AccountScreen
import app.ulap.feature.account.language.LanguageScreen
import app.ulap.feature.account.link.ResetPasswordScreen
import app.ulap.feature.account.link.VerifyEmailScreen
import app.ulap.feature.account.signin.SignInStep
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
 * The verification email's link, `https://ulap.app/verify?token=…` (api-spec.md §6.5). [token]
 * has a default only so the link carries it as a query parameter; a link without one reads as spent.
 */
@Serializable
data class VerifyEmailRoute(val token: String = "")

/** The reset email's link, `https://ulap.app/reset?token=…` (api-spec.md §6.7, §6.8). */
@Serializable
data class ResetPasswordRoute(val token: String = "")

/**
 * Where the email links point. The server builds them from `APP_LINK_BASE`, and the manifest's
 * App Link filter names the same host and paths.
 */
private const val APP_LINK_BASE = "https://ulap.app"

/** Set on the Account screen's entry to open the sign-in sheet at that step when it shows. */
internal const val OPEN_SIGN_IN_STEP = "openSignInStep"

/**
 * Adds the Account tab: the settings list with sign-in, Language and the privacy notice, and the
 * two screens the account emails' links open (design-spec.md §7.8).
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
            val openSignInStep by entry.savedStateHandle
                .getStateFlow<String?>(OPEN_SIGN_IN_STEP, null)
                .collectAsStateWithLifecycle()
            AccountScreen(
                onOpenLanguage = {
                    entry.ifResumed { navController.navigate(LanguageRoute) { launchSingleTop = true } }
                },
                onOpenPrivacyNotice = { entry.ifResumed(onOpenPrivacyNotice) },
                openSignInStep = openSignInStep?.let(SignInStep::valueOf),
                onClearSignInStep = { entry.savedStateHandle[OPEN_SIGN_IN_STEP] = null },
            )
        }
        composable<LanguageRoute> { entry ->
            LanguageScreen(onBack = { entry.ifResumed { navController.popBackStack() } })
        }
        composable<VerifyEmailRoute>(
            deepLinks = listOf(navDeepLink<VerifyEmailRoute>(basePath = "$APP_LINK_BASE/verify")),
        ) { entry ->
            VerifyEmailScreen(
                onBack = { entry.ifResumed { navController.backFromLink() } },
                onGoToAccount = { entry.ifResumed { navController.backToAccount(openSignIn = null) } },
            )
        }
        composable<ResetPasswordRoute>(
            deepLinks = listOf(navDeepLink<ResetPasswordRoute>(basePath = "$APP_LINK_BASE/reset")),
        ) { entry ->
            ResetPasswordScreen(
                onBack = { entry.ifResumed { navController.backFromLink() } },
                onSignIn = { entry.ifResumed { navController.backToAccount(openSignIn = SignInStep.SIGN_IN) } },
                onAskForNewLink = {
                    entry.ifResumed { navController.backToAccount(openSignIn = SignInStep.FORGOT_PASSWORD) }
                },
            )
        }
        nestedScreens()
    }
}

/** Back from a link's screen: to whatever was open before, or to Account when it opened the app. */
private fun NavController.backFromLink() {
    if (!popBackStack()) backToAccount(openSignIn = null)
}

/**
 * Closes the link's screen and shows Account, opening the sign-in sheet at [openSignIn] if given.
 * A link can open the app on any screen, so Account may not be on the back stack yet.
 */
private fun NavController.backToAccount(openSignIn: SignInStep?) {
    if (!popBackStack<AccountRoute>(inclusive = false)) {
        navigate(AccountRoute) {
            popUpTo(graph.findStartDestination().id)
            launchSingleTop = true
        }
    }
    if (openSignIn != null) getBackStackEntry<AccountRoute>().savedStateHandle[OPEN_SIGN_IN_STEP] = openSignIn.name
}

/** Runs [action] only while this screen is on top, so a quick double tap can't act twice. */
private inline fun NavBackStackEntry.ifResumed(action: () -> Unit) {
    if (lifecycle.currentState == Lifecycle.State.RESUMED) action()
}
