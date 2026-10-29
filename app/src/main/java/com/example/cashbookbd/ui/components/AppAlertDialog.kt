package com.example.cashbookbd.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.cashbookbd.ui.theme.AppFontWeight

/**
 * The app's confirmation dialog: a title, a message, and the shared button pair
 * (a filled confirm, a borderless Cancel).
 *
 * The app had over a hundred ad-hoc `AlertDialog`s, so the confirm action could
 * be a `PrimaryButton` on one screen and a `LinkButton` on the next, and a
 * destructive confirm was red on some and plain on others. This is the one
 * shape new and touched screens use: [destructive] fills the confirm with the
 * danger colour, and [isLoading] spins it while the action runs.
 */
@Composable
fun AppAlertDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String = "Cancel",
    destructive: Boolean = false,
    isLoading: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title, fontWeight = AppFontWeight.SemiBold) },
        text = if (message.isBlank()) null else {
            { Text(message, style = MaterialTheme.typography.bodyMedium) }
        },
        confirmButton = {
            PrimaryButton(
                text = confirmText,
                onClick = onConfirm,
                isLoading = isLoading,
                compact = true,
                containerColor = if (destructive) MaterialTheme.colorScheme.error else null,
            )
        },
        dismissButton = {
            LinkButton(text = dismissText, onClick = onDismiss)
        },
    )
}
