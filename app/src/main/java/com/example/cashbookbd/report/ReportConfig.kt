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

    /**
     * An object of `{ "<id>": {row}, … }` keyed by a dynamic id (Requisition
     * Comparison) — each entry's object becomes one row. `[]` when empty.
     */
    KEYED_OBJECTS,
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
 * Where a report's rows carry their voucher attachments. [imageKey] holds the
 * pipe-separated file names (`main_trx_master.voucher_image`); the branch
 * context comes from [branchPadKey] (already zero-padded, Cash Book style) or,
 * failing that, [branchIdKey] (a raw id the client pads to 4). The raw keys are
 * hidden from the text table — the thumbnails column replaces them, gated on
 * the branch's `show_voucher_image` switch like the web.
 */
data class ReportVoucherImages(
    val imageKey: String = "voucher_image",
    val branchIdKey: String? = "branch_id",
    val branchPadKey: String? = null,
)

/**
 * Two adjacent data columns rendered as ONE stacked cell — the [topKey] value on
 * top, the [bottomKey] value beneath — mirroring the web's combined column (e.g.
 * the Loan Ledger's "Vr No & Date": the voucher number over its date). The merged
 * column takes the [topKey]'s position and [header]; the [bottomKey] column drops.
 */
data class ReportStackedColumn(
    val header: String,
    val topKey: String,
    val bottomKey: String,
    /** Reformat the bottom value from a yyyy-MM-dd wire date to dd/MM/yyyy. */
    val bottomIsDate: Boolean = false,
)

/**
 * A client-computed running-balance column, the web's Product In Out "Stock":
 * seeded from the payload's sibling `opening` object (first non-blank of
 * [openingKeys]), then per detail row `+= Σ addKeys − Σ subtractKeys`. A
 * synthetic Opening row leads the table — the web renders that row even when
 * no opening exists — carrying the word "Opening" in [labelCellKey]'s column.
 */
data class ReportRunningBalance(
    val openingKeys: List<String>,
    val addKeys: List<String>,
    val subtractKeys: List<String>,
    /** The computed column's row key. */
    val columnKey: String = "stock",
    /** The opening figure's own column (dash on every detail row). */
    val openingColumnKey: String = "opening",
    /** Which column carries the word "Opening" on the synthetic first row. */
    val labelCellKey: String = "vr_no",
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
    /** What the Start Date opens on: the business date, or a wider window. */
    val startDateDefault: StartDateDefault = StartDateDefault.TRANSACTION_DATE,
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
     * Raw API row keys in the order the WEB table shows its columns
     * (case-insensitive). Without this the columns follow the server's key
     * order; keys not listed here trail behind the listed ones, still in
     * server order.
     */
    val columnOrder: List<String> = emptyList(),
    /**
     * Raw API row keys summed into a bold footer Total row (case-insensitive),
     * mirroring the web tables' tfoot. Empty = no footer.
     */
    val totalColumns: List<String> = emptyList(),
    /** The footer row's left label ("Total", "Grand Total", "Summary"…). */
    val totalRowLabel: String = "Total",
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
     * Raw API row keys (case-insensitive) holding a `yyyy-MM-dd` (or ISO
     * datetime) date, rendered as `dd/MM/yyyy` the way the web reformats its
     * date cells. Anything that does not parse passes through verbatim.
     */
    val dateColumns: List<String> = emptyList(),
    /** A client-computed running balance (Product In Out's Stock column). */
    val runningBalance: ReportRunningBalance? = null,
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
    /**
     * When set, the rows' voucher attachments become a tappable thumbnail
     * column (shown only while the branch's `show_voucher_image` is on).
     */
    val voucherImages: ReportVoucherImages? = null,
    /**
     * Adjacent column pairs to render as ONE stacked cell (top value over bottom),
     * e.g. the Loan Ledger's "Vr No & Date". Empty = no merging.
     */
    val stackedColumns: List<ReportStackedColumn> = emptyList(),
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

        /** [section] of reports listed under the Requisition parent menu. */
        const val SECTION_REQUISITION = "requisition"

        /** Listed by the Branch Transfer section, not the Reports home. */
        const val SECTION_BRANCH_TRANSFER = "branch_transfer"

        private val GENERIC_FILTER_TYPES = setOf(
            ReportFilterType.BRANCH_DATE_RANGE,
            ReportFilterType.BRANCH_END_DATE,
            ReportFilterType.BRANCH_ONLY,
            ReportFilterType.BRANCH_REPORT_TYPE_END_DATE,
            ReportFilterType.GROUP_REPORT,
        )
    }

    /** What the Start Date field opens on, before the clerk touches it. */
    enum class StartDateDefault {
        /** The backend's business date — a one-day window (the default). */
        TRANSACTION_DATE,

        /** The 1st of the business date's month — "the month so far". */
        MONTH_FIRST,

        /** January 1st of the business date's year — "the year so far". */
        YEAR_FIRST,
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
    HRM_ATTENDANCE_HIDDEN + listOf(
        "employee_serial", "branch_name",
        // The web's daily table never shows these; they belong to the
        // overtime report (which keeps its own hidden list).
        "overtime_minutes", "overtime_amount", "remarks",
        "employment_type", "is_other_branch", "leave_type_name",
    )

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

/** The web ClosingStockReport's visible six columns — the rest stay internal. */
private val CLOSING_STOCK_HIDDEN = listOf(
    "id", "vr_no", "category", "brand", "prodct_detls_id", "purchase_pct",
)

private val CLOSING_STOCK_LABELS = mapOf(
    "product_name" to "Product Details",
    "stock" to "Stock Qty",
    "rate" to "Rate (Tk.)",
    "total_stock" to "Total (Tk.)",
)

/** The web daily-attendance table's column order (ID/Branch stay hidden here). */
private val HRM_ATTENDANCE_WEB_ORDER = listOf(
    "attendance_date", "employee_name", "shift_name",
    "in_time", "out_time", "work_minutes", "status", "approval_status",
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

    /**
     * Any of these grants access to the Reports parent section. Kept as the union
     * of every report's own gate (below) so the section shows iff the user can
     * open at least one report — matching the web sidebar's per-item permissions.
     */
    val PARENT_PERMISSIONS = listOf(
        "cashbook.view",
        "bank.book",
        "cash.bank.summery",
        "profit.loss",
        "productwise.profit",
        "bank.information",
        "connected.member.view",
        "balancesheet.view",
        "trial.balance.l3",
        "trial.balance.l4",
        "installment.create",
        "ledger.view",
        "ledger.customer",
        "ledger.details",
        "product.in.out",
        "ledger.labour",
        "due.list",
        // The web sidebar's Reports parent is gated on ledger.due.view too — keep
        // it here so a holder of it sees the section like the web does.
        "ledger.due.view",
        "collection.sheet",
        "monthly.report",
        "date.wise.total",
        "product.stock.view",
        "product.stock.details",
        "imei.stock",
        "purchase.ledger",
        "sales.ledger",
        // Godown Stock answers to its own permission — deliberately NOT
        // product.stock.view, which almost everybody holds (web 55ffca9).
        "godown.stock",
        "group.report",
        "mitch.match",
        // The three branch stock-movement reports (web sidebar item gates).
        "branch.transfer.create",
        "branch.received.create",
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

        // The two product-tracking memo reports — bespoke screens: summary
        // cards, running balances and the unmapped-transactions notice don't
        // fit the generic table.
        // Product Statement / Product Receivable-Payable moved out to the
        // Product Tracking drawer section, next to the settings screen that
        // decides what they report on (web db96532).

        ReportConfig(
            key = "bankbook",
            title = "Bank Book",
            routeName = "ReportBankBook",
            webPath = "/reports/bankbook",
            // The web sidebar gates Bank Book on `bank.book` (not cashbook.view).
            anyOf = listOf("bank.book"),
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
            // The web sidebar gates this on its own `cash.bank.summery`.
            anyOf = listOf("cash.bank.summery"),
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
            // A day-by-day total is read for the month so far, not one day.
            startDateDefault = ReportConfig.StartDateDefault.MONTH_FIRST,
            // The web's abbreviated cumulative headers.
            columnLabels = mapOf(
                "cumulative_debit" to "Cum. Debit",
                "cumulative_credit" to "Cum. Credit",
            ),
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
            key = "expenseReport",
            title = "Expense Report",
            routeName = "ReportExpense",
            webPath = "/reports/expense-report",
            anyOf = listOf("expense.report"),
            endpointKey = "expenseReport",
            method = ReportMethod.GET,
            filterType = ReportFilterType.BRANCH_DATE_RANGE,
            // The bespoke ExpenseReportScreen: Trial Balance Group's layout
            // narrowed to expense heads, with tap-to-open detail rows.
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
            // The web shows only Bank Name + the two balances; the raw
            // debit/credit movement pair and the id stay internal.
            hiddenColumns = listOf("coa4_id", "debit", "credit"),
            columnLabels = mapOf(
                "dr_bal" to "Debit Balance",
                "cr_bal" to "Credit Balance",
            ),
            totalColumns = listOf("dr_bal", "cr_bal"),
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
            native = true,
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
            dateColumns = listOf("vr_date"),
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
                "period_in_qty",
            ),
            // The web's column order: product first, the two invoices, then
            // the qty/rate/total pairs, ending on Effect.
            columnOrder = listOf(
                "product_name", "purchase_invoice", "vr_no", "vr_date", "sold_qty",
                "unit_purchase_rate", "purchase_total", "unit_sale_rate", "sale_total", "profit",
            ),
            // The web's headers.
            columnLabels = mapOf(
                "purchase_invoice" to "Pur. Invoice",
                "vr_no" to "Sal. Invoice",
                "unit_purchase_rate" to "Unit Purchase",
                "unit_sale_rate" to "Unit Sale",
                "profit" to "Effect",
            ),
            // The web's Summary row also totals Effect (its "Net Profit/Loss");
            // here it is the plain signed sum.
            totalColumns = listOf("sold_qty", "purchase_total", "sale_total", "profit"),
            totalRowLabel = "Summary",
        ),
        ReportConfig(
            key = "customerSupplierStatement",
            title = "Customer Supplier Statement",
            routeName = "ReportCustomerSupplierStatement",
            webPath = "/reports/ledger-with-product",
            // The web sidebar's "Ledger Details" item is gated on `ledger.details`.
            anyOf = listOf("ledger.details"),
            endpointKey = "customerSupplierStatement",
            method = ReportMethod.GET,
            filterType = ReportFilterType.BRANCH_LEDGER_DATE_RANGE,
            ledgerParam = "party_id",
            highlightPaths = listOf("remarks"),
            highlightColumn = "remarks",
            // The web's twelve columns; the raw voucher internals stay hidden.
            hiddenColumns = listOf(
                "voucher_type", "trx_type", "product_name", "quantity", "total",
                "received", "payment", "party_name", "mtmid", "is_approved",
            ),
            columnOrder = listOf(
                "vr_no", "vr_date", "transaction_name", "sales_item_name",
                "order_number", "truck_no", "rate", "purchase_total",
                "sales_total", "debit", "credit", "balance", "remarks",
            ),
            columnLabels = mapOf(
                "truck_no" to "Vehicle No",
                "purchase_total" to "Pur. Total",
                "sales_total" to "Sal. Total",
            ),
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
            hiddenColumns = listOf(
                "installment_id", "payments",
                // Web shows none of these as columns.
                "invoice_no", "coa4_id", "area_id", "area_name", "received_date",
            ),
            // The web's order: the customer block first, then the schedule.
            columnOrder = listOf(
                "customer_name", "father", "customer_address", "customer_mobile",
                "employee", "installment_no", "due_date", "amount", "due_amount",
                "paid_amount", "status",
            ),
            columnLabels = mapOf(
                "installment_no" to "Inst No",
                "amount" to "Inst. Amount",
                "paid_amount" to "Rcv Amount",
            ),
            // The web's "Total due amount" line under the table.
            totalColumns = listOf("due_amount"),
            totalRowLabel = "Total due amount",
        ),
        ReportConfig(
            key = "dueList",
            title = "Due List",
            routeName = "ReportDueList",
            webPath = "/reports/due-list",
            // The web sidebar gates Due List on its own `due.list`.
            anyOf = listOf("due.list"),
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
            // Internal ids the web never shows.
            hiddenColumns = listOf("mtmid", "product_id"),
            columnLabels = mapOf(
                "vr_no" to "Invoice No.",
                "vr_date" to "Vr. Date",
                "opening" to "Opening",
                "stock" to "Stock",
            ),
            dateColumns = listOf("vr_date"),
            // The web computes the Stock column client-side: opening stock,
            // then + purchase + sales_return − sales − purchase_return per row,
            // with a synthetic Opening row leading the table.
            runningBalance = ReportRunningBalance(
                openingKeys = listOf("stock", "opening", "opening_qty"),
                addKeys = listOf("purchase", "sales_return"),
                subtractKeys = listOf("sales", "purchase_return"),
            ),
            columnOrder = listOf(
                "vr_no", "vr_date", "opening",
                "purchase", "sales_return", "sales", "purchase_return", "stock",
            ),
            totalColumns = listOf("purchase", "sales_return", "sales", "purchase_return"),
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
            // The web shows SL | VR No | Date | Description | Qty | Rate |
            // Total; every grouping/id helper stays internal.
            hiddenColumns = listOf(
                "is_approved", "group_key", "branch_id", "branch_name",
                "labour_id", "labour_name", "payment_this_invoice", "labour_item",
            ),
            columnOrder = listOf("vr_no", "vr_date", "coa4_name", "note", "qty", "rate", "total"),
            columnLabels = mapOf(
                "vr_date" to "Date",
                "coa4_name" to "Description",
            ),
            // The web totals only the amount, not the quantity.
            totalColumns = listOf("total"),
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
            // The API runs branch-wide when no supplier is chosen.
            ledgerRequired = false,
            extraParams = mapOf("delay" to "1"),
            // Rendered by the bespoke TradeLedgerScreen: the web's one-row-per-
            // voucher table with stacked product lines cannot ride the flat
            // generic table.
            native = true,
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
            // The API runs branch-wide when no customer is chosen.
            ledgerRequired = false,
            extraParams = mapOf("delay" to "1"),
            // Rendered by the bespoke TradeLedgerScreen — see purchaseLedger.
            native = true,
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
            columnLabels = mapOf(
                "total_debit" to "Debit (Tk)",
                "total_credit" to "Credit (Tk)",
            ),
            dateColumns = listOf("vr_date"),
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
            native = true,
            startParam = "startdate",
            endParam = "enddate",
            dateStyle = ReportDateStyle.DISPLAY,
        ),
        ReportConfig(
            key = "collectionSheet",
            title = "Collection Sheet",
            routeName = "ReportCollectionSheet",
            webPath = "/somity-report/collection-sheet",
            // The web sidebar gates Collection Sheet on its own `collection.sheet`.
            anyOf = listOf("collection.sheet"),
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
                // The web's wording and default: Status, Opening first.
                label = "Status",
                options = listOf(
                    ReportChoice("Opening", "1"),
                    ReportChoice("Closing", "2"),
                ),
            ),
            responseShape = ReportResponseShape.NORMAL,
            // The web's member sheet: the id/relation and the four server-side
            // Bangla-rendering helpers stay internal.
            hiddenColumns = listOf(
                "coa4_id", "relation", "bangla_font_family", "bangla_font_url",
                "bangla_html", "father_bangla_html",
            ),
            columnOrder = listOf(
                "bangla", "name", "idfr_code", "father_bangla", "mobile",
                "sales", "down_payment", "previous_collection", "installment",
                "this_month_collection",
            ),
            columnLabels = mapOf(
                "sales" to "Total Sales",
                "previous_collection" to "Prv. Coll.",
                "this_month_collection" to "This Month",
            ),
            totalColumns = listOf(
                "sales", "down_payment", "previous_collection", "this_month_collection",
            ),
            totalRowLabel = "Grand Total",
        ),
        ReportConfig(
            key = "monthlyReport",
            title = "Monthly Report",
            routeName = "ReportMonthly",
            webPath = "/somity-report/monthly-report",
            // The web sidebar gates Monthly Report on its own `monthly.report`.
            anyOf = listOf("monthly.report"),
            endpointKey = "monthlyReport",
            method = ReportMethod.POST,
            filterType = ReportFilterType.BRANCH_DATE_RANGE,
            startParam = "startdate",
            endParam = "enddate",
            dateStyle = ReportDateStyle.DISPLAY,
            // The payload is an object keyed by date — the NORMAL shape found
            // no rows at all, so this report rendered empty until now.
            responseShape = ReportResponseShape.KEYED_OBJECTS,
            // The web's fixed order and Bengali headers (the API emits keys in
            // an unstable order, first-nonzero first).
            columnOrder = listOf(
                "selected_date", "sales", "downpayment", "kistyaday",
                "cashsales", "expenditure", "comments",
            ),
            columnLabels = mapOf(
                "selected_date" to "তারিখ",
                "sales" to "বিতরণ",
                "downpayment" to "ডাউন পেমেন্ট",
                "kistyaday" to "কিস্তি আদায়",
                "cashsales" to "নগদ বিক্রয়",
                "expenditure" to "খরচ",
                "comments" to "মন্তব্য",
            ),
        ),
        ReportConfig(
            key = "closingStock",
            title = "Closing Stock",
            routeName = "ReportClosingStock",
            webPath = "/reports/closing-stock",
            // The web has no Closing Stock sidebar item; gate it like the detailed
            // stock report so it no longer leaks to plain product.stock.view roles.
            anyOf = listOf("product.stock.details"),
            endpointKey = "closingStock",
            method = ReportMethod.POST,
            filterType = ReportFilterType.BRANCH_DATE_RANGE,
            // The web sends both the new and legacy date keys.
            startParam = "start_date",
            endParam = "end_date",
            altStartParam = "startdate",
            altEndParam = "enddate",
            // The payload is a brand-keyed map of row arrays — the NORMAL shape
            // found no rows, so this report rendered empty until now.
            responseShape = ReportResponseShape.NESTED_GROUPS,
            hiddenColumns = CLOSING_STOCK_HIDDEN,
            columnLabels = CLOSING_STOCK_LABELS,
            totalColumns = listOf("total_stock"),
            totalRowLabel = "Grand Total",
        ),
        ReportConfig(
            key = "stockDetails",
            title = "Stock Details",
            routeName = "ReportStockDetails",
            webPath = "/somity-report/stock-details",
            // The web sidebar gates Stock Details on `product.stock.details`.
            anyOf = listOf("product.stock.details"),
            endpointKey = "closingStock",
            method = ReportMethod.POST,
            filterType = ReportFilterType.BRANCH_DATE_RANGE,
            startParam = "start_date",
            endParam = "end_date",
            altStartParam = "startdate",
            altEndParam = "enddate",
            // Same page as Closing Stock on the web — same shape fix.
            responseShape = ReportResponseShape.NESTED_GROUPS,
            hiddenColumns = CLOSING_STOCK_HIDDEN,
            columnLabels = CLOSING_STOCK_LABELS,
            totalColumns = listOf("total_stock"),
            totalRowLabel = "Grand Total",
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
            // each amount instead). The web shows category only as a group
            // header and folds the brand into the product cell, so neither is
            // its own column.
            hiddenColumns = listOf("product_id", "category_id", "unit", "cat_name", "brand_name"),
            // Show "-" for 0, and suffix the unit ("1 nos") for the stock amounts.
            zeroDashColumns = listOf("opening", "stock_in", "stock_out", "balance"),
            unitColumn = "unit",
            // The web's Grand Total row over the four stock amounts.
            totalColumns = listOf("opening", "stock_in", "stock_out", "balance"),
            totalRowLabel = "Grand Total",
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
            // The web sidebar gates IMEI Stock on its own `imei.stock`.
            anyOf = listOf("imei.stock"),
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
            key = "godownStock",
            title = "Godown Stock",
            routeName = "ReportGodownStock",
            webPath = "/reports/godown-stock",
            // Its own permission, not product.stock.view — that one is held by
            // almost everybody, so the report would open for people the menu
            // had already decided not to offer it to (web 55ffca9).
            anyOf = listOf("godown.stock"),
            endpointKey = "godownStock",
            method = ReportMethod.GET,
            // Stock held per warehouse as of a date — an end date only, sent
            // dd/MM/yyyy under `enddate` like the web.
            filterType = ReportFilterType.BRANCH_END_DATE,
            startParam = null,
            endParam = "enddate",
            dateStyle = ReportDateStyle.DISPLAY,
            selectors = listOf(
                ReportSelector(
                    // No warehouse chosen is a choice of its own — every
                    // warehouse — which the server reads as godown_id 0.
                    paramKey = "godown_id",
                    label = "Select Warehouse (optional)",
                    source = ReportSelectorSource.WAREHOUSE,
                    required = false,
                ),
            ),
            // One branch is already the filter; the unit rides each stock
            // figure ("5 nos") because a warehouse holds bags beside pieces
            // and the two must never be added together — hence no Total row.
            hiddenColumns = listOf("branch_name", "unit"),
            unitColumn = "unit",
            zeroDashColumns = listOf("stock_qty"),
            columnOrder = listOf("warehouse", "product_name", "stock_qty"),
            columnLabels = mapOf(
                "product_name" to "Product",
                "stock_qty" to "Stock (Qty)",
            ),
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
            // The web shows Sl | Product | Brand/Manufacturer | Quantity, with
            // the unit riding the quantity figure.
            hiddenColumns = listOf("id", "unit"),
            unitColumn = "unit",
            zeroDashColumns = listOf("quantity"),
            columnOrder = listOf("cat_name", "product_name", "manufacturer_name", "quantity"),
            columnLabels = mapOf("manufacturer_name" to "Brand Name / Manufacturer"),
            totalColumns = listOf("quantity"),
            totalRowLabel = "Grand Total",
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
            key = "branchTransferReport",
            title = "Branch Transfer Report",
            routeName = "ReportBranchTransfer",
            webPath = "/reports/branch-transfer",
            section = ReportConfig.SECTION_BRANCH_TRANSFER,
            // The web sidebar gates this item on branch.transfer.create.
            anyOf = listOf("branch.transfer.create"),
            // The web opens on the month so far.
            startDateDefault = ReportConfig.StartDateDefault.MONTH_FIRST,
            totalColumns = listOf("opening", "issued", "balance"),
            endpointKey = "branchTransferReport",
            method = ReportMethod.POST,
            filterType = ReportFilterType.BRANCH_DATE_RANGE,
            startParam = "startdate",
            endParam = "enddate",
            hiddenColumns = listOf("product_id"),
            columnLabels = mapOf(
                "sl_number" to "Sl",
                "product_name" to "Product",
                // The web's Available column.
                "balance" to "Available",
            ),
        ),
        ReportConfig(
            key = "branchReceiveReport",
            title = "Branch Receive Report",
            routeName = "ReportBranchReceive",
            webPath = "/reports/branch-receive",
            section = ReportConfig.SECTION_BRANCH_TRANSFER,
            anyOf = listOf("branch.received.create"),
            startDateDefault = ReportConfig.StartDateDefault.MONTH_FIRST,
            totalColumns = listOf("opening", "received", "damaged", "shortage", "balance"),
            endpointKey = "branchReceiveReport",
            method = ReportMethod.POST,
            filterType = ReportFilterType.BRANCH_DATE_RANGE,
            startParam = "startdate",
            endParam = "enddate",
            hiddenColumns = listOf("product_id"),
            columnLabels = mapOf(
                "sl_number" to "Sl",
                "product_name" to "Product",
                "shortage" to "Short",
                "balance" to "Available",
            ),
        ),
        ReportConfig(
            key = "branchStockReport",
            title = "Branch Stock",
            routeName = "ReportBranchStock",
            webPath = "/reports/branch-stock",
            section = ReportConfig.SECTION_BRANCH_TRANSFER,
            anyOf = listOf("product.stock.view"),
            startDateDefault = ReportConfig.StartDateDefault.MONTH_FIRST,
            totalColumns = listOf("opening", "total", "damaged", "shortage"),
            endpointKey = "branchStockReport",
            method = ReportMethod.POST,
            filterType = ReportFilterType.BRANCH_BRAND_CATEGORY_PRODUCT_DATE_RANGE,
            startParam = "startdate",
            endParam = "enddate",
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
            ),
            // Rows sit under data.data.rows; the sibling `branches` legend and
            // the per-row `branches` qty map are the web's dynamic per-branch
            // columns — with the filter fixed to one branch, `total` already is
            // that branch's stock, so the map is hidden here.
            hiddenColumns = listOf("product_id", "cat_name", "brand_name", "branches"),
            columnLabels = mapOf(
                "sl_number" to "Sl",
                "product_name" to "Product",
                "shortage" to "Short",
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
            // The web's seven columns: the raw wire date, the ids and the two
            // server-rendered styling helpers stay internal.
            hiddenColumns = listOf(
                "vr_date", "branch", "product_id", "product_name",
                "stock_tone", "stock_html",
            ),
            columnLabels = mapOf(
                "date_display" to "Date",
                "stock" to "Balance",
            ),
            // The web also totals the running Balance (stock) column.
            totalColumns = listOf("in_qty", "out_qty", "damage", "over", "stock"),
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
            columnOrder = HRM_ATTENDANCE_WEB_ORDER,
            textColumns = listOf("employee_serial"),
        ),
        // Overtime Report is NOT here: it needs the web's employee × day
        // matrix — see ui/hrm/OvertimeMatrixScreen, reached via the HRM form route.
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
        // Branch Attendance and Holiday Calendar are NOT here: they need the
        // web's branch aggregation table and calendar grid — see
        // ui/hrm/BranchAttendanceScreen and ui/hrm/HolidayCalendarScreen,
        // reached via the HRM form route.
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
            // installment is not a web column either.
            hiddenColumns = listOf("emp_id", "received_amt", "payment_amt", "installment"),
            columnLabels = mapOf(
                "employee_name" to "Employee Name",
                "total_senction" to "Total Sanction",
                "total_payment" to "Total Payment",
            ),
            // The web's "Total Balance" box under the table.
            totalColumns = listOf("balance"),
            totalRowLabel = "Total Balance",
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
                    // Optional: empty runs the whole branch (every employee), a
                    // choice narrows it to one — the backend keys off ledgerId.
                    required = false,
                ),
            ),
            section = ReportConfig.SECTION_HRM,
            hiddenColumns = listOf(
                // branch_name is hidden too: the report is always scoped to one
                // selected branch, so the column is redundant — like the web, which
                // shows the branch under Remarks rather than as its own column.
                // balance leaks off the Opening/Balance rows; web has no such column.
                "id", "loan_detail_id", "branch_id", "branch_name", "branch_pad", "voucher_image",
                "balance",
            ),
            // The web's order: the voucher pair first, then Remarks.
            columnOrder = listOf("vr_no", "vr_date", "remarks", "received_amt", "payment_amt"),
            columnLabels = mapOf(
                "employee_name" to "Employee",
                "received_amt" to "Received",
                "payment_amt" to "Payment",
            ),
            // vr_no over vr_date in a single "Vr No & Date" column, like the web.
            stackedColumns = listOf(
                ReportStackedColumn(
                    header = "Vr No & Date",
                    topKey = "vr_no",
                    bottomKey = "vr_date",
                    bottomIsDate = true,
                ),
            ),
            // Loan rows arrive with the branch already padded (`branch_pad`); the
            // thumbnail column trails the data, like the web ledger.
            voucherImages = ReportVoucherImages(branchPadKey = "branch_pad"),
        ),
        ReportConfig(
            key = "hrmSalaryMismatch",
            title = "Salary Mismatch",
            routeName = "HrmSalaryMismatch",
            webPath = "/reports/hrm-mismatch-payment",
            anyOf = listOf("salary.sheet.view"),
            endpointKey = "hrmMismatchPayment",
            method = ReportMethod.GET,
            filterType = ReportFilterType.BRANCH_DATE_RANGE,
            // The date filter is optional server-side (applies only when both
            // are sent); the defaults from the branch transaction date are fine.
            startParam = "start_date",
            endParam = "end_date",
            section = ReportConfig.SECTION_HRM,
            dateColumns = listOf("vr_date"),
            // serial_number is appended LAST server-side, so it landed as a
            // trailing Sl column — the engine's own # column already numbers.
            hiddenColumns = listOf("main_trx_id", "emp_id", "serial_number"),
            columnLabels = mapOf(
                "vr_no" to "Vr No.",
                "vr_date" to "Vr Date",
                "employee_name" to "Employee",
                "remarks" to "Month",
                "dup_count" to "Times",
                "total_deducted" to "Total Deducted (Tk)",
            ),
            // vr_no and the comma-joined amounts are labels, not amounts.
            textColumns = listOf("vr_no", "amounts", "remarks"),
            // The web's per-row "Clear" (delete duplicate deduction) is a
            // destructive admin action and stays web-only for now.
        ),
        ReportConfig(
            key = "hrmBonusReports",
            title = "Bonus Reports",
            routeName = "HrmBonusReports",
            webPath = "/hrms/festival-bonus",
            anyOf = listOf("salary.generate", "salary.sheet.view"),
            endpointKey = "hrmBonusSheet",
            method = ReportMethod.POST,
            filterType = ReportFilterType.BRANCH_YEAR,
            startParam = null,
            endParam = null,
            yearParam = "year_id",
            section = ReportConfig.SECTION_HRM,
            // main_trx is a nested relation object; payment_year duplicates the
            // year filter; serial_no is appended last server-side (the engine's
            // own # column numbers). The Update/Payment actions stay web-only.
            hiddenColumns = listOf("main_trx_id", "main_trx", "payment_year", "serial_no"),
            monthColumns = listOf("payment_month"),
            // The web's order: title before month.
            columnOrder = listOf(
                "bonus_title", "payment_month", "total_employee",
                "bonus_amount", "payment_amount",
            ),
            columnLabels = mapOf(
                "bonus_title" to "Bonus Title",
                "payment_month" to "Month",
                "total_employee" to "Employees",
                "bonus_amount" to "Bonus Amount",
                "payment_amount" to "Paid",
            ),
        ),

        // ---- Requisition section (listed by RequisitionMenu) ----
        ReportConfig(
            key = "requisitionComparison",
            title = "Comparison",
            routeName = "RequisitionComparison",
            webPath = "/requisition/comparison",
            anyOf = listOf("requisition.comparison"),
            endpointKey = "requisitionComparison",
            method = ReportMethod.GET,
            filterType = ReportFilterType.BRANCH_DATE_RANGE,
            // The only requisition endpoint with real Laravel validation:
            // branch_id + start_date + end_date, yyyy-MM-dd.
            startParam = "start_date",
            endParam = "end_date",
            section = ReportConfig.SECTION_REQUISITION,
            // data.data is `{ "<productId>": {row} }` — a map, not an array.
            responseShape = ReportResponseShape.KEYED_OBJECTS,
            hiddenColumns = listOf(
                "product_id", "requisition_item_total", "requisition_direct_qty",
                "requisition_direct_total", "direct_expense_qty",
                "direct_expense_total", "total_expenditure",
            ),
            columnLabels = mapOf(
                "serial_no" to "Sl",
                "product_name" to "Product",
                "requisition_qty" to "Req. Qty",
                "purchase_qty" to "Pur. Qty",
                "requisition_total" to "Requisition",
                "approved_amt" to "Approved",
                "purchase_total" to "Total Expense",
                "difference" to "Balance",
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
