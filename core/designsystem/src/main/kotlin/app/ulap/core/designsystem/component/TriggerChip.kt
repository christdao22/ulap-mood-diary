package app.ulap.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ulap.core.designsystem.theme.UlapShape
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme

/**
 * One trigger option (design-spec.md §6.5). Triggers are single-select: the caller keeps the
 * choice and clears it when the selected chip is tapped again. Put the chips in a container with
 * `Modifier.selectableGroup()` so TalkBack announces them as one group.
 *
 * The pill is 40dp high, but its touch area is padded to 48dp (§11).
 */
@Composable
fun TriggerChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ChoiceChip(label = label, selected = selected, onClick = onClick, modifier = modifier)
}

/**
 * The single-select pill that trigger chips and the Logs zone chips share (design-spec.md §6.5,
 * §6.12), with an optional [leading] mark before the label.
 */
@Composable
internal fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = UlapTheme.colors
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.semantics { role = Role.RadioButton },
        shape = UlapShape.Pill,
        color = if (selected) colors.ink else colors.surface,
        contentColor = if (selected) colors.onInk else colors.ink,
        border = BorderStroke(BorderWidth, if (selected) colors.ink else colors.lineStrong),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = MinHeight)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(UlapSpacing.Xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}

private val MinHeight = 40.dp
private val BorderWidth = 1.5.dp

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun TriggerChipPreview() {
    UlapTheme {
        var selected by remember { mutableStateOf<String?>("Work") }
        Row(
            modifier = Modifier
                .padding(UlapSpacing.M)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(UlapSpacing.Xs),
        ) {
            listOf("Work", "Family", "Money").forEach { option ->
                TriggerChip(
                    label = option,
                    selected = selected == option,
                    onClick = { selected = if (selected == option) null else option },
                )
            }
        }
    }
}
