package com.example.cashbookbd.ui.reports

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.example.cashbookbd.data.repository.DashboardRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.data.repository.TransactionRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.FilterActions
import com.example.cashbookbd.ui.components.InstallmentPaymentsDialog
import com.example.cashbookbd.ui.components.InstallmentReceiveDialog
import com.example.cashbookbd.ui.components.InstallmentStatusPill
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.theme.accents
import com.example.cashbookbd.ui.transaction.model.InstallmentRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Reports → Due Installments, rebuilt as a native screen so the rows read like
 * the web's DueInstallment page: a stacked customer block (name, father,
 * address, mobile, field officer), the installment/due amounts on top of each
 * other, the received amount, a per-row Receive action, and the status pill.
 * Filters: branch, status, date range and the web's "Show All" toggle
 * (due-only off).
 */

private val STATUS_OPTIONS = listOf(
    SelectorOption(id = "", label = "All"),
    SelectorOption(id = "overdue", label = "Overdue"),
    SelectorOption(id = "pending", label = "Pending"),
    SelectorOption(id = "upcoming", label = "Upcoming"),
    SelectorOption(id = "partial", label = "Partial"),
)

data class DueInstallmentsUiState(
    // Filter
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val isBranchesLoading: Boolean = false,
    val branchesError: String? = null,
    val selectedStatus: SelectorOption = STATUS_OPTIONS.first(),
    val startDate: SimpleDate = SimpleDate.today(),
    val endDate: SimpleDate = SimpleDate.today(),
    val showAll: Boolean = false,

    // Result
    val isLoading: Boolean = false,
    val loadError: String? = null,
    val hasApplied: Boolean = false,
    val rows: List<InstallmentRow> = emptyList(),

    // Receive dialog
    val receiveTarget: InstallmentRow? = null,
    val receiveAmount: String = "",
    val receiveRemarks: String = "",
    val isReceiving: Boolean = false,

    // Payments popup
    val paymentsFor: InstallmentRow? = null,

    val message: String? = null,
    val isError: Boolean = false,
    val sessionExpired: Boolean = false,
) {
    val canApply: Boolean get() = selectedBranch != null && !isLoading

    val canConfirmReceive: Boolean
        get() = receiveTarget != null &&
            (receiveAmount.toDoubleOrNull() ?: 0.0) > 0.0 &&
            !isReceiving
}

class DueInstallmentsViewModel(
    private val transactionRepository: TransactionRepository,
    private val reportRepository: ReportRepository,
    private val dashboardRepository: DashboardRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DueInstallmentsUiState())
    val uiState: StateFlow<DueInstallmentsUiState> = _uiState.asStateFlow()

    /** True once the default date range has been seeded, so later sources don't override it. */
    private var dateDefaulted = false

    init {
        applyDashboardTransactionDate()
        loadBranches()
    }

    /** Seed both dates from the backend's business date, like the other report screens. */
    private fun applyDashboardTransactionDate() {
        viewModelScope.launch {
            val dashboard = dashboardRepository.getCachedDashboard()
                ?: (dashboardRepository.getDashboard() as? Resource.Success)?.data
            val trDate = SimpleDate.fromDisplay(dashboard?.transactionDate) ?: return@launch
            dateDefaulted = true
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
                    if (applyBranchDate) dateDefaulted = true
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

    fun onBranchSelected(option: SelectorOption) {
        val branch = _uiState.value.branches.firstOrNull { it.id.toString() == option.id } ?: return
        _uiState.update { it.copy(selectedBranch = branch) }
    }

    fun onStatusSelected(option: SelectorOption) =
        _uiState.update { it.copy(selectedStatus = option) }

    fun onStartDateSelected(date: SimpleDate) = _uiState.update { it.copy(startDate = date) }

    fun onEndDateSelected(date: SimpleDate) = _uiState.update { it.copy(endDate = date) }

    fun onShowAllToggle(checked: Boolean) = _uiState.update { it.copy(showAll = checked) }

    fun apply() {
        val state = _uiState.value
        val branch = state.selectedBranch ?: return
        if (state.isLoading) return
        _uiState.update { it.copy(isLoading = true, loadError = null, message = null, isError = false) }
        viewModelScope.launch {
            val result = transactionRepository.fetchDueInstallments(
                branchId = branch.id,
                startDate = state.startDate.toApi(),
                endDate = state.endDate.toApi(),
                status = state.selectedStatus.id,
                dueOnly = !state.showAll,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, hasApplied = true, rows = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    // ---- Receive dialog (same flow as the Installments screen) ------------

    fun onReceiveRequested(row: InstallmentRow) = _uiState.update {
        it.copy(
            receiveTarget = row,
            receiveAmount = row.dueAmount.toPlainAmount(),
            receiveRemarks = "",
        )
    }

    fun onReceiveAmountChange(value: String) =
        _uiState.update { it.copy(receiveAmount = value.decimalOnly()) }

    fun onReceiveRemarksChange(value: String) =
        _uiState.update { it.copy(receiveRemarks = value) }

    fun onReceiveDismiss() = _uiState.update { it.copy(receiveTarget = null) }

    fun confirmReceive() {
        val state = _uiState.value
        val target = state.receiveTarget ?: return
        val amount = state.receiveAmount.toDoubleOrNull() ?: return
        if (amount <= 0 || state.isReceiving) return
        _uiState.update { it.copy(isReceiving = true) }
        viewModelScope.launch {
            val result = transactionRepository.receiveInstallment(
                installmentId = target.installmentId,
                amount = amount,
                remarks = state.receiveRemarks.trim(),
            )
            when (result) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isReceiving = false,
                            receiveTarget = null,
                            message = result.data,
                            isError = false,
                        )
                    }
                    apply()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isReceiving = false,
                        receiveTarget = null,
                        message = result.message,
                        isError = true,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onPaymentsRequested(row: InstallmentRow) = _uiState.update { it.copy(paymentsFor = row) }

    fun onPaymentsDismiss() = _uiState.update { it.copy(paymentsFor = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    private fun String.decimalOnly(): String =
        filterIndexed { i, c -> c.isDigit() || (c == '.' && !take(i).contains('.')) }

    private fun Double.toPlainAmount(): String =
        if (this % 1.0 == 0.0) toLong().toString() else toString()

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                DueInstallmentsViewModel(
                    transactionRepository = ServiceLocator.provideTransactionRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                    dashboardRepository = ServiceLocator.provideDashboardRepository(appContext),
                )
            }
        }
    }
}

@Composable
fun DueInstallmentsScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DueInstallmentsViewModel = viewModel(
        factory = DueInstallmentsViewModel.provideFactory(LocalContext.current)
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    AuthenticatedShell(
        title = "Due Installments",
        currentRoute = Routes.REPORTS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        val onScreen = MaterialTheme.colorScheme.onBackground
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppSelectDropdown(
                    label = "Select Branch",
                    options = state.branches.map { SelectorOption(id = it.id.toString(), label = it.name) },
                    selected = state.selectedBranch?.let { SelectorOption(id = it.id.toString(), label = it.name) },
                    onSelected = viewModel::onBranchSelected,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = if (state.isBranchesLoading) "Loading branches…" else "Select Branch",
                )
                state.branchesError?.let {
                    Text(it, color = onScreen, style = MaterialTheme.typography.bodySmall)
                }

                AppSelectDropdown(
                    label = "Select Status",
                    options = STATUS_OPTIONS,
                    selected = state.selectedStatus,
                    onSelected = viewModel::onStatusSelected,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PickerField(
                        label = "Start Date",
                        value = state.startDate.toDisplay(),
                        trailingIcon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f),
                        onClick = { showDueDatePicker(context, state.startDate, viewModel::onStartDateSelected) },
                    )
                    PickerField(
                        label = "End Date",
                        value = state.endDate.toDisplay(),
                        trailingIcon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f),
                        onClick = { showDueDatePicker(context, state.endDate, viewModel::onEndDateSelected) },
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = state.showAll, onCheckedChange = viewModel::onShowAllToggle)
                    Text(
                        text = "Show All",
                        style = MaterialTheme.typography.bodyMedium,
                        color = onScreen,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }

                FilterActions(
                    onApply = viewModel::apply,
                    canApply = state.canApply,
                    isLoading = state.isLoading,
                )

                state.message?.let { message ->
                    Text(
                        text = message,
                        color = if (state.isError) onScreen else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.isLoading -> CenteredBox { CircularProgressIndicator(color = onScreen) }

                    state.loadError != null -> CenteredBox {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.loadError!!, color = onScreen, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(16.dp))
                            PrimaryButton(text = "Retry", onClick = viewModel::apply)
                        }
                    }

                    !state.hasApplied -> CenteredBox {
                        Text(
                            text = "Choose your filters, then tap Apply.",
                            color = onScreen.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                        )
                    }

                    state.rows.isEmpty() -> CenteredBox {
                        Text(
                            text = "No installments found for this selection.",
                            color = onScreen.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                        )
                    }

                    else -> ReportTable(
                        columns = dueInstallmentColumns(viewModel),
                        data = state.rows,
                    )
                }
            }
        }
    }

    state.receiveTarget?.let { target ->
        InstallmentReceiveDialog(
            subtitle = "${target.customerName.ifBlank { target.invoiceNo }} — installment ${target.installmentNo}",
            amount = state.receiveAmount,
            remarks = state.receiveRemarks,
            isSaving = state.isReceiving,
            canSave = state.canConfirmReceive,
            onAmountChange = viewModel::onReceiveAmountChange,
            onRemarksChange = viewModel::onReceiveRemarksChange,
            onSave = viewModel::confirmReceive,
            onDismiss = viewModel::onReceiveDismiss,
        )
    }
    state.paymentsFor?.let { row ->
        InstallmentPaymentsDialog(payments = row.payments, onClose = viewModel::onPaymentsDismiss)
    }
}

/** The web table's columns: Sl / Customer / Inst No / Due Date / Inst-Due / Rcv / Action / Status. */
@Composable
private fun dueInstallmentColumns(
    viewModel: DueInstallmentsViewModel,
): List<ReportColumn<InstallmentRow>> {
    // Rows draw on the screen backdrop, so text carries its on-colour explicitly.
    val onScreen = MaterialTheme.colorScheme.onBackground
    val officerColor = MaterialTheme.accents.red
    return remember(onScreen, officerColor) {
        listOf(
            ReportColumn("Sl. No", ReportColWidth.Fixed(52.dp), TextAlign.Center) { r, index ->
                cellText(r.slNumber.ifBlank { (index + 1).toString() }, align = TextAlign.Center, color = onScreen)
            },
            ReportColumn("Customer Name", ReportColWidth.Fixed(200.dp)) { r, _ ->
                ReportTableCell.Slot { CustomerCell(r, onScreen, officerColor) }
            },
            ReportColumn("Inst No", ReportColWidth.Fixed(76.dp), TextAlign.Center) { r, _ ->
                if (r.payments.isEmpty()) {
                    cellText(r.installmentNo, align = TextAlign.Center, color = onScreen)
                } else {
                    ReportTableCell.Slot {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onPaymentsRequested(r) }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "#${r.installmentNo}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = onScreen,
                            )
                            Text(
                                text = "Details",
                                style = MaterialTheme.typography.labelSmall,
                                color = onScreen.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            },
            ReportColumn("Due Date", ReportColWidth.Fixed(100.dp)) { r, _ ->
                cellText(r.dueDate.ifBlank { "-" }, color = onScreen)
            },
            ReportColumn("Inst. Amount\nDue Amount", ReportColWidth.Fixed(112.dp), TextAlign.End) { r, _ ->
                ReportTableCell.Slot { StackedAmountCell(r, onScreen) }
            },
            ReportColumn("Rcv Amount", ReportColWidth.Fixed(100.dp), TextAlign.End) { r, _ ->
                cellText(AmountFormat.formatOrDash(r.paidAmount), align = TextAlign.End, color = onScreen)
            },
            ReportColumn("Action", ReportColWidth.Fixed(110.dp), TextAlign.Center) { r, _ ->
                if (r.dueAmount > 0) {
                    ReportTableCell.Slot {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            PrimaryButton(
                                text = "Receive",
                                onClick = { viewModel.onReceiveRequested(r) },
                                compact = true,
                            )
                        }
                    }
                } else {
                    cellText(r.receivedDate.ifBlank { "-" }, align = TextAlign.Center, color = onScreen)
                }
            },
            ReportColumn("Status", ReportColWidth.Fixed(104.dp), TextAlign.Center) { r, _ ->
                if (r.status.isBlank()) {
                    cellText("-", align = TextAlign.Center, color = onScreen)
                } else {
                    ReportTableCell.Slot { InstallmentStatusPill(r.status) }
                }
            },
        )
    }
}

/** The stacked customer block, field for field like the web's Customer Name cell. */
@Composable
private fun CustomerCell(
    row: InstallmentRow,
    onScreen: androidx.compose.ui.graphics.Color,
    officerColor: androidx.compose.ui.graphics.Color,
) {
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
        Text(
            text = row.customerName.ifBlank { "-" },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = onScreen,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        listOf(row.father, row.customerAddress, row.customerMobile).forEach { line ->
            if (line.isNotBlank()) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.labelSmall,
                    color = onScreen.copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (row.employee.isNotBlank()) {
            // The assigned field officer, red like the web row.
            Text(
                text = row.employee,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = officerColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Installment amount over due amount, split by a rule — the web's stacked cell. */
@Composable
private fun StackedAmountCell(row: InstallmentRow, onScreen: androidx.compose.ui.graphics.Color) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = AmountFormat.formatOrDash(row.amount),
            style = MaterialTheme.typography.bodySmall,
            color = onScreen,
        )
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            color = onScreen.copy(alpha = 0.4f),
        )
        Text(
            text = AmountFormat.formatOrDash(row.dueAmount),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = onScreen,
        )
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun showDueDatePicker(
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
