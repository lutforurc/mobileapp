package com.example.cashbookbd.ui.transaction

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

    val isSubmitting: Boolean = false,
    /** Transient result banner; [isError] chooses success vs error styling. */
    val message: String? = null,
    val isError: Boolean = false,

    val sessionExpired: Boolean = false,
) {
    val canSubmit: Boolean
        get() = isSupported &&
            !isSubmitting &&
            fields.all { selections[it.key] != null } &&
            (amount.toDoubleOrNull() ?: 0.0) > 0.0
}
