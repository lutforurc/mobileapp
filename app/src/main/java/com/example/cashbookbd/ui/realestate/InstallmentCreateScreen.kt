package com.example.cashbookbd.ui.realestate

import com.example.cashbookbd.ui.theme.PillShape
import com.example.cashbookbd.ui.theme.muted
import com.example.cashbookbd.ui.theme.AppFontWeight
import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
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
import com.example.cashbookbd.data.repository.FirstRowEarlyPayload
import com.example.cashbookbd.data.repository.RealEstateSalesRepository
import com.example.cashbookbd.data.repository.SaleOption
import com.example.cashbookbd.data.repository.SaleSummary
import com.example.cashbookbd.data.repository.SavedInstallmentRow
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.InstallmentStatusPill
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.components.SummaryTile
import com.example.cashbookbd.ui.reports.PickerField
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportFooterCell
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/**
 * Real Estate → Installment Create — a port of the web page: pick a unit sale,
 * see its money summary (booking / down payment / installment base), define the
 * schedule (amount, count, start date, optional early-payment discount), preview
 * the computed rows, and create. When a schedule already exists for the sale the
 * form locks ("Already Created") and the saved rows render with a per-row Edit
 * that rebalances the following installment against the installment base.
 */

/** Max installments the form accepts, matching the web's validation. */
private const val MAX_INSTALLMENTS = 240

/** Amount differences below this are treated as equal (the web's epsilon). */
private const val BALANCE_EPSILON = 0.01

/** One computed row of the pre-create preview table. */
data class InstallmentPreviewRow(
    val installmentNo: Int,
    val dueDate: SimpleDate,
    val amount: Double,
)

data class InstallmentCreateUiState(
    // Sale picker
    val saleQuery: String = "",
    val saleOptions: List<SaleOption> = emptyList(),
    val isSalesLoading: Boolean = false,
    val salesError: String? = null,
    val selectedSale: SaleOption? = null,

    // Summary
    val summary: SaleSummary? = null,
    val isSummaryLoading: Boolean = false,
    val summaryError: String? = null,

    // Schedule form
    val amountInput: String = "",
    val countInput: String = "",
    val startDate: SimpleDate = SimpleDate.today().plusMonths(1),
    val earlyPayment: Boolean = false,
    val earlyDiscountInput: String = "",
    val earlyPaymentDate: SimpleDate? = null,
    val isCreating: Boolean = false,

    // Saved schedule
    val savedRows: List<SavedInstallmentRow> = emptyList(),
    val isSavedLoading: Boolean = false,

    // Row-edit dialog
    val editTarget: SavedInstallmentRow? = null,
    val editDate: SimpleDate? = null,
    val editAmountInput: String = "",
    val editEarlyDate: SimpleDate? = null,
    val editEarlyDiscountInput: String = "",
    val isSavingEdit: Boolean = false,

    val message: String? = null,
    val isError: Boolean = false,
    val sessionExpired: Boolean = false,
) {
    val hasSavedSchedule: Boolean get() = savedRows.isNotEmpty()

    /**
     * The exact client math the web previews with: required = ⌈base ∕ amount⌉,
     * count = min(entered, required); the last row absorbs the remainder; rows
     * that fall to 0 are dropped; due dates step one month from the start date.
     */
    val previewRows: List<InstallmentPreviewRow>
        get() {
            val base = summary?.scheduleBase ?: return emptyList()
            val amount = amountInput.toDoubleOrNull() ?: return emptyList()
            val n = countInput.toIntOrNull() ?: return emptyList()
            if (base <= 0.0 || amount <= 0.0 || n <= 0) return emptyList()
            val requiredCount = ceil(base / amount).toInt()
            val count = minOf(n, requiredCount)
            return (0 until count).mapNotNull { i ->
                val isLast = i == count - 1
                val rowAmount = if (isLast) max(base - amount * i, 0.0) else amount
                if (rowAmount > 0.0) {
                    InstallmentPreviewRow(
                        installmentNo = i + 1,
                        dueDate = startDate.plusMonths(i),
                        amount = rowAmount,
                    )
                } else {
                    null
                }
            }
        }

    val canCreate: Boolean
        get() {
            val amount = amountInput.toDoubleOrNull() ?: 0.0
            val count = countInput.toIntOrNull() ?: 0
            val discount = earlyDiscountInput.toDoubleOrNull() ?: 0.0
            val earlyOk = !earlyPayment || discount <= 0.0 || earlyPaymentDate != null
            return summary != null && !hasSavedSchedule && !isCreating && !isSummaryLoading &&
                amount > 0.0 && count in 1..MAX_INSTALLMENTS && earlyOk
        }
}

class InstallmentCreateViewModel(
    private val repository: RealEstateSalesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InstallmentCreateUiState())
    val uiState: StateFlow<InstallmentCreateUiState> = _uiState.asStateFlow()

    init {
        loadSales()
    }

    // ---- Sale picker ------------------------------------------------------

    fun onSaleQueryChange(value: String) = _uiState.update { it.copy(saleQuery = value) }

    fun loadSales() {
        if (_uiState.value.isSalesLoading) return
        _uiState.update { it.copy(isSalesLoading = true, salesError = null) }
        viewModelScope.launch {
            when (val result = repository.searchSales(_uiState.value.saleQuery)) {
                is Resource.Success -> _uiState.update {
                    it.copy(isSalesLoading = false, saleOptions = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSalesLoading = false,
                        salesError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onSaleSelected(option: SelectorOption) {
        val sale = _uiState.value.saleOptions.firstOrNull { it.id.toString() == option.id } ?: return
        _uiState.update {
            it.copy(
                selectedSale = sale,
                summary = null,
                summaryError = null,
                savedRows = emptyList(),
                message = null,
                isError = false,
            )
        }
        loadSummary(sale.id)
    }

    private fun loadSummary(saleId: Long) {
        _uiState.update { it.copy(isSummaryLoading = true, summaryError = null) }
        viewModelScope.launch {
            when (val result = repository.fetchSaleSummary(saleId)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSummaryLoading = false, summary = result.data) }
                    refreshSavedSchedule(result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSummaryLoading = false,
                        summaryError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    // ---- Saved schedule ---------------------------------------------------

    private fun refreshSavedSchedule(summary: SaleSummary) {
        _uiState.update { it.copy(isSavedLoading = true) }
        viewModelScope.launch {
            val result = repository.fetchSavedSchedule(
                customerId = summary.customerId,
                receiptNo = summary.receiptNo,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isSavedLoading = false, savedRows = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSavedLoading = false,
                        message = result.message,
                        isError = true,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    // ---- Schedule form ----------------------------------------------------

    fun onAmountChange(value: String) =
        _uiState.update { it.copy(amountInput = value.decimalOnly()) }

    fun onCountChange(value: String) =
        _uiState.update { it.copy(countInput = value.filter(Char::isDigit)) }

    fun onStartDateSelected(date: SimpleDate) = _uiState.update { it.copy(startDate = date) }

    fun onEarlyPaymentToggle(checked: Boolean) = _uiState.update {
        if (checked) {
            it.copy(earlyPayment = true)
        } else {
            it.copy(earlyPayment = false, earlyDiscountInput = "", earlyPaymentDate = null)
        }
    }

    fun onEarlyDiscountChange(value: String) =
        _uiState.update { it.copy(earlyDiscountInput = value.decimalOnly()) }

    fun onEarlyPaymentDateSelected(date: SimpleDate) =
        _uiState.update { it.copy(earlyPaymentDate = date) }

    fun create() {
        val state = _uiState.value
        val summary = state.summary ?: return
        val sale = state.selectedSale ?: return
        if (!state.canCreate) return
        val amount = state.amountInput.toDoubleOrNull() ?: return
        val count = state.countInput.toIntOrNull() ?: return
        val discount = if (state.earlyPayment) state.earlyDiscountInput.toDoubleOrNull() ?: 0.0 else 0.0
        if (state.earlyPayment && discount > 0.0 && state.earlyPaymentDate == null) {
            _uiState.update {
                it.copy(message = "Pick an early payment date for the discount.", isError = true)
            }
            return
        }
        _uiState.update { it.copy(isCreating = true, message = null, isError = false) }
        viewModelScope.launch {
            val result = repository.createSchedule(
                bookingId = sale.id,
                amount = amount,
                startDate = state.startDate.toApi(),
                numberOfInstallments = count,
                earlyPayment = state.earlyPayment,
                earlyDiscount = discount,
                earlyPaymentDate = if (state.earlyPayment) state.earlyPaymentDate?.toApi() else null,
            )
            when (result) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(isCreating = false, message = result.data.message, isError = false)
                    }
                    refreshSavedSchedule(summary)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isCreating = false,
                        message = result.message,
                        isError = true,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    // ---- Row edit ---------------------------------------------------------

    fun onEditRequested(row: SavedInstallmentRow) = _uiState.update {
        it.copy(
            editTarget = row,
            editDate = parseAnyDate(row.dueDate),
            editAmountInput = row.amount.toPlainAmount(),
            editEarlyDate = parseAnyDate(row.earlyPaymentDate),
            editEarlyDiscountInput =
                if (row.earlyPaymentDiscount > 0.0) row.earlyPaymentDiscount.toPlainAmount() else "",
            message = null,
            isError = false,
        )
    }

    fun onEditDismiss() = _uiState.update { it.copy(editTarget = null) }

    fun onEditDateSelected(date: SimpleDate) = _uiState.update { it.copy(editDate = date) }

    fun onEditAmountChange(value: String) =
        _uiState.update { it.copy(editAmountInput = value.decimalOnly()) }

    fun onEditEarlyDateSelected(date: SimpleDate) =
        _uiState.update { it.copy(editEarlyDate = date) }

    fun onEditEarlyDiscountChange(value: String) =
        _uiState.update { it.copy(editEarlyDiscountInput = value.decimalOnly()) }

    /**
     * Saves the row and rebalances the schedule against the installment base:
     * any amount difference is pushed onto the next installment (which must stay
     * above 0); with no following row the edit must not change the schedule
     * total — the web's balancing-create path is server-rejected.
     */
    fun saveEdit() {
        val state = _uiState.value
        val target = state.editTarget ?: return
        val summary = state.summary ?: return
        if (state.isSavingEdit) return

        val newAmount = state.editAmountInput.toDoubleOrNull()
        if (newAmount == null || newAmount <= 0.0) {
            _uiState.update { it.copy(message = "Enter an amount greater than 0.", isError = true) }
            return
        }
        val dueApi = (state.editDate ?: parseAnyDate(target.dueDate))?.toApi()
        if (dueApi == null) {
            _uiState.update { it.copy(message = "Pick a due date.", isError = true) }
            return
        }

        // Rebalancing (validated before any request goes out).
        var nextRowUpdate: Pair<SavedInstallmentRow, Double>? = null
        val amountChanged = abs(newAmount - target.amount) >= BALANCE_EPSILON
        if (amountChanged) {
            val withEdit = state.savedRows.map {
                if (it.installmentId == target.installmentId) it.copy(amount = newAmount) else it
            }
            val balance = summary.scheduleBase - withEdit.sumOf { it.amount }
            if (abs(balance) >= BALANCE_EPSILON) {
                val index = state.savedRows.indexOfFirst { it.installmentId == target.installmentId }
                val next = state.savedRows.getOrNull(index + 1)
                when {
                    next != null -> {
                        val nextAmount = next.amount + balance
                        if (nextAmount <= 0.0) {
                            _uiState.update {
                                it.copy(
                                    message = "Next installment amount must remain greater than 0",
                                    isError = true,
                                )
                            }
                            return
                        }
                        nextRowUpdate = next to nextAmount
                    }
                    balance < 0 -> {
                        _uiState.update {
                            it.copy(
                                message = "Installment total cannot be greater than Installment Base",
                                isError = true,
                            )
                        }
                        return
                    }
                    else -> {
                        _uiState.update {
                            it.copy(
                                message = "Cannot auto-balance — no following installment",
                                isError = true,
                            )
                        }
                        return
                    }
                }
            }
        }

        _uiState.update { it.copy(isSavingEdit = true, message = null, isError = false) }
        viewModelScope.launch {
            val early = if (target.installmentNo == 1) {
                FirstRowEarlyPayload(
                    earlyPaymentDate = state.editEarlyDate?.toApi(),
                    earlyDiscount = state.editEarlyDiscountInput.toDoubleOrNull() ?: 0.0,
                )
            } else {
                null
            }
            val first = repository.updateInstallment(
                installmentId = target.installmentId,
                dueDate = dueApi,
                amount = newAmount,
                firstRowEarly = early,
            )
            if (first is Resource.Error) {
                _uiState.update {
                    it.copy(
                        isSavingEdit = false,
                        message = first.message,
                        isError = true,
                        sessionExpired = it.sessionExpired || first.isUnauthorized,
                    )
                }
                return@launch
            }

            var balanceError: String? = null
            nextRowUpdate?.let { (next, nextAmount) ->
                val nextDue = parseAnyDate(next.dueDate)?.toApi() ?: next.dueDate
                val second = repository.updateInstallment(
                    installmentId = next.installmentId,
                    dueDate = nextDue,
                    amount = nextAmount,
                )
                if (second is Resource.Error) balanceError = second.message
            }

            _uiState.update {
                it.copy(
                    isSavingEdit = false,
                    editTarget = null,
                    message = balanceError ?: "Installment updated successfully.",
                    isError = balanceError != null,
                )
            }
            // The first update went through even if the balancing one failed, so
            // the list must be re-fetched either way.
            refreshSavedSchedule(summary)
        }
    }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    private fun String.decimalOnly(): String =
        filterIndexed { i, c -> c.isDigit() || (c == '.' && !take(i).contains('.')) }

    private fun Double.toPlainAmount(): String =
        if (this % 1.0 == 0.0) toLong().toString() else toString()

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                InstallmentCreateViewModel(
                    repository = ServiceLocator.provideRealEstateSalesRepository(appContext),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@Composable
fun InstallmentCreateScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InstallmentCreateViewModel = viewModel(
        factory = InstallmentCreateViewModel.provideFactory(LocalContext.current)
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
        title = "Installment Create",
        currentRoute = Routes.REAL_ESTATE,
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
            // ---- Sale picker ----------------------------------------------
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                AppTextField(
                    value = state.saleQuery,
                    onValueChange = viewModel::onSaleQueryChange,
                    label = "Type to search sales…",
                    caption = "Search Sale",
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = "Load",
                    onClick = viewModel::loadSales,
                    isLoading = state.isSalesLoading,
                )
            }
            state.salesError?.let {
                Text(it, color = onScreen, style = MaterialTheme.typography.bodySmall)
            }
            AppSelectDropdown(
                label = "Select Sale",
                options = state.saleOptions.map { SelectorOption(id = it.id.toString(), label = it.label) },
                selected = state.selectedSale?.let { SelectorOption(id = it.id.toString(), label = it.label) },
                onSelected = viewModel::onSaleSelected,
                modifier = Modifier.fillMaxWidth(),
                placeholder = if (state.isSalesLoading) "Loading sales…" else "Select Sale",
            )

            // ---- Summary --------------------------------------------------
            when {
                state.isSummaryLoading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = onScreen)
                }

                state.summaryError != null -> Text(
                    text = state.summaryError!!,
                    color = onScreen,
                    style = MaterialTheme.typography.bodyMedium,
                )

                state.summary != null -> SaleSummaryPanel(
                    summary = state.summary!!,
                    alreadyCreated = state.hasSavedSchedule,
                )

                else -> Text(
                    text = "Select a sale to build its installment schedule.",
                    color = onScreen.muted(),
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }

            // ---- Schedule form --------------------------------------------
            if (state.summary != null) {
                ScheduleForm(state = state, viewModel = viewModel, context = context)
            }

            state.message?.let { message ->
                Text(
                    text = message,
                    color = if (state.isError) onScreen else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ---- Preview --------------------------------------------------
            if (state.summary != null && !state.hasSavedSchedule && state.previewRows.isNotEmpty()) {
                Text(
                    text = "Preview",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = AppFontWeight.Bold,
                    color = onScreen,
                )
                ReportTable(
                    columns = previewColumns(state),
                    data = state.previewRows,
                    scrollable = false,
                )
            }

            // ---- Saved schedule -------------------------------------------
            if (state.isSavedLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = onScreen)
                }
            } else if (state.hasSavedSchedule) {
                Text(
                    text = "Saved Schedule",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = AppFontWeight.Bold,
                    color = onScreen,
                )
                ReportTable(
                    columns = savedColumns(viewModel),
                    data = state.savedRows,
                    footerRows = savedFooterRows(state.savedRows),
                    scrollable = false,
                )
            }
        }
    }

    EditInstallmentDialog(state = state, viewModel = viewModel, context = context)
}

// ---------------------------------------------------------------------------
// Summary panel
// ---------------------------------------------------------------------------

/** Read-only money panel for the picked sale, plus the "Already Created" badge. */
@Composable
private fun SaleSummaryPanel(summary: SaleSummary, alreadyCreated: Boolean) {
    SummaryTile(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Sale Summary",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (alreadyCreated) AlreadyCreatedBadge()
        }
        Spacer(Modifier.height(4.dp))
        SummaryLine(
            "Customer",
            listOf(summary.customerName, summary.customerMobile)
                .filter { it.isNotBlank() }
                .joinToString(" — ")
                .ifBlank { "-" },
        )
        SummaryLine(
            "Unit",
            listOf(summary.unitLabel, summary.parkingLabel)
                .filter { it.isNotBlank() }
                .joinToString(" / ")
                .ifBlank { "-" },
        )
        SummaryLine("Total Sale", AmountFormat.format(summary.totalAmount))
        SummaryLine("Booking Money", AmountFormat.format(summary.effectiveBookingAmount))
        SummaryLine("Down Payment", AmountFormat.format(summary.effectiveDownPayment))
        SummaryLine("Installment Base", AmountFormat.format(summary.scheduleBase), bold = true)
    }
}

@Composable
private fun SummaryLine(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) AppFontWeight.Bold else AppFontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun AlreadyCreatedBadge() {
    Text(
        text = "Already Created",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = AppFontWeight.Bold,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

// ---------------------------------------------------------------------------
// Schedule form
// ---------------------------------------------------------------------------

@Composable
private fun ScheduleForm(
    state: InstallmentCreateUiState,
    viewModel: InstallmentCreateViewModel,
    context: Context,
) {
    val onScreen = MaterialTheme.colorScheme.onBackground
    // A saved schedule locks the whole form; only the per-row Edit remains.
    val locked = state.hasSavedSchedule

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppTextField(
                value = state.amountInput,
                onValueChange = viewModel::onAmountChange,
                label = "Installment Amount",
                caption = "Installment Amount *",
                enabled = !locked,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
            AppTextField(
                value = state.countInput,
                onValueChange = viewModel::onCountChange,
                label = "Max $MAX_INSTALLMENTS",
                caption = "Installments No. *",
                enabled = !locked,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }

        PickerField(
            label = "Start Date",
            value = state.startDate.toDisplay(),
            trailingIcon = Icons.Filled.DateRange,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (!locked) {
                    showInstallmentDatePicker(context, state.startDate, viewModel::onStartDateSelected)
                }
            },
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = state.earlyPayment,
                onCheckedChange = viewModel::onEarlyPaymentToggle,
                enabled = !locked,
            )
            Text(
                text = "Early Payment",
                style = MaterialTheme.typography.bodyMedium,
                color = onScreen,
            )
        }

        if (state.earlyPayment) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(
                    value = state.earlyDiscountInput,
                    onValueChange = viewModel::onEarlyDiscountChange,
                    label = "0",
                    caption = "Early Discount",
                    enabled = !locked,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                PickerField(
                    label = "Early Payment Date",
                    value = state.earlyPaymentDate?.toDisplay() ?: "",
                    placeholder = "Pick date",
                    trailingIcon = Icons.Filled.DateRange,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (!locked) {
                            showInstallmentDatePicker(
                                context,
                                state.earlyPaymentDate ?: SimpleDate.today(),
                                viewModel::onEarlyPaymentDateSelected,
                            )
                        }
                    },
                )
            }
        }

        if (!locked) {
            PrimaryButton(
                text = "Create Schedule",
                onClick = viewModel::create,
                enabled = state.canCreate,
                isLoading = state.isCreating,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Tables
// ---------------------------------------------------------------------------

/** Inst No | Due Date | Amount | Early Pay Date | Early Discount. */
@Composable
private fun previewColumns(
    state: InstallmentCreateUiState,
): List<ReportColumn<InstallmentPreviewRow>> {
    val onScreen = MaterialTheme.colorScheme.onBackground
    val earlyDate = if (state.earlyPayment) state.earlyPaymentDate?.toDisplay() else null
    val earlyDiscount = if (state.earlyPayment) state.earlyDiscountInput.toDoubleOrNull() ?: 0.0 else 0.0
    return listOf(
        ReportColumn("Inst No", ReportColWidth.Fixed(64.dp), TextAlign.Center) { r, _ ->
            cellText(r.installmentNo.toString(), align = TextAlign.Center, color = onScreen)
        },
        ReportColumn("Due Date", ReportColWidth.Fixed(96.dp)) { r, _ ->
            cellText(r.dueDate.toDisplay(), color = onScreen)
        },
        ReportColumn("Amount", ReportColWidth.Fixed(104.dp), TextAlign.End) { r, _ ->
            cellText(AmountFormat.formatOrDash(r.amount), align = TextAlign.End, color = onScreen)
        },
        // The early-payment terms belong to the first installment only.
        ReportColumn("Early Pay Date", ReportColWidth.Fixed(110.dp)) { r, _ ->
            val value = if (r.installmentNo == 1) earlyDate ?: "-" else "-"
            cellText(value, color = onScreen)
        },
        ReportColumn("Early Discount", ReportColWidth.Fixed(110.dp), TextAlign.End) { r, _ ->
            val value = if (r.installmentNo == 1) AmountFormat.formatOrDash(earlyDiscount) else "-"
            cellText(value, align = TextAlign.End, color = onScreen)
        },
    )
}

/** Inst No | Due Date | Amount | Paid | Due | Status | Edit. */
@Composable
private fun savedColumns(
    viewModel: InstallmentCreateViewModel,
): List<ReportColumn<SavedInstallmentRow>> {
    val onScreen = MaterialTheme.colorScheme.onBackground
    return listOf(
        ReportColumn("Inst No", ReportColWidth.Fixed(60.dp), TextAlign.Center) { r, _ ->
            cellText(r.installmentNo.toString(), align = TextAlign.Center, color = onScreen)
        },
        ReportColumn("Due Date", ReportColWidth.Fixed(94.dp)) { r, _ ->
            cellText(r.dueDate.ifBlank { "-" }, color = onScreen)
        },
        ReportColumn("Amount", ReportColWidth.Fixed(100.dp), TextAlign.End) { r, _ ->
            cellText(AmountFormat.formatOrDash(r.amount), align = TextAlign.End, color = onScreen)
        },
        ReportColumn("Paid", ReportColWidth.Fixed(94.dp), TextAlign.End) { r, _ ->
            cellText(AmountFormat.formatOrDash(r.paidAmount), align = TextAlign.End, color = onScreen)
        },
        ReportColumn("Due", ReportColWidth.Fixed(94.dp), TextAlign.End) { r, _ ->
            cellText(AmountFormat.formatOrDash(r.dueAmount), align = TextAlign.End, color = onScreen)
        },
        ReportColumn("Status", ReportColWidth.Fixed(104.dp), TextAlign.Center) { r, _ ->
            if (r.status.isBlank()) {
                cellText("-", align = TextAlign.Center, color = onScreen)
            } else {
                ReportTableCell.Slot { InstallmentStatusPill(r.status) }
            }
        },
        ReportColumn("Action", ReportColWidth.Fixed(84.dp), TextAlign.Center) { r, _ ->
            ReportTableCell.Slot {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SecondaryButton(
                        text = "Edit",
                        onClick = { viewModel.onEditRequested(r) },
                        compact = true,
                    )
                }
            }
        },
    )
}

/** A bold Total row under the saved table (amount / paid / due). */
private fun savedFooterRows(rows: List<SavedInstallmentRow>): List<List<ReportFooterCell>> = listOf(
    listOf(
        ReportFooterCell(cellText("Total", bold = true), colSpan = 2),
        ReportFooterCell(
            cellText(AmountFormat.formatOrDash(rows.sumOf { it.amount }), align = TextAlign.End, bold = true)
        ),
        ReportFooterCell(
            cellText(AmountFormat.formatOrDash(rows.sumOf { it.paidAmount }), align = TextAlign.End, bold = true)
        ),
        ReportFooterCell(
            cellText(AmountFormat.formatOrDash(rows.sumOf { it.dueAmount }), align = TextAlign.End, bold = true)
        ),
        ReportFooterCell(ReportTableCell.Empty, colSpan = 2),
    ),
)

// ---------------------------------------------------------------------------
// Edit dialog
// ---------------------------------------------------------------------------

/** Inline due-date/amount edit; early-payment inputs appear for row 1 only. */
@Composable
private fun EditInstallmentDialog(
    state: InstallmentCreateUiState,
    viewModel: InstallmentCreateViewModel,
    context: Context,
) {
    val target = state.editTarget ?: return
    AlertDialog(
        onDismissRequest = viewModel::onEditDismiss,
        title = { Text("Edit Installment #${target.installmentNo}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PickerField(
                    label = "Due Date",
                    value = state.editDate?.toDisplay() ?: "",
                    placeholder = "Pick date",
                    trailingIcon = Icons.Filled.DateRange,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        showInstallmentDatePicker(
                            context,
                            state.editDate ?: SimpleDate.today(),
                            viewModel::onEditDateSelected,
                        )
                    },
                )
                AppTextField(
                    value = state.editAmountInput,
                    onValueChange = viewModel::onEditAmountChange,
                    label = "Amount",
                    caption = "Amount",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (target.installmentNo == 1) {
                    PickerField(
                        label = "Early Payment Date",
                        value = state.editEarlyDate?.toDisplay() ?: "",
                        placeholder = "Pick date",
                        trailingIcon = Icons.Filled.DateRange,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            showInstallmentDatePicker(
                                context,
                                state.editEarlyDate ?: SimpleDate.today(),
                                viewModel::onEditEarlyDateSelected,
                            )
                        },
                    )
                    AppTextField(
                        value = state.editEarlyDiscountInput,
                        onValueChange = viewModel::onEditEarlyDiscountChange,
                        label = "0",
                        caption = "Early Discount",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            if (state.isSavingEdit) {
                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            } else {
                LinkButton(text = "Save", onClick = viewModel::saveEdit)
            }
        },
        dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::onEditDismiss) },
    )
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** [SimpleDate] shifted by whole months (Calendar clamps the day when needed). */
private fun SimpleDate.plusMonths(months: Int): SimpleDate {
    val c = Calendar.getInstance().apply {
        clear()
        set(year, month - 1, day)
        add(Calendar.MONTH, months)
    }
    return SimpleDate(
        year = c.get(Calendar.YEAR),
        month = c.get(Calendar.MONTH) + 1,
        day = c.get(Calendar.DAY_OF_MONTH),
    )
}

/** Dates arrive as dd/MM/yyyy in reports and yyyy-MM-dd on the wire; try both. */
private fun parseAnyDate(value: String?): SimpleDate? =
    SimpleDate.fromDisplay(value) ?: SimpleDate.fromApi(value)

private fun showInstallmentDatePicker(
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
