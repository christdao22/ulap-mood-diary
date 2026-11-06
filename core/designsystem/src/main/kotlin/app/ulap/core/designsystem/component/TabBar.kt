package app.ulap.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ulap.core.designsystem.icon.UlapIcons
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme

/**
 * The bottom tab bar (design-spec.md §6.10): `surface` with a 1dp `line` on top, four equal
 * columns, and 14dp under them plus the system's navigation bar inset. Fill it with [UlapTabItem]s.
 */
@Composable
fun UlapTabBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val colors = UlapTheme.colors
    Surface(modifier = modifier, color = colors.surface) {
        Column {
            HorizontalDivider(thickness = UlapSpacing.Border, color = colors.line)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = BottomPadding)
                    // TalkBack announces "tab, 1 of 4" for each item.
                    .selectableGroup(),
                content = content,
            )
        }
    }
}

/**
 * One tab. Active: `ink` icon and 12/600 label. Inactive: `muted` and 12/500 (§6.10). The weight
 * change means the active tab isn't marked by color alone (§11). TalkBack reads the label and
 * "selected"; the icon is decorative.
 */
@Composable
fun RowScope.UlapTabItem(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val colors = UlapTheme.colors
    val color = if (selected) colors.ink else colors.muted
    Column(
        modifier = modifier
            .weight(1f)
            .heightIn(min = ItemMinHeight)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(horizontal = UlapSpacing.Xxs, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(UlapSpacing.Xxs, Alignment.CenterVertically),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(IconSize))
        Text(
            text = label,
            color = color,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
        )
    }
}

private val ItemMinHeight = 52.dp
private val IconSize = 22.dp
private val BottomPadding = 14.dp

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun UlapTabBarPreview() {
    UlapTheme {
        UlapTabBar {
            UlapTabItem(selected = true, onClick = {}, label = "Check in", icon = UlapIcons.CheckIn)
            UlapTabItem(selected = false, onClick = {}, label = "Logs", icon = UlapIcons.Logs)
            UlapTabItem(selected = false, onClick = {}, label = "Reports", icon = UlapIcons.Reports)
            UlapTabItem(selected = false, onClick = {}, label = "Account", icon = UlapIcons.Account)
        }
    }
}
