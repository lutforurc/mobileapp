package com.example.cashbookbd.ui.hrm

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.HrmRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.hrm.model.SalaryDetailRow
import com.example.cashbookbd.ui.hrm.model.SalarySheetDetail
import com.example.cashbookbd.ui.hrm.model.SalarySheetSummary
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportFooterCell
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.reports.model.BranchOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class SalarySheetUiState(
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val isBranchesLoading: Boolean = false,

    val year: Int = Calendar.getInstance().get(Calendar.YEAR),

    val rows: List<SalarySheetSummary> = emptyList(),
    val isLoading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,

    /** Row serial whose Payment confirm dialog is open; null when none. */
    val confirmingSerial: Int? = null,
    val payingSerial: Int? = null,
    val message: String? = null,

    /** The month the user tapped into (the web's print view); null = the list. */
    val detail: SalarySheetDetail? = null,
    val isDetailLoading: Boolean = false,

    val sessionExpired: Boolean = false,
) {
    val canApply: Boolean get() = selectedBranch != null && !isLoading
}

class SalarySheetViewModel(
    private val hrmRepository: HrmRepository,
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SalarySheetUiState())
    val uiState: StateFlow<SalarySheetUiState> = _uiState.asStateFlow()

    init {
        loadBranches()
    }

    private fun loadBranches() {
        _uiState.update { it.copy(isBranchesLoading = true) }
        viewModelScope.launch {
            when (val result = reportRepository.getBranches()) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isBranchesLoading = false,
                        branches = result.data.branches,
                        selectedBranch = it.selectedBranch ?: result.data.branches.firstOrNull(),
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isBranchesLoading = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onBranchSelected(branch: BranchOption) =
        _uiState.update { it.copy(selectedBranch = branch, loaded = false, rows = emptyList()) }

    fun onYearSelected(year: Int) =
        _uiState.update { it.copy(year = year, loaded = false, rows = emptyList()) }

    fun load() {
        val state = _uiState.value
        val branch = state.selectedBranch ?: return
        if (state.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null, message = null) }
        viewModelScope.launch {
            when (val result = hrmRepository.salarySheetRows(branch.id, state.year)) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, loaded = true, rows = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onPaymentClick(serial: Int) = _uiState.update { it.copy(confirmingSerial = serial) }
    fun onConfirmDismiss() = _uiState.update { it.copy(confirmingSerial = null) }

    /** The month link: loads that sheet's per-employee detail. */
    fun openDetail(row: SalarySheetSummary) {
        if (_uiState.value.isDetailLoading) return
        _uiState.update { it.copy(isDetailLoading = true, error = null, message = null) }
        viewModelScope.launch {
            when (val result = hrmRepository.salarySheetDetail(row.raw)) {
                is Resource.Success -> _uiState.update {
                    it.copy(isDetailLoading = false, detail = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isDetailLoading = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun closeDetail() = _uiState.update { it.copy(detail = null) }

    fun pay(row: SalarySheetSummary) {
        if (_uiState.value.payingSerial != null) return
        _uiState.update {
            it.copy(confirmingSerial = null, payingSerial = row.serial, message = null, error = null)
        }
        viewModelScope.launch {
            when (val result = hrmRepository.salaryPaymentFull(row.raw)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(payingSerial = null, message = result.data) }
                    load()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        payingSerial = null,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                SalarySheetViewModel(
                    hrmRepository = ServiceLocator.provideHrmRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                )
            }
        }
    }
}

/** "012026" / "01-2026" → "January 2026"; anything else passes through. */
private fun monthLabel(raw: String): String {
    val match = Regex("""^(\d{2})-?(\d{4})$""").find(raw.trim()) ?: return raw
    val month = match.groupValues[1].toIntOrNull() ?: return raw
    if (month !in 1..12) return raw
    val names = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    return "${names[month - 1]} ${match.groupValues[2]}"
}

/**
 * Salary Reports — the web's Salary Sheet: branch + year filter over the
 * per-month voucher rows, with the same Paid badge / Select-action column.
 */
@Composable
fun SalarySheetScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: SalarySheetViewModel =
        viewModel(factory = SalarySheetViewModel.provideFactory(context))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.sessionExpired) {
        LaunchedEffect(Unit) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    state.confirmingSerial?.let { serial ->
        val row = state.rows.firstOrNull { it.serial == serial }
        if (row != null) {
            AlertDialog(
                onDismissRequest = viewModel::onConfirmDismiss,
                title = { Text("Pay salary?") },
                text = {
                    Text(
                        "This will pay the full due of ${AmountFormat.format(row.due)} for " +
                            "${monthLabel(row.paymentMonth)} (${row.employees} employees). " +
                            "This posts a real cash payment voucher.",
                    )
                },
                confirmButton = { LinkButton(text = "Pay", onClick = { viewModel.pay(row) }) },
                dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::onConfirmDismiss) },
            )
        }
    }

    // In the detail view, system Back returns to the sheet list first.
    androidx.activity.compose.BackHandler(enabled = state.detail != null) {
        viewModel.closeDetail()
    }

    AuthenticatedShell(
        title = "Salary Reports",
        currentRoute = Routes.HRM,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        state.detail?.let { detail ->
            SalaryDetailView(detail = detail, onBack = viewModel::closeDetail)
            return@AuthenticatedShell
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                HrmBranchDropdown(
                    branches = state.branches,
                    selected = state.selectedBranch,
                    isLoading = state.isBranchesLoading,
                    onSelected = viewModel::onBranchSelected,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    HrmYearField(
                        label = "Year",
                        value = state.year,
                        onSelected = viewModel::onYearSelected,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        text = "Search",
                        onClick = viewModel::load,
                        enabled = state.canApply,
                        isLoading = state.isLoading,
                    )
                }
                state.message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
                state.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            when {
                state.isLoading || state.isDetailLoading -> Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                !state.loaded -> Box(
                    Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Choose your filters, then tap Search.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                state.rows.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No salary sheets for this year.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    // The web's Salary Sheet table, through the shared template:
                    // same columns, same colours, action column at the end.
                    val errorColor = MaterialTheme.colorScheme.error
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val columns = listOf(
                        ReportColumn<SalarySheetSummary>(
                            header = "Sl",
                            width = ReportColWidth.Fixed(44.dp),
                            align = TextAlign.Center,
                        ) { row, _ -> cellText(row.serial.toString(), align = TextAlign.Center) },
                        ReportColumn(
                            header = "Payment Month",
                            width = ReportColWidth.Fixed(124.dp),
                        ) { row, _ ->
                            // The web's month link: tap opens that sheet's detail.
                            ReportTableCell.Slot {
                                Text(
                                    text = monthLabel(row.paymentMonth),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryColor,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.openDetail(row) }
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                )
                            }
                        },
                        ReportColumn(
                            header = "Employees",
                            width = ReportColWidth.Fixed(88.dp),
                            align = TextAlign.Center,
                        ) { row, _ -> cellText(row.employees.toString(), align = TextAlign.Center) },
                        ReportColumn(
                            header = "Gross Salary",
                            width = ReportColWidth.Fixed(108.dp),
                            align = TextAlign.End,
                        ) { row, _ -> cellText(AmountFormat.formatOrDash(row.gross)) },
                        ReportColumn(
                            header = "Net Salary",
                            width = ReportColWidth.Fixed(108.dp),
                            align = TextAlign.End,
                        ) { row, _ -> cellText(AmountFormat.formatOrDash(row.net), bold = true) },
                        ReportColumn(
                            header = "Loan Ded.",
                            width = ReportColWidth.Fixed(100.dp),
                            align = TextAlign.End,
                        ) { row, _ ->
                            cellText(
                                AmountFormat.formatOrDash(row.loanDeduction),
                                color = if (row.loanDeduction > 0) errorColor else Color.Unspecified,
                            )
                        },
                        ReportColumn(
                            header = "Payment",
                            width = ReportColWidth.Fixed(110.dp),
                            align = TextAlign.End,
                        ) { row, _ ->
                            cellText(
                                AmountFormat.formatOrDash(row.payment),
                                color = if (row.payment > 0) PaidGreen else Color.Unspecified,
                            )
                        },
                        ReportColumn(
                            header = "Due",
                            width = ReportColWidth.Fixed(104.dp),
                            align = TextAlign.End,
                        ) { row, _ ->
                            cellText(
                                AmountFormat.formatOrDash(row.due),
                                bold = row.due > 0,
                                color = if (row.due > 0) errorColor else Color.Unspecified,
                            )
                        },
                        ReportColumn(
                            header = "Action",
                            width = ReportColWidth.Fixed(112.dp),
                            align = TextAlign.Center,
                        ) { row, _ ->
                            ReportTableCell.Slot {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (row.isPaid) {
                                        PaidBadge()
                                    } else {
                                        ActionSelect(
                                            isPaying = state.payingSerial == row.serial,
                                            onPayment = { viewModel.onPaymentClick(row.serial) },
                                        )
                                    }
                                }
                            }
                        },
                    )
                    ReportTable(
                        columns = columns,
                        data = state.rows,
                        modifier = Modifier.weight(1f),
                        noDataMessage = "No salary sheets for this year.",
                    )
                }
            }
        }
    }
}

/**
 * The month-link detail — the web's print view: header (voucher no/date, month)
 * over the per-employee table with the same columns and grand-total footer.
 */
@Composable
private fun SalaryDetailView(
    detail: com.example.cashbookbd.ui.hrm.model.SalarySheetDetail,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LinkButton(text = "← Back", onClick = onBack)
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    text = "Salary for the Month of ${monthLabel(detail.monthId)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                val voucherLine = listOf(
                    detail.vrNo.takeIf { it.isNotBlank() }?.let { "Vr. No. $it" },
                    detail.vrDate.takeIf { it.isNotBlank() },
                    detail.levelName.takeIf { it.isNotBlank() },
                ).filterNotNull().joinToString("  •  ")
                if (voucherLine.isNotBlank()) {
                    Text(
                        text = voucherLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        val columns = listOf(
            ReportColumn<com.example.cashbookbd.ui.hrm.model.SalaryDetailRow>(
                header = "SL",
                width = ReportColWidth.Fixed(40.dp),
                align = TextAlign.Center,
            ) { row, index -> cellText(row.sl.ifBlank { (index + 1).toString() }, align = TextAlign.Center) },
            ReportColumn(
                header = "Employee Name",
                width = ReportColWidth.Fixed(150.dp),
            ) { row, _ ->
                cellText(
                    listOf(row.name, row.designation).filter { it.isNotBlank() }.joinToString("\n"),
                    maxLines = 2,
                )
            },
            ReportColumn(
                header = "M. Days",
                width = ReportColWidth.Fixed(60.dp),
                align = TextAlign.Center,
            ) { row, _ -> cellText(row.monthDays.ifBlank { "-" }, align = TextAlign.Center) },
            ReportColumn(
                header = "W. Days",
                width = ReportColWidth.Fixed(60.dp),
                align = TextAlign.Center,
            ) { row, _ -> cellText(row.workingDays.ifBlank { "-" }, align = TextAlign.Center) },
            ReportColumn(
                header = "M. Basic",
                width = ReportColWidth.Fixed(94.dp),
                align = TextAlign.End,
            ) { row, _ -> cellText(AmountFormat.formatOrDash(row.monthlyBasic)) },
            ReportColumn(
                header = "Salary",
                width = ReportColWidth.Fixed(94.dp),
                align = TextAlign.End,
            ) { row, _ -> cellText(AmountFormat.formatOrDash(row.salary)) },
            ReportColumn(
                header = "Mobile",
                width = ReportColWidth.Fixed(80.dp),
                align = TextAlign.End,
            ) { row, _ -> cellText(AmountFormat.formatOrDash(row.mobileAllowance)) },
            ReportColumn(
                header = "Total",
                width = ReportColWidth.Fixed(96.dp),
                align = TextAlign.End,
            ) { row, _ -> cellText(AmountFormat.formatOrDash(row.total)) },
            ReportColumn(
                header = "Loan",
                width = ReportColWidth.Fixed(88.dp),
                align = TextAlign.End,
            ) { row, _ -> cellText(AmountFormat.formatOrDash(row.loanDeduction)) },
            ReportColumn(
                header = "Att. Ded",
                width = ReportColWidth.Fixed(84.dp),
                align = TextAlign.End,
            ) { row, _ -> cellText(AmountFormat.formatOrDash(row.attendanceDeduction)) },
            ReportColumn(
                header = "Net Salary",
                width = ReportColWidth.Fixed(100.dp),
                align = TextAlign.End,
            ) { row, _ -> cellText(AmountFormat.formatOrDash(row.netSalary), bold = true) },
            ReportColumn(
                header = "Payment",
                width = ReportColWidth.Fixed(100.dp),
                align = TextAlign.End,
            ) { row, _ ->
                cellText(
                    AmountFormat.formatOrDash(row.payment),
                    bold = row.payment > 0,
                    color = if (row.payment > 0) PaidGreen else Color.Unspecified,
                )
            },
            ReportColumn(
                header = "Vr No",
                width = ReportColWidth.Fixed(112.dp),
                align = TextAlign.Center,
            ) { row, _ ->
                cellText(
                    row.vrNo.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        .joinToString("\n").ifBlank { "-" },
                    align = TextAlign.Center,
                    maxLines = 3,
                )
            },
        )

        val footer = listOf(
            listOf(
                ReportFooterCell(cellText("Total", bold = true), colSpan = 4),
                ReportFooterCell(cellText(AmountFormat.formatOrDash(detail.totalMonthlyBasic), bold = true)),
                ReportFooterCell(cellText(AmountFormat.formatOrDash(detail.totalSalary), bold = true)),
                ReportFooterCell(cellText(AmountFormat.formatOrDash(detail.totalMobile), bold = true)),
                ReportFooterCell(cellText(AmountFormat.formatOrDash(detail.totalGross), bold = true)),
                ReportFooterCell(cellText(AmountFormat.formatOrDash(detail.totalLoan), bold = true)),
                ReportFooterCell(cellText(AmountFormat.formatOrDash(detail.totalAttendance), bold = true)),
                ReportFooterCell(cellText(AmountFormat.formatOrDash(detail.totalNet), bold = true)),
                ReportFooterCell(cellText(AmountFormat.formatOrDash(detail.totalPayment), bold = true)),
                ReportFooterCell(ReportTableCell.Empty),
            ),
        )

        ReportTable(
            columns = columns,
            data = detail.rows,
            modifier = Modifier.weight(1f),
            footerRows = footer,
            noDataMessage = "No salary lines in this sheet.",
        )
    }
}

/** The web table's greens (emerald payment text and Paid pill). */
private val PaidGreen = Color(0xFF10B981)

/** The web's green "Paid" pill. */
@Composable
private fun PaidBadge() {
    Box(
        modifier = Modifier
            .background(PaidGreen.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text = "Paid",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF047857),
        )
    }
}

/**
 * The web's Action "Select" dropdown. Payment works; Update and Excel are the
 * web-only actions for now and show disabled.
 */
@Composable
private fun ActionSelect(isPaying: Boolean, onPayment: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        SecondaryButton(
            text = if (isPaying) "Paying…" else "Select ▾",
            onClick = { if (!isPaying) expanded = true },
            enabled = !isPaying,
            compact = true,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Update (web only)") },
                onClick = {},
                enabled = false,
            )
            DropdownMenuItem(
                text = { Text("Excel (web only)") },
                onClick = {},
                enabled = false,
            )
            DropdownMenuItem(
                text = { Text("Payment") },
                onClick = {
                    expanded = false
                    onPayment()
                },
            )
        }
    }
}
