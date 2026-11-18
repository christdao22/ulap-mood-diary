package app.ulap.feature.account

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.ulap.core.composetesting.HiltTestActivity
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.domain.account.AccountProfile
import app.ulap.core.domain.account.AccountRepository
import app.ulap.core.domain.account.AccountState
import app.ulap.core.domain.account.AuthProblem
import app.ulap.core.domain.account.GoogleSignInConfig
import app.ulap.core.domain.settings.SettingsRepository
import app.ulap.core.domain.sync.SyncRepository
import app.ulap.core.domain.sync.SyncStatus
import app.ulap.core.model.AppLanguage
import app.ulap.core.model.UserSettings
import app.ulap.core.testing.FakeAccountRepository
import app.ulap.core.testing.FakeSettingsRepository
import app.ulap.core.testing.FakeSyncRepository
import app.ulap.feature.account.navigation.AccountGraph
import app.ulap.feature.account.navigation.accountGraph
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Stands in for the Check in tab, which the app root starts on. Features never import each other. */
@Serializable
data object StandInCheckInRoute

/** Stands in for onboarding's privacy notice, which the app root nests in the Account tab. */
@Serializable
data object StandInPrivacyRoute

/**
 * The Account tab end to end (design-spec.md §7.8, §7.9, technical-plan.md §15 "UI"): the real
 * screens, ViewModels and tab graph, reading the saved session, sync status and settings the
 * repositories hold, the way they read DataStore and Room. Signing in, signing out and deleting
 * change what the tab shows; Language and the mascot switch change settings; the reset and verify
 * links open from their `https://ulap.app` addresses and come back to Account.
 *
 * Google's picker needs Play services, so sign-in here goes through email. SignInViewModelTest
 * covers Google's answers.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w360dp-h3000dp")
class AccountFlowTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<HiltTestActivity>()

    private val profile = AccountProfile(
        displayName = "Ana",
        email = "ana@example.com",
        emailVerified = true,
        memberSince = Instant.parse("2026-10-01T02:00:00Z"),
    )
    private val accounts = FakeAccountRepository().also { it.profile = profile }
    private val settings = FakeSettingsRepository(UserSettings(language = AppLanguage.ENGLISH))
    private val syncs = FakeSyncRepository()

    @BindValue
    @JvmField
    val accountRepository: AccountRepository = accounts

    @BindValue
    @JvmField
    val settingsRepository: SettingsRepository = settings

    @BindValue
    @JvmField
    val syncRepository: SyncRepository = syncs

    // A blank client ID hides the Google buttons, as in a build without one.
    @BindValue
    @JvmField
    val googleSignInConfig: GoogleSignInConfig = GoogleSignInConfig(webClientId = "")

    private lateinit var navController: NavHostController

    /** The app root's shape: tabs side by side, with the Account tab's graph nested in it. */
    private fun openApp(startOnAccount: Boolean = true) = compose.setContent {
        UlapTheme {
            navController = rememberNavController()
            NavHost(
                navController = navController,
                startDestination = if (startOnAccount) AccountGraph else StandInCheckInRoute,
            ) {
                composable<StandInCheckInRoute> { Text("Check in tab") }
                accountGraph(
                    navController = navController,
                    onOpenPrivacyNotice = { navController.navigate(StandInPrivacyRoute) },
                    nestedScreens = { composable<StandInPrivacyRoute> { Text("Privacy notice screen") } },
                )
            }
        }
    }

    private fun signedIn() {
        accounts.state.value = AccountState.SignedIn(profile)
    }

    /** What the phone does when an email link opens it: navigation matches the address to its screen. */
    private fun openLink(link: String) = compose.runOnUiThread { navController.navigate(link.toUri()) }

    private fun pressBack() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    private fun waitForText(text: String) = compose.waitUntilAtLeastOneExists(hasText(text), TIMEOUT)

    private fun tapInDialog(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isDialog())).performClick()

    /** The profile card reads as one line: the name and "Checking in since …". */
    private val profileCard: SemanticsMatcher = hasText("Ana") and hasText("Checking in since", substring = true)

    @Test
    fun `a guest creates an account by email, and the tab shows the profile and the sync row`() {
        openApp()
        waitForText("Back up and sync")

        compose.onNodeWithText("Use email instead").performClick()
        compose.onNodeWithText("Create an account").assertExists()
        compose.onNodeWithText("Email").performTextInput("ana@example.com")
        compose.onNodeWithText("Password").performTextInput("Bag-ong-Password-2026")
        compose.onNodeWithText("Create account").performClick()

        // The sheet closes on its own, and the card the repository now reports takes its place.
        compose.waitUntilAtLeastOneExists(profileCard, TIMEOUT)
        compose.onNodeWithText("Create an account").assertDoesNotExist()
        compose.onNodeWithText("Back up and sync").assertDoesNotExist()
        compose.onNodeWithText("Not backed up yet", substring = true).assertExists()
        assertEquals(listOf("create:ana@example.com"), accounts.calls)
    }

    @Test
    fun `Sign out asks first, Stay signed in keeps the account, and Sign out shows the guest card`() {
        signedIn()
        openApp()
        compose.waitUntilAtLeastOneExists(profileCard, TIMEOUT)

        compose.onNode(hasText("Sign out") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Sign out?").assertExists()
        tapInDialog("Stay signed in")
        compose.onNodeWithText("Sign out?").assertDoesNotExist()
        compose.onNode(profileCard).assertExists()
        assertEquals(emptyList(), accounts.calls)

        compose.onNode(hasText("Sign out") and hasClickAction()).performScrollTo().performClick()
        tapInDialog("Sign out")

        // Check-ins stay on the phone; only the card changes (design-spec.md §7.8).
        waitForText("Back up and sync")
        compose.onNode(profileCard).assertDoesNotExist()
        assertEquals(listOf("signOut"), accounts.calls)
    }

    @Test
    fun `deleting offline keeps everything and says so, and deleting online leaves a guest`() {
        signedIn()
        accounts.nextProblem = AuthProblem.OFFLINE
        openApp()
        compose.waitUntilAtLeastOneExists(profileCard, TIMEOUT)

        compose.onNodeWithText("Delete account and all data").performScrollTo().performClick()
        compose.onNodeWithText("Delete your account and all your data?").assertExists()
        tapInDialog("Keep my diary")
        assertEquals(emptyList(), accounts.calls)

        // The server deletes first, so offline nothing is deleted anywhere (api-spec.md §7.3).
        compose.onNodeWithText("Delete account and all data").performScrollTo().performClick()
        tapInDialog("Delete everything")
        waitForText("You're offline, so nothing was deleted. Try again when you're online.")
        compose.onNode(profileCard).assertExists()

        compose.onNodeWithText("Delete account and all data").performScrollTo().performClick()
        tapInDialog("Delete everything")
        waitForText("Back up and sync")
        compose.onNodeWithText("You're offline", substring = true).assertDoesNotExist()
        assertEquals(listOf("delete", "delete"), accounts.calls)
    }

    @Test
    fun `Language, the mascot switch and the privacy notice change settings and open inside the tab`() {
        openApp()
        waitForText("Back up and sync")

        compose.onNodeWithText("Show Ulap the cloud").performClick()
        compose.waitUntil(TIMEOUT) { !settings.settings.value.mascotEnabled }

        compose.onNode(hasText("Language") and hasText("English")).performClick()
        waitForText("Maligayang pagdating")
        compose.onNodeWithText("Filipino").performClick()
        compose.waitUntil(TIMEOUT) { settings.settings.value.language == AppLanguage.FILIPINO }
        compose.onNodeWithContentDescription("Back").performClick()

        // The row shows the new choice. The app root, not this test, redraws the app in Filipino.
        compose.waitUntilAtLeastOneExists(hasText("Language") and hasText("Filipino"), TIMEOUT)

        compose.onNodeWithText("Privacy notice").performScrollTo().performClick()
        compose.onNodeWithText("Privacy notice screen").assertExists()
        pressBack()
        waitForText("Back up and sync")
    }

    @Test
    fun `the reset link sets a new password, and Sign in opens the sheet on Account`() {
        openApp()
        waitForText("Back up and sync")

        openLink("https://ulap.app/reset?token=reset-token-1")
        waitForText("Set a new password")
        compose.onNodeWithText("New password").performTextInput("Bag-ong-Password-2026")
        compose.onNodeWithText("Save new password").performClick()
        waitForText("Your new password is set")

        compose.onNode(hasText("Sign in") and hasClickAction()).performClick()

        // Back on Account, with the sheet already at the email sign-in step.
        waitForText("Sign in with email")
        compose.onNodeWithText("Set a new password").assertDoesNotExist()
        assertEquals(listOf("resetPassword:reset-token-1"), accounts.calls)
    }

    @Test
    fun `a spent reset link offers a new one, which opens Forgot password on Account`() {
        accounts.nextProblem = AuthProblem.LINK_USED_OR_EXPIRED
        openApp()
        waitForText("Back up and sync")

        openLink("https://ulap.app/reset?token=old-token")
        compose.onNodeWithText("New password").performTextInput("Bag-ong-Password-2026")
        compose.onNodeWithText("Save new password").performClick()
        waitForText("This link doesn't work anymore")

        compose.onNodeWithText("Send a new link").performClick()
        waitForText("Reset your password")
    }

    @Test
    fun `the verify link opened from another tab confirms the email, and Go to Account shows Account`() {
        signedIn()
        openApp(startOnAccount = false)
        waitForText("Check in tab")

        // Account isn't on the back stack yet, so Go to Account has to put it there.
        openLink("https://ulap.app/verify?token=verify-token-1")
        waitForText("Your email is confirmed")
        compose.onNodeWithText("Go to Account").performClick()

        compose.waitUntilAtLeastOneExists(profileCard, TIMEOUT)
        assertEquals(listOf("verify:verify-token-1"), accounts.calls)

        // Back from Account goes to the start tab, not to the spent link.
        pressBack()
        waitForText("Check in tab")
        compose.onNodeWithText("Your email is confirmed").assertDoesNotExist()
    }

    @Test
    fun `signed in, Sync now and the add check-ins question reach the sync repository`() {
        signedIn()
        syncs.status.value = SyncStatus(askToAddCheckIns = true)
        openApp()

        waitForText("Add this phone's check-ins to your account?")
        tapInDialog("Add them")
        compose.waitUntilDoesNotExist(hasText("Add this phone's check-ins to your account?"), TIMEOUT)

        compose.onNodeWithText("Sync now").performClick()
        compose.waitUntil(TIMEOUT) { syncs.calls.size == 2 }
        assertEquals(listOf("add:true", "syncNow"), syncs.calls)
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
