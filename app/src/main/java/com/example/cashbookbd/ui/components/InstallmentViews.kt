package com.example.cashbookbd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.ui.theme.accents
import com.example.cashbookbd.ui.transaction.model.InstallmentPayment

/**
 * The installment UI pieces the Installments screen and the Due Installments
 * report share — the status pill, the Receive dialog and the payments popup —
 * kept in one place so both render identically.
 */

/**
 * The status as a card-coloured pill. The theme's accent colours are picked to
 * be legible on the card surface, not on the open screen backdrop — bare
 * coloured text there was muddy in light mode, so the pill supplies the
 * surface the accents were designed for.
 */
@Composable
fun InstallmentStatusPill(status: String) {
    val accents = MaterialTheme.accents
    val color = when {
        status.equals("overdue", ignoreCase = true) -> accents.red
        status.equals("pending", ignoreCase = true) -> accents.blue
        status.lowercase().startsWith("upcoming") ||
            status.equals("due today", ignoreCase = true) ||
            status.equals("partial", ignoreCase = true) -> accents.amber
        else -> accents.green
    }
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = status.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** The web's InstallmentModal: amount + remarks, then the real receive POST. */
@Composable
fun InstallmentReceiveDialog(
    subtitle: String,
    amount: String,
    remarks: String,
    isSaving: Boolean,
    canSave: Boolean,
    onAmountChange: (String) -> Unit,
    onRemarksChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Received Installment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AppTextField(
                    value = amount,
                    onValueChange = onAmountChange,
                    label = "Amount",
                    caption = "Amount",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextField(
                    value = remarks,
                    onValueChange = onRemarksChange,
                    label = "Remarks",
                    caption = "Remarks",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            } else {
                LinkButton(text = "Save", onClick = onSave, enabled = canSave)
            }
        },
        dismissButton = { LinkButton(text = "Cancel", onClick = onDismiss) },
    )
}

/** The web's PaymentDetailsModal: the receipts already taken (Vr No / Date / Amount). */
@Composable
fun InstallmentPaymentsDialog(
    payments: List<InstallmentPayment>,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Installment Details") },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    PaymentsHeaderCell("Vr No", Modifier.weight(1.2f))
                    PaymentsHeaderCell("Date", Modifier.weight(1f))
                    PaymentsHeaderCell("Amount", Modifier.weight(1f), TextAlign.End)
                }
                HorizontalDivider()
                payments.forEach { payment ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(payment.vrNo.ifBlank { "-" }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.2f))
                        Text(payment.date.ifBlank { "-" }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(
                            AmountFormat.format(payment.amount),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Total amount paid: ${AmountFormat.format(payments.sumOf { it.amount })}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        confirmButton = { LinkButton(text = "Close", onClick = onClose) },
    )
}

@Composable
private fun PaymentsHeaderCell(text: String, modifier: Modifier, align: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        textAlign = align,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
