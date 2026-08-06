package com.example.cashbookbd.ui.transaction

import com.example.cashbookbd.data.repository.CashVoucherLine
import com.example.cashbookbd.data.repository.TxnSelection
import com.example.cashbookbd.transaction.TxnField
import com.example.cashbookbd.ui.reports.model.SelectorOption

data class TransactionFormUiState(
    val title: String = "Transaction",
    val isSupported: Boolean = true,
    val fields: List<TxnField> = emptyList(),
    val remarksLabel: String = "Remarks",
    val amountLabel: String = "Amount (Tk.)",

    /** Chosen account per field key. */
    val selections: Map<String, TxnSelection> = emptyMap(),
    val amount: String = "",
    val remarks: String = "",

    /** Bank-account dropdown options (only when a BANK field is present). */
    val bankAccounts: List<SelectorOption> = emptyList(),
    val isBankLoading: Boolean = false,
    val bankError: String? = null,

    /**
     * Product tracking (bank forms): the party-scoped product options and the
     * picked one. Empty options hide the dropdown — the form never breaks when
     * the branch doesn't track products.
     */
    val trackedProducts: List<SelectorOption> = emptyList(),
    val trackedProduct: SelectorOption? = null,

    /**
     * Bank forms only: the web's multi-row batch — "Add New" collects rows
     * here and Save posts them as one voucher. Other forms leave it empty.
     */
    val isBankBatch: Boolean = false,
    val batchTotalLabel: String = "Total",
    val lines: List<CashVoucherLine> = emptyList(),

    val isSubmitting: Boolean = false,
    /** Transient result banner; [isError] chooses success vs error styling. */
    val message: String? = null,
    val isError: Boolean = false,

    val sessionExpired: Boolean = false,
) {
    val canSubmit: Boolean
        get() = when {
            !isSupported || isSubmitting -> false
            // Bank: the bank account plus at least one added row — like the
            // web, which saves only what was added to the table.
            isBankBatch -> selections["bank"] != null && lines.isNotEmpty()
            else -> fields.all { selections[it.key] != null } &&
                (amount.toDoubleOrNull() ?: 0.0) > 0.0
        }

    /** Add New's own rule: an account and a positive amount for the row. */
    val canAddLine: Boolean
        get() = selections["account"] != null && (amount.toDoubleOrNull() ?: 0.0) > 0.0

    val linesTotal: Double get() = lines.sumOf { it.amount }
}
