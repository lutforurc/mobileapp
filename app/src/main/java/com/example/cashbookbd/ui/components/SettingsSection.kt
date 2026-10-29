package com.example.cashbookbd.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cashbookbd.ui.theme.Spacing
import com.example.cashbookbd.ui.theme.appColors

/**
 * A titled block of settings — a [SectionHeading] over a [Card] that holds the
 * rows.
 *
 * The settings screens (Arrange Menu, Role Permission, the account menu) each
 * rebuilt this `heading + card + rows` shape by hand. This is the one version,
 * so the space above a section and the card behind its rows are the same
 * everywhere.
 */
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeading(title)
        Card(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
    }
}

/**
 * One row inside a [SettingsSection]: a label (with an optional muted
 * [subtitle]) and a trailing control. [divider] draws a hairline above the row,
 * so a caller can separate consecutive rows without tracking indices.
 */
@Composable
fun SettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    divider: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (divider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textMuted,
                    )
                }
            }
            trailing?.invoke()
        }
    }
}
