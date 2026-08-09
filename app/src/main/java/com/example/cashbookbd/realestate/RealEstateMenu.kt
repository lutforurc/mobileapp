package com.example.cashbookbd.realestate

import com.example.cashbookbd.session.Permission
import com.example.cashbookbd.session.Permissions
import com.example.cashbookbd.session.WILDCARD_PERMISSION

/**
 * One entry in the Real Estate menu, mirroring the web sidebar's "Real Estate"
 * group. The whole section is shown only to business type 9 (Real Estate)
 * branches, like the web — see [visibleForBranch].
 */
data class RealEstateItem(
    val key: String,
    val title: String,
    val anyOf: List<String>,
)

/** The Real Estate menu registry and its permission rules. */
object RealEstateMenu {

    /** The web sidebar shows the whole section on this single permission. */
    private const val SECTION_PERMISSION = "real.estate.view"

    /** Branches of this business type get the section (web sidebar condition). */
    const val BUSINESS_TYPE_ID = 9

    // Native screen keys (the rest resolve in AppLists).
    const val UNIT_SALES_KEY = "unitSales"
    const val SOLD_UNITS_KEY = "soldUnits"
    const val INSTALLMENT_CREATE_KEY = "reInstallmentCreate"
    const val FLAT_LAYOUT_KEY = "flatLayout"
    const val PROJECT_EXPENSE_KEY = "projectExpense"
    const val PROJECT_PURCHASE_KEY = "projectPurchase"
    const val PROJECT_COST_REPORT_KEY = "projectCostReport"

    val all: List<RealEstateItem> = listOf(
        // Check Register carries its own permission on the web route.
        RealEstateItem("reCheckRegister", "Check Register", listOf("check.register.view")),
        RealEstateItem("reAreas", "Location", listOf(SECTION_PERMISSION)),
        RealEstateItem("reProjects", "Projects", listOf(SECTION_PERMISSION)),
        RealEstateItem("reBuildings", "Buildings", listOf(SECTION_PERMISSION)),
        RealEstateItem("reFloors", "Floor List", listOf(SECTION_PERMISSION)),
        RealEstateItem("reUnits", "Unit List", listOf(SECTION_PERMISSION)),
        RealEstateItem("reChargeTypes", "Charges", listOf(SECTION_PERMISSION)),
        RealEstateItem(FLAT_LAYOUT_KEY, "Layout", listOf(SECTION_PERMISSION)),
        RealEstateItem(UNIT_SALES_KEY, "Unit Sales", listOf(SECTION_PERMISSION)),
        RealEstateItem(SOLD_UNITS_KEY, "Sold Units", listOf(SECTION_PERMISSION)),
        RealEstateItem(INSTALLMENT_CREATE_KEY, "Installment Create", listOf(SECTION_PERMISSION)),
        // The project-and-building cost batch (web sidebar order: last).
        RealEstateItem(PROJECT_EXPENSE_KEY, "Project Expense", listOf(SECTION_PERMISSION)),
        RealEstateItem(PROJECT_PURCHASE_KEY, "Project Purchase", listOf(SECTION_PERMISSION)),
        RealEstateItem(PROJECT_COST_REPORT_KEY, "Project Cost Report", listOf(SECTION_PERMISSION)),
    )

    private val byKey: Map<String, RealEstateItem> = all.associateBy { it.key }

    fun byKey(key: String?): RealEstateItem? = key?.let { byKey[it] }

    /** True when the user can see the Real Estate parent section at all. */
    fun hasParentAccess(permissions: List<Permission>?): Boolean =
        Permissions.hasAny(permissions, listOf(SECTION_PERMISSION))

    /** True when this branch's business type gets the section (web: type 9 only). */
    fun visibleForBranch(businessTypeId: Int?): Boolean = businessTypeId == BUSINESS_TYPE_ID

    /** Real Estate entries the user is allowed to open, in registry order. */
    fun visible(permissions: List<Permission>?): List<RealEstateItem> =
        all.filter { Permissions.hasAny(permissions, it.anyOf) }

    /**
     * The permissions guarding item [key], for route-level gates. An unknown key
     * falls back to the full-access wildcard — fail closed, like ReportMenu.
     */
    fun permissionsFor(key: String): List<String> =
        byKey(key)?.anyOf ?: listOf(WILDCARD_PERMISSION)
}
