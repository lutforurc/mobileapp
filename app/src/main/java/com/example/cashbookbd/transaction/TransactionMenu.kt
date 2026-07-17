package com.example.cashbookbd.transaction

import com.example.cashbookbd.session.MenuPermissions
import com.example.cashbookbd.session.Permission
import com.example.cashbookbd.session.Permissions

/**
 * One entry in the Transaction menu, mirroring the web sidebar's "Transaction"
 * group. Each item is gated by its own create permission; the parent section is
 * shown when the user holds any transaction child permission.
 */
data class TransactionItem(
    val key: String,
    val title: String,
    val anyOf: List<String>,
)

/** The Transaction menu registry and its permission rules. */
object TransactionMenu {

    val all: List<TransactionItem> = listOf(
        TransactionItem("cashReceived", "Cash Received", listOf("cash.received.create")),
        TransactionItem("installments", "Installments", listOf("installment.create")),
        TransactionItem("cashPayment", "Cash Payment", listOf("cash.payment.create")),
        TransactionItem("bankReceived", "Bank Received", listOf("bank.received.create")),
        TransactionItem("bankPayment", "Bank Payment", listOf("bank.payment.create")),
        TransactionItem("employeeLoan", "Employee Loan", listOf("hrm.loan.create")),
        TransactionItem("journal", "Journal", listOf("journal.create")),
    )

    private val byKey: Map<String, TransactionItem> = all.associateBy { it.key }

    fun byKey(key: String?): TransactionItem? = key?.let { byKey[it] }

    /** True when the user can see the Transaction parent section at all. */
    fun hasParentAccess(permissions: List<Permission>?): Boolean =
        MenuPermissions.hasMenu(permissions, "transaction")

    /** Transaction entries the user is allowed to open, in registry order. */
    fun visible(permissions: List<Permission>?): List<TransactionItem> =
        all.filter { Permissions.hasAny(permissions, it.anyOf) }
}
