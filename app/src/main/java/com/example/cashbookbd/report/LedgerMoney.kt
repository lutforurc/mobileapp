package com.example.cashbookbd.report

/**
 * The three money figures Ledger Details prints for one row, after the web's
 * rules (`ledgerWithProductUtils.ts`, commits c28dc0e1 / 333f6a6e / d3d4eaff).
 *
 * [isInvoiceRow] is true for a Sales or Purchase voucher that is not the
 * synthetic Opening row: those rows move their settlement leg into
 * [invoiceAmount] and leave the Debit/Credit cells blank, so the same money is
 * not counted twice.
 */
data class LedgerMoney(
    val debit: Double,
    val credit: Double,
    val invoiceAmount: Double,
    val isInvoiceRow: Boolean,
)

/** The raw figures one Ledger Details row carries. */
data class LedgerMoneyInput(
    val vrNo: String,
    val remarks: String,
    /** `trx_type` as the server sends it: Sales / Purchase / Transaction / Opening. */
    val trxType: String,
    /** `voucher_type` (the transaction type id), or 0 when absent. */
    val voucherType: Double,
    val total: Double,
    val received: Double,
    val payment: Double,
    val debit: Double,
    val credit: Double,
    val balance: Double,
    /** `displayed_received` when the server sent one, else null. */
    val displayedReceived: Double? = null,
    /** `displayed_payment` when the server sent one, else null. */
    val displayedPayment: Double? = null,
)

/** True for the report's synthetic first row. */
internal fun isOpeningRow(vrNo: String, remarks: String): Boolean =
    vrNo.equals("opening", ignoreCase = true) ||
        remarks.contains("opening balance", ignoreCase = true)

/**
 * Mirrors the web helpers step for step. Kept pure (no JSON, no Android types)
 * so the arithmetic can be tested without a device.
 */
fun ledgerMoney(input: LedgerMoneyInput): LedgerMoney {
    val opening = isOpeningRow(input.vrNo, input.remarks)
    val prefix = input.vrNo.substringBefore('-').trim()
    val voucherPrefix = prefix.toIntOrNull()?.toString() ?: prefix
    val voucherType = input.voucherType
    // getVoucherSideAmount: payment when there is one, else received.
    val side = if (input.payment != 0.0) input.payment else input.received

    // getDisplayedDebitValue / getDisplayedCreditValue. 1 is a receipt
    // (credit), 2 a payment (debit); any other kind falls back to the money
    // legs the server sent.
    val displayedDebit = if (opening) {
        if (input.debit != 0.0) input.debit else if (input.balance > 0) input.balance else 0.0
    } else when (voucherType) {
        2.0 -> side
        1.0 -> 0.0
        else -> input.displayedReceived ?: input.received
    }
    val displayedCredit = if (opening) {
        if (input.credit != 0.0) input.credit else if (input.balance < 0) -input.balance else 0.0
    } else when (voucherType) {
        1.0 -> side
        2.0 -> 0.0
        else -> input.displayedPayment ?: input.payment
    }

    // getBalanceDebitValue / getBalanceCreditValue: the bill's total is folded
    // onto its own side for the old 3/4 prefixes only. A credit sale or credit
    // purchase (10 / 9) already arrives in debit/credit.
    val balanceDebit = if (opening) {
        displayedDebit
    } else {
        (if (voucherPrefix == "3" && input.total > 0) input.total else 0.0) + displayedDebit
    }
    val balanceCredit = if (opening) {
        displayedCredit
    } else {
        (if (voucherPrefix == "4" && input.total > 0) input.total else 0.0) + displayedCredit
    }

    // isPurchaseRow / isSalesRow: ask trx_type first; the prefix is only the
    // fallback for a server too old to send it.
    val trxType = input.trxType.trim().lowercase(java.util.Locale.US)
    val isPurchase = if (trxType.isNotEmpty()) trxType == "purchase" else voucherPrefix == "4"
    val isSales = if (trxType.isNotEmpty()) trxType == "sales" else voucherPrefix == "3"
    val isInvoice = !opening && (isPurchase || isSales)

    // getInvoiceAmountValue: signed, so the footer is net cash across invoices.
    val invoiceAmount = when {
        isPurchase -> -balanceDebit
        isSales -> balanceCredit
        else -> 0.0
    }

    return LedgerMoney(
        debit = balanceDebit,
        credit = balanceCredit,
        invoiceAmount = invoiceAmount,
        isInvoiceRow = isInvoice,
    )
}
