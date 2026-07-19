package com.example.cashbookbd.report

import com.example.cashbookbd.session.Permission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the Reports menu to the web app's rules (`Sidebar/index.tsx`).
 *
 * Regression guard for the bug where Profit Loss, Balance Sheet and both Trial
 * Balances also accepted `cashbook.view`, so any user who could open the
 * cashbook could read the company's P&L — reports the web correctly hid.
 */
class ReportMenuPermissionTest {

    private fun perms(vararg names: String) = names.map { Permission(name = it) }

    /**
     * Exactly the reports the web's Sidebar guards with `cashbook.view`. The two
     * collection reports genuinely accept it (`group.report || ledger.due.view ||
     * cashbook.view` on the web); no financial statement does.
     */
    @Test
    fun `cashbook_view unlocks only the cashbook and collection reports`() {
        val titles = ReportMenu.visible(perms("cashbook.view")).map { it.title }

        assertEquals(listOf("Cashbook", "Collection Sheet", "Monthly Report"), titles)
    }

    @Test
    fun `financial statements need their own permission`() {
        val guarded = mapOf(
            "Profit Loss" to "profit.loss",
            "Balance Sheet" to "balancesheet.view",
            "Trial Balance Group" to "trial.balance.l3",
            "Trial Balance Details" to "trial.balance.l4",
        )

        guarded.forEach { (title, permission) ->
            val withoutIt = ReportMenu.visible(perms("cashbook.view")).map { it.title }
            assertFalse("$title must stay hidden without '$permission'", title in withoutIt)

            val withIt = ReportMenu.visible(perms(permission)).map { it.title }
            assertTrue("$title must appear with '$permission'", title in withIt)
        }
    }

    /** The web guards Product In Out with `product.in.out` only. */
    @Test
    fun `product in out is not unlocked by ledger permissions`() {
        val titles = ReportMenu.visible(perms("ledger.view", "ledger.customer")).map { it.title }

        assertFalse("Product In Out" in titles)
    }

    /** Every route gate must read its rule from the registry, never a copy. */
    @Test
    fun `permissionsFor matches the registry and fails closed on a typo`() {
        assertEquals(listOf("profit.loss"), ReportMenu.permissionsFor("profitLoss"))
        assertFalse(ReportMenu.permissionsFor("noSuchReport").any { it in setOf("cashbook.view") })
        assertFalse(ReportMenu.hasParentAccess(perms("nothing.at.all")))
    }
}
