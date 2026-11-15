package app.ulap.feature.account

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.ulap.core.designsystem.component.UlapConfirmDialog
import app.ulap.core.designsystem.component.UlapFieldNotice
import app.ulap.core.designsystem.component.UlapSettingsRow
import app.ulap.core.domain.account.AccountState
import app.ulap.core.ui.R

/**
 * The last row of the settings list (design-spec.md §7.8). The same row for everyone, as Google
 * Play asks of apps with accounts; the dialog says what it will delete.
 */
@Composable
internal fun DeleteDataRow(delete: DeleteState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    UlapSettingsRow(
        title = stringResource(R.string.account_delete),
        value = if (delete == DeleteState.Deleting) stringResource(R.string.account_deleting) else null,
        // One deletion at a time: the row waits while one is running.
        onClick = { if (delete != DeleteState.Deleting) onClick() },
        modifier = modifier,
    )
}

/** Under the settings list when a deletion didn't go through. It always says nothing was deleted. */
@Composable
internal fun DeleteFailedNotice(delete: DeleteState, modifier: Modifier = Modifier) {
    if (delete is DeleteState.Failed) UlapFieldNotice(stringResource(delete.message.res), modifier)
}

/**
 * Asks before deleting, in words that match what will go (api-spec.md §7.3): signed in, the
 * backup goes too; a guest or "Sign in again" has only this phone to erase.
 *
 * @param onConfirm Gets `withBackup`: true when the dialog promised to delete the backup.
 */
@Composable
internal fun DeleteDataDialog(account: AccountState?, onConfirm: (withBackup: Boolean) -> Unit, onDismiss: () -> Unit) {
    val withBackup = account is AccountState.SignedIn
    // "Sign in again" says what stays: the backup on the server, which needs a sign-in to delete.
    val (title, body) = when (account) {
        is AccountState.SignedIn -> R.string.account_delete_title_backup to R.string.account_delete_body_backup

        AccountState.SignInAgain ->
            R.string.account_delete_title_sign_in_again to R.string.account_delete_body_sign_in_again

        AccountState.Guest, null -> R.string.account_delete_title_phone to R.string.account_delete_body_phone
    }
    UlapConfirmDialog(
        title = stringResource(title),
        text = stringResource(body),
        confirmLabel = stringResource(
            if (account == AccountState.SignInAgain) {
                R.string.account_delete_confirm_phone_only
            } else {
                R.string.account_delete_confirm
            },
        ),
        dismissLabel = stringResource(R.string.account_delete_cancel),
        onConfirm = { onConfirm(withBackup) },
        onDismiss = onDismiss,
    )
}

@get:StringRes
private val DeleteMessage.res: Int
    get() = when (this) {
        DeleteMessage.OFFLINE -> R.string.account_delete_error_offline
        DeleteMessage.SIGN_IN_AGAIN -> R.string.account_delete_error_sign_in
        DeleteMessage.TOO_MANY_TRIES -> R.string.account_delete_error_too_many
        DeleteMessage.UPDATE_REQUIRED -> R.string.account_delete_error_update
        DeleteMessage.SERVER -> R.string.account_delete_error_server
    }
