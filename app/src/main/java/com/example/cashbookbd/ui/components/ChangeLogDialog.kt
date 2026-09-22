package com.example.cashbookbd.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cashbookbd.data.repository.ChangeLogEvent
import com.example.cashbookbd.data.repository.ChangeLogView
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors

private val ACTION_LABELS = mapOf(
    "create" to "Created",
    "update" to "Updated",
    "delete" to "Deleted",
)

/**
 * "What happened to this record" — a customer's or a product's own trail,
 * the same shape the Audit Trail shows for a voucher: `field: old → new`.
 * A create or a delete has nothing to diff against, so its own few fields
 * are shown plainly instead of a diff.
 */
@Composable
fun ChangeLogDialog(
    title: String,
    isLoading: Boolean,
    error: String?,
    view: ChangeLogView?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            when {
                isLoading && view == null -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { CircularProgressIndicator() }

                error != null && view == null -> Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                view != null -> Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (view.subjectSubtitle.isNotBlank()) {
                        Text(
                            text = view.subjectSubtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.appColors.textMuted,
                        )
                    }
                    if (view.events.isEmpty()) {
                        Text(
                            "Nothing recorded yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appColors.textMuted,
                        )
                    } else {
                        view.events.forEach { event -> ChangeLogEventRow(event) }
                    }
                    Text(
                        text = view.note,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.appColors.textMuted,
                    )
                }
            }
        },
        confirmButton = { LinkButton(text = "Close", onClick = onDismiss) },
    )
}

@Composable
private fun ChangeLogEventRow(event: ChangeLogEvent) {
    val muted = MaterialTheme.appColors.textMuted
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = listOf(
                ACTION_LABELS[event.action] ?: event.action.replaceFirstChar { it.uppercase() },
                event.user.takeIf { it.isNotBlank() },
                event.at.take(16).replace('T', ' '),
            ).filterNotNull().joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = AppFontWeight.SemiBold,
        )
        if (event.changes.isEmpty()) {
            Text("-", style = MaterialTheme.typography.labelSmall, color = muted)
        } else {
            event.changes.forEach { change ->
                Text(
                    text = "${change.field}: ${change.old.ifBlank { "—" }} → ${change.new.ifBlank { "—" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}
