package com.example.cashbookbd.ui.reports.model

import com.example.cashbookbd.data.remote.dto.BankBookRowDto

/**
 * One line of the bank book.
 *
 * [received] and [payment] are already the right way round: the API calls them
 * `credit` and `debit`, which is inverted from what they mean here, and the
 * swap happens once — in [toBankBookRow] — so nothing downstream has to know.
 */
data class BankBookRow(
    /** Blank on the opening and summary rows, which carry no serial. */
    val serial: String,
    val date: String,
    val voucherNo: String,
    /** The account/party name, HTML stripped. */
    val title: String,
    /** The second line the backend packs into `nam` after a `</br>`, if any. */
    val subtitle: String,
    val remarks: String,
    /** Only present for business_type_id 8 branches. */
    val orderNumber: String,
    /** The bank the money moved through; blank on every summary row. */
    val bank: String,
    val received: Double,
    val payment: Double,
    /** The first row the backend prepends. */
    val isOpening: Boolean,
    /** The three rows the backend appends: range total, Total, Balance. */
    val isTotal: Boolean,
) {
    /** Opening and totals are both styled apart from the transactions. */
    val isSummary: Boolean get() = isOpening || isTotal
}

data class BankBookReport(
    val rows: List<BankBookRow>,
)

private val htmlTag = Regex("<[^>]*>")
private val lineBreak = Regex("(?i)</?br\\s*/?>")
private val whitespace = Regex("\\s+")

/**
 * Splits the `nam` field the backend decorates with HTML — e.g.
 * `<span>Party</span></br>Sales` — into its two lines, tags removed.
 */
private fun String?.splitHtmlLines(): Pair<String, String> {
    if (this.isNullOrBlank()) return "" to ""
    val parts = replace(lineBreak, "\n")
        .replace(htmlTag, "")
        .replace("&nbsp;", " ")
        .split("\n")
        .map { it.replace(whitespace, " ").trim() }
        .filter { it.isNotBlank() }
    return when (parts.size) {
        0 -> "" to ""
        1 -> parts[0] to ""
        else -> parts[0] to parts.drop(1).joinToString(" ")
    }
}

private fun String?.toAmount(): Double = this?.trim()?.replace(",", "")?.toDoubleOrNull() ?: 0.0

fun BankBookRowDto.toBankBookRow(): BankBookRow {
    val (title, subtitle) = particulars.splitHtmlLines()

    // The row kinds are told apart by `nam`: the backend prepends one
    // "Opening Balance" row and appends "<from> to <to> Total", "Total" and
    // "Balance". Those three carry no voucher number.
    val isOpening = title.equals("Opening Balance", ignoreCase = true)
    val isTotal = !isOpening && vrNo.isNullOrBlank() &&
        (title.endsWith("Total", ignoreCase = true) || title.equals("Balance", ignoreCase = true))

    // sl_number is 0 on the opening row and absent on the summary rows; neither
    // gets a serial.
    val serial = slNumber?.trim()?.takeUnless { it.isBlank() || it == "0" }.orEmpty()

    return BankBookRow(
        serial = serial,
        date = vrDate?.trim().orEmpty(),
        voucherNo = vrNo?.trim().orEmpty(),
        title = title,
        subtitle = subtitle,
        remarks = remarks?.trim().orEmpty(),
        orderNumber = orderNumber?.trim().orEmpty(),
        bank = fromBank?.trim().orEmpty(),
        // The inversion, mapped once: their credit is money in, debit money out.
        received = credit.toAmount(),
        payment = debit.toAmount(),
        isOpening = isOpening,
        isTotal = isTotal,
    )
}
