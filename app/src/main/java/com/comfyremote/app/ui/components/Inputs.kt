package com.comfyremote.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.comfyremote.app.ui.IrisType

/**
 * [filled] paints the container. Only use it for fields without a floating label:
 * OutlinedTextField cuts a notch for the label out of the container too, which
 * shows as a hard-edged rectangle against a filled background.
 */
@Composable
fun irisTextFieldColors(filled: Boolean = false): TextFieldColors {
    val scheme = MaterialTheme.colorScheme
    val container = if (filled) scheme.surfaceContainer else Color.Transparent
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = container,
        unfocusedContainerColor = container,
        disabledContainerColor = if (filled) container.copy(alpha = 0.6f) else Color.Transparent,
        focusedBorderColor = scheme.primary,
        unfocusedBorderColor = if (filled) scheme.outlineVariant else scheme.outline,
        focusedLabelColor = scheme.primary,
        unfocusedLabelColor = scheme.onSurfaceVariant,
        cursorColor = scheme.primary,
    )
}

/**
 * Text field with Iris styling: filled container, hairline border, accent on
 * focus. Thin wrapper over [OutlinedTextField] so all its behaviour remains.
 */
@Composable
fun IrisTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) } },
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = MaterialTheme.shapes.medium,
        colors = irisTextFieldColors(filled = label == null),
        textStyle = MaterialTheme.typography.bodyLarge,
    )
}

/**
 * Material Switch with a visible off state. The default off thumb uses `outline`,
 * which sits too close to the track colour in the Iris palette to be seen.
 */
@Composable
fun IrisSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = scheme.primary,
            checkedThumbColor = scheme.onPrimary,
            uncheckedTrackColor = scheme.surfaceContainerHighest,
            uncheckedThumbColor = scheme.onSurfaceVariant,
            uncheckedBorderColor = scheme.onSurfaceVariant,
            disabledUncheckedTrackColor = scheme.surfaceContainerHigh,
            disabledUncheckedThumbColor = scheme.onSurfaceVariant.copy(alpha = 0.38f),
            disabledUncheckedBorderColor = scheme.onSurfaceVariant.copy(alpha = 0.24f),
            disabledCheckedTrackColor = scheme.primary.copy(alpha = 0.38f),
            disabledCheckedThumbColor = scheme.onPrimary.copy(alpha = 0.6f),
        ),
    )
}

/** − value + stepper. */
@Composable
fun Stepper(
    value: Int,
    onChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(2.dp)) {
            StepButton(Icons.Default.Remove, enabled && value > range.first) { onChange(value - 1) }
            Text(
                value.toString(),
                style = IrisType.meta,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 28.dp),
            )
            StepButton(Icons.Default.Add, enabled && value < range.last) { onChange(value + 1) }
        }
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(34.dp),
        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    ) { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) }
}
