package com.example.cashbookbd.ui.reports

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.example.cashbookbd.ui.components.FieldFrame
import com.example.cashbookbd.ui.components.fieldValueTextStyle

/**
 * Compact, high-visibility read-only picker shared by every report filter
 * (branch, choice, and date fields). The whole field is a button that invokes
 * [onClick] (dropdown, date picker, …).
 *
 * The look lives in [FieldFrame], which the searchable dropdowns render through
 * too, so a typed field and a picked one are the same height.
 */
@Composable
internal fun PickerField(
    label: String,
    value: String,
    trailingIcon: ImageVector,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    onClick: () -> Unit,
) {
    FieldFrame(
        label = label,
        modifier = modifier,
        onClick = onClick,
        trailingIcon = {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
    ) {
        val showPlaceholder = value.isBlank()
        Text(
            text = if (showPlaceholder) placeholder else value,
            style = fieldValueTextStyle(),
            color = if (showPlaceholder) {
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
