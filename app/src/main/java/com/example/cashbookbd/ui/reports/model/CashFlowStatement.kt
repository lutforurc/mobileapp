package com.example.cashbookbd.ui.reports.model

/** One grouped line of the statement (a working-capital or investing head). */
data class CashFlowLine(
    val key: String,
    val group: String,
    /** "asset" / "liability" / "operating" / "other" — decides the wording. */
    val type: String,
    /** The CASH effect: positive means cash came in. */
    val amount: Double,
)

/** The add-backs inside operating activities. */
data class CashFlowNonCash(
    val depreciation: Double = 0.0,
    val otherExpense: Double = 0.0,
    val income: Double = 0.0,
    val other: Double = 0.0,
    val total: Double = 0.0,
)

data class CashFlowOperating(
    val netProfit: Double = 0.0,
    val nonCash: CashFlowNonCash = CashFlowNonCash(),
    val workingCapital: List<CashFlowLine> = emptyList(),
    val workingCapitalTotal: Double = 0.0,
    val total: Double = 0.0,
)

data class CashFlowSection(
    val lines: List<CashFlowLine> = emptyList(),
    val total: Double = 0.0,
)

data class CashFlowReconciliation(
    val openingCash: Double = 0.0,
    val netIncrease: Double = 0.0,
    val closingCash: Double = 0.0,
    val ledgerClosingCash: Double = 0.0,
    val difference: Double = 0.0,
    val reconciles: Boolean = true,
)

/**
 * The indirect-method statement `GET reports/cash-flow-statement` answers.
 * Every figure is the server's own; the screen only lays the sections out.
 */
data class CashFlowStatementReport(
    val branchName: String,
    val startDate: String,
    val endDate: String,
    /** True when the Asset module nominated a depreciation expense head. */
    val depreciationSupported: Boolean,
    val operating: CashFlowOperating,
    val investing: CashFlowSection,
    val financing: CashFlowSection,
    val unclassified: CashFlowSection,
    val reconciliation: CashFlowReconciliation,
) {
    val isEmpty: Boolean
        get() = operating.netProfit == 0.0 &&
            operating.nonCash.total == 0.0 &&
            operating.workingCapital.isEmpty() &&
            investing.lines.isEmpty() &&
            financing.lines.isEmpty() &&
            unclassified.lines.isEmpty() &&
            reconciliation.openingCash == 0.0 &&
            reconciliation.closingCash == 0.0
}
