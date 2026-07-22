package com.example.cashbookbd.ui.transaction

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import com.example.cashbookbd.data.repository.LedgerRepository
import com.example.cashbookbd.data.repository.TransactionRepository
import com.example.cashbookbd.data.repository.TxnSelection
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.InstallmentPaymentsDialog
import com.example.cashbookbd.ui.components.InstallmentReceiveDialog
import com.example.cashbookbd.ui.components.InstallmentStatusPill
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SearchableLedgerDropdown
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.theme.accents
import com.example.cashbookbd.ui.transaction.model.EarlyPaymentSummary
import com.example.cashbookbd.ui.transaction.model.InstallmentRow
import com.example.cashbookbd.ui.transaction.model.computeEarlyPaymentSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Transaction â†’ Installments â€” a port of the web's InstallmentDetails page: a
 * customer picker with a "Show All" toggle, the customer's installment
 * schedule (Sl / Sales Invoice / Installment No / Inst. Date / Inst. Amount /
 * Action / Status), a Receive dialog per due row, the payments-details popup
 * behind an installment number, and the Early Payment panel when the invoice
 * carries a discount. Not the Due Installments report â€” that stays under
 * Reports.
 */

data class InstallmentsUiState(
    val customer: TxnSelection? = null,
    val showAll: Boolean = false,

    val isLoading: Boolean = false,
    val loadError: String? = null,
    val rows: List<InstallmentRow> = emptyList(),
    val earlyPayment: EarlyPaymentSummary? = null,

    // Receive dialog.
    val receiveTarget: InstallmentRow? = null,
    val receiveAmount: String = "",
    val receiveRemarks: String = "",
    val isReceiving: Boolean = false,

    // Payments-details dialog (the tapped row).
    val paymentsFor: InstallmentRow? = null,

    // Early Payment dialog.
    val showEarlyPayment: Boolean = false,
    val isApplyingEarlyPayment: Boolean = false,

    val message: String? = null,
    val isError: Boolean = false,
    val sessionExpired: Boolean = false,
) {
    val totalDue: Double get() = rows.sumOf { it.dueAmount }

    val canConfirmReceive: Boolean
        get() = receiveTarget != null &&
            (receiveAmount.toDoubleOrNull() ?: 0.0) > 0.0 &&
            !isReceiving
}

class InstallmentsViewModel(
    private val transactionRepository: TransactionRepository,
    private val ledgerRepository: LedgerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InstallmentsUiState())
    val uiState: StateFlow<InstallmentsUiState> = _uiState.asStateFlow()

    /** Customer accounts â€” same unrestricted COA search the web's picker uses. */
    suspend fun searchCustomers(query: String): Resource<List<LedgerDropdownItem>> =
        ledgerRepository.searchLedgers(query)

    fun onCustomerSelected(customer: TxnSelection) {
        _uiState.update { it.copy(customer = customer) }
        load()
    }

    fun onShowAllToggle(checked: Boolean) {
        _uiState.update { it.copy(showAll = checked) }
        if (_uiState.value.customer != null) load()
    }

    private fun load() {
        val state = _uiState.value
        val customer = state.customer ?: return
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            when (val result = transactionRepository.fetchInstallments(customer.id, state.showAll)) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        rows = result.data,
                        earlyPayment = computeEarlyPaymentSummary(result.data),
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = result.message,
                        rows = emptyList(),
                        earlyPayment = null,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    // ---- Receive dialog ---------------------------------------------------

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
                    load()
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

    // ---- Payments-details dialog -----------------------------------------

    fun onPaymentsRequested(row: InstallmentRow) = _uiState.update { it.copy(paymentsFor = row) }

    fun onPaymentsDismiss() = _uiState.update { it.copy(paymentsFor = null) }

    // ---- Early payment ----------------------------------------------------

    fun onEarlyPaymentOpen() = _uiState.update { it.copy(showEarlyPayment = true) }

    fun onEarlyPaymentDismiss() = _uiState.update { it.copy(showEarlyPayment = false) }

    fun applyEarlyPayment() {
        val summary = _uiState.value.earlyPayment ?: return
        if (!summary.canApply || _uiState.value.isApplyingEarlyPayment) return
        _uiState.update { it.copy(isApplyingEarlyPayment = true) }
        viewModelScope.launch {
            when (val result = transactionRepository.applyInstallmentEarlyPayment(summary.installmentId)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isApplyingEarlyPayment = false,
                            showEarlyPayment = false,
                            message = result.data,
                            isError = false,
                        )
                    }
                    load()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isApplyingEarlyPayment = false,
                        showEarlyPayment = false,
                        message = result.message,
                        isError = true,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    private fun String.decimalOnly(): String =
        filterIndexed { i, c -> c.isDigit() || (c == '.' && !take(i).contains('.')) }

    private fun Double.toPlainAmount(): String =
        if (this % 1.0 == 0.0) toLong().toString() else toString()

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                InstallmentsViewModel(
                    transactionRepository = ServiceLocator.provideTransactionRepository(appContext),
                    ledgerRepository = ServiceLocator.provideLedgerRepository(appContext),
                )
            }
        }
    }
}

@Composable
fun InstallmentsScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InstallmentsViewModel = viewModel(
        factory = InstallmentsViewModel.provideFactory(LocalContext.current)
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    AuthenticatedShell(
        title = "Installments",
        currentRoute = Routes.TRANSACTIONS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        val onScreen = MaterialTheme.colorScheme.onBackground
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SearchableLedgerDropdown(
                selectedLedger = state.customer?.let { LedgerDropdownItem(it.id.toIntOrNull() ?: 0, it.name, null) },
                onLedgerSelected = { viewModel.onCustomerSelected(TxnSelection(it.id.toString(), it.name)) },
                searchLedgers = viewModel::searchCustomers,
                label = "Select Customer",
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = state.showAll, onCheckedChange = viewModel::onShowAllToggle)
                Text(
                    text = "Show All",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onScreen,
                    modifier = Modifier.padding(start = 10.dp).weight(1f),
                )
                if (state.earlyPayment != null) {
                    SecondaryButton(
                        text = "Early Payment",
                        onClick = viewModel::onEarlyPaymentOpen,
                        compact = true,
                    )
                }
            }

            state.message?.let { message ->
                Text(
                    text = message,
                    color = if (state.isError) onScreen else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = onScreen)
                }

                state.loadError != null -> Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.loadError!!, color = onScreen, textAlign = TextAlign.Center)
                }

                state.customer == null -> Text(
                    text = "Select a customer to see their installments.",
                    color = onScreen.copy(alpha = 0.8f),
                    modifier = Modifier.padding(vertical = 24.dp),
                )

                else -> {
                    ReportTable(
                        columns = installmentColumns(state, viewModel),
                        data = state.rows,
                        noDataMessage = "No data found",
                        scrollable = false,
                    )
                    Text(
                        text = "Total due amount: ${AmountFormat.format(state.totalDue)}",
                        color = onScreen,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
        }
    }

    state.receiveTarget?.let { target ->
        InstallmentReceiveDialog(
            subtitle = "${target.invoiceNo} â€” installment ${target.installmentNo}",
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
    EarlyPaymentDialog(state, viewModel)
}

/** Sl / Sales Invoice / Installment No / Inst. Date / Inst. Amount / Action / Status. */
@Composable
private fun installmentColumns(
    state: InstallmentsUiState,
    viewModel: InstallmentsViewModel,
): List<ReportColumn<InstallmentRow>> {
    // Rows draw on the screen backdrop, so text carries its on-colour explicitly.
    val onScreen = MaterialTheme.colorScheme.onBackground
    return remember(state.isReceiving, onScreen) {
        listOf(
            ReportColumn("Sl. No", ReportColWidth.Fixed(56.dp), TextAlign.Center) { r, index ->
                cellText(r.slNumber.ifBlank { (index + 1).toString() }, align = TextAlign.Center, color = onScreen)
            },
            ReportColumn("Sales Invoice", ReportColWidth.Fixed(110.dp)) { r, _ ->
                cellText(r.invoiceNo.ifBlank { "-" }, color = onScreen)
            },
            ReportColumn("Inst. No", ReportColWidth.Fixed(88.dp), TextAlign.Center) { r, _ ->
                if (r.payments.isEmpty()) {
                    cellText(r.installmentNo, align = TextAlign.Center, color = onScreen)
                } else {
                    // Tappable, like the web's "#N Details" chip â€” opens the receipts.
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
            ReportColumn("Inst. Date", ReportColWidth.Fixed(100.dp)) { r, _ ->
                cellText(r.dueDate.ifBlank { "-" }, color = onScreen)
            },
            ReportColumn("Inst. Amount", ReportColWidth.Fixed(110.dp), TextAlign.End) { r, _ ->
                cellText(AmountFormat.formatOrDash(r.dueAmount), align = TextAlign.End, color = onScreen)
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
            ReportColumn("Status", ReportColWidth.Fixed(110.dp), TextAlign.Center) { r, _ ->
                if (r.status.isBlank()) {
                    cellText("-", align = TextAlign.Center, color = onScreen)
                } else {
                    ReportTableCell.Slot { InstallmentStatusPill(r.status) }
                }
            },
        )
    }
}

/** The web's EarlyPaymentModal: the discount summary and the Apply action. */
@Composable
private fun EarlyPaymentDialog(state: InstallmentsUiState, viewModel: InstallmentsViewModel) {
    if (!state.showEarlyPayment) return
    val summary = state.earlyPayment ?: return
    AlertDialog(
        onDismissRequest = viewModel::onEarlyPaymentDismiss,
        title = { Text("Early Payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SummaryLine("Invoice", summary.invoiceNo)
                SummaryLine("Deadline", summary.deadline)
                SummaryLine("Invoice total", AmountFormat.format(summary.invoiceTotal))
                SummaryLine("Discount", AmountFormat.format(summary.discount))
                SummaryLine("Payable with discount", AmountFormat.format(summary.earlyPaymentAmount))
                SummaryLine("Paid before deadline", AmountFormat.format(summary.paidBeforeDeadline))
                SummaryLine("Remaining", AmountFormat.format(maxOf(summary.remaining, 0.0)))
                Spacer(Modifier.height(4.dp))
                Text(
                    text = summary.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (summary.canApply) MaterialTheme.accents.green else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            if (state.isApplyingEarlyPayment) {
                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            } else {
                LinkButton(
                    text = "Apply",
                    onClick = viewModel::applyEarlyPayment,
                    enabled = summary.canApply,
                )
            }
        },
        dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::onEarlyPaymentDismiss) },
    )
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}
