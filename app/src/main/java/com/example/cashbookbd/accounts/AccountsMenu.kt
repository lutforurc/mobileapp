package com.example.cashbookbd.accounts

import com.example.cashbookbd.session.Permission
import com.example.cashbookbd.session.Permissions

/**
 * The six accounts screens the web app grew, and where each one hangs.
 *
 * Four of them live under the sidebar's **Transaction** group (bank
 * reconciliation, the cheque register, year closing, the budget) and two under
 * **Reports** (ageing, the audit trail) — the same split the web sidebar makes,
 * and the reason the route strings are prefixed differently.
 *
 * The constants live here rather than in `navigation.Routes` only because this
 * module was ported alongside a concurrent edit of that file; treat this object
 * as the registry the navigation graph and the drawer read from.
 */
data class AccountsItem(
    val key: String,
    val title: String,
    val route: String,
    /** The single backend permission the controller itself checks. */
    val permission: String,
)

object AccountsMenu {

    // ——— Routes ———
    const val ROUTE_BANK_RECONCILIATION = "accounts/bank-reconciliation"
    const val ROUTE_CHEQUE_REGISTER = "accounts/cheque-register"
    const val ROUTE_YEAR_CLOSING = "accounts/year-closing"
    const val ROUTE_BUDGET = "accounts/budget"
    const val ROUTE_AGEING = "reports/ageing"
    const val ROUTE_AUDIT_TRAIL = "reports/audit-trail"

    // ——— Permissions (exactly the controllers' own constants) ———
    const val PERM_BANK_RECONCILIATION = "bank.reconciliation.view"
    const val PERM_CHEQUE_REGISTER = "cheque.register.view"
    const val PERM_YEAR_CLOSING = "year.closing.run"
    const val PERM_BUDGET = "budget.view"
    const val PERM_AGEING = "ageing.report.view"
    const val PERM_AUDIT_TRAIL = "audit.trail.view"

    /** The Transaction-group entries, in the web sidebar's order. */
    val transactionItems: List<AccountsItem> = listOf(
        AccountsItem(
            key = "bankReconciliation",
            title = "Bank Reconciliation",
            route = ROUTE_BANK_RECONCILIATION,
            permission = PERM_BANK_RECONCILIATION,
        ),
        AccountsItem(
            key = "chequeRegister",
            title = "Cheque Register",
            route = ROUTE_CHEQUE_REGISTER,
            permission = PERM_CHEQUE_REGISTER,
        ),
        AccountsItem(
            key = "yearClosing",
            title = "Year Closing",
            route = ROUTE_YEAR_CLOSING,
            permission = PERM_YEAR_CLOSING,
        ),
        AccountsItem(
            key = "budget",
            title = "Budget",
            route = ROUTE_BUDGET,
            permission = PERM_BUDGET,
        ),
    )

    /** The Reports-group entries, in the web sidebar's order. */
    val reportItems: List<AccountsItem> = listOf(
        AccountsItem(
            key = "ageing",
            title = "Ageing",
            route = ROUTE_AGEING,
            permission = PERM_AGEING,
        ),
        AccountsItem(
            key = "auditTrail",
            title = "Audit Trail",
            route = ROUTE_AUDIT_TRAIL,
            permission = PERM_AUDIT_TRAIL,
        ),
    )

    val all: List<AccountsItem> = transactionItems + reportItems

    private val byKey: Map<String, AccountsItem> = all.associateBy { it.key }

    fun byKey(key: String?): AccountsItem? = key?.let { byKey[it] }

    /** The entries of [items] the user may open, in registry order. */
    fun visible(
        permissions: List<Permission>?,
        items: List<AccountsItem> = all,
    ): List<AccountsItem> = items.filter { Permissions.has(permissions, it.permission) }
}
