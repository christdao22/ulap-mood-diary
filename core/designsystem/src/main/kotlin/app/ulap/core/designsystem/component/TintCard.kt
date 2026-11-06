package app.ulap.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme

/**
 * A card on a zone tint, like the response screen's support cards (design-spec.md §6.7): surface,
 * no border, radius 18, padding 20. The tint around it already sets it apart, so no border.
 */
@Composable
fun UlapTintCard(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = UlapTheme.colors.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(UlapSpacing.TintCardPadding),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = Arrangement.spacedBy(UlapSpacing.S),
            content = content,
        )
    }
}

@Preview
@Composable
private fun UlapTintCardPreview() {
    UlapTheme {
        UlapTintCard { Text("Saved to your good moments") }
    }
}
