package com.example.cashbookbd.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cashbookbd.ui.theme.Spacing
import com.example.cashbookbd.ui.theme.appColors

/**
 * One tappable row in a section-home list — the "Customers", "Sales", "Reports"
 * entry that opens a screen.
 *
 * Seventeen section-home screens each hand-copied this same `Card + Row +
 * chevron`, so the row's padding, chevron and disabled treatment could drift
 * apart one screen at a time. This is the single definition: the established
 * `16/18dp` row, an optional muted [subtitle], and a chevron that drops away
 * when the row is not navigable ([trailing] = false, e.g. an "available on the
 * web" entry).
 *
 * A disabled row is dimmed and not clickable rather than hidden, so the user can
 * still see the feature exists.
 */
@Composable
fun MenuListRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    trailing: Boolean = true,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.appColors.textMuted
                    },
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textMuted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (trailing) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
