package com.example.cashbookbd.report

import com.example.cashbookbd.session.Permission
import com.example.cashbookbd.session.Permissions
import com.example.cashbookbd.session.WILDCARD_PERMISSION

enum class ReportMethod { GET, POST }

/**
 * How the generic parser should extract rows from a report's payload. Most
 * reports are [NORMAL] (an array under a known key, or a top-level array); a few
 * legacy endpoints return unusual shapes and opt into a dedicated mode.
 */
enum class ReportResponseShape {
    /** Array under a known key, or a top-level array (the default). */
    NORMAL,

    /**
     * An object of `{ "1": scalar, "2": scalar, … }` (IMEI Stock) — each entry
     * becomes a one-cell row. `[]` when empty.
     */
    KEYED_SCALARS,

    /**
     * A nested `{ group: { subgroup: [rows] } }` map with dynamic string keys
     * (Labour Ledger) — flattened into the underlying rows.
     */
    NESTED_GROUPS,
}

/** Date wire format expected by a report's API. */
enum class ReportDateStyle {
    /** `yyyy-MM-dd` (newer endpoints). */
    API,

    /** `dd/MM/yyyy` (legacy endpoints). */
    DISPLAY,
}

/**
 * The filter UX a report needs. Only a subset is wired into the generic engine
 * today (see [ReportConfig.isGenericSupported]); the rest are listed so the
 * registry is complete and the menu renders correctly, and can be built out
 * incrementally.
 */
enum class ReportFilterType {
    BRANCH_DATE_RANGE,
    BRANCH_END_DATE,
    /** Branch picker only, no dates (e.g. HRM Loan Balance). */
    BRANCH_ONLY,
    /** Branch + month + year sent as separate params (HRM monthly summaries). */
    BRANCH_MONTH_YEAR,
    /** Branch + year only (HRM salary sheet). */
    BRANCH_YEAR,
    BRANCH_LEDGER_DATE_RANGE,
    BRANCH_PRODUCT_DATE_RANGE,
    BRANCH_PRODUCT_ONLY,
    BRANCH_CATEGORY_DATE_RANGE,
    BRANCH_BRAND_CATEGORY_PRODUCT_DATE_RANGE,
    BRANCH_REPORT_TYPE_END_DATE,
    BRANCH_DATE_RANGE_WITH_OPTIONAL_PRODUCT,
    BRANCH_EMPLOYEE_INSTALLMENT,
    BRANCH_CUSTOMER_INSTALLMENT,
    COLLECTION_SHEET,
    GROUP_REPORT,
}

/** One selectable option in a report's single-select choice dropdown. */
data class ReportChoice(
    val label: String,
    val value: String,
)

/**
 * A single-select dropdown filter some reports need (e.g. Bank Information's
 * balance/loan type). The chosen [ReportChoice.value] is sent under [paramKey].
 */
data class ReportChoiceParam(
    val paramKey: String,
    val label: String,
    val options: List<ReportChoice>,
)

/**
 * One entry in the Reports menu, mirroring the web app's `REPORT_MENU`.
 *
 * The date/branch parameter names differ across the legacy PHP endpoints, so
 * each report declares its own [branchParam], [startParam], [endParam],
 * [dateStyle] and any [extraParams]. This lets a single generic repository build
 * the correct request for every report in the date-range family without a
 * per-report code path.
 *
 * [native] reports are handed to a hand-built screen instead of the generic
 * engine (Cash Book, Ledger).
 */
data class ReportConfig(
    val key: String,
    val title: String,
    val routeName: String,
    val webPath: String,
    val anyOf: List<String>,
    val endpointKey: String,
    val method: ReportMethod,
    val filterType: ReportFilterType,
    val native: Boolean = false,
    val branchParam: String = "branch_id",
    val startParam: String? = "start_date",
    val endParam: String? = "end_date",
    /** Optional second date-key pair some endpoints expect alongside the first. */
    val altStartParam: String? = null,
    val altEndParam: String? = null,
    val dateStyle: ReportDateStyle = ReportDateStyle.API,
    val extraParams: Map<String, String> = emptyMap(),
    /**
     * When set, the filter shows a searchable ledger/party picker and sends the
     * chosen id under this key (e.g. "ledger_id", "party_id"). Enables the
     * ledger/party report family in the generic engine.
     */
    val ledgerParam: String? = null,
    /** False when the ledger picker is optional (report runs branch-wide without it). */
    val ledgerRequired: Boolean = true,
    /**
     * When set, the filter shows a single-select dropdown (e.g. report type) and
     * sends the chosen value under [ReportChoiceParam.paramKey].
     */
    val choiceParam: ReportChoiceParam? = null,
    /**
     * Extra remote dropdown filters (category, brand, product, somity, labour)
     * this report needs beyond branch/date/ledger/choice.
     */
    val selectors: List<ReportSelector> = emptyList(),
    /**
     * When set, the filter shows a month/year picker and sends `MM/yyyy` under
     * this key (Collection Sheet's `month_year`).
     */
    val monthYearParam: String? = null,
    /**
     * When set (with [yearParam]), the month/year picker sends the month number
     * ("1".."12") under this key and the year under [yearParam] — the HRM
     * monthly-summary endpoints take them as two separate params.
     */
    val monthParam: String? = null,
    /**
     * Year param key. With [monthParam] it pairs with the month/year picker;
     * alone it shows a year-only picker (HRM salary sheet's `year_id`).
     */
    val yearParam: String? = null,
    /**
     * Which parent section lists this report. The Reports home shows only
     * [SECTION_REPORTS]; HRM report screens live in the HRM section but reuse
     * this registry and the generic engine.
     */
    val section: String = SECTION_REPORTS,
    /** How the generic parser should read this report's payload. */
    val responseShape: ReportResponseShape = ReportResponseShape.NORMAL,
    /** Column header for a [ReportResponseShape.KEYED_SCALARS] report (e.g. "IMEI"). */
    val scalarLabel: String = "Value",
    /**
     * Raw API row keys to drop from the rendered table (case-insensitive), e.g.
     * internal id columns the user doesn't need to see.
     */
    val hiddenColumns: List<String> = emptyList(),
    /**
     * Overrides a column's header text, keyed by the raw API row key
     * (case-insensitive). Without an entry the header is derived from the key
     * ("unit_sale_rate" -> "Unit Sale Rate"); use this to shorten headers that
     * are too wide for a phone ("unit_sale_rate" -> "Sale Rate").
     */
    val columnLabels: Map<String, String> = emptyMap(),
    /**
     * Raw API row keys (case-insensitive) whose zero value should render as "-"
     * instead of "0" — e.g. Product Stock's opening/in/out/balance amounts. Their
     * non-zero values also carry the [unitColumn] suffix when one is set.
     */
    val zeroDashColumns: List<String> = emptyList(),
    /**
     * Raw API row key holding a per-row unit (e.g. "nos", "kg"). When set, the
     * unit is appended to each [zeroDashColumns] amount ("1 nos") and its own
     * column is not shown separately.
     */
    val unitColumn: String? = null,
    /**
     * Raw API row keys (case-insensitive) rendered verbatim — no numeric
     * formatting. For digit-only codes that are labels, not amounts (e.g. an
     * employee serial "007", which must not become "7").
     */
    val textColumns: List<String> = emptyList(),
    /**
     * Raw API row keys (case-insensitive) holding a month code ("MMYYYY" or
     * "MM-YYYY"), rendered as "Sep 2025". Falls back to the raw text when the
     * value doesn't match either pattern.
     */
    val monthColumns: List<String> = emptyList(),
    /**
     * Highlight rules (the "phrase → coloured border" list): ordered fallback
     * dot-paths into the raw row JSON whose first non-blank value is the text
     * the rules match against. Numeric segments index arrays, e.g.
     * "acc_transaction_master.0.acc_transaction_details.0.remarks".
     */
    val highlightPaths: List<String> = emptyList(),
    /**
     * Raw row key of the column that shows the matched text and receives the
     * coloured box. When a row lacks this key but a [highlightPaths] value
     * exists (Purchase Ledger's nested notes), a cell is appended so the text
     * is visible. Also rendered verbatim, like [textColumns].
     */
    val highlightColumn: String? = null,
) {
    /** True when the generic filter → result flow can run this report today. */
    val isGenericSupported: Boolean
        get() = !native && (
            filterType in GENERIC_FILTER_TYPES ||
                ledgerParam != null ||
                selectors.isNotEmpty() ||
                monthYearParam != null ||
                monthParam != null ||
                yearParam != null
            )

    /** True when this report needs the searchable ledger/party picker. */
    val usesLedger: Boolean get() = ledgerParam != null

    /** True when this report needs the single-select choice dropdown. */
    val usesChoice: Boolean get() = choiceParam != null

    /** True when this report needs the month/year picker (single or split params). */
    val usesMonthYear: Boolean get() = monthYearParam != null || monthParam != null

    /** True when this report needs only a year picker (no month). */
    val usesYearOnly: Boolean get() = yearParam != null && monthParam == null

    companion object {
        /** [section] of reports listed under the Reports parent menu. */
        const val SECTION_REPORTS = "reports"

        /** [section] of reports listed under the HRM parent menu. */
        const val SECTION_HRM = "hrm"

        private val GENERIC_FILTER_TYPES = setOf(
            ReportFilterType.BRANCH_DATE_RANGE,
            ReportFilterType.BRANCH_END_DATE,
            ReportFilterType.BRANCH_ONLY,
            ReportFilterType.BRANCH_REPORT_TYPE_END_DATE,
            ReportFilterType.GROUP_REPORT,
        )
    }
}

/** Any of these opens the HRM attendance pages, mirroring the web sidebar. */
private val HRM_ATTENDANCE_PERMISSIONS = listOf("attendance.view", "employee.view")

/** Attendance status filter shared by the HRM attendance reports. */
private val HRM_STATUS_CHOICES = listOf(
    ReportChoice("All Status", ""),
    ReportChoice("Present", "present"),
    ReportChoice("Absent", "absent"),
    ReportChoice("Half Day", "half_day"),
    ReportChoice("Leave", "leave"),
    ReportChoice("Holiday", "holiday"),
    ReportChoice("Weekly Holiday", "weekly_holiday"),
    ReportChoice("Late", "late"),
    ReportChoice("Early Out", "early_out"),
    ReportChoice("Pending", "pending"),
)

/** Internal id/flag columns dropped from the attendance-entry report tables. */
private val HRM_ATTENDANCE_HIDDEN = listOf(
    "id", "company_id", "employee_id", "branch_id", "shift_id", "attendance_policy_id",
    "default_shift_id", "attendance_shift_id", "roster_id", "roster_shift_id",
    "leave_date_id", "leave_pay_status", "is_leave_day", "is_manual", "is_night_shift",
    "requires_approval", "attendance_source", "approved_by", "approved_at",
    "rejected_by", "rejected_at", "approval_remarks", "rejection_reason",
    "overtime_eligible", "daily_wage", "ot_rate", "standard_work_minutes",
    "grace_minutes", "shift_start_time", "shift_end_time", "late_minutes",
    "early_out_minutes", "created_by", "updated_by", "created_at", "updated_at",
)

/**
 * The Daily Attendance Report also drops ID (employee_serial) and Branch, which
 * the user does not want in that table — branch is redundant once filtered, and
 * the serial adds noise. Scoped to this report so the other HRM tables keep them.
 */
private val HRM_ATTENDANCE_REPORT_HIDDEN =
    HRM_ATTENDANCE_HIDDEN + listOf("employee_serial", "branch_name")

/** Header overrides for the attendance-entry report tables. */
private val HRM_ATTENDANCE_LABELS = mapOf(
    "attendance_date" to "Date",
    "employee_serial" to "ID",
    "employee_name" to "Employee",
    "branch_name" to "Branch",
    "shift_name" to "Shift",
    "in_time" to "In",
    "out_time" to "Out",
    "work_minutes" to "Minutes",
    "overtime_minutes" to "OT Min",
    "overtime_amount" to "OT Amount",
    "approval_status" to "Approval",
)

/** Internal columns dropped from the monthly-summary report tables. */
private val HRM_SUMMARY_HIDDEN = listOf(
    "employee_id", "branch_id", "company_id", "summary_month",
)

/** Header overrides for the monthly-summary report tables. */
private val HRM_SUMMARY_LABELS = mapOf(
    "employee_serial" to "ID",
    "employee_name" to "Employee",
    "branch_name" to "Branch",
    "month_days" to "Month Days",
    "present_days" to "Present",
    "leave_days" to "Leave",
    "paid_leave_days" to "Paid Leave",
    "unpaid_leave_days" to "Unpaid Leave",
    "holiday_days" to "Holiday",
    "weekly_holiday_days" to "Weekly Holiday",
    "absent_days" to "Absent",
    "half_days" to "Half Day",
    "late_count" to "Late",
    "early_out_count" to "Early Out",
    "pending_days" to "Pending",
    "payable_days" to "Payable",
    "deduction_days" to "Deduction",
    "late_deduction_days" to "Late Ded.",
    "early_out_deduction_days" to "Early Ded.",
    "overtime_minutes" to "OT Min",
    "overtime_amount" to "OT Amount",
)

/** Status filter shared by the two installment reports (blank = all statuses). */
private val INSTALLMENT_STATUS_CHOICE = ReportChoiceParam(
    paramKey = "status",
    label = "Select Status",
    options = listOf(
        ReportChoice("All", ""),
        ReportChoice("Overdue", "overdue"),
        ReportChoice("Pending", "pending"),
        ReportChoice("Upcoming", "upcoming"),
        ReportChoice("Partial", "partial"),
    ),
)

/**
 * The Reports menu registry and its permission rules. Mirrors the web app's
 * `reportMenu.ts` and `REPORTS_PARENT_PERMISSIONS`.
 */
object ReportMenu {

    /** Any of these grants access to the Reports parent section. */
    val PARENT_PERMISSIONS = listOf(
        "date.wise.total",
        "cashbook.view",
        "bankbook.view",
        "profit.loss",
        "balancesheet.view",
        "trial.balance.l3",
        "trial.balance.l4",
        "bank.information",
        "connected.member.view",
        "productwise.profit",
        "ledger.customer",
        "installment.create",
        "ledger.due.view",
        "ledger.view",
        "ledger.labour",
        "purchase.ledger",
        "sales.ledger",
        "mitch.match",
        "group.report",
        "product.stock.view",
        "product.in.out",
    )

    val all: List<ReportConfig> = listOf(


        ReportConfig(
            key = "cashbook",
            title = "Cashbook",
            routeName = "ReportCashbook",
            webPath = "/reports/cashbook",
            anyOf = listOf("cashbook.view"),
            endpointKey = "cashbook",
            method = ReportMethod.GET,
            filterType = ReportFilterType.BRANCH_DATE_RANGE,
            native = true,
        ),

        ReportConfig(
            key = "bankbook",
            title = "Bank Book",
            routeName = "ReportBankBook",
            webPath = "/reports/bankbook",
            // The web page is gated on bankbook.view, the React sidebar on
            // cashbook.view — accept either rather than hide it from someone
            // the web would let in.
            anyOf = listOf("bankbook.view", "cashbook.view"),
            endpointKey = "bankbook",
            method = ReportMethod.GET,
            filterType = ReportFilterType.BRANCH_DATE_RANGE,
            // Rendered by the bespoke BankBookScreen: the payload mixes an
            // opening row, transactions and three appended summary rows that
            // the generic table would render as ordinary data.
            native = true,
        ),

        ReportConfig(
            key = "ledger",
            title = "Ledger",
            routeName = "ReportLedger",
            webPath = "/reports/ledger",
            anyOf = listOf("ledger.view", "ledger.customer"),
            endpointKey = "ledger",
            method = ReportMethod.GET,
            filterType = ReportFilterType.BRANCH_LEDGER_DATE_RANGE,
            native = true,
        ),


        ReportConfig(
            key = "cashBankReceivedPayment",
            title = "Cash & Bank Summary",
            routeName = "ReportCashBankReceivedPayment",
            webPath = "/reports/cash-bank-received-payment",
            anyOf = listOf("cashbook.view"),
            endpointKey = "cashBankReceivedPayment",
            method = ReportMethod.GET,
            filterType = ReportFilterType.BRANCH_DATE_RANGE,
            dateStyle = ReportDateStyle.DISPLAY,
            // Rendered by the bespoke CashBankScreen. The endpoint answers with
            // two sibling arrays and no `success` envelope, so the generic
            // parser — which keeps a single array — would drop `bank_details`.
            native = true,
        ),
        ReportConfig(
            key = "dateWiseTotal",
            title = "Date Wise Total",
            routeName = "ReportDateWiseTotal",
            webPath = "/reports/date-wise-total-data",
            anyOf = listOf("date.wise.total"),
            endpointKey = "dateWiseTotal",
            method = ReportMethod.GET,
            filterType = ReportFilterType.BRANCH_DATE_RANGE,
        ),
        ReportConfig(
            key = "profitLoss",
            title = "Profit Loss",
            routeName = "ReportProfitLoss",
            webPath = "/reports/profit-loss",
            anyOf = listOf("profit.loss"),
            endpointKey = "profitLoss",
            method = ReportMethod.POST,
            filterType = ReportFilterType.BRANCH_DATE_RANGE,
            // Rendered by the bespoke ProfitLossReportScreen (grouped sections), not the generic flow.
            native = true,
        ),
        ReportConfig(
            key = "balanceSheet",
            title = "Balance Sheet",
            routeName = "ReportBalanceSheet",
            webPath = "/reports/balance-sheet",
            anyOf = listOf("balancesheet.view"),
            endpointKey = "balanceSheet",
            method = ReportMethod.POST,
            filterType = ReportFilterType.BRANCH_DATE_RANGE,
            branchParam = "branchId",
            startParam = "startDate",
            endParam = "endDate",
            // Rendered by the bespoke BalanceSheetReportScreen (structured sections).
            native = true,
        ),
        ReportConfig(
            key = "trialBalanceLevel3",
            title = "Trial Balance Group",
            routeName = "ReportTrialBalanceLevel3",
            webPath = "/reports/trialbalance-level3",
            anyOf = listOf("trial.balance.l3"),
            endpointKey = "trialBalanceLevel3",
            method = ReportMethod.GET,
            filterType = ReportFilterType.BRANCH_DATE_RANGE,
            // Rendered by the bespoke TrialBalanceScreen (grouped OPENING/MOVEMENT/
            // CLOSING header + Grand Total), sharing the Level-4 table.
            native = true,
        ),
        ReportConfig(
            key = "trialBalanceLevel4",
            title = "Trial Balance Details",
            routeName = "ReportTrialBalanceLevel4",
            webPath = "/reports/trialbalance-level4",
            anyOf = listOf("trial.balance.l4"),
            endpointKey = "trialBalanceLevel4",
            method = ReportMethod.GET,
            filterType = ReportFilterType.BRANCH_DATE_RANGE,
            // Rendered by the bespoke TrialBalanceScreen (real table), not the generic flow.
            native = true,
        ),

        ReportConfig(
            key = "bankInformation",
            title = "Bank Information",
            routeName = "ReportBankInformation",
            webPath = "/reports/bank-information",
            anyOf = listOf("bank.information"),
            endpointKey = "bankInformation",
            method = ReportMethod.GET,
            filterType = ReportFilterType.BRANCH_REPORT_TYPE_END_DATE,
            // End date only (no start), sent as dd/MM/yyyy under `enddate`.
            startParam = null,
            endParam = "enddate",
            dateStyle = ReportDateStyle.DISPLAY,
            choiceParam = ReportChoiceParam(
                paramKey = "report_type_id",
                label = "Report Type",
                options = listOf(
                    ReportChoice("Bank Balance", "1"),
                    ReportChoice("Bank Loan", "2"),
                ),
            ),
        ),
        ReportConfig(
            key = "connectedMember",
            title = "Connected Member",
            routeName = "ReportConnectedMember",
            webPath = "/reports/connected-member",
            anyOf = listOf("connected.member.view"),
            endpointKey = "connectedMember",
            method = ReportMethod.POST,
            filterType = ReportFilterType.BRANCH_DATE_RANGE,
            startParam = "startdate",
            endParam = "enddate",
            dateStyle = ReportDateStyle.DISPLAY,
        ),
        ReportConfig(
            key = "productProfitLoss",
            title = "Product Profit Loss",
            routeName = "ReportProductProfitLoss",
            webPath = "/reports/product-profit-loss",
            anyOf = listOf("productwise.profit"),
            endpointKey = "productProfitLoss",
            method = ReportMethod.POST,
            filterType = ReportFilterType.BRANCH_DATE_RANGE,
            // Internal ids, the opening/closing stock pair and the invoice
            // count/detail columns aren't useful on a phone-width table; the
            // profit figures are what the report is for.
            hiddenColumns = listOf(
                "mid",
                "product_id",
                "opening_qty",
                "opening_amount",
                "closing_qty",
                "closing_amount",
                "warning",
                "purchase_invoices",
                "purchase_details",
                "period_in_amount",
            ),
            // Shorter headers so the rate columns don't wrap on a phone.
            columnLabels = mapOf(
                "unit_purchase_rate" to "Pur. Rate",
                "unit_sale_rate" to "Sale Rate",
            ),
        ),
        ReportConfig(
            key = "customerSupplierStatement",
            title = "Customer Supplier Statement",
            routeName = "ReportCustomerSupplierStatement",
            webPath = "/reports/ledger-with-product",
            anyOf = listOf("ledger.customer"),
            endpointKey = "customerSupplierStatement",
            method = ReportMethod.GET,
            filterType = ReportFilterType.BRANCH_LEDGER_DATE_RANGE,
            ledgerParam = "party_id",
            highlightPaths = listOf("remarks"),
            highlightColumn = "remarks",
        ),
        ReportConfig(
            key = "dueInstallments",
            title = "Due Installments",
            routeName = "ReportDueInstallments",
            webPath = "/reports/due-installments",
            anyOf = listOf("installment.create"),
            endpointKey = "dueInstallments",
            method = ReportMethod.POST,
            filterType = ReportFilterType.BRANCH_DATE_RANGE,
            // Rendered by the bespoke DueInstallmentsScreen (web-style customer
            // rows + per-row Receive); the params live in its repository call.
            native = true,
            startParam = "startDate",
            endParam = "endDate",
            choiceParam = INSTALLMENT_STATUS_CHOICE,
        ),
        ReportConfig(
            key = "employeeInstallments",
            title = "Employee Installments",
            routeName = "ReportEmployeeInstallments",
            webPath = "/reports/employee-installment",
            anyOf = listOf("installment.create"),
            endpointKey = "employeeInstallments",
            method = ReportMethod.POST,
            filterType = ReportFilterType.BRANCH_DATE_RANGE,
            startParam = "startDate",
            endParam = "endDate",
            choiceParam = INSTALLMENT_STATUS_CHOICE,
            selectors = listOf(
                ReportSelector(
                    paramKey = "employee_id",
                    label = "Select Field Officer (optional)",
                    source = ReportSelectorSource.EMPLOYEE,
                    required = false,
                ),
            ),
            // "1" not "true"; no upcoming_day — see dueInstallments above.
            extraParams = mapOf("due_only" to "1"),
            hiddenColumns = listOf("installment_id", "payments"),
        ),
        ReportConfig(
            key = "dueList",
            title = "Due List",
            routeName = "ReportDueList",
            webPath = "/reports/due-list",
            anyOf = listOf("ledger.due.view"),
            endpointKey = "dueList",
            method = ReportMethod.GET,
            filterType = ReportFilterType.BRANCH_END_DATE,
            startParam = null,
            endParam = "enddate",
            dateStyle = ReportDateStyle.DISPLAY,
            // Rendered by the bespoke DueListScreen (nested data.data.original parser).
            native = true,
        ),

        ReportConfig(
            key = "productInOut",
            title = "Product In Out",
            routeName = "ReportProductInOut",
            webPath = "/reports/product-ledger-data",
            anyOf = listOf("product.in.out"),
            endpointKey = "productLedgerData",
            method = ReportMethod.GET,
            filterType = ReportFilterType.BRANCH_PRODUCT_DATE_RANGE,
            startParam = "startdate",
            endParam = "enddate",
            ledgerParam = "ledger_id",
            dateStyle = ReportDateStyle.DISPLAY,
        ),
        ReportConfig(
            key = "labourLedger",
            title = "Labour Ledger",
            routeName = "ReportLabourLedger",
            webPath = "/reports/labour/ledger",
            anyOf = listOf("ledger.labour"),
            endpointKey = "labourLedger",
            method = ReportMethod.POST,
            filterType = ReportFilterType.BRANCH_PRODUCT_DATE_RANGE,
            // camelCase params; nested {branch:{labour:[rows]}} payload.
            branchParam = "branchId",
            startParam = "startDate",
            endParam = "endDate",
            ledgerParam = "ledgerId",
            ledgerRequired = false,
            selectors = listOf(
                ReportSelector(
                    paramKey = "labourId",
                    label = "Select Labour (optional)",
                    source = ReportSelectorSource.LABOUR,
                    required = false,
                ),
            ),
            responseShape = ReportResponseShape.NESTED_GROUPS,
        ),
        ReportConfig(
            key = "purchaseLedger",
            title = "Purchase Ledger",
            routeName = "ReportPurchaseLedger",
            webPath = "/reports/purchase-ledger",
            anyOf = listOf("purchase.ledger"),
            endpointKey = "purchaseLedger",
            method = ReportMethod.GET,
            filterType = ReportFilterType.BRANCH_LEDGER_DATE_RANGE,
            startParam = "startdate",
            endParam = "enddate",
            ledgerParam = "ledger_id",
            extraParams = mapOf("delay" to "1"),
            // The purchase voucher's note lives only inside the nested master;
            // it is surfaced as a "Notes" column and highlight-rule matched.
            highlightPaths = listOf("purchase_master.notes"),
            highlightColumn = "notes",
        ),
        ReportConfig(
            key = "salesLedger",
            title = "Sales Ledger",
            routeName = "ReportSalesLedger",
            webPath = "/reports/sales-ledger",
            anyOf = listOf("sales.ledger"),
            endpointKey = "salesLedger",
            method = ReportMethod.GET,
            filterType = ReportFilterType.BRANCH_LEDGER_DATE_RANGE,
            startParam = "startdate",
            endParam = "enddate",
            ledgerParam = "ledger_id",
            extraParams = mapOf("delay" to "1"),
            // Same fallback order the web uses: the sales note, the flat copy
            // the API appends, then the journal detail's remarks.
            highlightPaths = listOf(
                "sales_master.notes",
                "notes",
                "acc_transaction_master.0.acc_transaction_details.0.remarks",
            ),
            highlightColumn = "notes",
        ),
        ReportConfig(
            key = "mitchMatch",
            title = "Mitch Match",
            routeName = "ReportMitchMatch",
            webPath = "/reports/mitch-match",
            anyOf = listOf("mitch.match"),
            endpointKey = "mitchMatch",
            method = ReportMethod.GET,
            // Only branch_id + delay=1; no dates.
            filterType = ReportFilterType.BRANCH_END_DATE,
            startParam = null,
            endParam = null,
            extraParams = mapOf("delay" to "1"),
        ),
        ReportConfig(
            key = "groupReport",
            title = "Group Report",
            routeName = "ReportGroup",
            webPath = "/reports/group-report",
            anyOf = listOf("group.report"),
            endpointKey = "groupReport",
            method = ReportMethod.POST,
            filterType = ReportFilterType.GROUP_REPORT,
            startParam = "startdate",
            endParam = "enddate",
            dateStyle = ReportDateStyle.DISPLAY,
        ),
        ReportConfig(
            key = "collectionSheet",
            title = "Collection Sheet",
            routeName = "ReportCollectionSheet",
            webPath = "/somity-report/collection-sheet",
            anyOf = listOf("group.report", "ledger.due.view", "cashbook.view"),
            endpointKey = "collectionSheet",
            method = ReportMethod.POST,
            filterType = ReportFilterType.COLLECTION_SHEET,
            // No date range: a month/year (sent MM/yyyy) plus a somity picker. The
            // server prepends "01/" to month_year, so MM/yyyy is what it expects.
            startParam = null,
            endParam = null,
            monthYearParam = "month_year",
            selectors = listOf(
                ReportSelector(
                    paramKey = "somity_id",
                    label = "Select Somity",
                    source = ReportSelectorSource.SOMITY,
                    required = true,
                ),
            ),
            choiceParam = ReportChoiceParam(
                paramKey = "type_id",
                label = "Collection Type",
                options = listOf(
                    ReportChoice("Full Month", "2"),
                    ReportChoice("Opening Day", "1"),
                ),
            ),
            responseShape = ReportResponseShape.NORMAL,
        ),
        ReportConfig(
            key = "monthlyReport",
            title = "Monthly Report",
            routeName = "ReportMonthly",
            webPath = "/somity-report/monthly-report",
            anyOf = listOf("group.report", "ledger.due.view", "cashbook.view"),
            endpointKey = "monthlyReport",
            method = ReportMethod.POST,
            filterType = ReportFilterType.BRANCH_DATE_RANGE,
            startParam = "startdate",
            endParam = "enddate",
            dateStyle = ReportDateStyle.DISPLAY,
        ),
        ReportConfig(
            key = "closingStock",
            title = "Closing Stock",
            routeName = "ReportClosingStock",
            webPath = "/reports/closing-stock",
            anyOf = listOf("product.stock.view"),
            endpointKey = "closingStock",
            method = ReportMethod.POST,
            filterType = ReportFilterType.BRANCH_DATE_RANGE,
            // The web sends both the new and legacy date keys.
            startParam = "start_date",
            endParam = "end_date",
            altStartParam = "startdate",
            altEndParam = "enddate",
        ),
        ReportConfig(
            key = "stockDetails",
            title = "Stock Details",
            routeName = "ReportStockDetails",
            webPath = "/somity-report/stock-details",
            anyOf = listOf("product.stock.view"),
            endpointKey = "closingStock",
            method = ReportMethod.POST,
            filterType = ReportFilterType.BRANCH_DATE_RANGE,
            startParam = "start_date",
            endParam = "end_date",
            altStartParam = "startdate",
            altEndParam = "enddate",
        ),
        ReportConfig(
            key = "productStock",
            title = "Product Stock",
            routeName = "ReportProductStock",
            webPath = "/reports/product/stock",
            anyOf = listOf("product.stock.view"),
            endpointKey = "productStock",
            method = ReportMethod.POST,
            filterType = ReportFilterType.BRANCH_BRAND_CATEGORY_PRODUCT_DATE_RANGE,
            startParam = "startdate",
            endParam = "enddate",
            // Internal ids + the standalone unit column (unit is shown inline on
            // each amount instead) the user doesn't need to see.
            hiddenColumns = listOf("product_id", "category_id", "unit"),
            // Show "-" for 0, and suffix the unit ("1 nos") for the stock amounts.
            zeroDashColumns = listOf("opening", "stock_in", "stock_out", "balance"),
            unitColumn = "unit",
            selectors = listOf(
                ReportSelector(
                    paramKey = "brand_id",
                    label = "Select Brand (optional)",
                    source = ReportSelectorSource.BRAND,
                    required = false,
                ),
                ReportSelector(
                    paramKey = "category_id",
                    label = "Select Category (optional)",
                    source = ReportSelectorSource.CATEGORY,
                    required = false,
                ),
                ReportSelector(
                    paramKey = "product_name",
                    label = "Select Product (optional)",
                    source = ReportSelectorSource.PRODUCT,
                    required = false,
                    // The endpoint filters on the product name, not its id.
                    sendLabel = true,
                ),
            ),
        ),
        ReportConfig(
            key = "imeiStock",
            title = "IMEI Stock",
            routeName = "ReportImeiStock",
            webPath = "/reports/stock-imei",
            anyOf = listOf("product.stock.view"),
            endpointKey = "imeiStock",
            method = ReportMethod.GET,
            filterType = ReportFilterType.BRANCH_PRODUCT_ONLY,
            // No date range — just a product (item_id). Payload is an object keyed
            // "1","2",… of IMEI strings, or [] when empty.
            startParam = null,
            endParam = null,
            selectors = listOf(
                ReportSelector(
                    paramKey = "item_id",
                    label = "Select Product",
                    source = ReportSelectorSource.PRODUCT,
                    required = true,
                ),
            ),
            responseShape = ReportResponseShape.KEYED_SCALARS,
            scalarLabel = "IMEI / Serial",
        ),
        ReportConfig(
            key = "categoryWiseInOut",
            title = "Category Wise In Out",
            routeName = "ReportCategoryWiseInOut",
            webPath = "/reports/cat-wise/in-out",
            anyOf = listOf("product.in.out"),
            endpointKey = "categoryWiseInOut",
            method = ReportMethod.POST,
            filterType = ReportFilterType.BRANCH_CATEGORY_DATE_RANGE,
            startParam = "startdate",
            endParam = "enddate",
            choiceParam = ReportChoiceParam(
                paramKey = "reportType",
                label = "Report Type",
                options = listOf(
                    ReportChoice("Purchase", "1"),
                    ReportChoice("Sales", "2"),
                ),
            ),
            selectors = listOf(
                ReportSelector(
                    paramKey = "category_id",
                    label = "Select Category (optional)",
                    source = ReportSelectorSource.CATEGORY,
                    required = false,
                ),
            ),
        ),
        ReportConfig(
            key = "dateWiseInOut",
            title = "Date Wise In Out",
            routeName = "ReportDateWiseInOut",
            webPath = "/reports/in-out/date-wise",
            anyOf = listOf("product.in.out"),
            endpointKey = "dateWiseInOut",
            method = ReportMethod.GET,
            filterType = ReportFilterType.BRANCH_DATE_RANGE_WITH_OPTIONAL_PRODUCT,
            startParam = "startdate",
            endParam = "enddate",
            ledgerParam = "ledger_id",
            ledgerRequired = false,
            dateStyle = ReportDateStyle.DISPLAY,
        ),

        // ---- HRM section (listed by HrmMenu, not the Reports home) ----
        // These ride the same generic engine; endpoints/params mirror the web's
        // hrms pages exactly (see the React module + Laravel Hrms controllers).
        ReportConfig(
            key = "hrmAttendanceReport",
            title = "Attendance Report",
            routeName = "HrmAttendanceReport",
            webPath = "/hrms/attendance/report",
            anyOf = HRM_ATTENDANCE_PERMISSIONS,
            endpointKey = "hrmAttendanceReport",
            method = ReportMethod.GET,
            filterType = ReportFilterType.BRANCH_DATE_RANGE,
            startParam = "date_from",
            endParam = "date_to",
            choiceParam = ReportChoiceParam(
                paramKey = "status",
                label = "Select Status",
                options = HRM_STATUS_CHOICES,
            ),
            section = ReportConfig.SECTION_HRM,
            hiddenColumns = HRM_ATTENDANCE_REPORT_HIDDEN,
            columnLabels = HRM_ATTENDANCE_LABELS,
            textColumns = listOf("employee_serial"),
        ),
        ReportConfig(
            key = "hrmOvertimeReport",
            title = "Overtime Report",
            routeName = "HrmOvertimeReport",
            webPath = "/hrms/attendance/overtime-report",
            anyOf = HRM_ATTENDANCE_PERMISSIONS,
            endpointKey = "hrmAttendanceReport",
            method = ReportMethod.GET,
            filterType = ReportFilterType.BRANCH_DATE_RANGE,
            startParam = "date_from",
            endParam = "date_to",
            extraParams = mapOf("overtime_only" to "1", "overtime_eligible" to "1"),
            section = ReportConfig.SECTION_HRM,
            hiddenColumns = HRM_ATTENDANCE_HIDDEN,
            columnLabels = HRM_ATTENDANCE_LABELS,
            textColumns = listOf("employee_serial"),
        ),
        ReportConfig(
            key = "hrmAuditHistory",
            title = "Audit History",
            routeName = "HrmAuditHistory",
            webPath = "/hrms/attendance/audit-history",
            anyOf = HRM_ATTENDANCE_PERMISSIONS,
            endpointKey = "hrmAuditHistory",
            method = ReportMethod.GET,
            filterType = ReportFilterType.BRANCH_DATE_RANGE,
            startParam = "date_from",
            endParam = "date_to",
            choiceParam = ReportChoiceParam(
                paramKey = "action",
                label = "Select Action",
                options = listOf(
                    ReportChoice("All Actions", ""),
                    ReportChoice("Submitted", "submitted"),
                    ReportChoice("Corrected", "corrected"),
                    ReportChoice("Approved", "approved"),
                    ReportChoice("Rejected", "rejected"),
                    ReportChoice("Cleared/Cancelled", "cancelled"),
                ),
            ),
            selectors = listOf(
                ReportSelector(
                    paramKey = "employee_id",
                    label = "Select Employee (optional)",
                    source = ReportSelectorSource.EMPLOYEE,
                    required = false,
                ),
            ),
            extraParams = mapOf("per_page" to "100"),
            section = ReportConfig.SECTION_HRM,
            hiddenColumns = listOf(
                "id", "attendance_entry_id", "employee_id", "branch_id", "action_by", "created_at",
            ),
            columnLabels = mapOf(
                "employee_serial" to "ID",
                "employee_name" to "Employee",
                "branch_name" to "Branch",
                "attendance_date" to "Date",
                "attendance_status" to "Status",
                "approval_status" to "Approval",
                "action_by_name" to "Action By",
                "action_at" to "Action Time",
            ),
            textColumns = listOf("employee_serial"),
        ),
        // Monthly Attendance is NOT here: it needs the web's two-tab layout
        // (summary + day-by-day matrix), so it has a native screen — see
        // ui/hrm/MonthlyAttendanceScreen, reached via the HRM form route.
        ReportConfig(
            key = "hrmAttendanceAlerts",
            title = "Attendance Alerts",
            routeName = "HrmAttendanceAlerts",
            webPath = "/hrms/attendance/exception-reports",
            anyOf = HRM_ATTENDANCE_PERMISSIONS,
            endpointKey = "hrmAttendanceReport",
            method = ReportMethod.GET,
            filterType = ReportFilterType.BRANCH_DATE_RANGE,
            startParam = "date_from",
            endParam = "date_to",
            choiceParam = ReportChoiceParam(
                paramKey = "status",
                label = "Alert Type",
                options = listOf(
                    ReportChoice("Absent", "absent"),
                    ReportChoice("Late", "late"),
                    ReportChoice("Early Out", "early_out"),
                ),
            ),
            section = ReportConfig.SECTION_HRM,
            hiddenColumns = HRM_ATTENDANCE_HIDDEN,
            columnLabels = HRM_ATTENDANCE_LABELS,
            textColumns = listOf("employee_serial"),
        ),
        ReportConfig(
            key = "hrmEmployeeAttendance",
            title = "Employee Attendance",
            routeName = "HrmEmployeeAttendance",
            webPath = "/hrms/attendance/employee-report",
            anyOf = HRM_ATTENDANCE_PERMISSIONS,
            endpointKey = "hrmAttendanceReport",
            method = ReportMethod.GET,
            filterType = ReportFilterType.BRANCH_DATE_RANGE,
            startParam = "date_from",
            endParam = "date_to",
            choiceParam = ReportChoiceParam(
                paramKey = "status",
                label = "Select Status",
                options = HRM_STATUS_CHOICES,
            ),
            selectors = listOf(
                ReportSelector(
                    paramKey = "employee_id",
                    label = "Select Employee",
                    source = ReportSelectorSource.EMPLOYEE,
                    required = true,
                ),
            ),
            section = ReportConfig.SECTION_HRM,
            hiddenColumns = HRM_ATTENDANCE_HIDDEN,
            columnLabels = HRM_ATTENDANCE_LABELS,
            textColumns = listOf("employee_serial"),
        ),
        ReportConfig(
            key = "hrmBranchAttendance",
            title = "Branch Attendance",
            routeName = "HrmBranchAttendance",
            webPath = "/hrms/attendance/branch-summary",
            anyOf = HRM_ATTENDANCE_PERMISSIONS,
            endpointKey = "hrmMonthlySummary",
            method = ReportMethod.GET,
            filterType = ReportFilterType.BRANCH_MONTH_YEAR,
            startParam = null,
            endParam = null,
            monthParam = "month",
            yearParam = "year",
            section = ReportConfig.SECTION_HRM,
            hiddenColumns = HRM_SUMMARY_HIDDEN,
            columnLabels = HRM_SUMMARY_LABELS,
            textColumns = listOf("employee_serial"),
        ),
        ReportConfig(
            key = "hrmHolidayCalendar",
            title = "Holiday Calendar",
            routeName = "HrmHolidayCalendar",
            webPath = "/hrms/attendance/holiday-calendar",
            anyOf = HRM_ATTENDANCE_PERMISSIONS,
            endpointKey = "hrmHolidays",
            method = ReportMethod.GET,
            filterType = ReportFilterType.BRANCH_DATE_RANGE,
            startParam = "date_from",
            endParam = "date_to",
            choiceParam = ReportChoiceParam(
                paramKey = "holiday_type",
                label = "Holiday Type",
                options = listOf(
                    ReportChoice("All Types", ""),
                    ReportChoice("Government", "government"),
                    ReportChoice("Festival", "festival"),
                    ReportChoice("Company", "company"),
                    ReportChoice("Optional", "optional"),
                    ReportChoice("Project", "project"),
                    ReportChoice("Weekly", "weekly"),
                    ReportChoice("Other", "other"),
                ),
            ),
            extraParams = mapOf("per_page" to "100"),
            section = ReportConfig.SECTION_HRM,
            hiddenColumns = listOf(
                "id", "company_id", "branch_id", "branch_type_id", "department_id",
                "created_by", "updated_by", "created_at", "updated_at",
            ),
            columnLabels = mapOf(
                "holiday_date" to "Date",
                "holiday_name" to "Holiday",
                "holiday_type" to "Type",
                "is_paid" to "Paid",
                "is_optional" to "Optional",
            ),
        ),
        ReportConfig(
            key = "hrmLoanBalance",
            title = "Loan Balance",
            routeName = "HrmLoanBalance",
            webPath = "/accounts/employee-loan/balance",
            anyOf = listOf("hrm.loan.create"),
            endpointKey = "hrmLoanBalance",
            method = ReportMethod.GET,
            filterType = ReportFilterType.BRANCH_ONLY,
            startParam = null,
            endParam = null,
            section = ReportConfig.SECTION_HRM,
            hiddenColumns = listOf("emp_id", "received_amt", "payment_amt"),
            columnLabels = mapOf(
                "employee_name" to "Employee",
                "total_senction" to "Total Sanction",
                "total_payment" to "Total Payment",
            ),
        ),
        ReportConfig(
            key = "hrmLoanLedger",
            title = "Loan Ledger",
            routeName = "HrmLoanLedger",
            webPath = "/accounts/employee-loan/ledger",
            anyOf = listOf("employee.loan.ledger.view"),
            endpointKey = "hrmLoanLedger",
            method = ReportMethod.POST,
            filterType = ReportFilterType.BRANCH_LEDGER_DATE_RANGE,
            branchParam = "branchId",
            startParam = "startDate",
            endParam = "endDate",
            selectors = listOf(
                ReportSelector(
                    paramKey = "ledgerId",
                    label = "Select Employee",
                    source = ReportSelectorSource.EMPLOYEE,
                    required = true,
                ),
            ),
            section = ReportConfig.SECTION_HRM,
            hiddenColumns = listOf(
                "id", "loan_detail_id", "branch_id", "branch_pad", "voucher_image",
            ),
            columnLabels = mapOf(
                "vr_no" to "Vr No",
                "vr_date" to "Vr Date",
                "branch_name" to "Branch",
                "received_amt" to "Received",
                "payment_amt" to "Payment",
            ),
        ),
        // Salary Reports is NOT here: it needs the web's Paid/Due action column
        // and payment flow, so it has a native screen — see
        // ui/hrm/SalarySheetScreen, reached via the HRM form route.
    )

    private val byKey: Map<String, ReportConfig> = all.associateBy { it.key }

    fun byKey(key: String?): ReportConfig? = key?.let { byKey[it] }

    /** True when the user can see the Reports parent section at all. */
    fun hasParentAccess(permissions: List<Permission>?): Boolean =
        Permissions.hasAny(permissions, PARENT_PERMISSIONS)

    /** Reports-section reports the user is allowed to open, in registry order. */
    fun visible(permissions: List<Permission>?): List<ReportConfig> =
        all.filter {
            it.section == ReportConfig.SECTION_REPORTS &&
                Permissions.hasAny(permissions, it.anyOf)
        }

    /**
     * The permissions guarding the report [key], for route-level gates. Routes
     * MUST use this rather than repeating the list, so a rule change here can
     * never leave the menu and the route disagreeing (a stale route gate would
     * let a hidden report open via deep link).
     *
     * An unknown key falls back to the full-access wildcard — fail closed, since
     * a gate that silently allows everyone is the worse failure.
     */
    fun permissionsFor(key: String): List<String> =
        byKey(key)?.anyOf ?: listOf(WILDCARD_PERMISSION)
}
