package com.example.cashbookbd.navigation

import com.example.cashbookbd.session.Permission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The client's mirror of the server's voucherEditPermission() must not drift. */
class VoucherEditTest {

    private fun perms(vararg names: String) = names.map { Permission(name = it) }

    @Test
    fun eachKindAnswersItsOwnPermission() {
        assertEquals("cash.received.edit", VoucherEdit.permission(VoucherEditRow("1-10")))
        assertEquals("bank.received.edit", VoucherEdit.permission(VoucherEditRow("1-10", isBankVoucher = true)))
        assertEquals("bank.payment.edit", VoucherEdit.permission(VoucherEditRow("2-10", isBankVoucher = true)))
        assertEquals("sales.edit", VoucherEdit.permission(VoucherEditRow("10-4")))
        assertEquals("purchase.edit", VoucherEdit.permission(VoucherEditRow("9-4")))
        assertEquals("purchase.return.edit", VoucherEdit.permission(VoucherEditRow("12-1")))
        assertEquals("sales.return.edit", VoucherEdit.permission(VoucherEditRow("13-1")))
        assertNull(VoucherEdit.permission(VoucherEditRow("23-1")))
    }

    @Test
    fun editButtonFollowsPermissionApprovalAndEndpoint() {
        assertTrue(VoucherEdit.canEdit(perms("sales.edit"), VoucherEditRow("3-1")))
        // One permission does not cover every kind.
        assertFalse(VoucherEdit.canEdit(perms("sales.edit"), VoucherEditRow("4-1")))
        assertFalse(VoucherEdit.canEdit(perms("sales.edit"), VoucherEditRow("3-1", isApproved = true)))
        // A journal has no update endpoint, whatever its permission.
        assertFalse(VoucherEdit.canEdit(perms("journal.edit"), VoucherEditRow("5-1")))
        // A tiles discount journal edits through the receipt it names.
        assertTrue(VoucherEdit.canEdit(perms("cash.received.edit"), VoucherEditRow("5-1", editVrNo = "1-7")))
    }

    @Test
    fun targetsAndLookupTypes() {
        assertEquals(VoucherEditTarget.BANK_PAYMENT, VoucherEdit.target(VoucherEditRow("2-1", isBankVoucher = true)))
        assertEquals(VoucherEditTarget.EMPLOYEE_LOAN, VoucherEdit.target(VoucherEditRow("2-1", editScreen = "employee_loan")))
        assertEquals(VoucherEditTarget.COMBINED, VoucherEdit.target(VoucherEditRow("4-1", combinedNumber = "C-1")))
        assertEquals("5", VoucherEdit.purchaseType("9-3"))
        assertEquals("2", VoucherEdit.purchaseType("4-3"))
        assertEquals("5", VoucherEdit.salesType("10-3"))
        assertEquals("1", VoucherEdit.salesType("3-3"))
    }

    @Test
    fun handOffIsTakenOnce() {
        VoucherEdit.handOff(VoucherEditTarget.SALES, " 3-9 ")
        assertEquals("3-9", VoucherEdit.take(VoucherEditTarget.SALES))
        assertNull(VoucherEdit.take(VoucherEditTarget.SALES))
    }
}
