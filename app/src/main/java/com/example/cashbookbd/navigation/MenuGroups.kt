package com.example.cashbookbd.navigation

/**
 * The web sidebar's `SIDEBAR_SUB_DEFAULTS` (react `components/Sidebar/
 * sidebarCustomization.tsx`): each top-level menu's entries grouped under
 * headings, in the order the web draws them *before the user arranges the menu*.
 *
 * The keys are this app's own registry keys, so a section-home screen can hand
 * its already-permission-filtered items straight to [rows] and get them back
 * under the web's headings. An entry no group names keeps its declared order
 * after every grouped entry, exactly as the web leaves a report with no group —
 * a screen added later is never lost, it simply has no heading until it is
 * given one here. A heading whose group ends up empty (a trade without
 * installments, a role without stock rights) is dropped by [rows].
 *
 * This is a *default* only: the moment the user's saved sidebar arrangement
 * says anything about a menu, the arranging clients draw their own order and
 * this map is not consulted (see [WebMenuIds.isArranged]).
 */
object MenuGroups {

    data class Group(val title: String, val keys: List<String>)

    /**
     * A row a section-home screen draws: a heading, or one of the menu's items.
     * Ordered exactly as it should appear.
     */
    sealed interface Row<out T> {
        data class Heading(val title: String) : Row<Nothing>
        data class Entry<out T>(val item: T) : Row<T>
    }

    private val REPORTS = listOf(
        Group(
            "Cash & Bank",
            listOf("cashbook", "bankbook", "cashBankReceivedPayment", "bankInformation", "dateWiseTotal"),
        ),
        Group(
            "Ledger & Due",
            listOf(
                "ledger", "customerSupplierStatement", "labourLedger", "dueList",
                "ageing", "connectedMember", "groupReport",
            ),
        ),
        Group("Purchase & Sales", listOf("purchaseLedger", "salesLedger")),
        Group(
            "Stock",
            listOf(
                "productStock", "stockDetails", "godownStock", "imeiStock",
                "productInOut", "dateWiseInOut", "categoryWiseInOut",
            ),
        ),
        Group(
            "Installment & Somity",
            listOf("dueInstallments", "employeeInstallments", "collectionSheet", "monthlyReport"),
        ),
        Group("Audit", listOf("auditTrail", "mitchMatch")),
        Group(
            "Financial Statements",
            listOf(
                "profitLoss", "productProfitLoss", "balanceSheet",
                "trialBalanceLevel3", "trialBalanceLevel4", "expenseReport",
            ),
        ),
    )

    private val TRANSACTION = listOf(
        Group("Cash", listOf("cashReceived", "cashPayment")),
        Group("Bank", listOf("bankReceived", "bankPayment", "bankReconciliation", "chequeRegister")),
        Group("Journal & Others", listOf("journal", "installments", "employeeLoan")),
        Group("Period End", listOf("budget", "yearClosing")),
    )

    private val INVOICE = listOf(
        Group("Sales", listOf("sales", "salesImport", "salesReturn")),
        Group("Purchase", listOf("purchase", "purchaseImport", "purchaseReturn")),
        Group("Other", listOf("tradingCombined", "labourInvoice")),
    )

    private val BRANCH_TRANSFER = listOf(
        Group("Transfer", listOf("branchIssue", "branchReceive")),
        Group(
            "Transfer Reports",
            listOf("transferList", "branchTransferReport", "branchReceiveReport", "branchStockReport"),
        ),
        Group("Material", listOf("materialIssue")),
    )

    private val PRODUCTS = listOf(
        Group("Products", listOf("productList")),
        Group("Classification", listOf("categoryList", "brandList", "productUnit")),
        Group("Stock Alerts", listOf("lowStock", "negativeStock", "slowMoving", "warehouseDifference")),
    )

    private val ADMIN = listOf(
        Group(
            "Daily Control",
            listOf("dayClose", "approvalCenter", "voucherApproval", "approvalRemove", "changeVoucherType"),
        ),
        Group("Orders", listOf("orders", "orderWithTransaction", "averagePrice")),
        Group("Uploads", listOf("voucherUpload", "bulkUpload")),
        Group("Company & Branch", listOf("companyList", "branchList", "softwareInfo")),
        Group(
            "Users & Access",
            listOf(
                "userList", "companyUser", "onlineUsers", "loginHistory",
                "roles", "addRoles", "addPermission",
            ),
        ),
        Group("Settings", listOf("addGroupReport", "highlightRules", "arrangeMenu")),
        Group(
            "Platform",
            listOf(
                "resellers", "adminNotifications", "inAppMessages", "businessTypes",
                "inventorySystems", "tutorialVideos",
            ),
        ),
        Group("SMS", listOf("smsLogs", "smsTemplates")),
    )

    private val HRM = listOf(
        Group("Employees", listOf("hrmEmployees", "hrmDesignationLevels", "hrmDesignations")),
        Group("Attendance", listOf("manualAttendance", "leaveApplications", "attendanceSetup")),
        Group(
            "Salary & Bonus",
            listOf("salaryGenerate", "bonusGenerate", "hrmSalarySheet", "hrmBonusReports", "hrmSalaryMismatch"),
        ),
        Group("Loan", listOf("hrmLoanBalance", "hrmLoanLedger")),
        Group(
            "Attendance Reports",
            listOf(
                "hrmAttendanceReport", "hrmMonthlyAttendance", "hrmEmployeeAttendance",
                "hrmBranchAttendance", "hrmOvertimeReport", "hrmAttendanceAlerts",
                "hrmHolidayCalendar", "hrmAuditHistory",
            ),
        ),
    )

    private val REAL_ESTATE = listOf(
        Group(
            "Setup",
            listOf(
                "reAreas", "reProjects", "reBuildings", "reFloors", "reUnits",
                "reChargeTypes", "flatLayout",
            ),
        ),
        Group("Sales", listOf("unitSales", "reInstallmentCreate", "soldUnits", "reSalesSummary")),
        Group(
            "Project Accounts",
            listOf("projectPurchase", "projectLabour", "projectExpense", "projectIncome", "reCheckRegister"),
        ),
        Group("Project Reports", listOf("reProjectSummary", "projectCostReport", "projectIncomeReport")),
    )

    private val HOTEL = listOf(
        Group("Bookings", listOf("hotelBookings", "hotelHallBookings", "hotelCalendar")),
        Group(
            "Operations",
            listOf("hotelHousekeeping", "hotelAmenityIssue", "hotelAssetPurchase"),
        ),
        Group("Accounts", listOf("hotelReceivedEntry", "hotelPaymentEntry")),
        Group("Reports & Setup", listOf("hotelReports", "hotelSetup")),
    )

    private val CUSTOMER = listOf(
        Group("Parties", listOf("customers", "customerOpeningImport")),
        Group("Chart of Accounts", listOf("coaL1", "coaL2", "coaL3", "coaL4", "bankOpening")),
    )

    private val VR_SETTINGS = listOf(
        Group("Corrections", listOf("voucherDateChange", "voucherDelete", "installmentDelete", "recycleBin")),
        Group("Audit", listOf("history", "logChanges", "reportMismatch")),
    )

    /** Keyed by the drawer/menu id the section-home screens pass in. */
    val menus: Map<String, List<Group>> = mapOf(
        "reports" to REPORTS,
        "transaction" to TRANSACTION,
        "invoice" to INVOICE,
        "branch-transfer" to BRANCH_TRANSFER,
        "products" to PRODUCTS,
        "admin" to ADMIN,
        "hrm" to HRM,
        "real-estate" to REAL_ESTATE,
        "hotel" to HOTEL,
        "customer-supplier" to CUSTOMER,
        "vr_settings" to VR_SETTINGS,
    )

    fun groupsFor(menuId: String): List<Group> = menus[menuId].orEmpty()

    /**
     * Lays [items] out under this menu's headings, in group order, dropping a
     * heading whose group shows nothing. Items no group names keep their
     * declared order after every grouped entry. A menu with no groups — or an
     * item list a group cannot fully claim — comes back as plain entries.
     */
    fun <T> rows(menuId: String, items: List<T>, keyOf: (T) -> String): List<Row<T>> {
        val groups = groupsFor(menuId)
        if (groups.isEmpty()) return items.map { Row.Entry(it) }

        val byKey = items.associateBy(keyOf)
        val used = HashSet<String>()
        val out = mutableListOf<Row<T>>()

        groups.forEach { group ->
            val members = group.keys.mapNotNull { key -> byKey[key]?.also { used.add(key) } }
            if (members.isNotEmpty()) {
                out += Row.Heading(group.title)
                members.forEach { out += Row.Entry(it) }
            }
        }

        // Whatever no group named — a report the list has not caught up with —
        // keeps its place at the end, under no heading.
        items.filter { keyOf(it) !in used }.forEach { out += Row.Entry(it) }
        return out
    }
}
