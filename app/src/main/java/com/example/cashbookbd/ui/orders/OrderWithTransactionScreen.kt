package com.example.cashbookbd.ui.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.example.cashbookbd.data.repository.OrderTransactionReport
import com.example.cashbookbd.data.repository.OrderTransactionRow
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.FilterActions
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SearchableSelectDropdown
import com.example.cashbookbd.ui.components.SummaryTile
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportFooterCell
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.reports.model.SelectorOption
import kotlin.math.abs

/**
 * Order With Transaction (web `/orders/with-transaction`, perm `order.view`):
 * branch + order filter, the order's summary tiles, and every voucher posted
 * against the order in a running-balance table — the web page's row math,
 * already computed by the repository.
 */
@Composable
fun OrderWithTransactionScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OrderWithTransactionViewModel = viewModel(
        factory = OrderWithTransactionViewModel.provideFactory(LocalContext.current)
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
        title = "Order With Transaction",
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
                onApply = viewModel::apply,
                onReset = viewModel::reset,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(modifier = Modifier.weight(1f)) {
                Results(state = uiState, onRetry = viewModel::apply)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Filter
// ---------------------------------------------------------------------------

@Composable
private fun FilterCard(
    state: OrderWithTransactionUiState,
    onBranchSelected: (SelectorOption) -> Unit,
    searchOrders: suspend (String) -> Resource<List<SelectorOption>>,
    onOrderSelected: (SelectorOption) -> Unit,
    onApply: () -> Unit,
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

        Spacer(Modifier.height(14.dp))
        FilterActions(
            onApply = onApply,
            onReset = onReset,
            canApply = state.canApply,
            isLoading = state.isReportLoading,
        )
    }
}

// ---------------------------------------------------------------------------
// Results
// ---------------------------------------------------------------------------

@Composable
private fun Results(state: OrderWithTransactionUiState, onRetry: () -> Unit) {
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
                text = "Choose a branch and an order, then tap Apply.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
        }

        else -> ReportContent(report = state.report)
    }
}

@Composable
private fun ReportContent(report: OrderTransactionReport) {
    Column(modifier = Modifier.fillMaxSize()) {
        OrderSummaryTiles(report = report)
        Spacer(Modifier.height(12.dp))
        Box(modifier = Modifier.weight(1f)) {
            TransactionTable(report = report)
        }
    }
}

/** Order No / Rate / Order Qty / Type — the web page's header block as tiles. */
@Composable
private fun OrderSummaryTiles(report: OrderTransactionReport) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SummaryBox("Order No", report.orderNumber.ifBlank { "-" }, Modifier.weight(1.2f))
        SummaryBox("Rate", AmountFormat.formatOrDash(report.orderRate), Modifier.weight(1f))
        SummaryBox(
            label = "Order Qty",
            value = if (report.totalOrder == 0.0) {
                "-"
            } else {
                listOf(AmountFormat.format(report.totalOrder), report.unitName)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
            },
            modifier = Modifier.weight(1f),
        )
        SummaryBox("Type", report.orderTypeLabel, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryBox(label: String, value: String, modifier: Modifier = Modifier) {
    SummaryTile(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------------------
// Table
// ---------------------------------------------------------------------------

// # | Chal No & Date | Product & Details | Qty | Rate | Total | Discount |
// Payment (Received) | Balance — fixed widths, horizontally scrolling.
private val COL_SL = 48.dp
private val COL_CHALLAN = 130.dp
private val COL_PRODUCT = 200.dp
private val COL_QTY = 96.dp
private val COL_RATE = 96.dp
private val COL_TOTAL = 110.dp
private val COL_DISCOUNT = 104.dp
private val COL_PAYMENT = 110.dp
private val COL_BALANCE = 120.dp

private fun orderColumns(paymentHeader: String) = listOf(
    ReportColumn<OrderTransactionRow>("#", ReportColWidth.Fixed(COL_SL), TextAlign.Center) { _, i ->
        cellText((i + 1).toString(), align = TextAlign.Center)
    },
    ReportColumn<OrderTransactionRow>("CHAL. NO. & DATE", ReportColWidth.Fixed(COL_CHALLAN)) { r, _ ->
        ReportTableCell.Slot { ChallanCell(r) }
    },
    ReportColumn<OrderTransactionRow>("PRODUCT & DETAILS", ReportColWidth.Fixed(COL_PRODUCT)) { r, _ ->
        ReportTableCell.Slot { ProductCell(r) }
    },
    ReportColumn<OrderTransactionRow>("QTY", ReportColWidth.Fixed(COL_QTY), TextAlign.End) { r, _ ->
        cellText(if (r.hasLineDetail) AmountFormat.formatOrDash(r.quantity) else "-", align = TextAlign.End)
    },
    ReportColumn<OrderTransactionRow>("RATE", ReportColWidth.Fixed(COL_RATE), TextAlign.End) { r, _ ->
        cellText(if (r.hasLineDetail) AmountFormat.formatOrDash(r.rate) else "-", align = TextAlign.End)
    },
    ReportColumn<OrderTransactionRow>("TOTAL", ReportColWidth.Fixed(COL_TOTAL), TextAlign.End) { r, _ ->
        cellText(if (r.hasLineDetail) AmountFormat.formatOrDash(r.total) else "-", align = TextAlign.End)
    },
    ReportColumn<OrderTransactionRow>("DISCOUNT", ReportColWidth.Fixed(COL_DISCOUNT), TextAlign.End) { r, _ ->
        cellText(AmountFormat.formatOrDash(r.discount), align = TextAlign.End)
    },
    ReportColumn<OrderTransactionRow>(paymentHeader, ReportColWidth.Fixed(COL_PAYMENT), TextAlign.End) { r, _ ->
        cellText(AmountFormat.formatOrDash(abs(r.payment)), align = TextAlign.End)
    },
    ReportColumn<OrderTransactionRow>("BALANCE", ReportColWidth.Fixed(COL_BALANCE), TextAlign.End) { r, _ ->
        cellText(formatBalance(r.balance), align = TextAlign.End)
    },
)

@Composable
private fun TransactionTable(report: OrderTransactionReport) {
    ReportTable(
        columns = orderColumns(report.paymentHeader),
        data = report.rows,
        footerRows = footerRows(report),
        noDataMessage = "No order transaction data found.",
    )
}

/** The web's single totals row: Σqty, Σtotal, Σdiscount, Σ|payment|, final balance. */
private fun footerRows(report: OrderTransactionReport): List<List<ReportFooterCell>> {
    if (report.rows.isEmpty()) return emptyList()
    return listOf(
        listOf(
            // "Total" sits right-aligned under # + Chal + Product.
            ReportFooterCell(cellText("Total", bold = true, align = TextAlign.End), colSpan = 3),
            ReportFooterCell(cellText(AmountFormat.formatOrDash(report.totalQuantity), align = TextAlign.End, bold = true)),
            ReportFooterCell(ReportTableCell.Empty), // Rate
            ReportFooterCell(cellText(AmountFormat.formatOrDash(report.totalAmount), align = TextAlign.End, bold = true)),
            ReportFooterCell(cellText(AmountFormat.formatOrDash(report.totalDiscount), align = TextAlign.End, bold = true)),
            ReportFooterCell(cellText(AmountFormat.formatOrDash(abs(report.totalPayment)), align = TextAlign.End, bold = true)),
            ReportFooterCell(cellText(formatBalance(report.finalBalance), align = TextAlign.End, bold = true)),
        ),
    )
}

/** Chal no over its date, the two-line cell of the web table. */
@Composable
private fun ChallanCell(row: OrderTransactionRow) {
    val onScreen = MaterialTheme.colorScheme.onBackground
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
        Text(
            text = row.challanNo,
            style = MaterialTheme.typography.bodySmall,
            color = onScreen,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = formatVrDate(row.challanDate),
            style = MaterialTheme.typography.labelSmall,
            color = onScreen.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Product (or ledger) name with the voucher's remarks muted beneath it. */
@Composable
private fun ProductCell(row: OrderTransactionRow) {
    val onScreen = MaterialTheme.colorScheme.onBackground
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
        Text(
            text = row.productLine,
            style = MaterialTheme.typography.bodySmall,
            color = onScreen,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (row.remarks.isNotBlank()) {
            Text(
                text = row.remarks,
                style = MaterialTheme.typography.labelSmall,
                color = onScreen.copy(alpha = 0.75f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Zero → "-", negative → "(-) n", like the web's balance column. */
private fun formatBalance(value: Double): String = when {
    value == 0.0 -> "-"
    value < 0 -> "(-) ${AmountFormat.format(abs(value))}"
    else -> AmountFormat.format(value)
}

/** The API sends dates as yyyy-MM-dd; the report shows dd/MM/yyyy. */
private fun formatVrDate(raw: String): String {
    val m = Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(raw) ?: return raw
    val (year, month, day) = m.destructured
    return "$day/$month/$year"
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
