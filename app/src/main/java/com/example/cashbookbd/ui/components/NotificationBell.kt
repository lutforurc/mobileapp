package com.example.cashbookbd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cashbookbd.notifications.AppNotification
import com.example.cashbookbd.notifications.NotificationCenter
import com.example.cashbookbd.notifications.NotificationPreviewRow
import com.example.cashbookbd.notifications.NotificationTone
import com.example.cashbookbd.ui.theme.accents

/**
 * The top-bar notification center, mirroring the web's DropdownNotification: a
 * bell with an unread badge, opening a list of tone-coloured alerts. Tapping an
 * item opens its target (via [onOpen]); the ✕ marks it read (via [onDismiss]).
 */
@Composable
fun NotificationBell(
    state: NotificationCenter.State,
    onRefresh: () -> Unit,
    onOpen: (AppNotification) -> Unit,
    onDismiss: (AppNotification) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(
            onClick = {
                expanded = true
                onRefresh()
            },
        ) {
            BadgedBox(
                badge = {
                    if (state.totalCount > 0) {
                        Badge(
                            containerColor = MaterialTheme.accents.red,
                            contentColor = Color.White,
                        ) {
                            Text(if (state.totalCount > 99) "99+" else state.totalCount.toString())
                        }
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = "Notifications",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(340.dp),
        ) {
            NotificationHeader(
                totalCount = state.totalCount,
                isLoading = state.isLoading,
                onRefresh = onRefresh,
            )
            HorizontalDivider()

            when {
                state.isLoading && state.items.isEmpty() -> EmptyRow("Loading notifications…")
                state.items.isEmpty() -> EmptyRow("No important notification right now.")
                else -> Column(modifier = Modifier.heightIn(max = 420.dp)) {
                    state.items.forEach { item ->
                        NotificationRow(
                            item = item,
                            onClick = {
                                expanded = false
                                onOpen(item)
                            },
                            onDismiss = { onDismiss(item) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationHeader(totalCount: Int, isLoading: Boolean, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Notification Center",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (totalCount > 0) "$totalCount item(s) need attention" else "Everything looks clear",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRefresh, enabled = !isLoading) {
            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
        }
    }
}

@Composable
private fun EmptyRow(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NotificationRow(
    item: AppNotification,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = toneColor(item.tone)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(accent.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = toneIcon(item.tone),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (item.count > 0) {
                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(50),
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = item.count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            if (item.message.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item.preview.forEach { row -> PreviewRow(row) }
        }
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Dismiss ${item.title}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun PreviewRow(row: NotificationPreviewRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            if (row.meta.isNotBlank()) {
                Text(
                    text = row.meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        if (row.value.isNotBlank()) {
            Text(
                text = row.value,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun toneColor(tone: NotificationTone): Color = when (tone) {
    NotificationTone.DANGER -> MaterialTheme.accents.red
    NotificationTone.WARNING -> MaterialTheme.accents.amber
    NotificationTone.INFO -> MaterialTheme.accents.blue
    NotificationTone.SUCCESS -> MaterialTheme.accents.green
}

private fun toneIcon(tone: NotificationTone): ImageVector = when (tone) {
    NotificationTone.DANGER -> Icons.Filled.Warning
    NotificationTone.WARNING -> Icons.Filled.Warning
    NotificationTone.INFO -> Icons.Filled.Info
    NotificationTone.SUCCESS -> Icons.Filled.CheckCircle
}
