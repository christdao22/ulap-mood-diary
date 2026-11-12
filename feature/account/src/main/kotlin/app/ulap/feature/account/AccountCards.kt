package app.ulap.feature.account

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.ulap.core.designsystem.component.UlapFieldNotice
import app.ulap.core.designsystem.component.UlapPrimaryButton
import app.ulap.core.designsystem.component.UlapTextLink
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.domain.account.AccountProfile
import app.ulap.core.ui.R
import app.ulap.feature.account.signin.SignInButtons
import app.ulap.feature.account.signin.SignInMessage
import app.ulap.feature.account.signin.res
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Instant

/**
 * A guest's card: "Back up and sync: Keep your diary safe across phones" with the two ways to
 * sign in (design-spec.md §7.8). Signing in is an offer, never a gate.
 */
@Composable
internal fun BackupCard(
    googleAvailable: Boolean,
    busy: Boolean,
    googleError: SignInMessage?,
    onContinueWithGoogle: () -> Unit,
    onUseEmail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AccountCard(modifier) {
        CardTitle(stringResource(R.string.account_backup_title))
        CardBody(stringResource(R.string.account_backup_body))
        SignInButtons(
            googleAvailable = googleAvailable,
            busy = busy,
            googleError = googleError,
            onContinueWithGoogle = onContinueWithGoogle,
            onUseEmail = onUseEmail,
            modifier = Modifier.padding(top = UlapSpacing.Xs),
        )
    }
}

/**
 * The server ended the session: the check-ins are still here, and signing in again picks up
 * syncing (api-spec.md §3, Client flow, step 3). Opens the sign-in sheet.
 */
@Composable
internal fun SignInAgainCard(onSignIn: () -> Unit, modifier: Modifier = Modifier) {
    AccountCard(modifier) {
        CardTitle(stringResource(R.string.account_sign_in_again_title))
        CardBody(stringResource(R.string.account_sign_in_again_body))
        UlapPrimaryButton(
            text = stringResource(R.string.account_sign_in_again_button),
            onClick = onSignIn,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = UlapSpacing.Xs),
        )
    }
}

/**
 * The signed-in profile card: avatar initial, name and "Checking in since October 2026"
 * (design-spec.md §7.8). Until the email is verified, a quiet note says backup waits for it.
 */
@Composable
internal fun ProfileCard(
    profile: AccountProfile,
    resend: ResendState,
    onResendVerification: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = profile.displayName?.takeIf { it.isNotBlank() } ?: profile.email
    AccountCard(modifier) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(UlapSpacing.S),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            name?.firstOrNull()?.let { Avatar(it.uppercaseChar()) }
            Column {
                if (name != null) CardTitle(name)
                profile.memberSince?.let {
                    CardBody(stringResource(R.string.account_member_since, rememberMonthYear(it)))
                }
            }
        }
        val unverifiedEmail = profile.email?.takeUnless { profile.emailVerified }
        if (unverifiedEmail != null) {
            VerifyNote(email = unverifiedEmail, resend = resend, onResend = onResendVerification)
        }
    }
}

@Composable
private fun ColumnScope.VerifyNote(email: String, resend: ResendState, onResend: () -> Unit) {
    Text(
        text = stringResource(R.string.account_verify_body, email),
        modifier = Modifier.padding(top = UlapSpacing.Xs),
        style = MaterialTheme.typography.bodyMedium,
        color = UlapTheme.colors.ink3,
    )
    when (resend) {
        ResendState.Sent -> UlapFieldNotice(stringResource(R.string.account_verify_sent))

        is ResendState.Failed -> {
            UlapFieldNotice(stringResource(resend.message.res))
            UlapTextLink(text = stringResource(R.string.account_verify_resend), onClick = onResend)
        }

        ResendState.Idle, ResendState.Sending -> UlapTextLink(
            text = stringResource(R.string.account_verify_resend),
            onClick = onResend,
        )
    }
}

/** The first letter of the name in a circle. Decorative: TalkBack reads the name beside it. */
@Composable
private fun Avatar(initial: Char) {
    Box(
        modifier = Modifier
            .size(AvatarSize)
            .background(UlapTheme.colors.infoBg, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = UlapTheme.colors.info,
        )
    }
}

/** A standard card (design-spec.md §6.7): surface, 1dp line, radius 16, padding 16. */
@Composable
private fun AccountCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = UlapTheme.colors.surface,
        border = BorderStroke(UlapSpacing.Border, UlapTheme.colors.line),
    ) {
        Column(
            modifier = Modifier.padding(UlapSpacing.CardPadding),
            verticalArrangement = Arrangement.spacedBy(UlapSpacing.Xxs),
            content = content,
        )
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color = UlapTheme.colors.ink,
    )
}

@Composable
private fun CardBody(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = UlapTheme.colors.muted)
}

/** "October 2026" in the app's language, in this phone's time zone. */
@Composable
private fun rememberMonthYear(instant: Instant): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(instant, locale) {
        val formatter = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMMy"), locale)
        formatter.format(java.time.Instant.ofEpochMilli(instant.toEpochMilliseconds()).atZone(ZoneId.systemDefault()))
    }
}

private val AvatarSize = 44.dp

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F8, widthDp = 360)
@Composable
private fun AccountCardsPreview() {
    UlapTheme {
        Column(Modifier.padding(UlapSpacing.M), verticalArrangement = Arrangement.spacedBy(UlapSpacing.M)) {
            BackupCard(googleAvailable = true, busy = false, googleError = null, onContinueWithGoogle = {
            }, onUseEmail = {})
            SignInAgainCard(onSignIn = {})
            ProfileCard(
                profile = AccountProfile(
                    "Christian",
                    "christian@example.com",
                    false,
                    Instant.parse("2026-10-01T02:00:00Z"),
                ),
                resend = ResendState.Idle,
                onResendVerification = {},
            )
        }
    }
}
