package app.ulap.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.ulap.core.designsystem.icon.UlapIcons
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme

/**
 * A standard card (design-spec.md §6.7: surface, 1dp `line`, radius 16) holding a list of
 * [UlapSettingsRow]s, like the Account settings list (§7.8). Put a [UlapSettingsDivider] between
 * rows. The rows pad themselves, so the whole row is the touch target.
 */
@Composable
fun UlapSettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = UlapTheme.colors.surface,
        border = BorderStroke(UlapSpacing.Border, UlapTheme.colors.line),
    ) {
        Column(content = content)
    }
}

/**
 * One row in a settings list that opens another screen: a [title], the current [value] if it has
 * one (e.g. the chosen language), and a chevron. At least 56dp tall, above the 48dp minimum
 * (§11). TalkBack reads the title and value as one button.
 */
@Composable
fun UlapSettingsRow(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, value: String? = null) {
    val colors = UlapTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinRowHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = UlapSpacing.CardPadding, vertical = UlapSpacing.S),
        horizontalArrangement = Arrangement.spacedBy(UlapSpacing.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.ink,
        )
        if (value != null) {
            Text(text = value, style = MaterialTheme.typography.bodyMedium, color = colors.ink2)
        }
        Icon(imageVector = UlapIcons.Forward, contentDescription = null, tint = colors.ink3)
    }
}

/** The 1dp `lineSoft` row divider between two [UlapSettingsRow]s (design-spec.md §2.1). */
@Composable
fun UlapSettingsDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = UlapSpacing.CardPadding),
        thickness = UlapSpacing.Border,
        color = UlapTheme.colors.lineSoft,
    )
}

private val MinRowHeight = 56.dp

@Preview(showBackground = true)
@Composable
private fun UlapSettingsCardPreview() {
    UlapTheme {
        UlapSettingsCard(modifier = Modifier.padding(UlapSpacing.M)) {
            UlapSettingsRow(title = "Language", value = "Bisaya", onClick = {})
            UlapSettingsDivider()
            UlapSettingsRow(title = "Privacy notice", onClick = {})
        }
    }
}
