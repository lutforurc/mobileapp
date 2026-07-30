package com.example.cashbookbd.ui.orders

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.AveragePriceReport
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.FilterActions
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SearchableSelectDropdown
import com.example.cashbookbd.ui.components.SummaryTile
import com.example.cashbookbd.ui.reports.model.SelectorOption

/**
 * Average Price (web `/orders/avg-price`, perm `order.avg.price`): branch,
 * order and report-type filters, then the web page's three stat cards —
 * Purchase Cost, Increase / Decrease, and Others Cost.
 */
@Composable
fun AveragePriceScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AveragePriceViewModel = viewModel(
        factory = AveragePriceViewModel.provideFactory(LocalContext.current)
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.sessionExpired) {
        if (uiState.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    AuthenticatedShell(
        title = "Average Price",
        currentRoute = Routes.ADMIN,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            FilterCard(
                state = uiState,
                onBranchSelected = viewModel::onBranchSelected,
                searchOrders = viewModel::searchOrders,
                onOrderSelected = viewModel::onOrderSelected,
                onReportTypeSelected = viewModel::onReportTypeSelected,
                onRun = viewModel::runReport,
                onReset = viewModel::reset,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(modifier = Modifier.weight(1f)) {
                Results(state = uiState, onRetry = viewModel::runReport)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Filter
// ---------------------------------------------------------------------------

@Composable
private fun FilterCard(
    state: AveragePriceUiState,
    onBranchSelected: (SelectorOption) -> Unit,
    searchOrders: suspend (String) -> Resource<List<SelectorOption>>,
    onOrderSelected: (SelectorOption) -> Unit,
    onReportTypeSelected: (SelectorOption) -> Unit,
    onRun: () -> Unit,
    onReset: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        AppSelectDropdown(
            label = "Select Branch",
            options = state.branches,
            selected = state.selectedBranch,
            onSelected = onBranchSelected,
            enabled = !state.isBranchesLoading,
            placeholder = if (state.isBranchesLoading) "Loading branches…" else "Select Branch",
        )
        state.branchesError?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(12.dp))
        SearchableSelectDropdown(
            selected = state.selectedOrder,
            onSelected = onOrderSelected,
            search = searchOrders,
            label = "Select Order",
            placeholder = "Type an order number…",
            emptyText = "No order found",
            minSearchChars = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))
        AppSelectDropdown(
            label = "Report Type",
            options = AveragePriceUiState.REPORT_TYPES,
            selected = state.selectedReportType,
            onSelected = onReportTypeSelected,
        )

        Spacer(Modifier.height(14.dp))
        FilterActions(
            onApply = onRun,
            onReset = onReset,
            canApply = state.canRun,
            isLoading = state.isReportLoading,
            applyText = "Run",
        )
    }
}

// ---------------------------------------------------------------------------
// Results
// ---------------------------------------------------------------------------

@Composable
private fun Results(state: AveragePriceUiState, onRetry: () -> Unit) {
    when {
        state.isReportLoading -> CenterBox {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
        }

        state.reportError != null -> CenterBox {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.reportError,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                PrimaryButton(text = "Retry", onClick = onRetry)
            }
        }

        state.report == null -> CenterBox {
            Text(
                text = "Choose a branch, an order and a report type, then tap Run.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
        }

        state.isEmptyResult -> CenterBox {
            Text(
                text = "No purchase details found for this order.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
        }

        else -> ReportCards(report = state.report)
    }
}

/** The three stat cards, computed like the web page. */
@Composable
private fun ReportCards(report: AveragePriceReport) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // 1 — Purchase Cost: qty, cost, and cost ÷ qty (guarded).
        StatCard(title = "${report.productName} Purchase Cost".trim()) {
            StatLine("Total Quantity", quantityText(report.totalQty, report.unitName))
            StatLine("Total Cost", AmountFormat.formatOrDash(report.totalCost))
            StatLine("Average Per Unit", AmountFormat.formatOrDash(report.averagePerUnit))
        }

        Spacer(Modifier.height(12.dp))

        // 2 — Increase / Decrease: the weight-variance quantities.
        StatCard(title = "${report.productName} Increase / Decrease".trim()) {
            StatLine("Total increase quantity", quantityText(report.increaseQty, report.unitName))
            StatLine("Total decrease quantity", quantityText(report.decreaseQty, report.unitName))
        }

        Spacer(Modifier.height(12.dp))

        // 3 — Others Cost: each non-cash ledger's debit, then the grand total
        // (purchase cost + others) and grand total ÷ qty (guarded).
        StatCard(title = "${report.productName} Others Cost".trim()) {
            report.otherExpenses.forEach { expense ->
                StatLine(expense.name, AmountFormat.formatOrDash(expense.debit))
            }
            StatLine("Grand Total Cost", AmountFormat.formatOrDash(report.grandTotalExpense))
            StatLine("Net Per Unit", AmountFormat.formatOrDash(report.netPerUnit))
        }

        Spacer(Modifier.height(16.dp))
    }
}

/** A titled tile of label/value rows — one of the web page's three sections. */
@Composable
private fun StatCard(title: String, content: @Composable () -> Unit) {
    SummaryTile(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        )
        content()
    }
}

/** One label/value row of a stat card, ruled beneath like the web's rows. */
@Composable
private fun StatLine(label: String, value: String) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** "1,200 Kg" — the quantity with its unit; zero shows the bare dash. */
private fun quantityText(qty: Double, unitName: String): String {
    if (qty == 0.0) return "-"
    return listOf(AmountFormat.format(qty), unitName)
        .filter { it.isNotBlank() }
        .joinToString(" ")
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}
