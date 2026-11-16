package app.ulap.feature.account

import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.domain.account.AccountProfile
import app.ulap.core.domain.account.AccountState
import app.ulap.core.domain.sync.SyncStatus
import app.ulap.core.model.AppLanguage
import app.ulap.core.ui.LocalHotlineNumber
import app.ulap.feature.account.signin.SignInStep
import app.ulap.feature.account.signin.SignInUiState
import app.ulap.shared.rules.HotlineRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
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
    private val deletes = mutableListOf<Boolean>()
    private var syncNows = 0
    private var tryAgains = 0
    private val addAnswers = mutableListOf<Boolean>()

    private val profile =
        AccountProfile("Christian", "christian@example.com", true, Instant.parse("2026-10-15T02:00:00Z"))

    private fun show(
        language: AppLanguage? = AppLanguage.ENGLISH,
        mascotEnabled: Boolean = true,
        account: AccountState? = AccountState.Guest,
        googleAvailable: Boolean = true,
        delete: DeleteState = DeleteState.Idle,
        sync: SyncStatus? = null,
        hotlineNumber: String = HotlineRules.BUILT_IN_NUMBER,
    ) {
        compose.setContent {
            UlapTheme {
                CompositionLocalProvider(LocalHotlineNumber provides hotlineNumber) {
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
                        delete = delete,
                        onDeleteEverything = { deletes += it },
                        sync = sync,
                        syncActions = SyncActions(
                            onSyncNow = { syncNows++ },
                            onTryAgain = { tryAgains++ },
                            onAnswerAddCheckIns = { addAnswers += it },
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun `the hotline row is always there and opens the dialer with 1553`() {
        show(account = null)
        compose.onNodeWithText("Need to talk to someone? NCMH 1553").performScrollTo().performClick()

        // ACTION_DIAL: the dialer opens with the number typed in and never places the call.
        val intent = checkNotNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals("tel:1553", intent.dataString)
    }

    @Test
    fun `TalkBack reads the hotline row's number digit by digit`() {
        show()
        compose.onNodeWithContentDescription("Need to talk to someone? NCMH 1 5 5 3").assertHasClickAction()
    }

    @Test
    fun `a newer hotline number from meta shows and dials`() {
        show(hotlineNumber = "1554")
        compose.onNodeWithText("Need to talk to someone? NCMH 1554").performScrollTo().performClick()

        val intent = checkNotNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
        assertEquals("tel:1554", intent.dataString)
    }

    @Test
    fun `the self-care disclaimer is under the list`() {
        show(account = AccountState.SignedIn(profile))
        compose.onNodeWithText("Ulap is a self-care diary, not a medical or emergency service.")
            .performScrollTo()
            .assertExists()
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

    @Test
    fun `signed in, delete asks first and says the backup goes too`() {
        show(account = AccountState.SignedIn(profile))

        compose.onNodeWithText("Delete account and all data").performScrollTo().performClick()
        compose.onNodeWithText(
            "This deletes every check-in and note, on this phone and in your backup. It can't be undone.",
        ).assertExists()
        compose.onNodeWithText("Keep my diary").performClick()
        assertEquals(emptyList(), deletes)

        compose.onNodeWithText("Delete account and all data").performScrollTo().performClick()
        compose.onNodeWithText("Delete everything").performClick()
        assertEquals(listOf(true), deletes)
    }

    @Test
    fun `a guest or an ended session deletes only this phone`() {
        show(account = AccountState.SignInAgain)

        compose.onNodeWithText("Delete account and all data").performScrollTo().performClick()
        compose.onNodeWithText(
            "This deletes every check-in and note on this phone. It can't be undone. " +
                "Your backup stays on Ulap's server. To delete it too, sign in again first.",
        ).assertExists()
        compose.onNodeWithText("Delete from this phone").performClick()

        assertEquals(listOf(false), deletes)
    }

    @Test
    fun `a delete that didn't go through says nothing was deleted`() {
        show(account = AccountState.SignedIn(profile), delete = DeleteState.Failed(DeleteMessage.OFFLINE))
        compose.onNodeWithText("You're offline, so nothing was deleted. Try again when you're online.")
            .performScrollTo()
            .assertExists()
    }

    @Test
    fun `while deleting, the row says so and doesn't open the dialog again`() {
        show(account = AccountState.SignedIn(profile), delete = DeleteState.Deleting)

        compose.onNodeWithText("Deleting…").performScrollTo().performClick()

        compose.onNodeWithText("Delete everything").assertDoesNotExist()
    }

    @Test
    fun `signed in and verified, the sync row shows when it last backed up, and Sync now asks for a sync`() {
        show(account = AccountState.SignedIn(profile), sync = SyncStatus())

        compose.onNodeWithText("Not backed up yet").assertExists()
        compose.onNodeWithText("Sync now").performClick()
        assertEquals(1, syncNows)
    }

    @Test
    fun `no sync row for a guest or before the email is verified`() {
        show(account = AccountState.SignedIn(profile.copy(emailVerified = false)), sync = SyncStatus())

        compose.onNodeWithText("Sync now").assertDoesNotExist()
    }

    @Test
    fun `offline with check-ins waiting, it says they're saved here and will back up`() {
        show(account = AccountState.SignedIn(profile), sync = SyncStatus(waitingCount = 2, online = false))

        compose.onNodeWithText("Saved on this phone. Will back up when you're online.").assertExists()
    }

    @Test
    fun `check-ins that stopped trying are counted, never their tries, with Try again`() {
        show(account = AccountState.SignedIn(profile), sync = SyncStatus(stuckCount = 1))
        compose.onNodeWithText("1 check-in couldn't back up yet. It's still saved on this phone.").assertExists()

        compose.onNodeWithText("Try again").performClick()
        assertEquals(1, tryAgains)
    }

    @Test
    fun `the add question takes Add them as an answer`() {
        show(account = AccountState.SignedIn(profile), sync = SyncStatus(askToAddCheckIns = true))

        compose.onNodeWithText("Add this phone's check-ins to your account?").assertExists()
        compose.onNode(hasText("Add them") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(listOf(true), addAnswers)
    }

    @Test
    fun `don't add is an answer of its own`() {
        show(account = AccountState.SignedIn(profile), sync = SyncStatus(askToAddCheckIns = true))

        compose.onNode(hasText("Don't add") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(listOf(false), addAnswers)
    }
}
