package com.example.cashbookbd.navigation

import com.example.cashbookbd.admin.AdminMenu
import com.example.cashbookbd.customer.CustomerMenu
import com.example.cashbookbd.hotel.HotelMenu
import com.example.cashbookbd.hrm.HrmMenu
import com.example.cashbookbd.inventory.BranchTransferMenu
import com.example.cashbookbd.invoice.InvoiceMenu
import com.example.cashbookbd.products.ProductsMenu
import com.example.cashbookbd.realestate.RealEstateMenu
import com.example.cashbookbd.report.ReportMenu
import com.example.cashbookbd.transaction.TransactionMenu
import com.example.cashbookbd.vrsettings.VrSettingsMenu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the web's `SIDEBAR_SUB_DEFAULTS` port.
 *
 * A group key that names no entry in its registry is silently useless: the item
 * it meant to place keeps its declared order at the end of the section, under
 * no heading, and nothing fails. So every grouped key is checked against the
 * registry it belongs to, the way a typo would be caught.
 */
class MenuGroupsTest {

    /** Each menu id's declared entry keys — the registries the home screens draw. */
    private val declared: Map<String, Set<String>> = mapOf(
        "reports" to ReportMenu.all.map { it.key }.toSet(),
        "transaction" to TransactionMenu.all.map { it.key }.toSet(),
        "invoice" to InvoiceMenu.all.map { it.key }.toSet(),
        "branch-transfer" to BranchTransferMenu.all.map { it.key }.toSet(),
        "products" to ProductsMenu.all.map { it.key }.toSet(),
        "admin" to AdminMenu.all.map { it.key }.toSet(),
        "hrm" to HrmMenu.all.map { it.key }.toSet(),
        "real-estate" to RealEstateMenu.all.map { it.key }.toSet(),
        "hotel" to HotelMenu.all.map { it.key }.toSet(),
        "customer-supplier" to CustomerMenu.all.map { it.key }.toSet(),
        "vr_settings" to VrSettingsMenu.all.map { it.key }.toSet(),
    )

    /** No menu may carry a group map the test does not know how to check. */
    @Test
    fun `every grouped menu has a registry wired for it`() {
        MenuGroups.menus.keys.forEach { menuId ->
            assertNotNull("MenuGroups has no registry check for '$menuId'", declared[menuId])
        }
    }

    @Test
    fun `every grouped key names a real entry`() {
        MenuGroups.menus.forEach { (menuId, groups) ->
            val keys = declared.getValue(menuId)
            groups.forEach { group ->
                group.keys.forEach { key ->
                    assertTrue(
                        "MenuGroups '$menuId' names '$key' under '${group.title}', " +
                            "which no registry declares",
                        key in keys,
                    )
                }
            }
        }
    }

    /** A key listed twice would draw the same menu entry under two headings. */
    @Test
    fun `no key is grouped twice within one menu`() {
        MenuGroups.menus.forEach { (menuId, groups) ->
            val seen = mutableListOf<String>()
            groups.forEach { group -> seen += group.keys }
            assertEquals(
                "MenuGroups '$menuId' lists a key more than once",
                seen.size,
                seen.toSet().size,
            )
        }
    }
}
