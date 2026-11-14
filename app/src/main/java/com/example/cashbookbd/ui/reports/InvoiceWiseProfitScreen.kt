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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.navigation.VoucherEditRow
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.FilterActions
import com.example.cashbookbd.ui.components.KpiTile
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.components.VoucherEditButton
import com.example.cashbookbd.ui.components.VoucherRowEditor
import com.example.cashbookbd.ui.components.rememberVoucherRowEditor
import com.example.cashbookbd.ui.reports.model.InvoiceWiseProfitRow
import com.example.cashbookbd.ui.reports.model.invoiceWiseProfitAmountText
import com.example.cashbookbd.ui.reports.model.invoiceWiseProfitReceivedText
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors

/**
 * Invoice Wise Profit (react `f0028a3b` / `c59b2cfb`).
 *
 * One row per posted invoice: the amount, what was taken at the counter, the
 * discount and the profit (net of discount less the FIFO cost of the lots the
 * sale consumed). The foot is the server's summary over every row the filters
 * keep, not just the page on screen. Printing stays on the web by policy.
 */
@Composable
fun InvoiceWiseProfitScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InvoiceWiseProfitViewModel = viewModel(
        factory = InvoiceWiseProfitViewModel.provideFactory(LocalContext.current)
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)
    val editor = rememberVoucherRowEditor(navController)

    AuthenticatedShell(
        title = "Invoice Wise Profit",
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
                BranchField(state.branch, viewModel::onBranch, label = "Select Branch")
                androidx.compose.runtime.key(state.resetKey) {
                    AppTextField(
                        value = state.search,
                        onValueChange = viewModel::onSearch,
                        label = "Invoice No.",
                        caption = "Search Invoice",
                        imeAction = ImeAction.Search,
                        keyboardActions = KeyboardActions(onSearch = { viewModel.apply() }),
                    )
                }
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
                // A failed first read has no rows to keep; a failed page turn
                // keeps the page already read and shows the error above it.
                state.error != null && state.report == null ->
                    ReportError(state.error!!, viewModel::apply)
                state.report == null && state.isLoading -> ReportLoading()
                state.report == null -> ReportNote(
                    "Select a branch and a date range, then tap Apply."
                )
                state.isEmptyResult -> ReportNote(
                    "No invoices found for ${state.appliedBranchName ?: "this branch"} in " +
                        "${state.appliedRange ?: "this period"}" +
                        if (state.appliedSearch.isBlank()) "." else " matching \"${state.appliedSearch}\"."
                )
                else -> InvoiceWiseProfitContent(state, viewModel, editor)
            }
        }
    }
}

@Composable
private fun InvoiceWiseProfitContent(
    state: InvoiceWiseProfitUiState,
    viewModel: InvoiceWiseProfitViewModel,
    editor: VoucherRowEditor,
) {
    val report = state.report ?: return
    val summary = report.summary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.error != null) {
            Text(
                text = state.error!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiTile(
                "Invoice Amount",
                invoiceWiseProfitAmountText(summary.invoiceAmount),
                Modifier.weight(1f),
            )
            KpiTile(
                "Received",
                invoiceWiseProfitReceivedText(summary.receivedAmount),
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiTile(
                "Discount",
                invoiceWiseProfitAmountText(summary.discount),
                Modifier.weight(1f),
            )
            KpiTile(
                "Profit",
                invoiceWiseProfitAmountText(summary.profit),
                Modifier.weight(1f),
                accent = if (summary.profit < 0) MaterialTheme.appColors.danger else null,
            )
        }

        Text(
            text = buildString {
                append(state.appliedBranchName ?: "This branch")
                append(" · ").append(state.appliedRange.orEmpty())
                if (state.appliedSearch.isNotBlank()) append(" · ").append(state.appliedSearch)
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = AppFontWeight.SemiBold,
        )

        val editable = report.rows.any { row -> row.editRow()?.let(editor.canEdit) == true }
        val danger = MaterialTheme.appColors.danger
        ReportTable(
            columns = columns(editable, editor),
            data = report.rows,
            noDataMessage = "No invoices found for these filters",
            scrollable = false,
            footerRows = if (report.pagination.total > 0) {
                listOf(
                    footerRow(
                        invoiceAmount = summary.invoiceAmount,
                        receivedAmount = summary.receivedAmount,
                        discount = summary.discount,
                        profit = summary.profit,
                        editable = editable,
                        danger = danger,
                    )
                )
            } else {
                emptyList()
            },
        )

        if (report.pagination.total > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${report.pagination.total} invoice" +
                        (if (report.pagination.total == 1) "" else "s") +
                        " · page ${report.pagination.page} of ${report.pagination.lastPage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton(
                        text = "Previous",
                        onClick = viewModel::previousPage,
                        enabled = state.canGoBack,
                        compact = true,
                    )
                    SecondaryButton(
                        text = "Next",
                        onClick = viewModel::nextPage,
                        enabled = state.canGoForward,
                        compact = true,
                    )
                }
            }
        }

        Text(
            text = "Basis: posted invoices only. Profit is the invoice amount less its discount, " +
                "less the FIFO cost of the stock the sale consumed; it does not depend on how " +
                "much the customer has paid. Totals cover the full filtered set, not only this page.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun columns(
    editable: Boolean,
    editor: VoucherRowEditor,
): List<ReportColumn<InvoiceWiseProfitRow>> {
    val danger = MaterialTheme.appColors.danger
    val columns = mutableListOf(
        ReportColumn<InvoiceWiseProfitRow>(
            "#", ReportColWidth.Fixed(44.dp), TextAlign.Center,
        ) { row, _ -> cellText(row.sl.toString()) },
        ReportColumn<InvoiceWiseProfitRow>(
            "INV. NO.", ReportColWidth.Fixed(116.dp),
        ) { row, _ -> cellText(row.voucherNo.ifBlank { "-" }) },
        ReportColumn<InvoiceWiseProfitRow>(
            "DATE", ReportColWidth.Fixed(88.dp),
        ) { row, _ -> cellText(row.voucherDate.ifBlank { "-" }) },
        ReportColumn<InvoiceWiseProfitRow>(
            "CUSTOMER", ReportColWidth.Fixed(180.dp),
        ) { row, _ -> cellText(row.customerName.ifBlank { "-" }, maxLines = 2) },
        ReportColumn<InvoiceWiseProfitRow>(
            "INV. AMOUNT", ReportColWidth.Fixed(112.dp), TextAlign.End,
        ) { row, _ -> cellText(invoiceWiseProfitAmountText(row.invoiceAmount)) },
        ReportColumn<InvoiceWiseProfitRow>(
            "RECEIVED", ReportColWidth.Fixed(108.dp), TextAlign.End,
        ) { row, _ -> cellText(invoiceWiseProfitReceivedText(row.receivedAmount)) },
        ReportColumn<InvoiceWiseProfitRow>(
            "DISCOUNT", ReportColWidth.Fixed(100.dp), TextAlign.End,
        ) { row, _ -> cellText(invoiceWiseProfitAmountText(row.discount)) },
        ReportColumn<InvoiceWiseProfitRow>(
            "PROFIT", ReportColWidth.Fixed(100.dp), TextAlign.End,
        ) { row, _ ->
            cellText(
                invoiceWiseProfitAmountText(row.profit),
                color = if (row.profit < 0) danger else Color.Unspecified,
            )
        },
    )
    if (editable) {
        columns += ReportColumn<InvoiceWiseProfitRow>(
            "EDIT", ReportColWidth.Fixed(56.dp), TextAlign.Center,
        ) { row, _ ->
            val target = row.editRow()
            if (target != null && editor.canEdit(target)) {
                ReportTableCell.Slot { VoucherEditButton(target, editor) }
            } else {
                ReportTableCell.Empty
            }
        }
    }
    return columns
}

private fun footerRow(
    invoiceAmount: Double,
    receivedAmount: Double,
    discount: Double,
    profit: Double,
    editable: Boolean,
    danger: Color,
): List<ReportFooterCell> {
    val cells = mutableListOf(
        ReportFooterCell(cellText("Total", TextAlign.End, bold = true), colSpan = 4),
        ReportFooterCell(cellText(invoiceWiseProfitAmountText(invoiceAmount), TextAlign.End, bold = true)),
        ReportFooterCell(cellText(invoiceWiseProfitReceivedText(receivedAmount), TextAlign.End, bold = true)),
        ReportFooterCell(cellText(invoiceWiseProfitAmountText(discount), TextAlign.End, bold = true)),
        ReportFooterCell(
            cellText(
                invoiceWiseProfitAmountText(profit),
                TextAlign.End,
                bold = true,
                color = if (profit < 0) danger else Color.Unspecified,
            ),
        ),
    )
    if (editable) cells += ReportFooterCell(ReportTableCell.Empty)
    return cells
}

/** The report-row facts the shared voucher editor reads, or null for no row. */
private fun InvoiceWiseProfitRow.editRow(): VoucherEditRow? {
    if (voucherNo.isBlank() || voucherNo == "-") return null
    return VoucherEditRow(vrNo = voucherNo)
}
