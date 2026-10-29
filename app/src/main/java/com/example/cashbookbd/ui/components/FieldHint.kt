package com.example.cashbookbd.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.cashbookbd.ui.theme.Spacing
import com.example.cashbookbd.ui.theme.appColors

/**
 * A small helper or validation line sitting under a field.
 *
 * The app used to render this inline in half a dozen screens with slightly
 * different styling, and — the real bug — an error hint was drawn in
 * `onBackground` on some screens (near-invisible, never red). This is the one
 * definition: muted ink normally, the theme's error colour when [isError], so a
 * validation message always reads as a validation message.
 */
@Composable
fun FieldHint(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    if (text.isNotBlank()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.appColors.textMuted,
            modifier = modifier.padding(top = Spacing.xs),
        )
    }
}
