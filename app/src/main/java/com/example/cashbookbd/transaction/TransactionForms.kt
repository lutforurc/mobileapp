package com.example.cashbookbd.transaction

/** Which picker a transaction form field uses. */
enum class TxnPicker {
    /** Searchable Chart-of-Accounts level-4 account (`chart_of_accounts/ddl/l4-list`). */
    LEDGER,

    /** Bank account list (`coal3/l4-list/2`), loaded once. */
    BANK,

    /** Searchable employee (`hrms/employee/ddl/list`). */
    EMPLOYEE,
}

/** How the submit body is assembled + which endpoint it targets. */
enum class TxnKind {
    /** Bare JSON array of one row `[{account, amount, remarks, …}]` (cash received). */
    CASH_RECEIVED,
    CASH_PAYMENT,

    /** Object `{mtmId, bank*Account, transactions:[…]}`. */
    BANK_RECEIVED,
    BANK_PAYMENT,

    /** Object `{payer_code, receiver_code, amount, note}`. */
    JOURNAL,

    /** Object `{id, account, accountName, remarks, amount}` (employee id). */
    EMPLOYEE_LOAN,
}

/** One account/picker field on a transaction form, in display order. */
data class TxnField(
    val key: String,
    val label: String,
    val picker: TxnPicker,
)

/**
 * A single transaction entry form. The exact request body shape is chosen by
 * [kind]; the [fields] declare the account pickers to show (all required).
 */
data class TxnFormSpec(
    val key: String,
    val title: String,
    val kind: TxnKind,
    val endpoint: String,
    val fields: List<TxnField>,
    val remarksLabel: String = "Remarks",
    val amountLabel: String = "Amount (Tk.)",
)

/**
 * Registry of the transaction entry forms, mirroring the web voucher forms
 * (General variant). Amounts, cash/bank contra legs, dates and voucher numbers
 * are all derived server-side — the client only sends the account(s), amount and
 * remarks. "Installments" is handled by navigation (the Due Installments screen),
 * not a form here.
 */
object TransactionForms {

    val all: List<TxnFormSpec> = listOf(
        TxnFormSpec(
            key = "cashReceived",
            title = "Cash Received",
            kind = TxnKind.CASH_RECEIVED,
            endpoint = "trading/cash/received",
            fields = listOf(TxnField("account", "Cash Received Account", TxnPicker.LEDGER)),
        ),
        TxnFormSpec(
            key = "cashPayment",
            title = "Cash Payment",
            kind = TxnKind.CASH_PAYMENT,
            endpoint = "trading/cash/payment",
            fields = listOf(TxnField("account", "Select Account", TxnPicker.LEDGER)),
        ),
        TxnFormSpec(
            key = "bankReceived",
            title = "Bank Received",
            kind = TxnKind.BANK_RECEIVED,
            endpoint = "general/bank/received",
            fields = listOf(
                TxnField("bank", "Bank Received Account", TxnPicker.BANK),
                TxnField("account", "Select Transaction Account", TxnPicker.LEDGER),
            ),
        ),
        TxnFormSpec(
            key = "bankPayment",
            title = "Bank Payment",
            kind = TxnKind.BANK_PAYMENT,
            endpoint = "general/bank/payment",
            fields = listOf(
                TxnField("bank", "Bank Payment Account", TxnPicker.BANK),
                TxnField("account", "Select Transaction Account", TxnPicker.LEDGER),
            ),
        ),
        TxnFormSpec(
            key = "journal",
            title = "Journal",
            kind = TxnKind.JOURNAL,
            endpoint = "accounts/journal/store",
            fields = listOf(
                TxnField("payer", "Payer Account (Debit)", TxnPicker.LEDGER),
                TxnField("receiver", "Receiver Account (Credit)", TxnPicker.LEDGER),
            ),
            remarksLabel = "Note",
        ),
        TxnFormSpec(
            key = "employeeLoan",
            title = "Employee Loan",
            kind = TxnKind.EMPLOYEE_LOAN,
            endpoint = "hrms/loan/disbursement",
            fields = listOf(TxnField("account", "Select Employee", TxnPicker.EMPLOYEE)),
        ),
    )

    private val byKey: Map<String, TxnFormSpec> = all.associateBy { it.key }

    fun byKey(key: String?): TxnFormSpec? = key?.let { byKey[it] }
}
