package com.example.cashbookbd.ui.reports

/*
 * Notes to the Trial Balance (Group) — the web's trialBalanceNotes.ts. The
 * numbering is fixed: [1] the heading, [2] the Movement column, [3] the
 * Description column, [4] and [5] the Grand Total, and [6] stock that moved
 * without a purchase or a sale — only for a period that moved some, and last,
 * so leaving it out moves no other number.
 */
fun buildTrialBalanceNotes(
    branchName: String?,
    startDate: String?,
    endDate: String?,
    productOut: Double,
    productIn: Double,
): List<ReportNoteItem> {
    val stockNote = stockMovementNote(
        productOut,
        productIn,
        "Neither figure is a balance on this Trial Balance: a write-off and a receipt each " +
            "move stock only and make no ledger posting, so neither is in any line above. Their " +
            "effect is in the inventory valuation and the cost of goods sold (note 4), and " +
            "neither is deducted or added again as a separate adjustment.",
    )
    val periodText = if (!startDate.isNullOrBlank() && !endDate.isNullOrBlank()) "$startDate to $endDate" else "the selected period"
    val scopeText = "This report covers $periodText for ${branchName?.ifBlank { null } ?: "the selected branch"} " +
        "and uses eligible posted vouchers within the selected reporting scope."

    return buildList {
        add(
            ReportNoteItem(
                1, "Basis of preparation",
                "$scopeText Year-end closing entries are excluded. Any posted adjusting " +
                    "entries are included unless explicitly excluded by the report settings.",
            ),
        )
        add(
            ReportNoteItem(
                2, "Reading the columns",
                "Opening balances represent ledger balances immediately before the start " +
                    "date. Movement Dr and Cr show the period's total debit and credit postings " +
                    "separately. Closing balances represent ledger balances as at the end date. " +
                    "Accounts with activity may appear even when their closing balance is zero.",
            ),
        )
        add(
            ReportNoteItem(
                3, "Group totals",
                "Each numbered row represents an account group. Opening and Closing debit " +
                    "and credit balances are calculated for each ledger and then summed " +
                    "separately within the group. Debit balances of one ledger are not offset " +
                    "against credit balances of another. Ledger details are available in the " +
                    "detailed report.",
            ),
        )
        add(
            ReportNoteItem(
                4, "Inventory and reconciliation",
                "Closing inventory is valued from stock records outside the general ledger " +
                    "and is therefore excluded from this Trial Balance. Reconciliation with the " +
                    "Balance Sheet requires the inventory valuation and the related profit " +
                    "calculation to be considered together, without double counting. Income and " +
                    "expense balances are presented through current-period profit in the Balance " +
                    "Sheet.",
            ),
        )
        add(
            ReportNoteItem(
                5, "Differences",
                "Opening, Movement and Closing debit-credit totals are expected to agree. " +
                    "Any difference is displayed without an artificial balancing adjustment. A " +
                    "difference should be investigated in the underlying postings, opening " +
                    "balances, report filters and calculation logic.",
            ),
        )
        if (stockNote != null) add(ReportNoteItem(6, stockNote.first, stockNote.second))
    }
}
