package com.example.cashbookbd.ui.transaction

import android.content.Context
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.CashVoucherLine
import com.example.cashbookbd.data.repository.InvoiceRepository
import com.example.cashbookbd.data.repository.LedgerRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.data.repository.TransactionRepository
import com.example.cashbookbd.data.repository.TxnSelection
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.session.SessionManager
import com.example.cashbookbd.session.Settings
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SearchableLedgerDropdown
import com.example.cashbookbd.ui.components.SearchableSelectDropdown
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.invoice.model.OrderOption
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SelectorOption
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Cash Received / Cash Payment entry form — one screen for both, a port of
 * the web's `CashReceivedIndex`/`CashPaymentIndex`. The branch picks the
 * variant —
 *
 *  - Head Office (business type 1; for payments a `branch_types_id` of 1 also
 *    forces it): a branch dropdown; the batch posts wrapped in branch/project
 *    meta to `accounts/received|payment`.
 *  - Trading (business type 8): a "Select Order (Optional)" picker; each line
 *    carries the linked order; posts to `trading/cash/received|payment`.
 *  - Everything else (General, business type 4 included): the same trading
 *    endpoint, no extras.
 *
 * All variants are multi-line: "Add New" collects lines into the batch below
 * (with a running total) and Save posts them as one voucher.
 */

private const val HEAD_OFFICE_BUSINESS_TYPE_ID = 1
private const val HEAD_OFFICE_BRANCH_TYPES_ID = 1
private const val TRADING_CASH_BUSINESS_TYPE_ID = 8

enum class CashVoucherVariant { GENERAL, TRADING, HEAD_OFFICE }

/** One cash-voucher kind's fixed wiring (titles + the two store endpoints). */
data class CashVoucherSpec(
    val key: String,
    val title: String,
    val totalLabel: String,
    /** General/Trading store — takes the bare rows array. */
    val rowsEndpoint: String,
    /** Head Office store — takes the branch/project-wrapped payload. */
    val headOfficeEndpoint: String,
    /** Payment only: a head-office *branch type* also forces the HO variant. */
    val branchTypeForcesHeadOffice: Boolean,
)

/** The two cash voucher forms, keyed by their TransactionMenu keys. */
object CashVoucherForms {

    val received = CashVoucherSpec(
        key = "cashReceived",
        title = "Cash Received",
        totalLabel = "Received Total",
        rowsEndpoint = "trading/cash/received",
        headOfficeEndpoint = "accounts/received",
        branchTypeForcesHeadOffice = false,
    )

    val payment = CashVoucherSpec(
        key = "cashPayment",
        title = "Cash Payment",
        totalLabel = "Payment Total",
        rowsEndpoint = "trading/cash/payment",
        headOfficeEndpoint = "accounts/payment",
        branchTypeForcesHeadOffice = true,
    )

    fun byKey(key: String?): CashVoucherSpec? = when (key) {
        received.key -> received
        payment.key -> payment
        else -> null
    }
}

/** How the current branch's settings pick this form's variant (web index logic). */
fun cashVoucherVariant(spec: CashVoucherSpec, settings: Settings?): CashVoucherVariant = when {
    spec.branchTypeForcesHeadOffice &&
        settings?.branchTypesId == HEAD_OFFICE_BRANCH_TYPES_ID -> CashVoucherVariant.HEAD_OFFICE
    settings?.businessTypeId == HEAD_OFFICE_BUSINESS_TYPE_ID -> CashVoucherVariant.HEAD_OFFICE
    settings?.businessTypeId == TRADING_CASH_BUSINESS_TYPE_ID -> CashVoucherVariant.TRADING
    else -> CashVoucherVariant.GENERAL
}

data class CashVoucherUiState(
    val title: String = "",
    val totalLabel: String = "Total",
    val variant: CashVoucherVariant = CashVoucherVariant.GENERAL,

    // Entry fields. Account and order survive Add New, like the web form.
    val account: TxnSelection? = null,
    val order: OrderOption? = null,
    val remarks: String = "",
    val amount: String = "",
    val remarkSuggestions: List<String> = emptyList(),

    // Head Office: the branch the money moves against.
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val isBranchesLoading: Boolean = false,
    val branchesError: String? = null,

    val lines: List<CashVoucherLine> = emptyList(),

    val isSubmitting: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val sessionExpired: Boolean = false,
) {
    val total: Double get() = lines.sumOf { it.amount }

    val canAdd: Boolean
        get() = account != null && (amount.toDoubleOrNull() ?: 0.0) > 0.0

    val canSave: Boolean
        get() = lines.isNotEmpty() &&
            !isSubmitting &&
            (variant != CashVoucherVariant.HEAD_OFFICE || selectedBranch != null)
}

class CashVoucherViewModel(
    private val spec: CashVoucherSpec,
    private val transactionRepository: TransactionRepository,
    private val ledgerRepository: LedgerRepository,
    private val invoiceRepository: InvoiceRepository,
    private val reportRepository: ReportRepository,
    sessionManager: SessionManager,
) : ViewModel() {

    private val variant: CashVoucherVariant =
        cashVoucherVariant(spec, sessionManager.state.value.settings)

    /** The user's own branch — the Head Office variant's default selection. */
    private val homeBranchId: Long? = sessionManager.state.value.settings?.branchId

    /** Last order search results, so a picked option maps back to its full row. */
    private var orderCache: Map<String, OrderOption> = emptyMap()

    private var suggestionsJob: Job? = null

    private val _uiState = MutableStateFlow(
        CashVoucherUiState(title = spec.title, totalLabel = spec.totalLabel, variant = variant)
    )
    val uiState: StateFlow<CashVoucherUiState> = _uiState.asStateFlow()

    init {
        if (variant == CashVoucherVariant.HEAD_OFFICE) loadBranches()
    }

    private fun loadBranches() {
        _uiState.update { it.copy(isBranchesLoading = true, branchesError = null) }
        viewModelScope.launch {
            when (val result = reportRepository.getBranches()) {
                is Resource.Success -> _uiState.update { state ->
                    state.copy(
                        isBranchesLoading = false,
                        branches = result.data.branches,
                        selectedBranch = state.selectedBranch
                            ?: result.data.branches.firstOrNull { it.id == homeBranchId }
                            ?: result.data.branches.firstOrNull(),
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isBranchesLoading = false,
                        branchesError = result.message,
                        sessionExpired = result.isUnauthorized,
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

    fun onAccountSelected(account: TxnSelection) = _uiState.update { it.copy(account = account) }

    suspend fun searchAccounts(query: String): Resource<List<LedgerDropdownItem>> =
        ledgerRepository.searchLedgers(query)

    /** Order search (all order types, like the web's cash voucher picker). */
    suspend fun searchOrders(query: String): Resource<List<SelectorOption>> =
        when (val result = invoiceRepository.searchOrders(query)) {
            is Resource.Success -> {
                orderCache = result.data.associateBy { it.id }
                Resource.Success(
                    result.data.map { SelectorOption(id = it.id, label = it.orderNumber, sublabel = it.customerName) }
                )
            }
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }

    fun onOrderSelected(option: SelectorOption) {
        val order = orderCache[option.id] ?: return
        _uiState.update { it.copy(order = order) }
    }

    fun onRemarksChange(value: String) {
        _uiState.update { it.copy(remarks = value) }
        suggestionsJob?.cancel()
        if (value.isBlank()) {
            _uiState.update { it.copy(remarkSuggestions = emptyList()) }
            return
        }
        suggestionsJob = viewModelScope.launch {
            delay(250) // Debounce, matching the web's suggestion fetch.
            val suggestions = transactionRepository.fetchRemarkSuggestions(value)
            _uiState.update { state ->
                // Only apply if the field still holds the query we searched for.
                if (state.remarks == value) state.copy(remarkSuggestions = suggestions) else state
            }
        }
    }

    fun onSuggestionPicked(value: String) {
        suggestionsJob?.cancel()
        _uiState.update { it.copy(remarks = value, remarkSuggestions = emptyList()) }
    }

    fun onAmountChange(value: String) =
        _uiState.update { it.copy(amount = value.decimalOnly()) }

    /** Adds the entry as a pending line; account and order stay for the next. */
    fun addLine() {
        val state = _uiState.value
        val account = state.account ?: return
        val amount = state.amount.toDoubleOrNull() ?: return
        if (amount <= 0) return
        suggestionsJob?.cancel()
        _uiState.update {
            it.copy(
                lines = it.lines + CashVoucherLine(
                    account = account,
                    remarks = it.remarks.trim(),
                    amount = amount,
                    orderNumber = if (variant == CashVoucherVariant.TRADING) it.order?.id.orEmpty() else "",
                    orderText = if (variant == CashVoucherVariant.TRADING) it.order?.orderNumber.orEmpty() else "",
                ),
                remarks = "",
                amount = "",
                remarkSuggestions = emptyList(),
            )
        }
    }

    /** Loads a pending line back into the form (and removes it from the batch). */
    fun editLine(index: Int) {
        _uiState.update { state ->
            val line = state.lines.getOrNull(index) ?: return@update state
            state.copy(
                account = line.account,
                remarks = line.remarks,
                amount = line.amount.toPlainAmount(),
                order = when {
                    line.orderNumber.isBlank() -> state.order
                    else -> OrderOption(
                        id = line.orderNumber,
                        orderNumber = line.orderText,
                        customerName = "",
                        productName = "",
                        rate = null,
                        remainingQty = null,
                    )
                },
                lines = state.lines.filterIndexed { i, _ -> i != index },
            )
        }
    }

    fun removeLine(index: Int) {
        _uiState.update {
            if (index !in it.lines.indices) it
            else it.copy(lines = it.lines.filterIndexed { i, _ -> i != index })
        }
    }

    fun submit() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(isSubmitting = true, message = null, isError = false) }
        viewModelScope.launch {
            val result = when (state.variant) {
                CashVoucherVariant.HEAD_OFFICE -> {
                    val branch = state.selectedBranch!!
                    transactionRepository.submitHeadOfficeCashVoucher(
                        endpoint = spec.headOfficeEndpoint,
                        branch = TxnSelection(branch.id.toString(), branch.name),
                        lines = state.lines,
                    )
                }
                else -> transactionRepository.submitCashVoucherRows(
                    endpoint = spec.rowsEndpoint,
                    lines = state.lines,
                    trading = state.variant == CashVoucherVariant.TRADING,
                )
            }
            when (result) {
                is Resource.Success -> _uiState.update {
                    // Like the web reset: the batch clears; account/order/branch stay.
                    it.copy(
                        isSubmitting = false,
                        message = result.data,
                        isError = false,
                        lines = emptyList(),
                        remarks = "",
                        amount = "",
                        remarkSuggestions = emptyList(),
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        message = result.message,
                        isError = true,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    private fun String.decimalOnly(): String =
        filterIndexed { i, c -> c.isDigit() || (c == '.' && !take(i).contains('.')) }

    /** "1200.0" → "1200", "1200.5" stays — for refilling the amount field. */
    private fun Double.toPlainAmount(): String =
        if (this % 1.0 == 0.0) toLong().toString() else toString()

    companion object {
        fun provideFactory(context: Context, spec: CashVoucherSpec) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                CashVoucherViewModel(
                    spec = spec,
                    transactionRepository = ServiceLocator.provideTransactionRepository(appContext),
                    ledgerRepository = ServiceLocator.provideLedgerRepository(appContext),
                    invoiceRepository = ServiceLocator.provideInvoiceRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                    sessionManager = ServiceLocator.provideSessionManager(appContext),
                )
            }
        }
    }
}

@Composable
fun CashVoucherScreen(
    spec: CashVoucherSpec,
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CashVoucherViewModel = viewModel(
        key = spec.key,
        factory = CashVoucherViewModel.provideFactory(LocalContext.current, spec)
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
        title = state.title,
        currentRoute = Routes.TRANSACTIONS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.variant == CashVoucherVariant.HEAD_OFFICE) {
                AppSelectDropdown(
                    label = "Select Branch",
                    options = state.branches.map { SelectorOption(id = it.id.toString(), label = it.name) },
                    selected = state.selectedBranch?.let { SelectorOption(id = it.id.toString(), label = it.name) },
                    onSelected = viewModel::onBranchSelected,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = if (state.isBranchesLoading) "Loading branches…" else "Select Branch",
                )
                state.branchesError?.let {
                    Text(it, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (state.variant == CashVoucherVariant.TRADING) {
                SearchableSelectDropdown(
                    selected = state.order?.let { SelectorOption(it.id, it.orderNumber, it.customerName) },
                    onSelected = viewModel::onOrderSelected,
                    search = viewModel::searchOrders,
                    label = "Select Order (Optional)",
                    placeholder = "Type 3+ chars to search…",
                    emptyText = "No order found",
                )
            }

            SearchableLedgerDropdown(
                selectedLedger = state.account?.let { LedgerDropdownItem(it.id.toIntOrNull() ?: 0, it.name, null) },
                onLedgerSelected = { viewModel.onAccountSelected(TxnSelection(it.id.toString(), it.name)) },
                searchLedgers = viewModel::searchAccounts,
                label = "Select Account",
            )

            AppTextField(
                value = state.remarks,
                onValueChange = viewModel::onRemarksChange,
                label = "Enter Remarks",
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.remarkSuggestions.isNotEmpty()) {
                RemarkSuggestions(
                    suggestions = state.remarkSuggestions,
                    onPicked = viewModel::onSuggestionPicked,
                )
            }

            AppTextField(
                value = state.amount,
                onValueChange = viewModel::onAmountChange,
                label = "Amount (Tk.)",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton(
                    text = "Add New",
                    onClick = viewModel::addLine,
                    enabled = state.canAdd,
                    icon = Icons.Filled.Add,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = "Save",
                    onClick = viewModel::submit,
                    enabled = state.canSave,
                    isLoading = state.isSubmitting,
                    modifier = Modifier.weight(1f),
                )
            }

            state.message?.let { message ->
                Text(
                    text = message,
                    color = if (state.isError) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.lines.isNotEmpty()) {
                VoucherLinesList(
                    lines = state.lines,
                    totalLabel = state.totalLabel,
                    onEdit = viewModel::editLine,
                    onRemove = viewModel::removeLine,
                    total = state.total,
                )
            }
        }
    }
}

/** Server remark suggestions under the field; tapping one fills it in. */
@Composable
private fun RemarkSuggestions(suggestions: List<String>, onPicked: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            suggestions.take(5).forEachIndexed { index, suggestion ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPicked(suggestion) }
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}

/** The pending batch: one row per line, with edit/remove, and the running total. */
@Composable
private fun VoucherLinesList(
    lines: List<CashVoucherLine>,
    totalLabel: String,
    onEdit: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    total: Double,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            lines.forEachIndexed { index, line ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(line.account.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        if (line.remarks.isNotBlank()) {
                            Text(
                                text = line.remarks,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                        if (line.orderText.isNotBlank()) {
                            Text(
                                text = "Order: ${line.orderText}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text = AmountFormat.format(line.amount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(onClick = { onEdit(index) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit line", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { onRemove(index) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove line", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(totalLabel, fontWeight = FontWeight.Bold)
                Text(AmountFormat.format(total), fontWeight = FontWeight.Bold)
            }
        }
    }
}
