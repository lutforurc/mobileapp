package com.example.cashbookbd.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A rounded status badge — "Active", "This device".
 *
 * The fill is `primaryContainer`, not a translucent wash of `primary`. A wash
 * only lands where the surface behind it happens to be light; over a dark card
 * it turns muddy and the primary-coloured text on top loses contrast. The
 * container/on-container pair is defined per theme, so the badge stays legible
 * wherever it is dropped.
 *
 * [compact] is the smaller badge that sits inside a row of text rather than
 * beside a heading.
 */
@Composable
fun BrandPill(
    text: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = text,
            style = if (compact) {
                MaterialTheme.typography.labelSmall
            } else {
                MaterialTheme.typography.labelLarge
            },
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(
                if (compact) {
                    PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                } else {
                    PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                },
            ),
        )
    }
}
