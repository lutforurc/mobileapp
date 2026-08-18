package com.example.cashbookbd.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.data.repository.StockShortageWarning
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.accents

/**
 * The question a voucher asks before it overdraws the stock — the web's
 * StockShortageModal, asked the same way by every screen that needs it.
 *
 * Each shortage is listed with what is on hand against what is being asked
 * for, coloured apart, because "not enough stock" on its own tells a seller
 * nothing they can act on — they need to see by how much, and for which
 * product.
 *
 * Where the branch blocks, the same figures appear with no Continue button at
 * all — not a disabled one, because a way forward that would be refused anyway
 * reads as a choice and teaches people the button lies.
 *
 * @param action What the operator is about to do: "sell", "transfer".
 */
@Composable
fun StockShortageDialog(
    warning: StockShortageWarning,
    action: String,
    onCancel: () -> Unit,
    onContinue: () -> Unit,
) {
    val closing = if (warning.blocked) {
        "This branch does not allow selling below stock."
    } else {
        "You can $action anyway."
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Not Enough Stock") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    warning.rows.isNotEmpty() -> {
                        Text("Not enough stock for", style = MaterialTheme.typography.bodyMedium)
                        warning.rows.forEachIndexed { i, row ->
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = AppFontWeight.SemiBold)) {
                                        append("${i + 1}. ${row.name}")
                                    }
                                    append(" — ")
                                    withStyle(
                                        SpanStyle(
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = AppFontWeight.SemiBold,
                                        )
                                    ) {
                                        append("Available ${AmountFormat.format(row.available, 0)}")
                                    }
                                    append(", ")
                                    withStyle(
                                        SpanStyle(
                                            color = MaterialTheme.accents.amber,
                                            fontWeight = AppFontWeight.SemiBold,
                                        )
                                    ) {
                                        append("Requested ${AmountFormat.format(row.requested, 0)}")
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(closing, style = MaterialTheme.typography.bodyMedium)
                    }

                    warning.shortages.isNotEmpty() -> {
                        Text("Not enough stock for", style = MaterialTheme.typography.bodyMedium)
                        warning.shortages.forEachIndexed { i, line ->
                            Text("${i + 1}. $line", style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(closing, style = MaterialTheme.typography.bodyMedium)
                    }

                    else -> Text(warning.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            // A blocked voucher is shown the figures and nothing else.
            if (!warning.blocked) {
                LinkButton(text = "Continue Save", onClick = onContinue)
            }
        },
        dismissButton = {
            LinkButton(text = if (warning.blocked) "Close" else "Cancel", onClick = onCancel)
        },
    )
}
