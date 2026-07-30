package com.example.cashbookbd.ui.realestate

import com.example.cashbookbd.ui.theme.AppFontWeight
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.KeyboardType
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
import com.example.cashbookbd.data.repository.ChargeTypeOption
import com.example.cashbookbd.data.repository.LedgerRepository
import com.example.cashbookbd.data.repository.RealEstateOption
import com.example.cashbookbd.data.repository.UnitSaleLine
import com.example.cashbookbd.data.repository.UnitSaleRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SearchableLedgerDropdown
import com.example.cashbookbd.ui.components.SearchableSelectDropdown
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.components.SummaryTile
import com.example.cashbookbd.ui.reports.model.SelectorOption
import kotlin.math.abs
import kotlin.math.floor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Unit Sales — the port of the web's UnitSalePage pricing builder. Pick a
 * customer and a unit (which auto-prices a locked "Unit Price" line at
 * size × rate), optionally a parking, add custom charges (a "-" charge type
 * subtracts), enter the booking money and payment mode, then Save.
 *
 * ⚠️ Saving posts a REAL sale voucher + cash voucher via
 * `real-estate/unit/sales` — never submit as a test.
 */

private const val TYPE_UNIT_PRICE = "UNIT_PRICE"
private const val TYPE_PARKING = "PARKING"
private const val TYPE_CUSTOM = "CUSTOM"

/** The web's UNIT_SALE_PAYMENT_MODES; CASH is the default. */
private val PAYMENT_MODES = listOf(
    SelectorOption("CASH", "Cash"),
    SelectorOption("BKASH", "bKash"),
    SelectorOption("NAGAD", "Nagad"),
    SelectorOption("ROCKET", "Rocket"),
    SelectorOption("UPAY", "Upay"),
    SelectorOption("BANK_TRANSFER", "Bank Transfer"),
    SelectorOption("CHEQUE", "Cheque"),
    SelectorOption("POS_CARD", "POS Card"),
    SelectorOption("MOBILE_BANKING", "Mobile Banking"),
    SelectorOption("OTHERS", "Others"),
)

data class UnitSaleUiState(
    val customer: LedgerDropdownItem? = null,
    val unit: RealEstateOption? = null,
    val parking: RealEstateOption? = null,

    val chargeTypes: List<ChargeTypeOption> = emptyList(),
    val selectedChargeType: ChargeTypeOption? = null,
    val chargeAmount: String = "",

    val paymentMode: SelectorOption = PAYMENT_MODES.first(),
    val receiptNo: String = "",
    val bookingAmount: String = "",
    val referenceNo: String = "",
    val bankName: String = "",
    val branchName: String = "",

    val lines: List<UnitSaleLine> = emptyList(),
    val editingLineId: Long? = null,
    val editDraft: String = "",
    val confirmParkingRemoval: Boolean = false,

    val isSubmitting: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val sessionExpired: Boolean = false,
) {
    /** Σ(effect "-" subtracts) — the web's calculateTotal. */
    val total: Double get() = lines.sumOf { it.signedAmount }

    /** Booking money floored to an INT (the DB column is an int), never negative. */
    val bookingAmt: Int
        get() {
            val n = bookingAmount.toDoubleOrNull() ?: 0.0
            return if (n < 0) 0 else floor(n).toInt()
        }

    val due: Double get() = (total - bookingAmt).coerceAtLeast(0.0)

    val showBankFields: Boolean
        get() = paymentMode.id == "CHEQUE" || paymentMode.id == "BANK_TRANSFER"

    val canAddCharge: Boolean
        get() = selectedChargeType != null && (chargeAmount.toDoubleOrNull() ?: 0.0) > 0.0

    val bookingExceedsTotal: Boolean get() = lines.isNotEmpty() && bookingAmt > total

    val canSubmit: Boolean
        get() = customer != null &&
            unit != null &&
            lines.isNotEmpty() &&
            bookingAmt > 0 &&
            !bookingExceedsTotal &&
            !isSubmitting
}

class UnitSaleViewModel(
    private val unitSaleRepository: UnitSaleRepository,
    private val ledgerRepository: LedgerRepository,
) : ViewModel() {

    private var unitCache: Map<String, RealEstateOption> = emptyMap()
    private var parkingCache: Map<String, RealEstateOption> = emptyMap()

    private val _uiState = MutableStateFlow(UnitSaleUiState())
    val uiState: StateFlow<UnitSaleUiState> = _uiState.asStateFlow()

    init {
        loadChargeTypes()
    }

    private fun loadChargeTypes() {
        viewModelScope.launch {
            when (val result = unitSaleRepository.getChargeTypes()) {
                is Resource.Success -> _uiState.update { it.copy(chargeTypes = result.data) }
                is Resource.Error -> _uiState.update {
                    it.copy(sessionExpired = it.sessionExpired || result.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    // ---- Customer ----------------------------------------------------------

    /** Shared coa4 search, all account types — the web's DdlMultiline acType="". */
    suspend fun searchCustomers(query: String): Resource<List<LedgerDropdownItem>> =
        ledgerRepository.searchLedgers(query, acType = "")

    fun onCustomerSelected(customer: LedgerDropdownItem) =
        _uiState.update { it.copy(customer = customer) }

    // ---- Unit --------------------------------------------------------------

    suspend fun searchUnits(query: String): Resource<List<SelectorOption>> =
        when (val result = unitSaleRepository.searchUnits(query)) {
            is Resource.Success -> {
                unitCache = unitCache + result.data.associateBy { it.id.toString() }
                Resource.Success(result.data.map { it.toSelectorOption() })
            }
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }

    /** Picking a unit adds — or replaces — the locked "Unit Price" line. */
    fun onUnitSelected(option: SelectorOption) {
        val unit = unitCache[option.id] ?: return
        _uiState.update { state ->
            val amount = unit.autoAmount
            val note = "Auto: ${plain(unit.sizeSqft)} × ${plain(unit.ratePerSqft)}"
            val existing = state.lines.firstOrNull { it.type == TYPE_UNIT_PRICE }
            val lines = if (existing != null) {
                state.lines.map {
                    if (it.type == TYPE_UNIT_PRICE) {
                        it.copy(linkedLabel = unit.label, linkedId = unit.id, amount = amount, note = note)
                    } else {
                        it
                    }
                }
            } else {
                state.lines + UnitSaleLine(
                    id = System.currentTimeMillis(),
                    type = TYPE_UNIT_PRICE,
                    title = "Unit Price",
                    effect = "+",
                    linkedKind = "unit",
                    linkedLabel = unit.label,
                    linkedId = unit.id,
                    amount = amount,
                    note = note,
                    editMode = "LOCKED",
                )
            }
            state.copy(unit = unit, lines = lines)
        }
    }

    // ---- Parking -----------------------------------------------------------

    suspend fun searchParking(query: String): Resource<List<SelectorOption>> =
        when (val result = unitSaleRepository.searchParking(query)) {
            is Resource.Success -> {
                parkingCache = parkingCache + result.data.associateBy { it.id.toString() }
                Resource.Success(result.data.map { it.toSelectorOption() })
            }
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }

    /** Picking a parking adds — or replaces — the locked "Parking" line. */
    fun onParkingSelected(option: SelectorOption) {
        val parking = parkingCache[option.id] ?: return
        _uiState.update { state ->
            val amount = parking.autoAmount
            val note = "Auto: ${plain(parking.sizeSqft)} × ${plain(parking.ratePerSqft)}"
            val existing = state.lines.firstOrNull { it.type == TYPE_PARKING }
            val lines = if (existing != null) {
                state.lines.map {
                    if (it.type == TYPE_PARKING) {
                        it.copy(linkedLabel = parking.label, linkedId = parking.id, amount = amount, note = note)
                    } else {
                        it
                    }
                }
            } else {
                state.lines + UnitSaleLine(
                    id = System.currentTimeMillis() + 1,
                    type = TYPE_PARKING,
                    title = "Parking",
                    effect = "+",
                    linkedKind = "parking",
                    linkedLabel = parking.label,
                    linkedId = parking.id,
                    amount = amount,
                    note = note,
                    editMode = "LOCKED",
                )
            }
            state.copy(parking = parking, lines = lines)
        }
    }

    fun requestRemoveParking() = _uiState.update { it.copy(confirmParkingRemoval = true) }
    fun dismissRemoveParking() = _uiState.update { it.copy(confirmParkingRemoval = false) }

    fun confirmRemoveParking() = _uiState.update {
        it.copy(
            parking = null,
            lines = it.lines.filterNot { line -> line.type == TYPE_PARKING },
            confirmParkingRemoval = false,
        )
    }

    // ---- Custom charges ----------------------------------------------------

    fun onChargeTypeSelected(option: SelectorOption) = _uiState.update { state ->
        state.copy(selectedChargeType = state.chargeTypes.firstOrNull { it.id.toString() == option.id })
    }

    fun onChargeAmountChange(value: String) =
        _uiState.update { it.copy(chargeAmount = value.decimalOnly()) }

    /** An existing CUSTOM row with the same title is updated, not duplicated. */
    fun addCharge() {
        val state = _uiState.value
        val chargeType = state.selectedChargeType ?: return
        val amt = state.chargeAmount.toDoubleOrNull() ?: return
        if (amt <= 0) return
        _uiState.update {
            val existing = it.lines.firstOrNull { line ->
                line.type == TYPE_CUSTOM && line.title == chargeType.name
            }
            val lines = if (existing != null) {
                it.lines.map { line ->
                    if (line.id == existing.id) {
                        line.copy(amount = abs(amt), effect = chargeType.effect)
                    } else {
                        line
                    }
                }
            } else {
                it.lines + UnitSaleLine(
                    id = System.currentTimeMillis(),
                    type = TYPE_CUSTOM,
                    title = chargeType.name,
                    effect = chargeType.effect,
                    linkedKind = null,
                    linkedLabel = null,
                    linkedId = null,
                    amount = abs(amt),
                    note = null,
                    editMode = "EDITABLE",
                )
            }
            it.copy(lines = lines, selectedChargeType = null, chargeAmount = "")
        }
    }

    // ---- Inline amount edit (custom rows only) -----------------------------

    fun startEditLine(line: UnitSaleLine) {
        if (line.editMode != "EDITABLE") return
        _uiState.update { it.copy(editingLineId = line.id, editDraft = plain(line.amount)) }
    }

    fun onEditDraftChange(value: String) =
        _uiState.update { it.copy(editDraft = value.decimalOnly()) }

    fun saveEditLine() {
        val state = _uiState.value
        val id = state.editingLineId ?: return
        val value = state.editDraft.toDoubleOrNull() ?: return
        if (value <= 0) return
        _uiState.update {
            it.copy(
                lines = it.lines.map { line -> if (line.id == id) line.copy(amount = abs(value)) else line },
                editingLineId = null,
                editDraft = "",
            )
        }
    }

    fun cancelEditLine() = _uiState.update { it.copy(editingLineId = null, editDraft = "") }

    fun removeLine(line: UnitSaleLine) {
        if (line.editMode != "EDITABLE") return
        _uiState.update { it.copy(lines = it.lines.filterNot { l -> l.id == line.id }) }
    }

    // ---- Payment fields ----------------------------------------------------

    fun onPaymentModeSelected(option: SelectorOption) = _uiState.update {
        val bank = option.id == "CHEQUE" || option.id == "BANK_TRANSFER"
        it.copy(
            paymentMode = option,
            referenceNo = if (bank) it.referenceNo else "",
            bankName = if (bank) it.bankName else "",
            branchName = if (bank) it.branchName else "",
        )
    }

    fun onReceiptNoChange(value: String) = _uiState.update { it.copy(receiptNo = value) }
    fun onBookingAmountChange(value: String) = _uiState.update { it.copy(bookingAmount = value.decimalOnly()) }
    fun onReferenceNoChange(value: String) = _uiState.update { it.copy(referenceNo = value) }
    fun onBankNameChange(value: String) = _uiState.update { it.copy(bankName = value) }
    fun onBranchNameChange(value: String) = _uiState.update { it.copy(branchName = value) }

    // ---- Submit ------------------------------------------------------------

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        _uiState.update { it.copy(isSubmitting = true, message = null, isError = false) }
        viewModelScope.launch {
            val result = unitSaleRepository.submitSale(
                customer = state.customer!!,
                unit = state.unit!!,
                parking = state.parking,
                paymentMode = state.paymentMode.id,
                receiptNo = state.receiptNo.trim().ifBlank { null },
                bookingAmt = state.bookingAmt,
                referenceNo = if (state.showBankFields) state.referenceNo.trim().ifBlank { null } else null,
                bankName = if (state.showBankFields) state.bankName.trim().ifBlank { null } else null,
                branchName = if (state.showBankFields) state.branchName.trim().ifBlank { null } else null,
                items = state.lines,
                total = state.total,
                due = state.due,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    // Full reset — the sold unit can't be sold again, so nothing
                    // on the form is worth keeping except the loaded charge types.
                    UnitSaleUiState(
                        chargeTypes = it.chargeTypes,
                        message = result.data,
                        isError = false,
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

    private fun RealEstateOption.toSelectorOption() = SelectorOption(
        id = id.toString(),
        label = label,
        sublabel = listOfNotNull(flatName, buildingName, branchName)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
            .ifBlank { null },
    )

    /** "1250" for whole numbers, "1250.5" otherwise — the web's template literals. */
    private fun plain(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    private fun String.decimalOnly(): String =
        filterIndexed { i, c -> c.isDigit() || (c == '.' && !take(i).contains('.')) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                UnitSaleViewModel(
                    unitSaleRepository = ServiceLocator.provideUnitSaleRepository(appContext),
                    ledgerRepository = ServiceLocator.provideLedgerRepository(appContext),
                )
            }
        }
    }
}

@Composable
fun UnitSaleScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UnitSaleViewModel = viewModel(
        factory = UnitSaleViewModel.provideFactory(LocalContext.current)
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
        title = "Unit Sales",
        currentRoute = Routes.REAL_ESTATE,
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
            TotalsHeader(state)

            SearchableLedgerDropdown(
                selectedLedger = state.customer,
                onLedgerSelected = viewModel::onCustomerSelected,
                searchLedgers = viewModel::searchCustomers,
                label = "Select Customer",
            )
            SearchableSelectDropdown(
                selected = state.unit?.let { SelectorOption(it.id.toString(), it.label) },
                onSelected = viewModel::onUnitSelected,
                search = viewModel::searchUnits,
                label = "Select Unit",
                placeholder = "Type 2+ chars to search…",
                emptyText = "No unit found",
                minSearchChars = UnitSaleRepository.DDL_MIN_CHARS,
            )
            SearchableSelectDropdown(
                selected = state.parking?.let { SelectorOption(it.id.toString(), it.label) },
                onSelected = viewModel::onParkingSelected,
                search = viewModel::searchParking,
                label = "Select Parking (optional)",
                placeholder = "Type 2+ chars to search…",
                emptyText = "No parking found",
                minSearchChars = UnitSaleRepository.DDL_MIN_CHARS,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppSelectDropdown(
                    label = "Charge Type",
                    options = state.chargeTypes.map { SelectorOption(it.id.toString(), it.name) },
                    selected = state.selectedChargeType?.let { SelectorOption(it.id.toString(), it.name) },
                    onSelected = viewModel::onChargeTypeSelected,
                    modifier = Modifier.weight(1f),
                )
                AppTextField(
                    value = state.chargeAmount,
                    onValueChange = viewModel::onChargeAmountChange,
                    label = "Amount (Tk.)",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
            }
            SecondaryButton(
                text = "Add Charge",
                onClick = viewModel::addCharge,
                enabled = state.canAddCharge,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppSelectDropdown(
                    label = "Payment Mode",
                    options = PAYMENT_MODES,
                    selected = state.paymentMode,
                    onSelected = viewModel::onPaymentModeSelected,
                    modifier = Modifier.weight(1f),
                )
                AppTextField(
                    value = state.receiptNo,
                    onValueChange = viewModel::onReceiptNoChange,
                    label = "Money Receipt No.",
                    modifier = Modifier.weight(1f),
                )
            }
            AppTextField(
                value = state.bookingAmount,
                onValueChange = viewModel::onBookingAmountChange,
                label = "Booking Tk.",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.bookingExceedsTotal) {
                Text(
                    text = "Booking money cannot be greater than the grand total.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (state.showBankFields) {
                AppTextField(
                    value = state.referenceNo,
                    onValueChange = viewModel::onReferenceNoChange,
                    label = "Check/Ref. Number",
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppTextField(
                        value = state.bankName,
                        onValueChange = viewModel::onBankNameChange,
                        label = "Bank Name",
                        modifier = Modifier.weight(1f),
                    )
                    AppTextField(
                        value = state.branchName,
                        onValueChange = viewModel::onBranchNameChange,
                        label = "Branch Name",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            PrimaryButton(
                text = "Save Unit Sale",
                onClick = viewModel::submit,
                enabled = state.canSubmit,
                isLoading = state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )

            state.message?.let { message ->
                Text(
                    text = message,
                    color = if (state.isError) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            LinesTable(state = state, viewModel = viewModel)
        }
    }

    if (state.confirmParkingRemoval) {
        AlertDialog(
            onDismissRequest = viewModel::dismissRemoveParking,
            title = { Text("Remove parking") },
            text = { Text("Remove parking from this sale?") },
            confirmButton = { LinkButton(text = "Remove", onClick = viewModel::confirmRemoveParking) },
            dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::dismissRemoveParking) },
        )
    }
}

/** Grand Total / Booking / Due readouts, like the web's page header. */
@Composable
private fun TotalsHeader(state: UnitSaleUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HeaderTile("Grand Total", formatSigned(state.total), Modifier.weight(1f))
        HeaderTile("Booking", AmountFormat.format(state.bookingAmt.toDouble(), 0), Modifier.weight(1f))
        HeaderTile("Due", formatSigned(state.due), Modifier.weight(1f))
    }
}

@Composable
private fun HeaderTile(label: String, value: String, modifier: Modifier = Modifier) {
    SummaryTile(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = AppFontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The pricing table: Item | Linked | Amount | Action. */
@Composable
private fun LinesTable(state: UnitSaleUiState, viewModel: UnitSaleViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                HeaderCell("Item", Modifier.weight(0.26f))
                HeaderCell("Linked", Modifier.weight(0.32f))
                HeaderCell("Amount", Modifier.weight(0.26f), TextAlign.End)
                HeaderCell("Action", Modifier.weight(0.16f), TextAlign.Center)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (state.lines.isEmpty()) {
                Text(
                    text = "No pricing items yet. Select Unit to start.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                )
            }

            state.lines.forEachIndexed { index, line ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LineRow(line = line, viewModel = viewModel)
                if (state.editingLineId == line.id) {
                    EditAmountRow(state = state, viewModel = viewModel)
                }
            }

            if (state.lines.isNotEmpty()) {
                HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Grand Total", fontWeight = AppFontWeight.Bold)
                    Text(formatSigned(state.total), fontWeight = AppFontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier, align: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = AppFontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        modifier = modifier,
    )
}

@Composable
private fun LineRow(line: UnitSaleLine, viewModel: UnitSaleViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(0.26f)) {
            Text(line.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            line.note?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = when (line.linkedKind) {
                "unit" -> "Unit: ${line.linkedLabel.orEmpty()}"
                "parking" -> "Parking: ${line.linkedLabel.orEmpty()}"
                else -> "-"
            },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.32f),
        )
        Row(
            modifier = Modifier.weight(0.26f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (line.effect == "-") "(-) " else "(+) ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = AmountFormat.format(abs(line.amount), 0),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = AppFontWeight.SemiBold,
                maxLines = 1,
            )
        }
        Row(
            modifier = Modifier.weight(0.16f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (line.editMode == "LOCKED") {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = "Locked",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(4.dp),
                )
                if (line.type == TYPE_PARKING) {
                    IconButton(onClick = viewModel::requestRemoveParking) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove Parking", tint = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                IconButton(onClick = { viewModel.startEditLine(line) }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit amount", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { viewModel.removeLine(line) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** The inline amount editor shown under the custom row being edited. */
@Composable
private fun EditAmountRow(state: UnitSaleUiState, viewModel: UnitSaleViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AppTextField(
            value = state.editDraft,
            onValueChange = viewModel::onEditDraftChange,
            label = "New amount",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = viewModel::saveEditLine) {
            Icon(Icons.Filled.Check, contentDescription = "Save amount", tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = viewModel::cancelEditLine) {
            Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The web's formatAmount: a leading "-" plus the absolute thousands figure. */
private fun formatSigned(value: Double): String =
    (if (value < 0) "-" else "") + AmountFormat.format(abs(value), 0)
