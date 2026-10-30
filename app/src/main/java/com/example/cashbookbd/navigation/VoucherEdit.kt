package com.example.cashbookbd.navigation

import androidx.navigation.NavHostController
import com.example.cashbookbd.session.Permission
import com.example.cashbookbd.session.Permissions

/** The screen a report row's voucher is edited on. */
enum class VoucherEditTarget {
    CASH_RECEIVED, CASH_PAYMENT, BANK_RECEIVED, BANK_PAYMENT, EMPLOYEE_LOAN,
    SALES, PURCHASE, SALES_RETURN, PURCHASE_RETURN, COMBINED,
}

/** What a report row knows about its voucher — the facts the web's Edit reads. */
data class VoucherEditRow(
    val vrNo: String,
    /** The receipt a tiles discount journal belongs to; blank = the row edits itself. */
    val editVrNo: String = "",
    /** A 1-/2- voucher whose money moved through the bank (server-decided). */
    val isBankVoucher: Boolean = false,
    val isApproved: Boolean = false,
    val combinedNumber: String = "",
    /** A screen the number cannot name (`employee_loan`). */
    val editScreen: String = "",
)

/**
 * Report → voucher edit, ported from the web's voucherEditPermission.ts and
 * voucherEditNavigation.ts. The voucher number's prefix names the type; the
 * row's `is_bank_voucher` picks the bank pair for 1-/2-; `edit_screen` wins
 * over the prefix. The number travels to the form through [handOff] — a
 * one-shot like the web's navigation state — and the form loads it itself.
 */
object VoucherEdit {

    /** Kinds with an update endpoint. A journal (5-) has none, so no button. */
    private val EDITABLE_PREFIXES = setOf("1", "2", "3", "4", "9", "10", "12", "13")

    fun prefix(vrNo: String): String = vrNo.trim().substringBefore('-').trim()

    fun editableNo(row: VoucherEditRow): String = row.editVrNo.trim().ifBlank { row.vrNo.trim() }

    /** The client's mirror of the server's voucherEditPermission(). */
    fun permission(row: VoucherEditRow): String? = when (prefix(editableNo(row))) {
        "1" -> if (row.isBankVoucher) "bank.received.edit" else "cash.received.edit"
        "2" -> if (row.isBankVoucher) "bank.payment.edit" else "cash.payment.edit"
        "3", "10" -> "sales.edit"
        "4", "9" -> "purchase.edit"
        "5" -> "journal.edit"
        "12" -> "purchase.return.edit"
        "13" -> "sales.return.edit"
        else -> null
    }

    /** Each kind answers its own permission; an approved voucher shows no Edit. */
    fun canEdit(permissions: List<Permission>?, row: VoucherEditRow): Boolean {
        if (row.isApproved) return false
        if (prefix(editableNo(row)) !in EDITABLE_PREFIXES) return false
        val permission = permission(row) ?: return false
        return Permissions.has(permissions, permission)
    }

    /** Where the row's voucher is edited: its own screen first, then the prefix. */
    fun target(row: VoucherEditRow): VoucherEditTarget? {
        if (row.combinedNumber.isNotBlank()) return VoucherEditTarget.COMBINED
        if (row.editScreen == "employee_loan") return VoucherEditTarget.EMPLOYEE_LOAN
        return targetForNumber(editableNo(row), row.isBankVoucher)
    }

    fun targetForNumber(vrNo: String, bank: Boolean = false): VoucherEditTarget? = when (prefix(vrNo)) {
        "1" -> if (bank) VoucherEditTarget.BANK_RECEIVED else VoucherEditTarget.CASH_RECEIVED
        "2" -> if (bank) VoucherEditTarget.BANK_PAYMENT else VoucherEditTarget.CASH_PAYMENT
        // A credit sale (10-) / credit purchase (9-) is edited on the ordinary screen.
        "3", "10" -> VoucherEditTarget.SALES
        "4", "9" -> VoucherEditTarget.PURCHASE
        "12" -> VoucherEditTarget.PURCHASE_RETURN
        "13" -> VoucherEditTarget.SALES_RETURN
        else -> null
    }

    /**
     * The edit lookup's type for a purchase: a 9- credit purchase was booked
     * as a journal (5); a paid one as a payment (2) — the web's default.
     */
    fun purchaseType(vrNo: String): String = if (prefix(vrNo) == "9") "5" else "2"

    /** And the selling side: a 10- credit sale is a journal (5), else a receipt (1). */
    fun salesType(vrNo: String): String = if (prefix(vrNo) == "10") "5" else "1"

    /** The line every edit form shows over a loaded voucher. */
    fun note(vrNo: String): String = "Editing voucher ${vrNo.trim()} — Save is now Update."

    private val pending = mutableMapOf<VoucherEditTarget, String>()

    /** Leaves [number] for [target]'s screen to pick up once. */
    fun handOff(target: VoucherEditTarget, number: String) = synchronized(pending) {
        pending[target] = number.trim()
    }

    /** The number left for [target], consumed — a second screen gets nothing. */
    fun take(target: VoucherEditTarget): String? = synchronized(pending) { pending.remove(target) }

    /**
     * Opens the row's voucher on its own screen. [isTiles] picks the Tiles and
     * Sanitary invoice screens, as the Invoice menu does. Returns a message
     * when the voucher has no screen here, else null.
     */
    fun open(navController: NavHostController, row: VoucherEditRow, isTiles: Boolean): String? {
        val target = target(row) ?: return "Edit route not found for this voucher."
        val number = if (target == VoucherEditTarget.COMBINED) row.combinedNumber else editableNo(row)
        if (number.isBlank()) return "Edit route not found for this voucher."
        if (target == VoucherEditTarget.COMBINED && isTiles) {
            return "The Tiles Combined Invoice is edited on the web."
        }
        handOff(target, number)
        navController.navigate(route(target, isTiles))
        return null
    }

    fun route(target: VoucherEditTarget, isTiles: Boolean): String = when (target) {
        VoucherEditTarget.CASH_RECEIVED -> Routes.txnView("cashReceived")
        VoucherEditTarget.CASH_PAYMENT -> Routes.txnView("cashPayment")
        VoucherEditTarget.BANK_RECEIVED -> Routes.txnView("bankReceived")
        VoucherEditTarget.BANK_PAYMENT -> Routes.txnView("bankPayment")
        VoucherEditTarget.EMPLOYEE_LOAN -> Routes.txnView("employeeLoan")
        VoucherEditTarget.SALES -> if (isTiles) ParityRoutes.TILES_SALES else Routes.invoiceView("sales")
        VoucherEditTarget.PURCHASE -> if (isTiles) ParityRoutes.TILES_PURCHASE else Routes.invoiceView("purchase")
        VoucherEditTarget.SALES_RETURN -> if (isTiles) ParityRoutes.TILES_SALES_RETURN else Routes.invoiceView("salesReturn")
        VoucherEditTarget.PURCHASE_RETURN -> if (isTiles) ParityRoutes.TILES_PURCHASE_RETURN else Routes.invoiceView("purchaseReturn")
        VoucherEditTarget.COMBINED -> Routes.COMBINED_INVOICE
    }
}
