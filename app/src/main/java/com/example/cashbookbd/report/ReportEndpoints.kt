package com.example.cashbookbd.report

/**
 * Maps each report's [ReportConfig.endpointKey] to its API path, mirroring the
 * web app's `reportEndpoints`.
 *
 * Paths are RELATIVE to `BuildConfig.BASE_URL` (which already ends in `/api/`),
 * so they carry no leading slash and no `/api` prefix — Retrofit's `@Url`
 * resolves them against the base URL.
 */
object ReportEndpoints {

    val map: Map<String, String> = mapOf(
        "dateWiseTotal" to "reports/date-wise-total-data",
        "cashbook" to "reports/cashbook",
        "profitLoss" to "reports/profit-loss",
        "profitLossExpenseSummary" to "reports/profit-loss-expense-summary",
        "balanceSheet" to "reports/balance-sheet",
        "trialBalanceLevel3" to "reports/trialbalance-level3",
        "trialBalanceLevel4" to "reports/trialbalance-level4",
        "cashBankReceivedPayment" to "reports/cash-bank-received-payment",
        "bankInformation" to "reports/bank-information-data",
        "connectedMember" to "reports/connected-member-data",
        "productProfitLoss" to "reports/product-profit-loss",
        "customerSupplierStatement" to "reports/ledger-with-product",
        "dueInstallments" to "accounts/installment/filter",
        "employeeInstallments" to "accounts/installment/employees",
        "dueList" to "reports/duelist",
        "ledger" to "reports/api-ledger",
        "productLedgerData" to "reports/product-ledger-data",
        "labourLedger" to "reports/labour/ledger",
        "purchaseLedger" to "reports/purchase/ledger",
        "salesLedger" to "reports/sales/ledger",
        "mitchMatch" to "reports/mitch-match/data",
        "groupReport" to "reports/group/report/data",
        "collectionSheet" to "somity-report/collection-sheet",
        "monthlyReport" to "somity-report/monthly-report/data",
        "closingStock" to "reports/closing-stock",
        "productStock" to "reports/product-stock",
        "imeiStock" to "reports/stock-imei-data",
        "categoryWiseInOut" to "reports/category-wise-in-out",
        "dateWiseInOut" to "reports/in-out/date-wise/data",
        "dateWiseInOutDetails" to "reports/in-out/date-wise/details",

        // HRM section (see the hrms route group in the Laravel api.php).
        "hrmAttendanceReport" to "hrms/attendance/entries/report",
        "hrmAuditHistory" to "hrms/attendance/entries/audit-history",
        "hrmMonthlySummary" to "hrms/attendance/monthly-summary",
        "hrmHolidays" to "hrms/attendance/holidays",
        "hrmLoanBalance" to "hrms/loan/balance",
        "hrmLoanLedger" to "hrms/loan/ledger",
        "hrmSalarySheet" to "hrms/salary-sheet",
    )

    fun path(endpointKey: String): String? = map[endpointKey]
}
