package app.ulap.feature.account

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import app.ulap.core.designsystem.component.UlapConfirmDialog
import app.ulap.core.designsystem.component.UlapTextLink
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.domain.sync.SyncStatus
import app.ulap.core.ui.R
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Instant

/**
 * The sync row under the profile card (design-spec.md §7.8): the last-synced time and
 * **Sync now**. Below it, quietly: the offline line while check-ins wait to back up, and the
 * check-ins that stopped trying, with **Try again**. Never how many times it was tried, and
 * "saved", not "safe": a check-in only on this phone is lost with the phone.
 */
@Composable
internal fun SyncCard(
    status: SyncStatus,
    onSyncNow: () -> Unit,
    onTryAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AccountCard(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UlapSpacing.S),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val lastSyncedAt = status.lastSyncedAt
            Text(
                text = when {
                    status.running -> stringResource(R.string.account_sync_running)
                    lastSyncedAt != null -> stringResource(R.string.account_sync_last, rememberDateTime(lastSyncedAt))
                    else -> stringResource(R.string.account_sync_never)
                },
                // TalkBack hears "Backing up…" and then the new time without moving there.
                modifier = Modifier
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = UlapTheme.colors.ink,
            )
            UlapTextLink(text = stringResource(R.string.account_sync_now), onClick = onSyncNow)
        }
        if (!status.online && status.waitingCount > 0) {
            Quiet(stringResource(R.string.account_sync_offline))
        }
        if (status.stuckCount > 0) {
            Quiet(pluralStringResource(R.plurals.account_sync_stuck, status.stuckCount, status.stuckCount))
            UlapTextLink(text = stringResource(R.string.account_sync_try_again), onClick = onTryAgain)
        }
    }
}

/**
 * "Add this phone's check-ins to your account?" (api-spec.md §8.5). **Don't add** removes them
 * from this phone, so tapping outside or Back doesn't count as an answer: it only closes the
 * dialog until Account opens again, and nothing syncs meanwhile.
 */
@Composable
internal fun AddCheckInsDialog(onAnswer: (add: Boolean) -> Unit, onLater: () -> Unit) {
    UlapConfirmDialog(
        title = stringResource(R.string.account_add_title),
        text = stringResource(R.string.account_add_body),
        confirmLabel = stringResource(R.string.account_add_confirm),
        dismissLabel = stringResource(R.string.account_add_dismiss),
        onConfirm = { onAnswer(true) },
        onDismiss = { onAnswer(false) },
        onDismissRequest = onLater,
    )
}

@Composable
private fun Quiet(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = UlapTheme.colors.ink3)
}

/** "Nov 15, 3:15 PM" in the app's language, in this phone's time zone. */
@Composable
private fun rememberDateTime(instant: Instant): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(instant, locale) {
        val pattern = DateFormat.getBestDateTimePattern(locale, "MMMdjmm")
        DateTimeFormatter.ofPattern(pattern, locale)
            .format(java.time.Instant.ofEpochMilli(instant.toEpochMilliseconds()).atZone(ZoneId.systemDefault()))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F8, widthDp = 360)
@Composable
private fun SyncCardPreview() {
    UlapTheme {
        SyncCard(
            status = SyncStatus(
                lastSyncedAt = Instant.parse("2026-11-15T07:15:00Z"),
                waitingCount = 2,
                stuckCount = 1,
                online = false,
            ),
            onSyncNow = {},
            onTryAgain = {},
        )
    }
}
