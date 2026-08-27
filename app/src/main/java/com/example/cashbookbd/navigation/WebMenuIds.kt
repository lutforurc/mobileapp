package com.example.cashbookbd.navigation

import com.example.cashbookbd.data.repository.MenuPreferences

/**
 * The web sidebar's per-entry ids, keyed by the mobile registry key — the
 * `sidebar-sub` record stores "menuId/webEntryId", so this table is what lets
 * one saved arrangement drive both clients. The web ids are whatever each
 * NavLink was written with (route keys or literal paths), verbatim.
 */
object WebMenuIds {

    private val REPORTS: Map<String, String> = mapOf(
        "cashbook" to "reports/cashbook",
        "bankbook" to "report_bankbook",
        "cashBankReceivedPayment" to "cash_bank_received_payment",
        "profitLoss" to "profit_loss",
        "productProfitLoss" to "product_profit_loss",
        "bankInformation" to "bank_information",
        "connectedMember" to "connected_member",
        "balanceSheet" to "balance_sheet",
        "trialBalanceLevel3" to "trial_balance_level3",
        "trialBalanceLevel4" to "trial_balance_level4",
        "expenseReport" to "expense_report",
        "dueInstallments" to "reports/due-installments",
        "employeeInstallments" to "reports/employee-installment",
        "ledger" to "reports/ledger",
        "customerSupplierStatement" to "customer_supplier_statement",
        "productInOut" to "product_ledger_data",
        "dateWiseInOut" to "report_date_wise_in_out",
        "labourLedger" to "reports/labour/ledger",
        "dueList" to "reports/due-list",
        "collectionSheet" to "somity_collection_sheet",
        "monthlyReport" to "somity_monthly_report",
        "dateWiseTotal" to "reports/date-wise-total-data",
        "productStock" to "reports/product/stock",
        "stockDetails" to "somity_stock_details",
        "imeiStock" to "report_imei_stock",
        "godownStock" to "report_godown_stock",
        "categoryWiseInOut" to "reports/cat-wise/in-out",
        "purchaseLedger" to "reports/purchase-ledger",
        "salesLedger" to "reports/sales-ledger",
        "groupReport" to "reports/group-report",
        "mitchMatch" to "reports/mitch-match",
    )

    private val TRANSACTION: Map<String, String> = mapOf(
        "cashReceived" to "cash_receive",
        "cashPayment" to "accounts/cash/payment",
        "bankReceived" to "accounts/bank/receive",
        "bankPayment" to "accounts/bank/payment",
        "installments" to "installment_list",
        "employeeLoan" to "employee_loan",
        "journal" to "accounts/journal",
    )

    private val ADMIN: Map<String, String> = mapOf(
        "companyList" to "company_list",
        "branchList" to "branch/branch-list",
        "softwareInfo" to "software_info",
        "arrangeMenu" to "menu_arrangement",
        "userList" to "user_list",
        "onlineUsers" to "online_users",
        "loginHistory" to "user_login_log",
        "companyUser" to "company_user_list",
        "resellers" to "reseller_admin",
        "adminNotifications" to "admin_notifications",
        "inAppMessages" to "admin_in_app_messages",
        "inventorySystems" to "inventory_systems",
        "tutorialVideos" to "tutorial_videos",
        "highlightRules" to "highlight_rules",
        "roles" to "roles",
        "addRoles" to "add_role",
        "addPermission" to "add_permission",
        "dayClose" to "admin/dayclose",
        "addGroupReport" to "group_report_setup",
        "orders" to "order/order-list",
        "orderWithTransaction" to "order_with_transaction",
        "averagePrice" to "orders/avg-price",
        "approvalCenter" to "approval_center",
        "voucherApproval" to "admin/voucher-approval",
        "removeApproval" to "admin/remove-approval",
        "changeVoucherType" to "admin/voucher/type-change",
        "voucherUpload" to "admin/image-upload",
        "bulkUpload" to "admin/bulk-upload",
        "smsLogs" to "sms_send",
        "smsTemplates" to "sms_template_list",
    )

    /** Hotel — the web's five-entry group. */
    private val HOTEL: Map<String, String> = mapOf(
        "hotelBookings" to "hotel_bookings",
        "hotelCalendar" to "hotel_calendar",
        "hotelHousekeeping" to "hotel_housekeeping",
        "hotelReports" to "hotel_reports",
        "hotelSetup" to "hotel_setup",
    )

    /** Labour Items — arrangeable since web d933c6a. */
    private val LABOUR_ITEMS: Map<String, String> = mapOf(
        "labourCategories" to "labour_category",
        "labourItems" to "labour_item",
    )

    /** The arrangeable menus and their mobile-key → web-id tables. */
    val menus: Map<String, Map<String, String>> = mapOf(
        "reports" to REPORTS,
        "transaction" to TRANSACTION,
        "admin" to ADMIN,
        "labour_items" to LABOUR_ITEMS,
        "hotel" to HOTEL,
    )

    fun forMenu(menuId: String): Map<String, String> = menus[menuId].orEmpty()

    /**
     * Applies the user's saved sidebar-sub arrangement to one menu's items —
     * the web's idsOf/slot verbatim: saved ids place their entries first,
     * unseen entries keep their declared order at the end, hidden entries are
     * dropped. A mobile key with no web id (a mobile-only entry) can never be
     * placed or hidden from the web, exactly like an id the web has that the
     * other client lacks.
     */
    fun <T> arrange(
        menuId: String,
        items: List<T>,
        sub: MenuPreferences,
        keyOf: (T) -> String,
    ): List<T> {
        val webIdByKey = forMenu(menuId)
        if (webIdByKey.isEmpty()) return items
        val prefix = "$menuId/"
        val hiddenWeb = sub.hidden.asSequence()
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix) }
            .toSet()
        val visible = items.filter { webIdByKey[keyOf(it)] !in hiddenWeb }
        if (sub.order.none { it.startsWith(prefix) }) return visible

        val byWebId = LinkedHashMap<String, T>()
        visible.forEach { item -> webIdByKey[keyOf(item)]?.let { byWebId.putIfAbsent(it, item) } }
        val placed = mutableListOf<T>()
        sub.order.asSequence()
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix) }
            .forEach { id -> byWebId.remove(id)?.let { placed.add(it) } }
        // Whatever the saved order never saw keeps its declared position.
        visible.forEach { item ->
            val webId = webIdByKey[keyOf(item)]
            if (webId == null || byWebId.containsKey(webId)) placed.add(item)
        }
        return placed
    }
}
