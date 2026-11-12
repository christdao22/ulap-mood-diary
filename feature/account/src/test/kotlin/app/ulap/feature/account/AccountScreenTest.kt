package app.ulap.feature.account

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.domain.account.AccountProfile
import app.ulap.core.domain.account.AccountState
import app.ulap.core.model.AppLanguage
import app.ulap.feature.account.signin.SignInStep
import app.ulap.feature.account.signin.SignInUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class AccountScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var languageOpens = 0
    private var privacyOpens = 0
    private val mascotChanges = mutableListOf<Boolean>()

    private val signInOpens = mutableListOf<SignInStep>()
    private var googleTaps = 0
    private var signOuts = 0
    private var resends = 0

    private val profile =
        AccountProfile("Christian", "christian@example.com", true, Instant.parse("2026-10-15T02:00:00Z"))

    private fun show(
        language: AppLanguage? = AppLanguage.ENGLISH,
        mascotEnabled: Boolean = true,
        account: AccountState? = AccountState.Guest,
        googleAvailable: Boolean = true,
    ) {
        compose.setContent {
            UlapTheme {
                AccountContent(
                    language = language,
                    mascotEnabled = mascotEnabled,
                    onMascotEnabledChange = { mascotChanges += it },
                    onOpenLanguage = { languageOpens++ },
                    onOpenPrivacyNotice = { privacyOpens++ },
                    account = account,
                    signIn = SignInUiState(googleAvailable = googleAvailable),
                    onContinueWithGoogle = { googleTaps++ },
                    onOpenSignIn = { signInOpens += it },
                    onResendVerification = { resends++ },
                    onSignOut = { signOuts++ },
                )
            }
        }
    }

    @Test
    fun `the Language row names the chosen language in itself`() {
        show(AppLanguage.BISAYA)
        // One button that TalkBack reads as "Language, Bisaya".
        compose.onNode(hasText("Language") and hasText("Bisaya") and hasClickAction()).assertExists()
    }

    @Test
    fun `the Language row opens the language screen`() {
        show()
        compose.onNodeWithText("Language").performClick()
        assertEquals(1, languageOpens)
    }

    @Test
    fun `the Privacy notice row opens the privacy notice`() {
        show()
        compose.onNodeWithText("Privacy notice").performClick()
        assertEquals(1, privacyOpens)
    }

    @Test
    fun `the Ulap row is one switch that shows whether Ulap is on`() {
        show(mascotEnabled = true)
        // TalkBack reads "Show Ulap the cloud, switch, on" from the row itself.
        compose.onNodeWithText("Show Ulap the cloud")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assertIsOn()
    }

    @Test
    fun `tapping the Ulap row asks to turn Ulap off`() {
        show(mascotEnabled = true)
        compose.onNodeWithText("Show Ulap the cloud").performClick()
        assertEquals(listOf(false), mascotChanges)
    }

    @Test
    fun `tapping the Ulap row while off asks to turn Ulap back on`() {
        show(mascotEnabled = false)
        compose.onNodeWithText("Show Ulap the cloud").assertIsOff().performClick()
        assertEquals(listOf(true), mascotChanges)
    }

    @Test
    @Config(qualifiers = "b+ceb-w360dp-h800dp")
    fun `the list speaks Bisaya once Bisaya is the app language`() {
        show(AppLanguage.BISAYA)
        compose.onNodeWithText("Pinulongan").assertExists()
    }

    @Test
    fun `a guest is offered backup with Google or email`() {
        show(account = AccountState.Guest)
        compose.onNodeWithText("Back up and sync").assertExists()
        compose.onNodeWithText("Keep your diary safe across phones").assertExists()

        compose.onNodeWithText("Continue with Google").performClick()
        compose.onNodeWithText("Use email instead").performClick()

        assertEquals(1, googleTaps)
        assertEquals(listOf(SignInStep.CREATE_ACCOUNT), signInOpens)
        compose.onNodeWithText("Sign out").assertDoesNotExist()
    }

    @Test
    fun `without a Web client ID only email is offered`() {
        show(account = AccountState.Guest, googleAvailable = false)
        compose.onNodeWithText("Continue with Google").assertDoesNotExist()
        compose.onNodeWithText("Use email instead").assertExists()
    }

    @Test
    fun `no card shows until the saved session is read`() {
        show(account = null)
        compose.onNodeWithText("Back up and sync").assertDoesNotExist()
        compose.onNodeWithText("Language").assertExists()
    }

    @Test
    fun `an ended session says the check-ins are still here and opens the sheet`() {
        show(account = AccountState.SignInAgain)
        compose.onNodeWithText("Your check-ins are still saved on this phone.").assertExists()

        compose.onNodeWithText("Sign in").performClick()

        assertEquals(listOf(SignInStep.CHOOSE), signInOpens)
    }

    @Test
    fun `signed in shows the profile card`() {
        show(account = AccountState.SignedIn(profile))
        compose.onNodeWithText("Christian").assertExists()
        compose.onNodeWithText("Checking in since October 2026").assertExists()
        compose.onNodeWithText("Send the link again").assertDoesNotExist()
    }

    @Test
    fun `an unverified email says backup waits for the link`() {
        show(account = AccountState.SignedIn(profile.copy(emailVerified = false)))
        compose.onNodeWithText(
            "We sent a link to christian@example.com. Backup starts once you open it.",
        ).assertExists()

        compose.onNodeWithText("Send the link again").performClick()

        assertEquals(1, resends)
    }

    @Test
    fun `sign out asks first, and staying signed in does nothing`() {
        show(account = AccountState.SignedIn(profile))

        compose.onNodeWithText("Sign out").performClick()
        compose.onNodeWithText("Your check-ins stay on this phone. Sign in again any time to keep backing up.")
            .assertExists()
        compose.onNodeWithText("Stay signed in").performClick()
        assertEquals(0, signOuts)

        compose.onNodeWithText("Sign out").performClick()
        compose.onNode(hasText("Sign out") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(1, signOuts)
    }
}
