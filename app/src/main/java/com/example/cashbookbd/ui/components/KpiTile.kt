package com.example.cashbookbd.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.Spacing

/**
 * One figure on a dashboard: a small uppercase label over a big value, with an
 * optional [caption] and [sparkline].
 *
 * The two dashboards each had their own `KpiTile` with a different look, and the
 * hotel dashboard a third; a tile that says the same thing (a labelled figure)
 * should look the same on all of them. Built on [SummaryTile] so the fill and
 * radius match the report summary boxes. [accent] colours the value and the
 * sparkline; null leaves the value in the card's own ink.
 */
@Composable
fun KpiTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    caption: String? = null,
    sparkline: List<Double>? = null,
) {
    SummaryTile(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = AppFontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = AppFontWeight.Bold,
            color = accent ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (!caption.isNullOrBlank()) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (sparkline != null && sparkline.size >= 2) {
            Sparkline(
                values = sparkline,
                color = accent ?: MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}
