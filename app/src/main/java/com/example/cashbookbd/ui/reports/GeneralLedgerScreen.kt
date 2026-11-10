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
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.navigation.VoucherEditRow
import com.example.cashbookbd.ui.components.FilterActions
import com.example.cashbookbd.ui.components.KpiTile
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.SearchableSelectDropdown
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.components.VoucherEditButton
import com.example.cashbookbd.ui.components.VoucherRowEditor
import com.example.cashbookbd.ui.components.rememberVoucherRowEditor
import com.example.cashbookbd.ui.reports.model.GeneralLedgerAccount
import com.example.cashbookbd.ui.reports.model.GeneralLedgerTableRow
import com.example.cashbookbd.ui.reports.model.generalLedgerBalanceText
import com.example.cashbookbd.ui.reports.model.generalLedgerTableRows
import com.example.cashbookbd.ui.theme.AppFontWeight
import kotlin.math.abs

/**
 * General Ledger (react 08ca7cf4): one Accounts Head, or all of them, with its
 * opening balance, every posted entry, and a running balance the server computed
 * in voucher-date order. The screen mirrors the web's summary boxes, one card per
 * Accounts Head, and its page-by-Accounts-Head pagination.
 */
@Composable
fun GeneralLedgerScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GeneralLedgerViewModel = viewModel(
        factory = GeneralLedgerViewModel.provideFactory(LocalContext.current)
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)
    val editor = rememberVoucherRowEditor(navController)

    AuthenticatedShell(
        title = "General Ledger",
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
                androidx.compose.runtime.key(state.resetKey) {
                    SearchableSelectDropdown(
                        selected = state.account,
                        onSelected = viewModel::onAccount,
                        search = viewModel::searchAccounts,
                        label = "Accounts Head",
                        placeholder = "All Accounts",
                        emptyText = "No accounts head found",
                        minSearchChars = 1,
                    )
                }
                if (state.account != null) {
                    LinkButton(
                        text = "All Accounts",
                        onClick = { viewModel.onAccount(null) },
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
                state.error != null -> ReportError(state.error!!, viewModel::apply)
                state.isLoading -> ReportLoading()
                state.report == null -> ReportNote(
                    "Select a branch, an Accounts Head (or All Accounts) and a date range, then tap Apply."
                )
                state.isEmptyResult -> ReportNote(
                    "No transactions found for ${state.appliedAccountsHead} in " +
                        "${state.appliedBranchName ?: "this branch"} for ${state.appliedRange ?: "this period"}."
                )
                else -> GeneralLedgerContent(state, viewModel, editor)
            }
        }
    }
}

@Composable
private fun GeneralLedgerContent(
    state: GeneralLedgerUiState,
    viewModel: GeneralLedgerViewModel,
    editor: VoucherRowEditor,
) {
    val report = state.report ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiTile("Total Debit", AmountFormat.format(report.totals.debit), Modifier.weight(1f))
            KpiTile("Total Credit", AmountFormat.format(report.totals.credit), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiTile("Closing Debit", AmountFormat.format(report.totals.closingDebit), Modifier.weight(1f))
            KpiTile("Closing Credit", AmountFormat.format(report.totals.closingCredit), Modifier.weight(1f))
        }

        Text(
            text = "${state.appliedAccountsHead} · ${state.appliedBranchName ?: report.branchName}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = AppFontWeight.SemiBold,
        )
        Text(
            text = state.appliedRange.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        report.accounts.forEach { account -> AccountCard(account, editor) }

        if (report.pagination.totalAccounts > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${report.pagination.totalAccounts} Accounts Head" +
                        (if (report.pagination.totalAccounts == 1) "" else "s") +
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
            text = "Basis: posted vouchers only, with year-closing entries excluded — the same posted " +
                "ledger Trial Balance Details prints. Opening is the balance before the start date, and " +
                "the closing balance is the two together. Totals cover the full filtered set, not only " +
                "this page.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AccountCard(account: GeneralLedgerAccount, editor: VoucherRowEditor) {
    val rows = generalLedgerTableRows(account)
    val columns = mutableListOf(
        ReportColumn<GeneralLedgerTableRow>(
            "Date", ReportColWidth.Fixed(84.dp),
        ) { row, _ -> cellText(row.dateText) },
        ReportColumn<GeneralLedgerTableRow>(
            "Voucher", ReportColWidth.Fixed(104.dp),
        ) { row, _ -> cellText(row.voucherNo) },
        ReportColumn<GeneralLedgerTableRow>(
            "Narration", ReportColWidth.Fixed(170.dp),
        ) { row, _ -> cellText(row.narration, maxLines = 3) },
        ReportColumn<GeneralLedgerTableRow>(
            "Debit", ReportColWidth.Fixed(100.dp), TextAlign.End,
        ) { row, _ -> cellText(if (row.debit != 0.0) AmountFormat.format(row.debit) else "-") },
        ReportColumn<GeneralLedgerTableRow>(
            "Credit", ReportColWidth.Fixed(100.dp), TextAlign.End,
        ) { row, _ -> cellText(if (row.credit != 0.0) AmountFormat.format(row.credit) else "-") },
        ReportColumn<GeneralLedgerTableRow>(
            "Balance", ReportColWidth.Fixed(126.dp), TextAlign.End,
        ) { row, _ -> cellText(generalLedgerBalanceText(row.balance, row.balanceType)) },
    )

    // The web's Voucher column opens the voucher; the pencil appears only for
    // the kinds this user may edit, exactly like every other report's ACTION cell.
    val editable = rows.any { row -> row.editRow()?.let(editor.canEdit) == true }
    if (editable) {
        columns += ReportColumn<GeneralLedgerTableRow>(
            "Edit", ReportColWidth.Fixed(56.dp), TextAlign.Center,
        ) { row, _ ->
            val target = row.editRow()
            if (target != null && editor.canEdit(target)) {
                ReportTableCell.Slot { VoucherEditButton(target, editor) }
            } else {
                ReportTableCell.Empty
            }
        }
    }

    val openingBg = MaterialTheme.colorScheme.surfaceVariant

    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = AppFontWeight.Bold,
            )
            val trail = listOfNotNull(
                account.groupTrail.takeIf { it.isNotBlank() },
                account.codeLabel.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (trail.isNotBlank()) {
                Text(
                    text = trail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Closing: ${generalLedgerBalanceText(account.closingBalance)}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = AppFontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))

            ReportTable(
                columns = columns,
                data = rows,
                noDataMessage = "No transactions in this period",
                scrollable = false,
                rowBackground = { row, _ -> if (row.isOpening) openingBg else null },
                footerRows = listOf(
                    listOf(
                        ReportFooterCell(cellText("Total", TextAlign.End, bold = true), colSpan = 3),
                        ReportFooterCell(cellText(AmountFormat.format(abs(account.debit)), TextAlign.End)),
                        ReportFooterCell(cellText(AmountFormat.format(abs(account.credit)), TextAlign.End)),
                        ReportFooterCell(cellText(generalLedgerBalanceText(account.closingBalance), TextAlign.End)),
                    ),
                    listOf(
                        ReportFooterCell(cellText("Closing Balance", TextAlign.End, bold = true), colSpan = 5),
                        ReportFooterCell(cellText(generalLedgerBalanceText(account.closingBalance), TextAlign.End)),
                    ),
                ),
            )
        }
    }
}

/** The report-row facts the shared voucher editor reads, or null for a non-row. */
private fun GeneralLedgerTableRow.editRow(): VoucherEditRow? {
    if (isOpening || voucherNo.isBlank() || voucherNo == "-") return null
    return VoucherEditRow(
        vrNo = voucherNo,
        isBankVoucher = isBankVoucher,
        isApproved = isApproved,
    )
}
