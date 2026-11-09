package app.ulap.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.ulap.core.designsystem.theme.UlapShape
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme

/**
 * A large single-select option: a [title] with a smaller [subtitle] under it, e.g. a language and
 * its greeting in onboarding (design-spec.md §7.1). Put the cards in a container with
 * `Modifier.selectableGroup()` so TalkBack announces them as one group.
 */
@Composable
fun UlapOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UlapTheme.colors
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics { role = Role.RadioButton },
        shape = UlapShape.Button,
        color = colors.surface,
        contentColor = colors.ink,
        border = BorderStroke(
            if (selected) SelectedBorderWidth else UlapSpacing.Border,
            if (selected) colors.ink else colors.lineStrong,
        ),
    ) {
        Column(
            modifier = Modifier
                .heightIn(min = MinHeight)
                .padding(horizontal = UlapSpacing.L, vertical = UlapSpacing.M),
            verticalArrangement = Arrangement.spacedBy(UlapSpacing.Xxs),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.ink2)
        }
    }
}

private val MinHeight = 72.dp
private val SelectedBorderWidth = 2.dp

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun UlapOptionCardPreview() {
    UlapTheme {
        var selected by remember { mutableIntStateOf(0) }
        Column(
            modifier = Modifier
                .padding(UlapSpacing.M)
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(UlapSpacing.S),
        ) {
            listOf("English" to "Welcome", "Filipino" to "Maligayang pagdating").forEachIndexed { index, (t, s) ->
                UlapOptionCard(title = t, subtitle = s, selected = selected == index, onClick = { selected = index })
            }
        }
    }
}
