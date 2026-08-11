package com.example.cashbookbd.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The "look a saved voucher up by its number" row: the box and the button that
 * searches it.
 *
 * One component rather than the same Row written out on every form, so the
 * two read as one control everywhere — the web sat its button hard against
 * the box it belongs to for the same reason (react 879ddca), and a gap that
 * differs from screen to screen is exactly what that change was correcting.
 *
 * The gap here is deliberately not zero: a touch target has to be its own
 * shape to be aimed at, which a mouse pointer does not.
 */
@Composable
fun VoucherSearchRow(
    query: String,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Voucher number…",
    isSearching: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AppTextField(
            value = query,
            onValueChange = onQuery,
            label = label,
            modifier = Modifier.weight(1f),
            trailingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        // Levelled with the field beside it by the constant the field itself
        // uses, so restyling FieldFrame keeps the two in step.
        PrimaryButton(
            text = "Search",
            onClick = onSearch,
            compact = true,
            enabled = !isSearching,
            isLoading = isSearching,
            modifier = Modifier.height(FormFieldHeight),
        )
    }
}
