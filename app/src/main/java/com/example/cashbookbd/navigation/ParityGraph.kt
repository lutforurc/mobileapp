package com.example.cashbookbd.navigation

import android.net.Uri
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.cashbookbd.ui.common.PermissionGate

/**
 * Routes of the forms and reports brought across in the 2026-10 web parity
 * pass (react up to 2d0d2762). Kept apart from [Routes] so the pass reads as
 * one piece; every destination is gated on the permission its web page's own
 * route guard checks, so a deep link can never open what the menu hides.
 *
 * Printing is deliberately not carried: these screens show and save, and the
 * papers stay on the web.
 */
object ParityRoutes {
    // ---- Reports ----
    const val CUSTOMER_SALES = "reports/customer-sales"
    const val DAILY_ACCOUNT_BOOK = "reports/daily-account-book"
    const val SALES_REFERRER = "reports/sales-referrer"
    const val DELIVERY_REPORT = "reports/delivery"
    private const val DELIVERY_REPORT_PATTERN = "reports/delivery?from={from}&to={to}"

    fun deliveryReport(from: String?, to: String?): String =
        "reports/delivery?from=${Uri.encode(from.orEmpty())}&to=${Uri.encode(to.orEmpty())}"

    // ---- Delivery (Tiles and Sanitary) ----
    const val DELIVERY_DASHBOARD = "delivery/dashboard"
    private const val DELIVERY_PATTERN = "invoice/delivery?invoice_no={invoice_no}"

    fun delivery(invoiceNo: String?): String =
        "invoice/delivery?invoice_no=${Uri.encode(invoiceNo.orEmpty())}"

    // ---- Branch Transfer: Product Out / Product In and the split lists ----
    const val PRODUCT_OUT = "inventory/product-out"
    const val PRODUCT_OUT_REASONS = "inventory/product-out/reasons"
    const val PRODUCT_OUT_REGISTER = "inventory/product-out/register"
    const val PRODUCT_IN = "inventory/product-in"
    const val PRODUCT_IN_REASONS = "inventory/product-in/reasons"
    const val PRODUCT_IN_REGISTER = "inventory/product-in/register"
    const val TRANSFER_LIST = "reports/branch-transfer-list"
    const val RECEIVE_LIST = "reports/branch-receive-list"

    // ---- Company Scheme ----
    const val COMPANY_SCHEME = "company-scheme/home"
    const val COMPANY_SCHEME_RECEIVABLE = "company-scheme/receivable"
    const val COMPANY_SCHEME_RECEIPTS = "company-scheme/receipts"

    // ---- HRM attendance (built on the client like the web) ----
    const val ATTENDANCE_DAILY = "hrms/attendance/report"

    fun attendanceException(type: com.example.cashbookbd.ui.hrm.AttendanceReportType): String =
        "hrms/attendance/exception/${type.name}"

    // ---- Account (the user menu) ----
    const val SWITCH_BRANCH = "account/switch-branch"
    const val CHANGE_PASSWORD = "account/change-password"

    // ---- Admin ----
    const val APPROVAL_CENTER = "admin/approval-center"
    const val APPROVAL_AUDIT = "admin/approval-center/audit"
    const val VOUCHER_UPLOAD = "admin/image-upload"
    const val BULK_UPLOAD = "admin/bulk-upload"
    const val WEBSITE_TEMPLATES = "admin/website-templates"
    const val RESELLER_ADMIN = "admin/resellers"

    // ---- Old Software ----
    const val LEGACY_ACCOUNTS = "legacy/accounts"

    // ---- Excel imports ----
    const val PRODUCT_OPENING_IMPORT = "product/opening-import"
    const val PRODUCT_STOCK_STATEMENT = "product/product-statement"
    const val CUSTOMER_OPENING_IMPORT = "customer/opening-import"
    const val PURCHASE_IMPORT = "invoice/purchase-import"
    const val SALES_IMPORT = "invoice/sales-import"

    // ---- Tiles and Sanitary's own invoices ----
    const val TILES_SALES = "invoice/tiles/sales"
    const val TILES_PURCHASE = "invoice/tiles/purchase"
    const val TILES_SALES_RETURN = "invoice/tiles/sales-return"
    const val TILES_PURCHASE_RETURN = "invoice/tiles/purchase-return"

    // ---- VR Settings ----
    const val REPORT_MISMATCH = "vr-settings/report-mismatch"

    internal val deliveryPattern get() = DELIVERY_PATTERN
    internal val deliveryReportPattern get() = DELIVERY_REPORT_PATTERN
}

private fun optionalString(name: String) = navArgument(name) {
    type = NavType.StringType
    nullable = true
    defaultValue = null
}

fun NavGraphBuilder.parityGraph(
    navController: NavHostController,
    backToLogin: () -> Unit,
) {
    // ---- Reports ----
    composable(ParityRoutes.CUSTOMER_SALES) {
        PermissionGate(anyOf = listOf("sales.ledger")) {
            com.example.cashbookbd.ui.reports.CustomerSalesScreen(navController, backToLogin)
        }
    }
    composable(ParityRoutes.DAILY_ACCOUNT_BOOK) {
        PermissionGate(anyOf = listOf("cashbook.view")) {
            com.example.cashbookbd.ui.reports.DailyAccountBookScreen(navController, backToLogin)
        }
    }
    composable(ParityRoutes.SALES_REFERRER) {
        PermissionGate(anyOf = listOf("sales.referrer")) {
            com.example.cashbookbd.ui.reports.SalesReferrerScreen(navController, backToLogin)
        }
    }
    composable(
        route = ParityRoutes.deliveryReportPattern,
        arguments = listOf(optionalString("from"), optionalString("to")),
    ) { entry ->
        PermissionGate(anyOf = listOf("delivery.view")) {
            com.example.cashbookbd.ui.delivery.DeliveryReportScreen(
                navController = navController,
                onLogout = backToLogin,
                from = entry.arguments?.getString("from")?.ifBlank { null },
                to = entry.arguments?.getString("to")?.ifBlank { null },
            )
        }
    }
    // The Reports menu opens the report with no window: the web's blank boxes.
    composable(ParityRoutes.DELIVERY_REPORT) {
        PermissionGate(anyOf = listOf("delivery.view")) {
            com.example.cashbookbd.ui.delivery.DeliveryReportScreen(navController, backToLogin)
        }
    }

    // ---- Delivery ----
    composable(
        route = ParityRoutes.deliveryPattern,
        arguments = listOf(optionalString("invoice_no")),
    ) { entry ->
        PermissionGate(anyOf = listOf("delivery.view")) {
            com.example.cashbookbd.ui.delivery.DeliveryScreen(
                navController = navController,
                onLogout = backToLogin,
                invoiceNo = entry.arguments?.getString("invoice_no")?.ifBlank { null },
            )
        }
    }
    composable(ParityRoutes.DELIVERY_DASHBOARD) {
        PermissionGate(anyOf = listOf("delivery.view")) {
            com.example.cashbookbd.ui.delivery.DeliveryDashboardScreen(navController, backToLogin)
        }
    }

    // ---- Account: personal, no permission stands in front of either ----
    composable(ParityRoutes.SWITCH_BRANCH) {
        com.example.cashbookbd.ui.account.SwitchBranchScreen(navController, backToLogin)
    }
    composable(ParityRoutes.CHANGE_PASSWORD) {
        com.example.cashbookbd.ui.account.ChangePasswordScreen(navController, backToLogin)
    }

    // ---- Admin: the Approval Center and its audit ----
    composable(ParityRoutes.APPROVAL_CENTER) {
        PermissionGate(anyOf = listOf("voucher.approval", "attendance.view", "approval.center")) {
            com.example.cashbookbd.ui.admin.ApprovalCenterScreen(navController, backToLogin)
        }
    }
    composable(ParityRoutes.APPROVAL_AUDIT) {
        PermissionGate(anyOf = listOf("voucher.approval", "attendance.view")) {
            com.example.cashbookbd.ui.admin.ApprovalAuditScreen(navController, backToLogin)
        }
    }

    composable(ParityRoutes.VOUCHER_UPLOAD) {
        PermissionGate(anyOf = listOf("voucher.photo.upload")) {
            com.example.cashbookbd.ui.admin.VoucherUploadScreen(navController, backToLogin)
        }
    }
    composable(ParityRoutes.WEBSITE_TEMPLATES) {
        PermissionGate(anyOf = listOf("reseller.view", "subscription.view", "all.user.view")) {
            com.example.cashbookbd.ui.admin.WebsiteTemplatesScreen(navController, backToLogin)
        }
    }
    composable(ParityRoutes.RESELLER_ADMIN) {
        PermissionGate(anyOf = listOf("reseller.view", "subscription.view", "all.user.view")) {
            com.example.cashbookbd.ui.admin.ResellerAdminScreen(navController, backToLogin)
        }
    }
    composable(ParityRoutes.BULK_UPLOAD) {
        PermissionGate(anyOf = listOf("bulk.photo.upload")) {
            com.example.cashbookbd.ui.admin.BulkUploadScreen(navController, backToLogin)
        }
    }

    composable(ParityRoutes.TILES_SALES) {
        PermissionGate(anyOf = listOf("sales.create")) {
            com.example.cashbookbd.ui.invoice.TilesInvoiceScreen(navController, backToLogin, com.example.cashbookbd.ui.invoice.TilesInvoiceKind.SALES)
        }
    }
    composable(ParityRoutes.TILES_PURCHASE) {
        PermissionGate(anyOf = listOf("purchase.create")) {
            com.example.cashbookbd.ui.invoice.TilesInvoiceScreen(navController, backToLogin, com.example.cashbookbd.ui.invoice.TilesInvoiceKind.PURCHASE)
        }
    }
    composable(ParityRoutes.TILES_SALES_RETURN) {
        PermissionGate(anyOf = listOf("sales.return")) {
            com.example.cashbookbd.ui.invoice.TilesInvoiceScreen(navController, backToLogin, com.example.cashbookbd.ui.invoice.TilesInvoiceKind.SALES_RETURN)
        }
    }
    composable(ParityRoutes.TILES_PURCHASE_RETURN) {
        PermissionGate(anyOf = listOf("purchase.return.view")) {
            com.example.cashbookbd.ui.invoice.TilesInvoiceScreen(navController, backToLogin, com.example.cashbookbd.ui.invoice.TilesInvoiceKind.PURCHASE_RETURN)
        }
    }
    composable(com.example.cashbookbd.asset.AssetMenu.ROUTE_ITEMS) {
        PermissionGate(anyOf = listOf(com.example.cashbookbd.asset.AssetMenu.PERM_CATEGORY_VIEW)) {
            com.example.cashbookbd.ui.asset.AssetItemsScreen(navController, backToLogin)
        }
    }
    composable(ParityRoutes.PRODUCT_STOCK_STATEMENT) {
        PermissionGate(anyOf = listOf("products.view")) {
            com.example.cashbookbd.ui.products.ProductStockStatementScreen(navController, backToLogin)
        }
    }
    composable(ParityRoutes.LEGACY_ACCOUNTS) {
        PermissionGate(anyOf = listOf("legacy.record.view")) {
            com.example.cashbookbd.ui.legacy.LegacyAccountsScreen(navController, backToLogin)
        }
    }

    // ---- Excel imports ----
    composable(ParityRoutes.PRODUCT_OPENING_IMPORT) {
        PermissionGate(anyOf = listOf("product.opening.edit", "products.edit")) {
            com.example.cashbookbd.ui.imports.OpeningImportScreen(navController, backToLogin, com.example.cashbookbd.ui.imports.OpeningImportKind.PRODUCT)
        }
    }
    composable(ParityRoutes.CUSTOMER_OPENING_IMPORT) {
        PermissionGate(anyOf = listOf("party.opening.edit", "cs.edit")) {
            com.example.cashbookbd.ui.imports.OpeningImportScreen(navController, backToLogin, com.example.cashbookbd.ui.imports.OpeningImportKind.CUSTOMER)
        }
    }
    composable(ParityRoutes.PURCHASE_IMPORT) {
        PermissionGate(anyOf = listOf("purchase.create", "purchase.import")) {
            com.example.cashbookbd.ui.imports.TradingImportScreen(navController, backToLogin, com.example.cashbookbd.ui.imports.TradingImportKind.PURCHASE)
        }
    }
    composable(ParityRoutes.SALES_IMPORT) {
        PermissionGate(anyOf = listOf("sales.create", "sales.import")) {
            com.example.cashbookbd.ui.imports.TradingImportScreen(navController, backToLogin, com.example.cashbookbd.ui.imports.TradingImportKind.SALES)
        }
    }

    // ---- Hotel: the money side ----
    val hotelGate = listOf("hotel.building.view", "hotel.floor.view", "hotel.room.type.view", "hotel.resource.view", "hotel.booking.view")
    composable(com.example.cashbookbd.hotel.HotelMenu.ROUTE_ASSET_PURCHASE) {
        PermissionGate(anyOf = hotelGate) {
            com.example.cashbookbd.ui.hotel.HotelAssetPurchaseScreen(navController, backToLogin)
        }
    }
    composable(com.example.cashbookbd.hotel.HotelMenu.ROUTE_PAYMENT_ENTRY) {
        PermissionGate(anyOf = hotelGate) {
            com.example.cashbookbd.ui.hotel.HotelAccountsEntryScreen(navController, backToLogin, com.example.cashbookbd.ui.hotel.HotelEntryKind.PAYMENT)
        }
    }
    composable(com.example.cashbookbd.hotel.HotelMenu.ROUTE_RECEIVED_ENTRY) {
        PermissionGate(anyOf = hotelGate) {
            com.example.cashbookbd.ui.hotel.HotelAccountsEntryScreen(navController, backToLogin, com.example.cashbookbd.ui.hotel.HotelEntryKind.RECEIVED)
        }
    }

    // ---- VR Settings ----
    composable(ParityRoutes.REPORT_MISMATCH) {
        PermissionGate(anyOf = listOf("mitmatch.report")) {
            com.example.cashbookbd.ui.vrsettings.ReportMismatchScreen(navController, backToLogin)
        }
    }

    // ---- HRM attendance ----
    com.example.cashbookbd.ui.hrm.AttendanceReportType.entries.forEach { type ->
        val route = if (type == com.example.cashbookbd.ui.hrm.AttendanceReportType.DAILY) {
            ParityRoutes.ATTENDANCE_DAILY
        } else {
            ParityRoutes.attendanceException(type)
        }
        composable(route) {
            PermissionGate(anyOf = listOf("attendance.view", "employee.view")) {
                com.example.cashbookbd.ui.hrm.AttendanceReportScreen(navController, backToLogin, type)
            }
        }
    }

    // ---- Company Scheme ----
    composable(ParityRoutes.COMPANY_SCHEME) {
        PermissionGate(anyOf = listOf("company.scheme.view")) {
            com.example.cashbookbd.ui.companyscheme.CompanySchemeHomeScreen(navController, backToLogin)
        }
    }
    composable(ParityRoutes.COMPANY_SCHEME_RECEIVABLE) {
        PermissionGate(anyOf = listOf("company.scheme.view")) {
            com.example.cashbookbd.ui.companyscheme.CompanySchemeReceivableScreen(navController, backToLogin)
        }
    }
    composable(ParityRoutes.COMPANY_SCHEME_RECEIPTS) {
        PermissionGate(anyOf = listOf("company.scheme.view")) {
            com.example.cashbookbd.ui.companyscheme.CompanySchemeReceiptsScreen(navController, backToLogin)
        }
    }

    // ---- Product Out / Product In ----
    stockSlipRoutes(navController, backToLogin)
    composable(ParityRoutes.TRANSFER_LIST) {
        PermissionGate(
            anyOf = listOf("branch.issue.create", "branch.transfer.create", "inventory.transfer.create", "product.transfer.create", "product.stock.view"),
        ) {
            com.example.cashbookbd.ui.inventory.TransferListScreen(navController, backToLogin, fixedTab = 0)
        }
    }
    composable(ParityRoutes.RECEIVE_LIST) {
        PermissionGate(
            anyOf = listOf("branch.received.create", "inventory.received.create", "product.received.create", "product.stock.view"),
        ) {
            com.example.cashbookbd.ui.inventory.TransferListScreen(navController, backToLogin, fixedTab = 1)
        }
    }
}

private fun NavGraphBuilder.stockSlipRoutes(navController: NavHostController, backToLogin: () -> Unit) {
    val kinds = listOf(
        com.example.cashbookbd.ui.stockslip.StockSlipKind.OUT to Triple(
            ParityRoutes.PRODUCT_OUT, ParityRoutes.PRODUCT_OUT_REASONS, ParityRoutes.PRODUCT_OUT_REGISTER,
        ),
        com.example.cashbookbd.ui.stockslip.StockSlipKind.IN to Triple(
            ParityRoutes.PRODUCT_IN, ParityRoutes.PRODUCT_IN_REASONS, ParityRoutes.PRODUCT_IN_REGISTER,
        ),
    )
    kinds.forEach { (kind, routes) ->
        composable(routes.first) {
            PermissionGate(anyOf = listOf(kind.createPermission)) {
                com.example.cashbookbd.ui.stockslip.StockSlipScreen(navController, backToLogin, kind)
            }
        }
        composable(routes.second) {
            PermissionGate(anyOf = listOf(kind.reasonsPermission)) {
                com.example.cashbookbd.ui.stockslip.StockSlipReasonsScreen(navController, backToLogin, kind)
            }
        }
        composable(routes.third) {
            PermissionGate(anyOf = listOf(kind.reportPermission)) {
                com.example.cashbookbd.ui.stockslip.StockSlipRegisterScreen(navController, backToLogin, kind)
            }
        }
    }
}
