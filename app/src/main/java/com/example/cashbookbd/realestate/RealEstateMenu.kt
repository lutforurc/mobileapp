package com.example.cashbookbd.realestate

import com.example.cashbookbd.session.Permission
import com.example.cashbookbd.session.Permissions
import com.example.cashbookbd.session.WILDCARD_PERMISSION

/**
 * One entry in the Real Estate menu, mirroring the web sidebar's "Real Estate"
 * group. The whole section is shown only to business type 9 (Real Estate)
 * branches, like the web â€” see [visibleForBranch].
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
    const val PROJECT_LABOUR_KEY = "projectLabour"
    const val PROJECT_INCOME_KEY = "projectIncome"
    const val PROJECT_SUMMARY_KEY = "reProjectSummary"
    const val PROJECT_COST_REPORT_KEY = "projectCostReport"
    const val PROJECT_INCOME_REPORT_KEY = "projectIncomeReport"
    const val SALES_SUMMARY_KEY = "reSalesSummary"

    val all: List<RealEstateItem> = listOf(
        // Each screen answers to a permission of its own since api 758e5b79 /
        // react fac73c8a â€” the menu used to be gated as a whole, so whoever
        // could open it could open every screen in it. The migration SQL
        // grants each new permission to exactly the roles that already hold
        // real.estate.view, so nobody gains or loses access on the day it
        // runs; what changes is that a screen can now be taken away alone.
        // Check Register carries its own permission on the web route.
        RealEstateItem("reCheckRegister", "Check Register", listOf("check.register.view")),
        // The web sidebar has no Location entry â€” this mobile-only list keeps
        // the section's base permission.
        RealEstateItem("reAreas", "Location", listOf(SECTION_PERMISSION)),
        RealEstateItem("reProjects", "Projects", listOf("real.estate.project.view")),
        RealEstateItem("reBuildings", "Buildings", listOf("real.estate.building.view")),
        RealEstateItem("reFloors", "Floor List", listOf("real.estate.floor.view")),
        RealEstateItem("reUnits", "Unit List", listOf("real.estate.unit.view")),
        RealEstateItem("reChargeTypes", "Charges", listOf("real.estate.charge.view")),
        RealEstateItem(FLAT_LAYOUT_KEY, "Layout", listOf("real.estate.layout.view")),
        RealEstateItem(UNIT_SALES_KEY, "Unit Sales", listOf("real.estate.unit.sale.view")),
        RealEstateItem(SOLD_UNITS_KEY, "Sold Units", listOf("real.estate.sold.unit.view")),
        // The sales book of a project read end to end â€” its own permission, so
        // reading what every buyer paid is not handed out with the right to
        // enter a sale. Web sidebar order: right before Installment Create.
        RealEstateItem(SALES_SUMMARY_KEY, "Sales Summary", listOf("real.estate.sales.summary")),
        // The one entry the web left ungated â€” it rides the menu's own base.
        RealEstateItem(INSTALLMENT_CREATE_KEY, "Installment Create", listOf(SECTION_PERMISSION)),
        // The project-and-building cost batch (web sidebar order: last).
        RealEstateItem(PROJECT_EXPENSE_KEY, "Project Expense", listOf("real.estate.project.expense.view")),
        RealEstateItem(PROJECT_PURCHASE_KEY, "Project Purchase", listOf("real.estate.project.purchase.view")),
        RealEstateItem(PROJECT_LABOUR_KEY, "Project Labour", listOf("real.estate.project.labour.view")),
        // Income has an entry of its own rather than taking over Cash Received
        // the way Project Expense took over Cash Payment: most of what a
        // real-estate branch takes in is a unit sale, which has its own screens.
        RealEstateItem(PROJECT_INCOME_KEY, "Project Income", listOf("real.estate.project.income.view")),
        // The all-projects overview (web 92b3798): sits right before the cost
        // report, as in the web sidebar.
        RealEstateItem(PROJECT_SUMMARY_KEY, "Project Summary", listOf("real.estate.project.summary.view")),
        RealEstateItem(PROJECT_COST_REPORT_KEY, "Project Cost Report", listOf("real.estate.project.cost.view")),
        RealEstateItem(PROJECT_INCOME_REPORT_KEY, "Project Income Report", listOf("real.estate.project.income.report.view")),
    )

    private val byKey: Map<String, RealEstateItem> = all.associateBy { it.key }

    fun byKey(key: String?): RealEstateItem? = key?.let { byKey[it] }

    /**
     * True when the user can see the Real Estate parent section at all — any
     * child permission suffices (the web's menuPermissions real_estate union,
     * fac73c8a), so a role granted only one screen still gets the menu it
     * lives in. real.estate.view stays in the union: it is what opened the
     * whole module before the screens had permissions of their own.
     */
    fun hasParentAccess(permissions: List<Permission>?): Boolean =
        Permissions.hasAny(permissions, listOf(SECTION_PERMISSION) + all.flatMap { it.anyOf })

    /** True when this branch's business type gets the section (web: type 9 only). */
    fun visibleForBranch(businessTypeId: Int?): Boolean = businessTypeId == BUSINESS_TYPE_ID

    /** Real Estate entries the user is allowed to open, in registry order. */
    fun visible(permissions: List<Permission>?): List<RealEstateItem> =
        all.filter { Permissions.hasAny(permissions, it.anyOf) }

    /**
     * The permissions guarding item [key], for route-level gates. An unknown key
     * falls back to the full-access wildcard â€” fail closed, like ReportMenu.
     */
    fun permissionsFor(key: String): List<String> =
        byKey(key)?.anyOf ?: listOf(WILDCARD_PERMISSION)
}
