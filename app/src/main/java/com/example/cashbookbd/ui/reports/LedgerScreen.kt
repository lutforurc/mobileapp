package com.example.cashbookbd.ui.reports

import com.example.cashbookbd.ui.theme.muted
import com.example.cashbookbd.ui.theme.appColors
import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.ui.components.FilterActions
import com.example.cashbookbd.ui.components.HighlightedText
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.components.highlightBorderColor
import com.example.cashbookbd.ui.components.rememberHighlightRules
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.report.HighlightRule
import com.example.cashbookbd.report.matchHighlightRule
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.components.SearchableLedgerDropdown
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.LedgerStatement
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.core.VoucherAttachment
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.components.VoucherAttachmentsCell
import com.example.cashbookbd.ui.components.VoucherImageViewerDialog
import com.example.cashbookbd.ui.components.openVoucherAttachment

@Composable
fun LedgerScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    /** A preselected account (the deep link behind an opening-balance voucher). */
    initialAccountId: String = "",
    initialAccountName: String = "",
    viewModel: LedgerViewModel = viewModel(
        factory = LedgerViewModel.provideFactory(LocalContext.current)
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Arrived from somewhere that already knows which account to look at — the
    // report runs itself once the branch list is in, as on the web.
    LaunchedEffect(initialAccountId) {
        if (initialAccountId.isNotBlank()) {
            viewModel.presetLedger(initialAccountId, initialAccountName)
        }
    }

    LaunchedEffect(uiState.sessionExpired) {
        if (uiState.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    AuthenticatedShell(
        title = "Ledger",
        currentRoute = Routes.LEDGER,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LedgerFilterForm(
                state = uiState,
                onBranchSelected = viewModel::onBranchSelected,
                searchLedgers = viewModel::searchLedgers,
                onLedgerSelected = viewModel::onLedgerSelected,
                onStartDate = viewModel::onStartDateSelected,
                onEndDate = viewModel::onEndDateSelected,
                onApply = viewModel::apply,
            )

            Box(modifier = Modifier.weight(1f)) {
                LedgerResults(state = uiState, onRetry = viewModel::apply)
            }
        }
    }
}

@Composable
private fun LedgerFilterForm(
    state: LedgerUiState,
    onBranchSelected: (BranchOption) -> Unit,
    searchLedgers: suspend (String) -> Resource<List<LedgerDropdownItem>>,
    onLedgerSelected: (LedgerDropdownItem) -> Unit,
    onStartDate: (SimpleDate) -> Unit,
    onEndDate: (SimpleDate) -> Unit,
    onApply: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 28.dp),
    ) {
        BranchDropdown(
            branches = state.branches,
            selected = state.selectedBranch,
            isLoading = state.isBranchesLoading,
            onSelected = onBranchSelected,
        )
        state.branchesError?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(12.dp))

        SearchableLedgerDropdown(
            selectedLedger = state.selectedLedger,
            onLedgerSelected = onLedgerSelected,
            searchLedgers = searchLedgers,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PickerField(
                label = "Start Date",
                value = state.startDate.toDisplay(),
                trailingIcon = Icons.Filled.DateRange,
                modifier = Modifier.weight(1f),
                onClick = { showDatePicker(context, state.startDate, onStartDate) },
            )
            PickerField(
                label = "End Date",
                value = state.endDate.toDisplay(),
                trailingIcon = Icons.Filled.DateRange,
                modifier = Modifier.weight(1f),
                onClick = { showDatePicker(context, state.endDate, onEndDate) },
            )
        }

        Spacer(Modifier.height(16.dp))

        FilterActions(
            onApply = onApply,
            canApply = state.canApply,
            isLoading = state.isReportLoading,
        )
    }
}

@Composable
private fun BranchDropdown(
    branches: List<BranchOption>,
    selected: BranchOption?,
    isLoading: Boolean,
    onSelected: (BranchOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        PickerField(
            label = "Select Branch",
            value = selected?.name ?: if (isLoading) "Loading branches…" else "",
            placeholder = "Select Branch",
            trailingIcon = Icons.Filled.ArrowDropDown,
            modifier = Modifier.fillMaxWidth(),
            onClick = { if (branches.isNotEmpty()) expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            branches.forEach { branch ->
                DropdownMenuItem(
                    text = { Text(branch.name) },
                    onClick = {
                        onSelected(branch)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** A read-only text field whose taps are forwarded to [onClick]. */
// ---------------------------------------------------------------------------
// Results (ledger statement from /reports/api-ledger)
// ---------------------------------------------------------------------------

@Composable
private fun LedgerResults(state: LedgerUiState, onRetry: () -> Unit) {
    when {
        state.isReportLoading -> CenterBox { CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground) }

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

        state.statement == null -> CenterBox {
            Text(
                text = "Choose a branch and ledger, pick a date range, then tap Apply.",
                color = MaterialTheme.appColors.textOnScreenMuted,
                textAlign = TextAlign.Center,
            )
        }

        else -> LedgerTable(
            statement = state.statement,
            showBranchNames = state.appliedAllBranches,
        )
    }
}

// Columns for the horizontally-scrollable ledger table.
// Order: SL. NO | VR DATE | VR NO | DESCRIPTION | DEBIT | CREDIT | BALANCE
private val COL_SL = 56.dp
private val COL_DATE = 96.dp
private val COL_VR = 116.dp
private val COL_DESCRIPTION = 220.dp
private val COL_DEBIT = 120.dp
private val COL_CREDIT = 120.dp
private val COL_BALANCE = 130.dp

/** The trailing thumbnail column's context, captured outside the render lambdas. */
private data class LedgerVoucherColumn(
    val isLocalEnv: Boolean,
    val onOpen: (VoucherAttachment) -> Unit,
)

// [summaryColor] inks the opening-balance / summary rows, which draw on a pale
// secondaryContainer band where the body's on-teal ink washes out.
private fun ledgerColumns(
    rules: List<HighlightRule>,
    summaryColor: Color,
    voucherColumn: LedgerVoucherColumn? = null,
    showBranchNames: Boolean = false,
) = listOf(
    ReportColumn<LedgerDisplayRow>("#", ReportColWidth.Fixed(COL_SL), TextAlign.Center) { r, _ ->
        cellText(r.sl, bold = r.isSummary, align = TextAlign.Center, color = r.summaryInk(summaryColor))
    },
    ReportColumn<LedgerDisplayRow>("VR DATE", ReportColWidth.Fixed(COL_DATE)) { r, _ ->
        cellText(r.date, bold = r.isSummary, color = r.summaryInk(summaryColor))
    },
    ReportColumn<LedgerDisplayRow>("VR NO", ReportColWidth.Fixed(COL_VR)) { r, _ ->
        cellText(r.voucherNo, bold = r.isSummary, color = r.summaryInk(summaryColor))
    },
    ReportColumn<LedgerDisplayRow>("DESCRIPTION", ReportColWidth.Fixed(COL_DESCRIPTION)) { r, _ ->
        val branchLine = showBranchNames && r.branchName.isNotBlank()
        if (r.remarks.isBlank() && !branchLine) {
            cellText(r.description, bold = r.isSummary, maxLines = 3, color = r.summaryInk(summaryColor))
        } else {
            ReportTableCell.Slot {
                LedgerDescriptionCell(
                    row = r,
                    rule = matchHighlightRule(r.remarks, rules),
                    showBranchName = branchLine,
                )
            }
        }
    },
    ReportColumn<LedgerDisplayRow>("DEBIT", ReportColWidth.Fixed(COL_DEBIT), TextAlign.End) { r, _ ->
        cellText(r.debit, align = TextAlign.End, bold = r.isSummary, color = r.summaryInk(summaryColor))
    },
    ReportColumn<LedgerDisplayRow>("CREDIT", ReportColWidth.Fixed(COL_CREDIT), TextAlign.End) { r, _ ->
        cellText(r.credit, align = TextAlign.End, bold = r.isSummary, color = r.summaryInk(summaryColor))
    },
    ReportColumn<LedgerDisplayRow>("BALANCE", ReportColWidth.Fixed(COL_BALANCE), TextAlign.End) { r, _ ->
        cellText(r.balance, align = TextAlign.End, bold = r.isSummary, color = r.summaryInk(summaryColor))
    },
) + listOfNotNull(
    voucherColumn?.let { vc ->
        ReportColumn<LedgerDisplayRow>("VOUCHER", ReportColWidth.Fixed(96.dp), TextAlign.Center) { r, _ ->
            if (r.attachments.isEmpty()) {
                ReportTableCell.Empty
            } else {
                ReportTableCell.Slot {
                    VoucherAttachmentsCell(
                        attachments = r.attachments,
                        isLocalEnv = vc.isLocalEnv,
                        onOpen = vc.onOpen,
                    )
                }
            }
        }
    },
)

/** Summary rows take the band's on-colour; normal rows keep the default ink. */
private fun LedgerDisplayRow.summaryInk(summaryColor: Color): Color =
    if (isSummary) summaryColor else Color.Unspecified

/**
 * Description plus the voucher's free-text remarks beneath it (as on the web
 * report), the remarks boxed in a highlight rule's colour when one matches.
 * When the remarks ARE the description (blank `name`), the single line is boxed.
 * In All Branch mode the row's branch name is shown as a final line, mirroring
 * the web's per-row branch tag.
 */
@Composable
private fun LedgerDescriptionCell(
    row: LedgerDisplayRow,
    rule: HighlightRule?,
    showBranchName: Boolean = false,
) {
    val remarksOnly = row.description == row.remarks
    // Normal rows draw on the screen backdrop (light on-teal ink); summary rows
    // draw on the pale secondaryContainer band and need its dark on-colour, or
    // the on-teal ink washes out. onSurfaceVariant is unreadable on the teal.
    val onScreen = if (row.isSummary) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onBackground
    }
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
        if (!remarksOnly && row.description.isNotBlank()) {
            Text(
                text = row.description,
                style = MaterialTheme.typography.bodySmall,
                color = onScreen,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
        }
        if (row.remarks.isNotBlank()) {
            HighlightedText(
                text = row.remarks,
                borderColor = highlightBorderColor(rule),
                color = if (remarksOnly) onScreen else onScreen.muted(),
                maxLines = 3,
            )
        }
        if (showBranchName && row.branchName.isNotBlank()) {
            Text(
                text = row.branchName,
                style = MaterialTheme.typography.labelSmall,
                color = onScreen.muted(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One rendered line of the report (the opening-balance line, or a transaction). */
private data class LedgerDisplayRow(
    val sl: String,
    val date: String,
    val voucherNo: String,
    val description: String,
    val remarks: String,
    val debit: String,
    val credit: String,
    /** The running Balance column; footer-style rows show "-". */
    val balance: String,
    val isSummary: Boolean,
    /** The voucher's branch — a final description line in All Branch mode. */
    val branchName: String = "",
    /** The voucher's attachments — the flag-gated VOUCHER column. */
    val attachments: List<VoucherAttachment> = emptyList(),
)

/**
 * Opening line first (netted, as on the web: only one side non-zero, the
 * Balance cell seeded from it), then the transaction rows numbered from 1 with
 * their cumulative running balance.
 */
private fun LedgerStatement.toDisplayRows(): List<LedgerDisplayRow> {
    val list = ArrayList<LedgerDisplayRow>(rows.size + 1)
    list += LedgerDisplayRow(
        sl = "-",
        date = "",
        voucherNo = "",
        description = "Opening",
        remarks = "",
        debit = amountOrDash(openingNetDebit),
        credit = amountOrDash(openingNetCredit),
        balance = amountOrDash(openingRunning),
        isSummary = true,
    )
    rows.forEachIndexed { index, r ->
        list += LedgerDisplayRow(
            sl = (index + 1).toString(),
            date = formatVrDate(r.date),
            voucherNo = r.voucherNo,
            description = r.description,
            remarks = r.remarks,
            debit = amountOrDash(r.debit),
            credit = amountOrDash(r.credit),
            balance = amountOrDash(r.runningBalance),
            isSummary = false,
            branchName = r.branchName,
            attachments = r.attachments,
        )
    }
    return list
}

@Composable
private fun LedgerTable(statement: LedgerStatement, showBranchNames: Boolean) {
    val rules = rememberHighlightRules()
    val summaryBg = MaterialTheme.colorScheme.secondaryContainer
    val summaryInk = MaterialTheme.colorScheme.onSecondaryContainer

    // The voucher-image column, behind the same branch switch as the web.
    val context = LocalContext.current
    val settings = remember { ServiceLocator.provideSessionManager(context).state.value.settings }
    val showVouchers = settings?.showVoucherImage == true &&
        statement.rows.any { it.attachments.isNotEmpty() }
    val isLocalEnv = settings?.isLocalEnv == true
    var viewing by remember { mutableStateOf<VoucherAttachment?>(null) }

    val columns = remember(rules, summaryInk, showVouchers, isLocalEnv, showBranchNames) {
        ledgerColumns(
            rules = rules,
            summaryColor = summaryInk,
            voucherColumn = if (showVouchers) {
                LedgerVoucherColumn(isLocalEnv = isLocalEnv) { attachment ->
                    if (attachment.isImage) {
                        viewing = attachment
                    } else {
                        openVoucherAttachment(context, attachment, isLocalEnv)
                    }
                }
            } else {
                null
            },
            showBranchNames = showBranchNames,
        )
    }
    ReportTable(
        columns = columns,
        data = statement.toDisplayRows(),
        footerRows = ledgerFooterRows(statement),
        // The Opening Balance line is styled like the summary rows.
        rowBackground = { row, _ -> if (row.isSummary) summaryBg else null },
    )

    viewing?.let { attachment ->
        VoucherImageViewerDialog(
            attachment = attachment,
            isLocalEnv = isLocalEnv,
            onDismiss = { viewing = null },
        )
    }
}

/** Range Total, Total, and the net Balance line — each label sits under DESCRIPTION. */
private fun ledgerFooterRows(statement: LedgerStatement): List<List<ReportFooterCell>> {
    val balance = statement.balance
    // The web's getLedgerRowName: the label follows the side the balance is on.
    val balanceLabel = when {
        balance > 0.0 -> "Balance Receivable"
        balance < 0.0 -> "Balance Payable"
        else -> "Balance"
    }
    return listOf(
        ledgerFooterRow("Range Total", statement.rangeDebit, statement.rangeCredit),
        ledgerFooterRow("Total", statement.totalDebit, statement.totalCredit),
        ledgerFooterRow(
            balanceLabel,
            // Net balance sits on its side: receivable => debit, payable => credit.
            if (balance > 0.0) balance else 0.0,
            if (balance < 0.0) -balance else 0.0,
        ),
    )
}

private fun ledgerFooterRow(label: String, debit: Double, credit: Double): List<ReportFooterCell> =
    listOf(
        // Blank SL / VR DATE / VR NO under one span; the label sits under DESCRIPTION.
        ReportFooterCell(ReportTableCell.Empty, colSpan = 3),
        ReportFooterCell(cellText(label, bold = true)),
        ReportFooterCell(cellText(amountOrDash(debit), align = TextAlign.End, bold = true)),
        ReportFooterCell(cellText(amountOrDash(credit), align = TextAlign.End, bold = true)),
        // Footer rows carry no running balance — a "-" under BALANCE, as on the web.
        ReportFooterCell(cellText("-", align = TextAlign.End, bold = true)),
    )

/** Debit/Credit cells show "-" for zero/empty, else the branch-formatted amount. */
private fun amountOrDash(value: Double): String = AmountFormat.formatOrDash(value)

/** The API sends `vr_date` as yyyy-MM-dd; the report shows dd/MM/yyyy. */
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

private fun showDatePicker(
    context: Context,
    initial: SimpleDate,
    onPicked: (SimpleDate) -> Unit,
) {
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            onPicked(SimpleDate(year = year, month = month + 1, day = dayOfMonth))
        },
        initial.year,
        initial.month - 1,
        initial.day,
    ).show()
}
