package com.comfyremote.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.comfyremote.app.ui.IrisType

/**
 * Settings group: overline title followed by a single rounded container whose
 * rows are separated by hairlines. Put [SettingRow] / [SwitchRow] /
 * [StepperRow] / [ChoiceRow] inside.
 */
@Composable
fun SettingsGroup(
    title: String?,
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        title?.let {
            Text(
                it.uppercase(),
                style = IrisType.overline,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
        footer?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/** Navigational or informational row. Shows a chevron when [onClick] is set and no [trailing]. */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    icon: ImageVector? = null,
    value: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            icon?.let {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Icon(it, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(7.dp).size(20.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            value?.let { Text(it, style = IrisType.meta, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            when {
                trailing != null -> trailing()
                onClick != null -> Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (divider) IrisDivider(Modifier.padding(start = if (icon != null) 64.dp else 16.dp))
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    enabled: Boolean = true,
    divider: Boolean = true,
) {
    SettingRow(
        title = title,
        detail = detail,
        modifier = modifier,
        divider = divider,
        onClick = if (enabled) ({ onCheckedChange(!checked) }) else null,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )
        },
    )
}

@Composable
fun StepperRow(
    title: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    divider: Boolean = true,
) {
    SettingRow(
        title = title,
        detail = detail,
        modifier = modifier,
        divider = divider,
        trailing = { Stepper(value = value, onChange = onChange, range = range) },
    )
}

/** Row whose choice is a [SegmentedControl] shown under the title. */
@Composable
fun ChoiceRow(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    divider: Boolean = true,
) {
    Column(modifier) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            SegmentedControl(
                options = options.map { it.second },
                selectedIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0),
                onSelect = { onSelect(options[it].first) },
            )
        }
        if (divider) IrisDivider(Modifier.padding(start = 16.dp))
    }
}
