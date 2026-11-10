package app.ulap.feature.onboarding.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import app.ulap.feature.onboarding.language.ChooseLanguageScreen
import app.ulap.feature.onboarding.meet.MeetUlapScreen
import app.ulap.feature.onboarding.privacy.PrivacyNoticeScreen
import app.ulap.feature.onboarding.privacy.YourPrivacyScreen
import kotlinx.serialization.Serializable

/** The first-run steps (design-spec.md §7.1): language, meet Ulap, privacy. */
@Serializable
data object OnboardingGraph

/** Onboarding step 1: choose a language. */
@Serializable
data object ChooseLanguageRoute

/** Onboarding step 2: meet Ulap. */
@Serializable
data object MeetUlapRoute

/** Onboarding step 3: your privacy, then "Start my first check-in". */
@Serializable
data object YourPrivacyRoute

/** The privacy notice, opened from onboarding step 3. */
@Serializable
data object PrivacyNoticeRoute

/**
 * The same privacy notice, opened from Account (design-spec.md §7.8). It has its own route so it
 * can sit inside the Account tab, which then stays selected while it's open. The app root places
 * it with [accountPrivacyNoticeScreen].
 */
@Serializable
data object AccountPrivacyNoticeRoute

/**
 * Adds the three onboarding steps and their privacy notice as one nested graph. The steps belong
 * to this feature, so it moves between them itself. [onFinish] runs after step 3 has marked
 * onboarding done; the app root then opens Check in and drops onboarding from the back stack.
 */
fun NavGraphBuilder.onboardingGraph(navController: NavController, onFinish: () -> Unit) {
    navigation<OnboardingGraph>(startDestination = ChooseLanguageRoute) {
        composable<ChooseLanguageRoute> {
            ChooseLanguageScreen(onContinue = { navController.navigate(MeetUlapRoute) { launchSingleTop = true } })
        }
        composable<MeetUlapRoute> {
            MeetUlapScreen(onContinue = { navController.navigate(YourPrivacyRoute) { launchSingleTop = true } })
        }
        composable<YourPrivacyRoute> {
            YourPrivacyScreen(
                onStart = onFinish,
                onReadPrivacyNotice = { navController.navigate(PrivacyNoticeRoute) { launchSingleTop = true } },
            )
        }
        privacyNotice<PrivacyNoticeRoute>(navController)
    }
}

/** Adds the privacy notice that Account opens. Place it inside the Account tab's graph. */
fun NavGraphBuilder.accountPrivacyNoticeScreen(navController: NavController) {
    privacyNotice<AccountPrivacyNoticeRoute>(navController)
}

/** Opens the privacy notice from Account. */
fun NavController.navigateToAccountPrivacyNotice() = navigate(AccountPrivacyNoticeRoute) { launchSingleTop = true }

/** The privacy notice under route [T]. Back returns to whichever screen opened it. */
private inline fun <reified T : Any> NavGraphBuilder.privacyNotice(navController: NavController) {
    composable<T> { entry ->
        // Only while this screen is on top, so a quick double tap can't pop the screen below too.
        PrivacyNoticeScreen(
            onBack = {
                if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) navController.popBackStack()
            },
        )
    }
}
