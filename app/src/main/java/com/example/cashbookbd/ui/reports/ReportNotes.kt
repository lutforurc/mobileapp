package com.example.cashbookbd.ui.reports

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors

/*
 * The numbered notes under the Balance Sheet, Profit & Loss and Trial Balance
 * (web ReportNotes.tsx, 790659a3 / 7648427f). A note exists only where a row
 * points at it, and the rows carry "[n]" from the same index the list is
 * numbered from, so a pointer cannot disagree with the note beneath it.
 */

/** One note: the number the rows point at, its title and its paragraph. */
data class ReportNoteItem(val n: Int, val title: String, val body: String)

/** A row's pointer at the note that explains it. */
fun noteRef(n: Int?): String = if (n == null) "" else " [$n]"

/**
 * The note on stock that changed hands with no purchase and no sale behind it
 * — write-offs out, Product In receipts in. Two amounts, never a net; null
 * when neither moved, so leaving it out moves no other note's number.
 */
fun stockMovementNote(inventoryLoss: Double, stockReceipts: Double, closing: String): Pair<String, String>? {
    val loss = inventoryLoss
    val receipts = stockReceipts
    if (loss <= 0 && receipts <= 0) return null
    val title = when {
        loss > 0 && receipts > 0 -> "Stock moved without a purchase or a sale"
        loss > 0 -> "Inventory loss — damaged or lost goods"
        else -> "Goods received without a purchase"
    }
    val body = buildString {
        if (loss > 0) {
            append("Goods written off as damaged or lost in this period are valued at ${AmountFormat.format(loss)}, ")
            append("at the cost recorded on each write-off. ")
        }
        if (receipts > 0) {
            append("Goods received in the same period without being bought are valued at ${AmountFormat.format(receipts)}, ")
            append("at the rate the books held for them. ")
        }
        append(closing)
    }
    return title to body
}

/** "Notes to the …": a bordered panel of the numbered, justified notes. */
@Composable
fun ReportNotesPanel(title: String, notes: List<ReportNoteItem>, modifier: Modifier = Modifier) {
    if (notes.isEmpty()) return
    val ink = MaterialTheme.appColors.textOnScreen
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = AppFontWeight.SemiBold, color = ink)
        notes.forEach { note ->
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = AppFontWeight.SemiBold)) { append("${note.n}. ${note.title}") }
                    append(" ")
                    append(note.body)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.textOnScreenMuted,
                textAlign = TextAlign.Justify,
            )
        }
    }
}
