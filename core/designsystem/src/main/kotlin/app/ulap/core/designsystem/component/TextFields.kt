package app.ulap.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.ulap.core.designsystem.icon.UlapIcons
import app.ulap.core.designsystem.theme.UlapSpacing
import app.ulap.core.designsystem.theme.UlapTheme

/**
 * A labelled text field for forms such as sign-in (design-spec.md §7.9). [error] sits under the
 * field in ink with an icon. It never uses Red: a typo isn't an alarm. [supporting] is a quiet
 * hint shown while there's no error, e.g. the password length.
 */
@Composable
fun UlapTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = UlapTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(label) },
        trailingIcon = trailingIcon,
        supportingText = when {
            error != null -> {
                { UlapFieldNotice(error) }
            }

            supporting != null -> {
                { Text(supporting, color = colors.muted) }
            }

            else -> null
        },
        isError = error != null,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.ink,
            unfocusedTextColor = colors.ink,
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
            disabledContainerColor = colors.surface,
            focusedBorderColor = colors.ink,
            unfocusedBorderColor = colors.lineStrong,
            focusedLabelColor = colors.ink,
            unfocusedLabelColor = colors.muted,
            cursorColor = colors.ink,
            // An error keeps the ink look and adds the icon line underneath (§7.9).
            errorBorderColor = colors.ink,
            errorLabelColor = colors.ink,
            errorCursorColor = colors.ink,
            errorTextColor = colors.ink,
            errorContainerColor = colors.surface,
            errorSupportingTextColor = colors.ink,
            errorTrailingIconColor = colors.ink2,
            focusedTrailingIconColor = colors.ink2,
            unfocusedTrailingIconColor = colors.ink2,
        ),
    )
}

/**
 * A password [UlapTextField] with a show/hide button (design-spec.md §7.9). The labels are the
 * button's TalkBack names, e.g. "Show password".
 */
@Composable
fun UlapPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    showLabel: String,
    hideLabel: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    UlapTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        supporting = supporting,
        error = error,
        enabled = enabled,
        keyboardOptions = keyboardOptions.copy(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        keyboardActions = keyboardActions,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) UlapIcons.Hide else UlapIcons.Show,
                    contentDescription = if (visible) hideLabel else showLabel,
                )
            }
        },
    )
}

/**
 * A line of text with the notice icon, in ink: an error under a field, or under a whole form when
 * no single field is to blame (design-spec.md §7.9). TalkBack reads it as soon as it appears.
 */
@Composable
fun UlapFieldNotice(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(UlapSpacing.Xxs),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = UlapIcons.Notice,
            contentDescription = null,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(NoticeIconSize),
            tint = UlapTheme.colors.ink,
        )
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = UlapTheme.colors.ink)
    }
}

private val NoticeIconSize = 16.dp

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F8, widthDp = 360)
@Composable
private fun TextFieldsPreview() {
    UlapTheme {
        Column(
            modifier = Modifier.padding(UlapSpacing.M),
            verticalArrangement = Arrangement.spacedBy(UlapSpacing.S),
        ) {
            UlapTextField(value = "ana@", onValueChange = {
            }, label = "Email", error = "Enter an email like name@example.com.")
            UlapPasswordField(
                value = "Kalma-sa-Ulap",
                onValueChange = {},
                label = "Password",
                showLabel = "Show password",
                hideLabel = "Hide password",
                supporting = "At least 10 characters.",
            )
            UlapFieldNotice("You're offline right now.")
        }
    }
}
