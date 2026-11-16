package app.ulap.core.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.ulap.core.designsystem.component.UlapHotlineCard
import app.ulap.core.designsystem.component.UlapSettingsRow
import app.ulap.core.designsystem.component.spokenDigits
import app.ulap.shared.rules.HotlineRules

/**
 * The hotline number every hotline surface shows. The app root provides the number saved from
 * `GET /meta` (api-spec.md §9.3), already passed through [HotlineRules.numberToShow]. Without a
 * provider, as in previews and tests, it's the built-in NCMH 1553, so the hotline never needs the
 * network (technical-plan.md §13). Safety (🔒 in PHASES.md): a change here needs two reviewers.
 */
val LocalHotlineNumber = staticCompositionLocalOf { HotlineRules.BUILT_IN_NUMBER }

/** The number to show and dial, from [LocalHotlineNumber]. */
@Composable
@ReadOnlyComposable
fun hotlineNumber(): String = LocalHotlineNumber.current

/**
 * The NCMH 1553 card with its words, wired to the phone's dialer (technical-plan.md §13). Used on
 * the Hopeless Response and After screens. It needs no network, account or permission, so it
 * works offline. Safety (🔒 in PHASES.md): a change here needs two reviewers.
 */
@Composable
fun HotlineCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val number = hotlineNumber()
    UlapHotlineCard(
        title = stringResource(R.string.hotline_title),
        number = number,
        callLabel = stringResource(R.string.hotline_call, number),
        // TalkBack: "Call 1 5 5 3", the way the person will dial it (design-spec.md §6.8).
        callDescription = stringResource(R.string.hotline_call, number.spokenDigits()),
        helper = stringResource(R.string.hotline_helper),
        onCall = { context.openDialer(number) },
        modifier = modifier,
    )
}

/**
 * "Need to talk to someone? NCMH 1553", the Account settings row that's always there
 * (technical-plan.md §13, design-spec.md §7.8). One tap opens the dialer, so the hotline is two
 * taps from anywhere: the Account tab, then this row (design-spec.md §11). Safety 🔒.
 */
@Composable
fun HotlineSettingsRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val number = hotlineNumber()
    UlapSettingsRow(
        title = stringResource(R.string.account_hotline, number),
        onClick = { context.openDialer(number) },
        modifier = modifier,
        // TalkBack reads the number digit by digit, as on the card.
        contentDescription = stringResource(R.string.account_hotline, number.spokenDigits()),
    )
}

/**
 * Opens the dialer with [number] typed in. ACTION_DIAL never places the call itself, so the person
 * stays in control and the app needs no CALL_PHONE permission (technical-plan.md §13).
 */
fun Context.openDialer(number: String) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // A tablet with no phone app. Say so, so the tap never looks broken to someone in crisis,
        // and point to another phone (design-spec.md §6.8). Nothing to log.
        Toast.makeText(this, getString(R.string.hotline_no_phone, number), Toast.LENGTH_LONG).show()
    }
}
