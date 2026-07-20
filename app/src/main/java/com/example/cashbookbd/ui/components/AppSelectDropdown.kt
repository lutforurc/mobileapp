package com.example.cashbookbd.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.example.cashbookbd.ui.reports.model.SelectorOption

/**
 * The shared [FieldFrame] anchor every dropdown field renders through, so a
 * picker's closed state is the same 44dp box as a typed [AppTextField]. Shows
 * [valueText], or [placeholder] in the muted colour when nothing is selected.
 *
 * @param trailingIcon defaults to the dropdown arrow; pass e.g. a spinner
 *   while options load.
 */
@Composable
fun DropdownAnchorField(
    label: String,
    valueText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    FieldFrame(
        label = label,
        modifier = modifier,
        onClick = onClick,
        trailingIcon = trailingIcon ?: {
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        Text(
            text = valueText ?: placeholder,
            style = fieldValueTextStyle(),
            color = if (valueText == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The app's non-searchable "pick one from a fixed list" dropdown — the shared
 * component for warehouse, variance type, branch/business type, role, etc. Kept
 * in one place so every such picker looks and behaves the same (see the OOP
 * component rule). For type-ahead server search use [SearchableSelectDropdown].
 *
 * The anchor is a [DropdownAnchorField] (a [FieldFrame]), so it shares the same
 * 44dp box as every other form field.
 *
 * @param options the choices; each option's [SelectorOption.label] is shown.
 * @param selected the current selection (drives the field text); null shows [placeholder].
 * @param onSelected called with the chosen option.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectDropdown(
    label: String,
    options: List<SelectorOption>,
    selected: SelectorOption?,
    onSelected: (SelectorOption) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "",
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        DropdownAnchorField(
            label = label,
            valueText = selected?.label,
            placeholder = placeholder,
            onClick = { if (enabled && options.isNotEmpty()) expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = { onSelected(option); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
