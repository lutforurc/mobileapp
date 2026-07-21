package com.example.cashbookbd.ui.reports.model

/**
 * One account line of the Cash & Bank summary. The API names the columns from
 * the cash/bank account's side, so `*_debit` is money **received** and
 * `*_credit` money **paid** — the opposite of what the names suggest.
 */
data class CashBankSummaryRow(
    val name: String,
    val cashReceived: Double,
    val cashPayment: Double,
    val bankReceived: Double,
    val bankPayment: Double,
)

/** One bank account's movement over the selected period. */
data class BankDetailRow(
    val bankName: String,
    val received: Double,
    val payment: Double,
) {
    /**
     * Movement, not balance: `bank_details` covers the selected period only and
     * excludes the opening balance, so this will not agree with the bank figures
     * in the summary table above it.
     */
    val movement: Double get() = received - payment
}

/**
 * The Cash & Bank (Received & Payment) report.
 *
 * The endpoint returns neither totals nor a balance row, so both are computed
 * here — and the totals deliberately include the "Opening Balance" row (the
 * first row the API prepends), exactly as the web report does.
 */
data class CashBankReport(
    val rows: List<CashBankSummaryRow>,
    val bankDetails: List<BankDetailRow>,
) {
    val totalCashReceived: Double get() = rows.sumOf { it.cashReceived }
    val totalCashPayment: Double get() = rows.sumOf { it.cashPayment }
    val totalBankReceived: Double get() = rows.sumOf { it.bankReceived }
    val totalBankPayment: Double get() = rows.sumOf { it.bankPayment }

    // Balance is the net of each pair, clamped at zero, so exactly one side of
    // Received/Payment carries a figure and the other shows a dash.
    val balanceCashReceived: Double get() = (totalCashReceived - totalCashPayment).coerceAtLeast(0.0)
    val balanceCashPayment: Double get() = (totalCashPayment - totalCashReceived).coerceAtLeast(0.0)
    val balanceBankReceived: Double get() = (totalBankReceived - totalBankPayment).coerceAtLeast(0.0)
    val balanceBankPayment: Double get() = (totalBankPayment - totalBankReceived).coerceAtLeast(0.0)

    val totalBankDetailReceived: Double get() = bankDetails.sumOf { it.received }
    val totalBankDetailPayment: Double get() = bankDetails.sumOf { it.payment }
    val totalBankDetailMovement: Double get() = bankDetails.sumOf { it.movement }

    /**
     * The API always prepends an Opening Balance row, so a report carrying only
     * that row has no activity to show.
     */
    val isEmpty: Boolean get() = rows.size <= 1 && bankDetails.isEmpty()
}
