package app.ulap.feature.checkin.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme

// Small building blocks of the Details screen.

/** A standard card (design-spec.md §6.7): surface, 1dp line, radius 16. */
@Composable
internal fun DetailsCard(content: @Composable () -> Unit) {
    val colors = UlapTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.surface,
        contentColor = colors.ink,
        border = BorderStroke(UlapSpacing.Border, colors.line),
    ) {
        Column(
            modifier = Modifier.padding(UlapSpacing.CardPadding),
            verticalArrangement = Arrangement.spacedBy(UlapSpacing.S),
        ) { content() }
    }
}

@Composable
internal fun ulapTextFieldColors(): TextFieldColors {
    val colors = UlapTheme.colors
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.ink,
        unfocusedBorderColor = colors.lineStrong,
        focusedPlaceholderColor = colors.placeholder,
        unfocusedPlaceholderColor = colors.placeholder,
        cursorColor = colors.ink,
        focusedContainerColor = colors.surface,
        unfocusedContainerColor = colors.surface,
    )
}
