package app.ulap.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.ulap.core.designsystem.icon.UlapIcons
import app.ulap.core.designsystem.theme.UlapShape
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.shared.model.Zone

/**
 * One chip in the Logs zone filter (design-spec.md §6.12): an 8dp zone dot, then the label.
 * [zone] null is the "All" chip, which has no dot. The label always names the zone, so the dot is
 * never the only sign of it (§11). Put the chips in a `Modifier.selectableGroup()` row.
 */
@Composable
fun ZoneFilterChip(label: String, zone: Zone?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dotColor = zone?.let { UlapTheme.colors.zone(it).strong }
    ChoiceChip(
        label = label,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        leading = dotColor?.let { color ->
            { Box(Modifier.size(ZoneDotSize).background(color, CircleShape)) }
        },
    )
}

/**
 * The Logs search field (design-spec.md §7.6): 46dp high, `radiusL` corners, a search icon in
 * front and a clear button once there's text. Filtering happens as the person types, so the
 * keyboard's search key only closes the keyboard.
 *
 * @param placeholder Shown while empty, and TalkBack's name for the field.
 * @param clearLabel TalkBack's name for the clear button, e.g. "Clear search".
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    clearLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = UlapTheme.colors
    val focusManager = LocalFocusManager.current
    val textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.ink)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = placeholder },
        textStyle = textStyle,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        cursorBrush = SolidColor(colors.ink),
        decorationBox = { innerTextField ->
            Surface(
                shape = UlapShape.Button,
                color = colors.surface,
                border = BorderStroke(UlapSpacing.Border, colors.lineStrong),
            ) {
                Row(
                    modifier = Modifier
                        .heightIn(min = SearchFieldHeight)
                        .padding(start = UlapSpacing.S),
                    horizontalArrangement = Arrangement.spacedBy(UlapSpacing.Xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(imageVector = UlapIcons.Search, contentDescription = null, tint = colors.muted)
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(text = placeholder, style = textStyle, color = colors.placeholder, maxLines = 1)
                        }
                        innerTextField()
                    }
                    if (value.isNotEmpty()) {
                        // Drawn inside the 46dp field; Compose still gives it a 48dp touch area (§11).
                        IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(SearchFieldHeight)) {
                            Icon(imageVector = UlapIcons.Clear, contentDescription = clearLabel, tint = colors.muted)
                        }
                    } else {
                        // Keeps the text from running into the rounded corner.
                        Box(Modifier.size(UlapSpacing.Xxs))
                    }
                }
            }
        },
    )
}

private val ZoneDotSize = 8.dp
private val SearchFieldHeight = 46.dp

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F8, widthDp = 360)
@Composable
private fun LogFiltersPreview() {
    UlapTheme {
        var query by remember { mutableStateOf("") }
        var zone by remember { mutableStateOf<Zone?>(Zone.RED) }
        Column(
            modifier = Modifier.padding(UlapSpacing.M),
            verticalArrangement = Arrangement.spacedBy(UlapSpacing.S),
        ) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search mood, trigger or journal",
                clearLabel = "Clear search",
            )
            Row(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(UlapSpacing.Xs),
            ) {
                ZoneFilterChip(label = "All", zone = null, selected = zone == null, onClick = { zone = null })
                listOf(Zone.RED to "Red", Zone.BLUE to "Blue").forEach { (option, label) ->
                    ZoneFilterChip(label = label, zone = option, selected = zone == option, onClick = { zone = option })
                }
            }
        }
    }
}
