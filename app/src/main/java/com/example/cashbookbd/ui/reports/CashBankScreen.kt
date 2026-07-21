package com.example.cashbookbd.ui.reports

import android.app.DatePickerDialog
import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.cashbookbd.data.repository.CashBankRepository
import com.example.cashbookbd.data.repository.DashboardRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.FilterActions
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SummaryTile
import com.example.cashbookbd.ui.reports.model.BankDetailRow
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.CashBankReport
import com.example.cashbookbd.ui.reports.model.CashBankSummaryRow
import com.example.cashbookbd.ui.reports.model.SimpleDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CashBankUiState(
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val startDate: SimpleDate = SimpleDate.today(),
    val endDate: SimpleDate = SimpleDate.today(),
    val isBranchesLoading: Boolean = false,
    val branchesError: String? = null,

    val isReportLoading: Boolean = false,
    val reportError: String? = null,
    val report: CashBankReport? = null,
    val appliedRange: String? = null,

    val sessionExpired: Boolean = false,
) {
    val canApply: Boolean get() = selectedBranch != null && !isReportLoading
}

/**
 * Drives the Cash & Bank screen: branches, a date range seeded from the
 * business date, and the report call.
 */
class CashBankViewModel(
    private val reportRepository: ReportRepository,
    private val dashboardRepository: DashboardRepository,
    private val cashBankRepository: CashBankRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CashBankUiState())
    val uiState: StateFlow<CashBankUiState> = _uiState.asStateFlow()

    private var defaultDate: SimpleDate = SimpleDate.today()
    private var dateDefaulted = false

    init {
        applyDashboardTransactionDate()
        loadBranches()
    }

    private fun applyDashboardTransactionDate() {
        viewModelScope.launch {
            val dashboard = dashboardRepository.getCachedDashboard()
                ?: (dashboardRepository.getDashboard() as? Resource.Success)?.data
            val trDate = SimpleDate.fromDisplay(dashboard?.transactionDate) ?: return@launch
            dateDefaulted = true
            defaultDate = trDate
            _uiState.update { it.copy(startDate = trDate, endDate = trDate) }
        }
    }

    fun loadBranches() {
        _uiState.update { it.copy(isBranchesLoading = true, branchesError = null) }
        viewModelScope.launch {
            when (val result = reportRepository.getBranches()) {
                is Resource.Success -> _uiState.update {
                    val branchTrDate = result.data.transactionDate
                    val applyBranchDate = !dateDefaulted && branchTrDate != null
                    if (applyBranchDate) {
                        dateDefaulted = true
                        defaultDate = branchTrDate!!
                    }
                    it.copy(
                        isBranchesLoading = false,
                        branches = result.data.branches,
                        selectedBranch = it.selectedBranch ?: result.data.branches.firstOrNull(),
                        startDate = if (applyBranchDate) branchTrDate!! else it.startDate,
                        endDate = if (applyBranchDate) branchTrDate!! else it.endDate,
                    )
                }

                is Resource.Error -> _uiState.update {
                    it.copy(
                        isBranchesLoading = false,
                        branchesError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }

                Resource.Loading -> Unit
            }
        }
    }

    fun onBranchSelected(branch: BranchOption) = _uiState.update { it.copy(selectedBranch = branch) }
    fun onStartDateSelected(date: SimpleDate) = _uiState.update { it.copy(startDate = date) }
    fun onEndDateSelected(date: SimpleDate) = _uiState.update { it.copy(endDate = date) }

    fun reset() {
        _uiState.update {
            it.copy(
                startDate = defaultDate,
                endDate = defaultDate,
                report = null,
                reportError = null,
                appliedRange = null,
            )
        }
    }

    fun apply() {
        val state = _uiState.value
        val branch = state.selectedBranch ?: return
        if (state.isReportLoading) return
        if (state.startDate.toApi() > state.endDate.toApi()) {
            _uiState.update { it.copy(reportError = "Start date cannot be after end date.") }
            return
        }

        _uiState.update { it.copy(isReportLoading = true, reportError = null) }
        viewModelScope.launch {
            // The endpoint parses dd-MM-yyyy day-first, so send the display form
            // the web sends rather than the ISO one.
            when (val result = cashBankRepository.fetch(
                branchId = branch.id,
                startDate = state.startDate.toDisplay(),
                endDate = state.endDate.toDisplay(),
            )) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isReportLoading = false,
                        report = result.data,
                        reportError = null,
                        appliedRange = "${state.startDate.toDisplay()} — ${state.endDate.toDisplay()}",
                    )
                }

                is Resource.Error -> _uiState.update {
                    it.copy(
                        isReportLoading = false,
                        reportError = result.message,
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
                CashBankViewModel(
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                    dashboardRepository = ServiceLocator.provideDashboardRepository(appContext),
                    cashBankRepository = ServiceLocator.provideCashBankRepository(appContext),
                )
            }
        }
    }
}

/**
 * Cash & Bank (Received & Payment): the web report's two tables — an account-wise
 * summary with Cash and Bank Received/Payment pairs, and a bank-wise breakdown.
 */
@Composable
fun CashBankScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CashBankViewModel = viewModel(
        factory = CashBankViewModel.provideFactory(LocalContext.current)
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
        title = "Cash & Bank Summary",
        currentRoute = Routes.REPORTS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            FilterCard(
                state = uiState,
                onBranchSelected = viewModel::onBranchSelected,
                onStartDate = viewModel::onStartDateSelected,
                onEndDate = viewModel::onEndDateSelected,
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
    state: CashBankUiState,
    onBranchSelected: (BranchOption) -> Unit,
    onStartDate: (SimpleDate) -> Unit,
    onEndDate: (SimpleDate) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        BranchDropdown(
            branches = state.branches,
            selected = state.selectedBranch,
            isLoading = state.isBranchesLoading,
            onSelected = onBranchSelected,
        )
        state.branchesError?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                it,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodySmall,
            )
        }

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

        Spacer(Modifier.height(14.dp))
        FilterActions(
            onApply = onApply,
            onReset = onReset,
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
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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

// ---------------------------------------------------------------------------
// Results
// ---------------------------------------------------------------------------

@Composable
private fun Results(state: CashBankUiState, onRetry: () -> Unit) {
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
                text = "Choose a branch and date range, then tap Apply.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
        }

        else -> ReportContent(report = state.report, range = state.appliedRange)
    }
}

@Composable
private fun ReportContent(report: CashBankReport, range: String?) {
    // Both tables scroll together: the bank-wise breakdown belongs under the
    // summary, not in a pane of its own, so each table gives up its own scroll.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SummaryBox(
                label = "Cash Balance",
                value = formatAmount(report.balanceCashReceived - report.balanceCashPayment),
                modifier = Modifier.weight(1f),
            )
            SummaryBox(
                label = "Bank Balance",
                value = formatAmount(report.balanceBankReceived - report.balanceBankPayment),
                modifier = Modifier.weight(1f),
            )
        }

        if (!range.isNullOrBlank()) {
            Text(
                text = "Reporting date: $range",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
        }

        SummaryTable(report = report)

        if (report.bankDetails.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Bank-wise Details",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                // The API's bank_details covers the selected period only and
                // excludes the opening balance, so this column cannot agree with
                // the bank figures above — say so rather than call it a balance.
                text = "Movement over this period only — excludes the opening balance.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            BankDetailTable(report = report)
        }

        Spacer(Modifier.height(24.dp))
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
// Tables
// ---------------------------------------------------------------------------

private val COL_SL = 44.dp
private val COL_NAME = 190.dp
private val COL_NUM = 112.dp

private val summaryColumns = listOf(
    ReportColumn<CashBankSummaryRow>("#", ReportColWidth.Fixed(COL_SL), TextAlign.Center) { _, i ->
        cellText((i + 1).toString(), align = TextAlign.Center)
    },
    ReportColumn<CashBankSummaryRow>("ACCOUNT NAME", ReportColWidth.Fixed(COL_NAME)) { r, _ ->
        cellText(r.name.ifBlank { "-" }, maxLines = 2)
    },
    ReportColumn<CashBankSummaryRow>("RECEIVED (TK.)", ReportColWidth.Fixed(COL_NUM), TextAlign.End) { r, _ ->
        cellText(formatCell(r.cashReceived), align = TextAlign.End)
    },
    ReportColumn<CashBankSummaryRow>("PAYMENT (TK.)", ReportColWidth.Fixed(COL_NUM), TextAlign.End) { r, _ ->
        cellText(formatCell(r.cashPayment), align = TextAlign.End)
    },
    ReportColumn<CashBankSummaryRow>("RECEIVED (TK.)", ReportColWidth.Fixed(COL_NUM), TextAlign.End) { r, _ ->
        cellText(formatCell(r.bankReceived), align = TextAlign.End)
    },
    ReportColumn<CashBankSummaryRow>("PAYMENT (TK.)", ReportColWidth.Fixed(COL_NUM), TextAlign.End) { r, _ ->
        cellText(formatCell(r.bankPayment), align = TextAlign.End)
    },
)

private val summaryHeaderGroups = listOf(
    ReportHeaderGroup("", 1),               // #
    ReportHeaderGroup("", 1),               // Account name
    ReportHeaderGroup("CASH DETAILS", 2),
    ReportHeaderGroup("BANK DETAILS", 2),
)

@Composable
private fun SummaryTable(report: CashBankReport) {
    // The endpoint sends no totals and no balance row, so both are computed —
    // and Total includes the Opening Balance row, as the web report does.
    val total = listOf(
        ReportFooterCell(cellText("Total", align = TextAlign.End, bold = true), colSpan = 2),
        totalCell(report.totalCashReceived),
        totalCell(report.totalCashPayment),
        totalCell(report.totalBankReceived),
        totalCell(report.totalBankPayment),
    )
    val balance = listOf(
        ReportFooterCell(cellText("Balance", align = TextAlign.End, bold = true), colSpan = 2),
        totalCell(report.balanceCashReceived),
        totalCell(report.balanceCashPayment),
        totalCell(report.balanceBankReceived),
        totalCell(report.balanceBankPayment),
    )
    ReportTable(
        columns = summaryColumns,
        data = report.rows,
        headerGroups = summaryHeaderGroups,
        footerRows = listOf(total, balance),
        noDataMessage = "No report data found",
        scrollable = false,
    )
}

private val bankColumns = listOf(
    ReportColumn<BankDetailRow>("#", ReportColWidth.Fixed(COL_SL), TextAlign.Center) { _, i ->
        cellText((i + 1).toString(), align = TextAlign.Center)
    },
    ReportColumn<BankDetailRow>("BANK ACCOUNT", ReportColWidth.Fixed(COL_NAME)) { r, _ ->
        cellText(r.bankName.ifBlank { "-" }, maxLines = 2)
    },
    ReportColumn<BankDetailRow>("RECEIVED (TK.)", ReportColWidth.Fixed(COL_NUM), TextAlign.End) { r, _ ->
        cellText(formatCell(r.received), align = TextAlign.End)
    },
    ReportColumn<BankDetailRow>("PAYMENT (TK.)", ReportColWidth.Fixed(COL_NUM), TextAlign.End) { r, _ ->
        cellText(formatCell(r.payment), align = TextAlign.End)
    },
    ReportColumn<BankDetailRow>("MOVEMENT (TK.)", ReportColWidth.Fixed(COL_NUM), TextAlign.End) { r, _ ->
        cellText(formatCell(r.movement), align = TextAlign.End, bold = true)
    },
)

@Composable
private fun BankDetailTable(report: CashBankReport) {
    val total = listOf(
        ReportFooterCell(cellText("Total", align = TextAlign.End, bold = true), colSpan = 2),
        totalCell(report.totalBankDetailReceived),
        totalCell(report.totalBankDetailPayment),
        totalCell(report.totalBankDetailMovement),
    )
    ReportTable(
        columns = bankColumns,
        data = report.bankDetails,
        footerRows = listOf(total),
        noDataMessage = "No bank movement in this period",
        scrollable = false,
    )
}

private fun totalCell(value: Double): ReportFooterCell =
    ReportFooterCell(cellText(formatCell(value), align = TextAlign.End, bold = true))

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun formatAmount(value: Double): String = AmountFormat.format(value)

/** Zeros render as a dash, like every other report table. */
private fun formatCell(value: Double): String = AmountFormat.formatOrDash(value)

private fun showDatePicker(
    context: Context,
    current: SimpleDate,
    onPicked: (SimpleDate) -> Unit,
) {
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            onPicked(SimpleDate(year = year, month = month + 1, day = dayOfMonth))
        },
        current.year,
        current.month - 1,
        current.day,
    ).show()
}
