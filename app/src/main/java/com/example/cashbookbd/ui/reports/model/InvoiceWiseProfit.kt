package com.example.cashbookbd.ui.reports.model

import com.example.cashbookbd.core.AmountFormat

/**
 * One invoice as `POST reports/invoice-wise-profit` answers it.
 *
 * [voucherDate] is the server's `dd/mm/yyyy` string and is printed as sent.
 * `profit` is net sales minus the FIFO cost of the lots the sale consumed — it
 * does not depend on how much the customer has paid (web `f0028a3b`).
 */
data class InvoiceWiseProfitRow(
    val sl: Int,
    val mid: Long?,
    val voucherNo: String,
    val voucherDate: String,
    val customerName: String,
    val invoiceAmount: Double,
    val receivedAmount: Double,
    val discount: Double,
    val profit: Double,
)

/** The server's foot over the whole filtered set, not just the visible page. */
data class InvoiceWiseProfitSummary(
    val invoiceAmount: Double = 0.0,
    val receivedAmount: Double = 0.0,
    val discount: Double = 0.0,
    val profit: Double = 0.0,
)

data class InvoiceWiseProfitPagination(
    val page: Int = 1,
    val perPage: Int = 10,
    val total: Int = 0,
    val lastPage: Int = 1,
)

data class InvoiceWiseProfitReport(
    val rows: List<InvoiceWiseProfitRow> = emptyList(),
    val summary: InvoiceWiseProfitSummary = InvoiceWiseProfitSummary(),
    val pagination: InvoiceWiseProfitPagination = InvoiceWiseProfitPagination(),
) {
    val isEmpty: Boolean get() = rows.isEmpty()
}

/**
 * An amount in this report: the branch's decimal places, a zero written "-" —
 * the web's `thousandSeparator`.
 */
fun invoiceWiseProfitAmountText(value: Double): String = AmountFormat.formatOrDash(value)

/**
 * The Received Amt cell. An invoice that took nothing is written "0.00" (at
 * least two decimals) rather than a dash, because the column answers "how much
 * of this bill was paid" and a dash would read as "not applicable" (web
 * `formatReceivedAmount`).
 */
fun invoiceWiseProfitReceivedText(
    value: Double,
    places: Int = AmountFormat.current,
): String = if (value == 0.0) {
    AmountFormat.format(0.0, maxOf(places, 2))
} else {
    AmountFormat.format(value)
}
