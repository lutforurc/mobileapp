package com.example.cashbookbd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.cashbookbd.ui.theme.AppShape
import com.example.cashbookbd.ui.theme.Spacing
import com.example.cashbookbd.ui.theme.appColors
import com.example.cashbookbd.ui.theme.asDivider
import com.example.cashbookbd.ui.theme.asTint

/**
 * The result of a form submission, shown inline where the form was.
 *
 * Transaction, Invoice, Requisition and VrSettings each drew this themselves,
 * coloured with `onBackground` — so a failure looked the same as a success and
 * neither carried a status colour. This banner fixes that: a tinted fill and
 * border in the theme's danger colour on error, its success colour otherwise,
 * with an icon so the outcome is readable at a glance. A blank [text] draws
 * nothing, so a caller can always call it and let it decide.
 */
@Composable
fun FormMessage(
    text: String?,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    val message = text?.takeIf { it.isNotBlank() } ?: return
    val accent = if (isError) MaterialTheme.appColors.danger else MaterialTheme.appColors.success
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShape)
            .background(accent.asTint())
            .border(1.dp, accent.asDivider(), AppShape)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            imageVector = if (isError) Icons.Filled.Warning else Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = accent,
            modifier = Modifier.weight(1f),
        )
    }
}
