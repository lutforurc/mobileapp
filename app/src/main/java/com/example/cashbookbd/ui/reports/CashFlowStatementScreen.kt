package com.example.cashbookbd.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.FilterActions
import com.example.cashbookbd.ui.components.KpiTile
import com.example.cashbookbd.ui.theme.AppFontWeight

/**
 * Cash Flow Statement (react 08ca7cf4): the indirect statement the server builds
 * from the posted ledger. The four summary boxes and every sentence of the basis
 * note are the web's; the row wording and ordering come from [cashFlowRows], one
 * ported function.
 */
@Composable
fun CashFlowStatementScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CashFlowStatementViewModel = viewModel(
        factory = CashFlowStatementViewModel.provideFactory(LocalContext.current)
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)

    AuthenticatedShell(
        title = "Cash Flow Statement",
        currentRoute = Routes.REPORTS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BranchField(state.branch, viewModel::onBranch, label = "Branch")
                DateRangeFields(state.startDate, state.endDate, viewModel::onStart, viewModel::onEnd)
                FilterActions(
                    onApply = viewModel::apply,
                    onReset = viewModel::reset,
                    canApply = state.canApply,
                    isLoading = state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            when {
                state.error != null -> ReportError(state.error!!, viewModel::apply)
                state.isLoading -> ReportLoading()
                !state.hasRun && state.report == null -> ReportNote(
                    "Select a branch and date range, then tap Apply."
                )
                state.report != null -> CashFlowContent(state)
                else -> ReportNote("No cash flow statement loaded yet.")
            }
        }
    }
}

@Composable
private fun CashFlowContent(state: CashFlowStatementUiState) {
    val report = state.report ?: return
    val rows = cashFlowRows(report)
    val netIncrease = report.reconciliation.netIncrease
    val netIncreaseTitle = when {
        netIncrease < 0 -> "Net Decrease"
        netIncrease > 0 -> "Net Increase"
        else -> "Net Increase / (Decrease)"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiTile("Operating", cashFlowAmountText(report.operating.total), Modifier.weight(1f))
            KpiTile("Investing", cashFlowAmountText(report.investing.total), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiTile("Financing", cashFlowAmountText(report.financing.total), Modifier.weight(1f))
            KpiTile(netIncreaseTitle, cashFlowAmountText(netIncrease), Modifier.weight(1f))
        }

        Card {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Text(
                    text = "Cash Flow Statement",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = AppFontWeight.Bold,
                )
                Text(
                    text = "${state.appliedBranchName ?: report.branchName}, " +
                        (state.appliedRange ?: "${report.startDate} to ${report.endDate}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Indirect method",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = AppFontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))

                CashFlowTable(rows)

                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Net profit is the Profit & Loss Account's own result for the same branch " +
                        "and period. Inventory is adjusted separately by opening stock less closing stock " +
                        "on that account's valuation, because the closing stock is a valuation and not a " +
                        "ledger balance. Cash and cash equivalents are the accounts in this branch's " +
                        "Cash, Bank and Mobile Banking groups; transfers between them are internal and " +
                        "are not counted as cash flow.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Basis: posted vouchers only, with year-closing entries excluded — the same " +
                        "posted ledger Trial Balance Details prints. Investing and financing show only " +
                        "movements from vouchers that touched a cash or cash-equivalent account; a " +
                        "non-cash entry is excluded from those sections and its P&L effect is added back " +
                        "above. A posted line whose chart parent cannot be resolved is shown under " +
                        "\"Other movements not classified\" rather than assigned to operating activities.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = if (report.depreciationSupported) {
                        "Non-cash adjustments: depreciation on the expense heads nominated by the Asset " +
                            "module, plus non-cash purchases, sales and other P&L items whose vouchers " +
                            "did not touch cash."
                    } else {
                        "Non-cash adjustments: non-cash purchases, sales and other P&L items whose " +
                            "vouchers did not touch cash; no Asset category has nominated a depreciation " +
                            "expense head."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (!report.reconciliation.reconciles) {
                    Text(
                        text = "The ledger does not balance for this period, so the statement cannot " +
                            "reconcile exactly. The difference against the ledger cash balances is shown " +
                            "above; it is the same difference Trial Balance Details carries for this " +
                            "branch and period.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CashFlowTable(rows: List<CashFlowRow>) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val sectionBg = MaterialTheme.colorScheme.surfaceVariant
    val columns = listOf(
        ReportColumn<CashFlowRow>("Particulars", ReportColWidth.Weight(1f)) { row, _ ->
            val bold = row.kind == CashFlowRowKind.SECTION ||
                row.kind == CashFlowRowKind.SUBTOTAL ||
                row.kind == CashFlowRowKind.TOTAL
            cellText(
                text = row.label,
                bold = bold,
                color = if (row.kind == CashFlowRowKind.NOTE) muted else androidx.compose.ui.graphics.Color.Unspecified,
                maxLines = 4,
                startPadding = if (row.kind == CashFlowRowKind.LINE) 16.dp else 0.dp,
            )
        },
        ReportColumn<CashFlowRow>("Amount (BDT)", ReportColWidth.Fixed(140.dp), TextAlign.End) { row, _ ->
            val bold = row.kind == CashFlowRowKind.SUBTOTAL || row.kind == CashFlowRowKind.TOTAL
            cellText(
                text = cashFlowAmountText(row.amount),
                bold = bold,
                color = if (row.kind == CashFlowRowKind.NOTE) muted else androidx.compose.ui.graphics.Color.Unspecified,
            )
        },
    )

    ReportTable(
        columns = columns,
        data = rows,
        scrollable = false,
        rowBackground = { row, _ -> if (row.kind == CashFlowRowKind.SECTION) sectionBg else null },
    )
}
