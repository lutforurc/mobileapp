package com.example.cashbookbd.ui.realestate

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.data.repository.UnitPaymentRow
import com.example.cashbookbd.data.repository.UnitPaymentSubmit
import com.example.cashbookbd.data.repository.UnitSaleOption
import com.example.cashbookbd.data.repository.UnitSalePaymentRepository
import com.example.cashbookbd.data.repository.UnitSaleSummary
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.components.SummaryTile
import com.example.cashbookbd.ui.components.SummaryTileRow
import com.example.cashbookbd.ui.reports.PickerField
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

// ---- Fixed choice lists (the web's enums, verbatim) ----

private val PaymentModes = listOf(
    "CASH", "BKASH", "NAGAD", "ROCKET", "UPAY", "BANK_TRANSFER", "CHEQUE",
    "POS_CARD", "MOBILE_BANKING", "OTHERS",
).map { SelectorOption(it, it.prettyEnum()) }

private val PaymentTypes = listOf(
    "BOOKING", "DOWN_PAYMENT", "INSTALLMENT", "ADJUSTMENT", "PENALTY",
    "REFUND", "SECURITY_DEPOSIT", "OTHER",
).map { SelectorOption(it, it.prettyEnum()) }

private val EntryStatuses = listOf("PENDING", "CONFIRMED", "REJECTED", "REVERSED")
    .map { SelectorOption(it, it.prettyEnum()) }

private val ChequeStatuses = listOf("PENDING", "COLLECTED", "BOUNCED", "CANCELLED")
    .map { SelectorOption(it, it.prettyEnum()) }

private const val MODE_CHEQUE = "CHEQUE"
private const val MODE_BANK_TRANSFER = "BANK_TRANSFER"

data class UnitPaymentUiState(
    val paymentId: String? = null,
    val isLoading: Boolean = true,
    val loadError: String? = null,

    // ADD mode: the Unit Sale picker (search + Load feeds the ddl's q).
    val saleQuery: String = "",
    val isLoadingSales: Boolean = false,
    val saleOptions: List<UnitSaleOption> = emptyList(),
    val selectedSale: UnitSaleOption? = null,
    val isLoadingSummary: Boolean = false,
    val summary: UnitSaleSummary? = null,

    /** EDIT mode: the fetched row — identity echoed on update, labels shown. */
    val editRow: UnitPaymentRow? = null,

    val bankAccounts: List<SelectorOption> = emptyList(),
    /** The branch's transaction date (yyyy-MM-dd) — the ADD form's default. */
    val defaultDate: String = "",

    // Form fields (wire values; dates are yyyy-MM-dd).
    val paymentDate: String = "",
    val paymentMode: String = "CASH",
    val paymentType: String = "INSTALLMENT",
    val amount: String = "",
    val receiptNo: String = "",
    val referenceNo: String = "",
    val bankName: String = "",
    val branchName: String = "",
    val coal4Id: String = "",
    val chequeDepositDueDate: String = "",
    val chequeCollectDate: String = "",
    val note: String = "",
    // EDIT-only fields.
    val status: String = "",
    val chequeCollectStatus: String = "",
    val chequeBounceDate: String = "",
    val chequeReturnReason: String = "",

    val isSaving: Boolean = false,
    val saveError: String? = null,
    val savedMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    val isEdit: Boolean get() = paymentId != null
    val isCheque: Boolean get() = paymentMode == MODE_CHEQUE
    val isBankTransfer: Boolean get() = paymentMode == MODE_BANK_TRANSFER
    val isBounced: Boolean
        get() = chequeCollectStatus == "BOUNCED" || chequeCollectStatus == "CANCELLED"

    val canSave: Boolean
        get() {
            if (isSaving || isLoading) return false
            if (!isEdit && selectedSale == null) return false
            if (isEdit && editRow?.bookingId == null) return false
            if (paymentDate.isBlank() || paymentMode.isBlank() || paymentType.isBlank()) return false
            if ((amount.toDoubleOrNull() ?: 0.0) <= 0.0) return false
            // Cheque No (reference_no) and Bank Name are required for a cheque.
            if (isCheque && (referenceNo.isBlank() || bankName.isBlank())) return false
            if ((isCheque || isBankTransfer) && coal4Id.isBlank()) return false
            if (isEdit && isCheque && isBounced &&
                (chequeBounceDate.isBlank() || chequeReturnReason.isBlank())
            ) return false
            return true
        }
}

class UnitPaymentViewModel(
    private val paymentId: String?,
    private val repository: UnitSalePaymentRepository,
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UnitPaymentUiState(paymentId = paymentId))
    val uiState: StateFlow<UnitPaymentUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** Initial load — bank accounts, the default date (add) or the row (edit). */
    fun load() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            when (val accounts = repository.getBankAccounts()) {
                is Resource.Success -> _uiState.update { it.copy(bankAccounts = accounts.data) }
                is Resource.Error -> {
                    // Non-fatal: the dropdown just stays empty; a cheque save
                    // then blocks on the required account, which is honest.
                    if (accounts.isUnauthorized) {
                        _uiState.update { it.copy(sessionExpired = true) }
                    }
                }
                Resource.Loading -> Unit
            }

            if (paymentId == null) {
                // ADD: default the payment date to the branch's transaction date.
                val fallback = SimpleDate.today().toApi()
                val defaultDate = (reportRepository.getBranches() as? Resource.Success)
                    ?.data?.transactionDate?.toApi() ?: fallback
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        defaultDate = defaultDate,
                        paymentDate = it.paymentDate.ifBlank { defaultDate },
                    )
                }
                return@launch
            }

            when (val row = repository.getPayment(paymentId)) {
                is Resource.Success -> _uiState.update { it.prefilledFrom(row.data) }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = row.message,
                        sessionExpired = it.sessionExpired || row.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    private fun UnitPaymentUiState.prefilledFrom(row: UnitPaymentRow) = copy(
        isLoading = false,
        loadError = null,
        editRow = row,
        paymentDate = row.paymentDate,
        paymentMode = row.paymentMode.ifBlank { "CASH" },
        paymentType = row.paymentType.ifBlank { "INSTALLMENT" },
        amount = row.amount,
        receiptNo = row.receiptNo,
        referenceNo = row.referenceNo,
        bankName = row.bankName,
        branchName = row.branchName,
        coal4Id = row.coal4Id,
        chequeDepositDueDate = row.chequeDepositDueDate,
        chequeCollectDate = row.chequeCollectDate,
        note = row.note,
        status = row.status,
        // NOT_APPLICABLE is the server's "no cheque" filler, not a choice.
        chequeCollectStatus = row.chequeCollectStatus.takeIf { it != "NOT_APPLICABLE" }.orEmpty(),
        chequeBounceDate = row.chequeBounceDate,
        chequeReturnReason = row.chequeReturnReason,
    )

    // ---- Unit Sale picker (ADD mode) ----

    fun onSaleQueryChanged(query: String) = _uiState.update { it.copy(saleQuery = query) }

    fun loadSales() {
        _uiState.update { it.copy(isLoadingSales = true, saveError = null) }
        viewModelScope.launch {
            when (val result = repository.searchSales(_uiState.value.saleQuery.trim())) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoadingSales = false, saleOptions = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoadingSales = false,
                        saveError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onSaleSelected(sale: UnitSaleOption) {
        _uiState.update {
            it.copy(
                selectedSale = sale,
                summary = null,
                isLoadingSummary = true,
                // Prefill from the ddl row now; the summary refines it below.
                amount = sale.dueAmount.takeIf { due -> due > 0.0 }?.toAmountText()
                    ?: it.amount,
            )
        }
        viewModelScope.launch {
            when (val result = repository.getSummary(sale.id)) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoadingSummary = false,
                        summary = result.data,
                        amount = result.data.dueAmount.takeIf { due -> due > 0.0 }
                            ?.toAmountText() ?: it.amount,
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoadingSummary = false,
                        saveError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    // ---- Field setters ----

    fun onPaymentDateChanged(value: String) = _uiState.update { it.copy(paymentDate = value) }
    fun onPaymentTypeChanged(value: String) = _uiState.update { it.copy(paymentType = value) }
    fun onAmountChanged(value: String) = _uiState.update { it.copy(amount = value) }
    fun onReceiptNoChanged(value: String) = _uiState.update { it.copy(receiptNo = value) }
    fun onReferenceNoChanged(value: String) = _uiState.update { it.copy(referenceNo = value) }
    fun onBankNameChanged(value: String) = _uiState.update { it.copy(bankName = value) }
    fun onBranchNameChanged(value: String) = _uiState.update { it.copy(branchName = value) }
    fun onBankAccountChanged(value: String) = _uiState.update { it.copy(coal4Id = value) }
    fun onDepositDueDateChanged(value: String) = _uiState.update { it.copy(chequeDepositDueDate = value) }
    fun onCollectDateChanged(value: String) = _uiState.update { it.copy(chequeCollectDate = value) }
    fun onNoteChanged(value: String) = _uiState.update { it.copy(note = value) }
    fun onStatusChanged(value: String) = _uiState.update { it.copy(status = value) }
    fun onBounceDateChanged(value: String) = _uiState.update { it.copy(chequeBounceDate = value) }
    fun onReturnReasonChanged(value: String) = _uiState.update { it.copy(chequeReturnReason = value) }

    /**
     * The web's mode-switch cleanups, ported: leaving CHEQUE clears every
     * cheque field; leaving CHEQUE/BANK_TRANSFER clears the received account.
     */
    fun onPaymentModeChanged(mode: String) = _uiState.update { state ->
        var next = state.copy(paymentMode = mode)
        if (mode != MODE_CHEQUE) {
            next = next.copy(
                chequeCollectStatus = "",
                chequeDepositDueDate = "",
                chequeCollectDate = "",
                chequeBounceDate = "",
                chequeReturnReason = "",
            )
        }
        if (mode != MODE_CHEQUE && mode != MODE_BANK_TRANSFER) {
            next = next.copy(coal4Id = "")
        }
        next
    }

    /** Leaving BOUNCED/CANCELLED clears the bounce date and return reason. */
    fun onChequeStatusChanged(value: String) = _uiState.update { state ->
        val bounced = value == "BOUNCED" || value == "CANCELLED"
        state.copy(
            chequeCollectStatus = value,
            chequeBounceDate = if (bounced) state.chequeBounceDate else "",
            chequeReturnReason = if (bounced) state.chequeReturnReason else "",
        )
    }

    // ---- Actions ----

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        val bookingId = (if (state.isEdit) state.editRow?.bookingId else state.selectedSale?.id)
            ?: return
        val amount = state.amount.toDoubleOrNull() ?: return

        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            // Reference/bank/branch belong to cheque and bank-transfer payments;
            // in ADD they are hidden for other modes, so don't post stale text.
            val bankish = state.isCheque || state.isBankTransfer
            val submit = UnitPaymentSubmit(
                bookingId = bookingId,
                receiptNo = state.receiptNo,
                paymentDate = state.paymentDate,
                amount = amount,
                paymentType = state.paymentType,
                paymentMode = state.paymentMode,
                referenceNo = if (state.isEdit || bankish) state.referenceNo else null,
                bankName = if (state.isEdit || bankish) state.bankName else null,
                branchName = if (state.isEdit || bankish) state.branchName else null,
                coal4Id = if (bankish) state.coal4Id.toLongOrNull() else null,
                chequeDepositDueDate = if (state.isCheque) state.chequeDepositDueDate else null,
                chequeCollectDate = if (state.isCheque) state.chequeCollectDate else null,
                note = state.note,
                id = state.editRow?.id,
                branchId = state.editRow?.branchId,
                chequeCollectStatus = if (state.isCheque) state.chequeCollectStatus else null,
                chequeBounceDate = if (state.isCheque) state.chequeBounceDate else null,
                chequeReturnReason = if (state.isCheque) state.chequeReturnReason else null,
                status = state.status,
            )
            val result = if (state.isEdit) repository.update(submit) else repository.create(submit)
            when (result) {
                is Resource.Success -> _uiState.update {
                    if (it.isEdit) {
                        it.copy(isSaving = false, savedMessage = result.data)
                    } else {
                        // ADD: confirm, clear, and stay for the next entry.
                        it.cleared().copy(savedMessage = result.data)
                    }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    /** ADD's Reset: back to a blank form (options and default date kept). */
    fun reset() = _uiState.update { it.cleared() }

    /** EDIT's Reload: refetch the row and prefill again. */
    fun reload() = load()

    private fun UnitPaymentUiState.cleared() = UnitPaymentUiState(
        paymentId = paymentId,
        isLoading = false,
        bankAccounts = bankAccounts,
        defaultDate = defaultDate,
        paymentDate = defaultDate,
    )

    fun onSavedMessageShown() = _uiState.update { it.copy(savedMessage = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context, paymentId: String?) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                UnitPaymentViewModel(
                    paymentId = paymentId,
                    repository = ServiceLocator.provideUnitSalePaymentRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                )
            }
        }
    }
}

/**
 * The Real Estate Check Register's payment entry/edit form — the web's unit
 * sale payment form, ported. A null [paymentId] is a new entry; otherwise the
 * row is fetched and prefilled for editing.
 *
 * On success an edit pops back with the confirmation for the list to show; a
 * create confirms in place and resets, ready for the next cheque in the stack.
 */
@Composable
fun UnitPaymentScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    paymentId: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: UnitPaymentViewModel = viewModel(
        key = paymentId ?: "add",
        factory = UnitPaymentViewModel.provideFactory(context, paymentId),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    if (state.sessionExpired) {
        LaunchedEffect(Unit) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    LaunchedEffect(state.savedMessage) {
        val message = state.savedMessage ?: return@LaunchedEffect
        if (state.isEdit) {
            // Hand the confirmation to the list, which reloads and shows it.
            navController.previousBackStackEntry
                ?.savedStateHandle
                ?.set(Routes.CREATED_MESSAGE, message)
            navController.popBackStack()
        } else {
            snackbarHostState.showSnackbar(message)
            viewModel.onSavedMessageShown()
        }
    }

    AuthenticatedShell(
        title = if (state.isEdit) "Edit Unit Payment" else "Add Unit Payment",
        currentRoute = Routes.REAL_ESTATE,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
                }

                state.loadError != null -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        state.loadError.orEmpty(),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    LinkButton(text = "Retry", onClick = viewModel::load)
                }

                else -> PaymentForm(
                    state = state,
                    context = context,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun PaymentForm(
    state: UnitPaymentUiState,
    context: Context,
    viewModel: UnitPaymentViewModel,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!state.isEdit) {
                    SalePicker(state, viewModel)
                } else {
                    state.editRow?.let { row ->
                        SaleHeaderCards(
                            unitLabel = row.unitLabel,
                            parkingLabel = row.parkingLabel,
                            customerName = row.customerName,
                            customerMobile = row.customerMobile,
                            dueAmount = null,
                        )
                    }
                }

                DateField(
                    label = "Payment Date *",
                    value = state.paymentDate,
                    context = context,
                    onPicked = viewModel::onPaymentDateChanged,
                )

                AppSelectDropdown(
                    label = "Payment Mode *",
                    options = PaymentModes,
                    selected = PaymentModes.firstOrNull { it.id == state.paymentMode },
                    onSelected = { viewModel.onPaymentModeChanged(it.id) },
                )

                AppSelectDropdown(
                    label = "Payment For *",
                    options = PaymentTypes,
                    selected = PaymentTypes.firstOrNull { it.id == state.paymentType },
                    onSelected = { viewModel.onPaymentTypeChanged(it.id) },
                )

                AppTextField(
                    value = state.amount,
                    onValueChange = viewModel::onAmountChanged,
                    label = "0",
                    caption = "Amount *",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.fillMaxWidth(),
                )

                AppTextField(
                    value = state.receiptNo,
                    onValueChange = viewModel::onReceiptNoChanged,
                    label = "Auto when blank",
                    caption = "Receipt No",
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.isCheque) {
                    ChequeFields(state, context, viewModel)
                } else if (state.isBankTransfer) {
                    BankTransferFields(state, viewModel)
                } else if (state.isEdit) {
                    // The web keeps these visible on every edit.
                    BankNameFields(state, viewModel)
                }

                if (state.isEdit) {
                    AppSelectDropdown(
                        label = "Entry Status",
                        options = EntryStatuses,
                        selected = EntryStatuses.firstOrNull { it.id == state.status },
                        onSelected = { viewModel.onStatusChanged(it.id) },
                    )
                }

                AppTextField(
                    value = state.note,
                    onValueChange = viewModel::onNoteChanged,
                    label = "Note",
                    caption = "Note",
                    multiline = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryButton(
                        text = if (state.isEdit) "Update" else "Save",
                        onClick = viewModel::save,
                        enabled = state.canSave,
                        isLoading = state.isSaving,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = if (state.isEdit) "Reload" else "Reset",
                        onClick = if (state.isEdit) viewModel::reload else viewModel::reset,
                        enabled = !state.isSaving,
                        modifier = Modifier.weight(1f),
                    )
                }
                SecondaryButton(
                    text = "Back",
                    onClick = onBack,
                    enabled = !state.isSaving,
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    modifier = Modifier.fillMaxWidth(),
                )

                state.saveError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/** ADD mode's sale search box + Load button + dropdown + summary header. */
@Composable
private fun SalePicker(
    state: UnitPaymentUiState,
    viewModel: UnitPaymentViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            AppTextField(
                value = state.saleQuery,
                onValueChange = viewModel::onSaleQueryChanged,
                label = "Customer / receipt…",
                caption = "Search Unit Sale",
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = "Load",
                onClick = viewModel::loadSales,
                isLoading = state.isLoadingSales,
            )
        }

        AppSelectDropdown(
            label = "Unit Sale *",
            options = state.saleOptions.map { SelectorOption(it.id.toString(), it.label) },
            selected = state.selectedSale?.let { SelectorOption(it.id.toString(), it.label) },
            onSelected = { option ->
                state.saleOptions.firstOrNull { it.id.toString() == option.id }
                    ?.let(viewModel::onSaleSelected)
            },
            placeholder = if (state.saleOptions.isEmpty()) "Tap Load to fetch sales" else "Select sale",
        )

        if (state.isLoadingSummary) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "  Loading sale summary…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        state.summary?.let { summary ->
            SaleHeaderCards(
                unitLabel = summary.unitLabel,
                parkingLabel = summary.parkingLabel,
                customerName = summary.customerName,
                customerMobile = summary.customerMobile,
                dueAmount = summary.dueAmount,
            )
        }
    }
}

/** The cheque-only block: number, bank, dates, status and the received account. */
@Composable
private fun ChequeFields(
    state: UnitPaymentUiState,
    context: Context,
    viewModel: UnitPaymentViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppTextField(
            value = state.referenceNo,
            onValueChange = viewModel::onReferenceNoChanged,
            label = "Cheque number",
            caption = "Cheque No *",
            modifier = Modifier.fillMaxWidth(),
        )
        AppTextField(
            value = state.bankName,
            onValueChange = viewModel::onBankNameChanged,
            label = "Bank name",
            caption = "Bank Name *",
            modifier = Modifier.fillMaxWidth(),
        )
        AppTextField(
            value = state.branchName,
            onValueChange = viewModel::onBranchNameChanged,
            label = "Branch name",
            caption = "Branch Name",
            modifier = Modifier.fillMaxWidth(),
        )
        DateField(
            label = "Cheque Deposit Due Date",
            value = state.chequeDepositDueDate,
            context = context,
            onPicked = viewModel::onDepositDueDateChanged,
        )
        DateField(
            label = "Cheque Collect Date",
            value = state.chequeCollectDate,
            context = context,
            onPicked = viewModel::onCollectDateChanged,
        )
        BankAccountDropdown(state, viewModel)

        if (state.isEdit) {
            AppSelectDropdown(
                label = "Cheque Status",
                options = ChequeStatuses,
                selected = ChequeStatuses.firstOrNull { it.id == state.chequeCollectStatus },
                onSelected = { viewModel.onChequeStatusChanged(it.id) },
                placeholder = "Pending",
            )
            if (state.isBounced) {
                DateField(
                    label = "Cheque Bounce Date *",
                    value = state.chequeBounceDate,
                    context = context,
                    onPicked = viewModel::onBounceDateChanged,
                )
                AppTextField(
                    value = state.chequeReturnReason,
                    onValueChange = viewModel::onReturnReasonChanged,
                    label = "Why the cheque came back",
                    caption = "Cheque Return Reason *",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * The bank-transfer block. The received-account dropdown shows here too —
 * fixing the web form's bug of hiding it for bank transfers even though the
 * server requires it.
 */
@Composable
private fun BankTransferFields(
    state: UnitPaymentUiState,
    viewModel: UnitPaymentViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppTextField(
            value = state.referenceNo,
            onValueChange = viewModel::onReferenceNoChanged,
            label = "Transfer reference",
            caption = "Reference No",
            modifier = Modifier.fillMaxWidth(),
        )
        BankNameFields(state, viewModel)
        BankAccountDropdown(state, viewModel)
    }
}

@Composable
private fun BankNameFields(
    state: UnitPaymentUiState,
    viewModel: UnitPaymentViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppTextField(
            value = state.bankName,
            onValueChange = viewModel::onBankNameChanged,
            label = "Bank name",
            caption = "Bank Name",
            modifier = Modifier.fillMaxWidth(),
        )
        AppTextField(
            value = state.branchName,
            onValueChange = viewModel::onBranchNameChanged,
            label = "Branch name",
            caption = "Branch Name",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BankAccountDropdown(
    state: UnitPaymentUiState,
    viewModel: UnitPaymentViewModel,
) {
    AppSelectDropdown(
        label = "Bank Received Account *",
        options = state.bankAccounts,
        selected = state.bankAccounts.firstOrNull { it.id == state.coal4Id },
        onSelected = { viewModel.onBankAccountChanged(it.id) },
        placeholder = if (state.bankAccounts.isEmpty()) "No bank accounts loaded" else "Select account",
    )
}

/** The read-only Booking / Customer / Due header shown once a sale is known. */
@Composable
private fun SaleHeaderCards(
    unitLabel: String,
    parkingLabel: String,
    customerName: String,
    customerMobile: String,
    dueAmount: Double?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryTile(modifier = Modifier.weight(1f)) {
                HeaderCaption("Booking")
                HeaderValue("Unit: ${unitLabel.orDash()}")
                HeaderValue("Parking: ${parkingLabel.orDash()}")
            }
            SummaryTile(modifier = Modifier.weight(1f)) {
                HeaderCaption("Customer")
                HeaderValue(customerName.orDash())
                HeaderValue(customerMobile.orDash())
            }
        }
        if (dueAmount != null) {
            SummaryTileRow(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Due Amount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    dueAmount.toAmountDisplay(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun HeaderCaption(text: String) = Text(
    text,
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
private fun HeaderValue(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodySmall,
    fontWeight = FontWeight.Medium,
)

/** A `yyyy-MM-dd` field shown as dd/MM/yyyy, picked with the platform dialog. */
@Composable
private fun DateField(
    label: String,
    value: String,
    context: Context,
    onPicked: (String) -> Unit,
) {
    PickerField(
        label = label,
        value = value.toDisplayDate(),
        placeholder = "dd/mm/yyyy",
        trailingIcon = Icons.Filled.DateRange,
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            val calendar = Calendar.getInstance()
            Regex("""^(\d{4})-(\d{2})-(\d{2})$""").find(value)?.let { match ->
                val (year, month, day) = match.destructured
                calendar.set(year.toInt(), month.toInt() - 1, day.toInt())
            }
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    onPicked(
                        String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth),
                    )
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH),
            ).show()
        },
    )
}

// ---- Small formatting helpers ----

/** "BANK_TRANSFER" → "Bank Transfer". */
private fun String.prettyEnum(): String = split('_').joinToString(" ") { word ->
    word.lowercase(Locale.US).replaceFirstChar { it.uppercase(Locale.US) }
}

/** "2026-01-05" → "05/01/2026" for display; blank stays blank. */
private fun String.toDisplayDate(): String {
    val match = Regex("""^(\d{4})-(\d{2})-(\d{2})$""").find(this) ?: return this
    val (year, month, day) = match.destructured
    return "$day/$month/$year"
}

/** 15000.0 → "15000" for an editable amount field. */
private fun Double.toAmountText(): String =
    if (this == toLong().toDouble()) toLong().toString() else toString()

/** 15000.0 → "15,000"; keeps two decimals when fractional. */
private fun Double.toAmountDisplay(): String =
    if (this == toLong().toDouble()) String.format(Locale.US, "%,d", toLong())
    else String.format(Locale.US, "%,.2f", this)

private fun String.orDash(): String = ifBlank { "-" }
