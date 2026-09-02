package com.example.cashbookbd.ui.accounts

import android.content.Context
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.cashbookbd.data.repository.AccountHead
import com.example.cashbookbd.data.repository.AccountsRepository
import com.example.cashbookbd.data.repository.ChequeDishonourPlan
import com.example.cashbookbd.data.repository.ChequeDue
import com.example.cashbookbd.data.repository.ChequeRow
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AddButton
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.components.SummaryTile
import com.example.cashbookbd.ui.reports.PickerField
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Cheque Register — which cheque, drawn on which bank, dated when, and what
 * became of it.
 *
 * **The voucher records the money; this records the paper.** Banking a cheque
 * and seeing it clear post nothing at all — they are facts about a slip in a
 * drawer, and the money was already accounted for when the voucher was passed.
 *
 * The BOUNCE is the one exception, and it has an endpoint and a confirmation of
 * its own so that nobody reaches it by picking a word out of a dropdown: it
 * turns the receipt voucher around leg for leg, and the legs are shown first.
 */

private val DIRECTIONS = listOf(
    "" to "All",
    "received" to "Received",
    "issued" to "Issued",
)

private val STATUSES = listOf(
    "" to "All",
    "in_hand" to "In hand",
    "deposited" to "Deposited",
    "cleared" to "Cleared",
    "dishonoured" to "Bounced",
    "cancelled" to "Cancelled",
)

/** The statuses a clerk may set by hand — the bounce is not among them. */
private val SETTABLE_STATUSES = listOf(
    "in_hand" to "In hand",
    "deposited" to "Deposited",
    "cleared" to "Cleared",
    "cancelled" to "Cancelled",
)

private fun statusLabel(status: String): String =
    STATUSES.firstOrNull { it.first == status }?.second ?: status.ifBlank { "-" }

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

/** The add/edit form — one paper cheque being written down. */
data class ChequeFormState(
    val id: Long? = null,
    val direction: String = "received",
    val chequeNo: String = "",
    val bankName: String = "",
    val branchName: String = "",
    val chequeDate: SimpleDate = SimpleDate.today(),
    val onDate: SimpleDate = SimpleDate.today(),
    val partyCoa4Id: Long? = null,
    val partyName: String = "",
    val accountCoa4Id: Long? = null,
    val amount: String = "",
    val note: String = "",
) {
    /** A cheque with nobody's name on it is not a record of anything. */
    val isValid: Boolean
        get() = chequeNo.isNotBlank() &&
            (amount.trim().toDoubleOrNull() ?: 0.0) > 0.0 &&
            (partyCoa4Id != null || partyName.isNotBlank())
}

data class ChequeRegisterUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val rows: List<ChequeRow> = emptyList(),
    val due: List<ChequeDue> = emptyList(),
    val partyHeads: List<AccountHead> = emptyList(),
    val bankHeads: List<AccountHead> = emptyList(),
    val expenseHeads: List<AccountHead> = emptyList(),
    val note: String = "",
    val direction: String = "",
    val status: String = "",
    val search: String = "",
    val currentPage: Int = 1,
    val lastPage: Int = 1,
    val total: Int = 0,
    val form: ChequeFormState? = null,
    val isSaving: Boolean = false,
    /** The cheque a bounce is being considered for, and what it would post. */
    val dishonourFor: ChequeRow? = null,
    val plan: ChequeDishonourPlan? = null,
    val isPlanLoading: Boolean = false,
    val dishonourOn: SimpleDate = SimpleDate.today(),
    val dishonourReason: String = "",
    val dishonourCharge: String = "",
    val dishonourChargeCoa4Id: Long? = null,
    val pendingDelete: ChequeRow? = null,
    val sessionExpired: Boolean = false,
) {
    val receivedDue: ChequeDue? get() = due.firstOrNull { it.direction == "received" }
    val issuedDue: ChequeDue? get() = due.firstOrNull { it.direction == "issued" }
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class ChequeRegisterViewModel(
    private val repository: AccountsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChequeRegisterUiState())
    val uiState: StateFlow<ChequeRegisterUiState> = _uiState.asStateFlow()

    init {
        load(1)
    }

    fun load(page: Int = _uiState.value.currentPage) {
        val state = _uiState.value
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = repository.fetchCheques(
                branchId = null,
                direction = state.direction,
                status = state.status,
                query = state.search,
                page = page,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        rows = result.data.rows,
                        due = result.data.due,
                        partyHeads = result.data.partyHeads,
                        bankHeads = result.data.bankHeads,
                        expenseHeads = result.data.expenseHeads,
                        note = result.data.note,
                        currentPage = result.data.currentPage,
                        lastPage = result.data.lastPage,
                        total = result.data.total,
                    )
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

    fun onDirection(value: String) {
        _uiState.update { it.copy(direction = value) }
        load(1)
    }

    fun onStatus(value: String) {
        _uiState.update { it.copy(status = value) }
        load(1)
    }

    fun onSearchChange(value: String) = _uiState.update { it.copy(search = value) }

    fun search() = load(1)

    fun goToPage(page: Int) = load(page)

    // ——— The form ———

    fun openNew() = _uiState.update { it.copy(form = ChequeFormState()) }

    fun openEdit(row: ChequeRow) = _uiState.update {
        it.copy(
            form = ChequeFormState(
                id = row.id,
                direction = row.direction.ifBlank { "received" },
                chequeNo = row.chequeNo,
                bankName = row.bankName,
                branchName = row.branchName,
                chequeDate = SimpleDate.fromApi(row.chequeDate) ?: SimpleDate.today(),
                onDate = SimpleDate.fromApi(row.onDate) ?: SimpleDate.today(),
                partyCoa4Id = row.partyCoa4Id,
                partyName = row.partyName,
                accountCoa4Id = row.accountCoa4Id,
                amount = if (row.amount == 0.0) "" else AmountFormat.format(row.amount),
                note = row.note,
            ),
        )
    }

    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun onForm(change: (ChequeFormState) -> ChequeFormState) = _uiState.update {
        it.copy(form = it.form?.let(change))
    }

    fun saveForm() {
        val form = _uiState.value.form ?: return
        if (!form.isValid) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = repository.saveCheque(
                id = form.id,
                branchId = null,
                direction = form.direction,
                chequeNo = form.chequeNo,
                bankName = form.bankName,
                branchName = form.branchName,
                chequeDate = form.chequeDate.toApi(),
                onDate = form.onDate.toApi(),
                partyCoa4Id = form.partyCoa4Id,
                partyName = form.partyName,
                accountCoa4Id = form.accountCoa4Id,
                amount = form.amount,
                note = form.note,
            )
            when (result) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSaving = false, form = null, message = result.data) }
                    load()
                }

                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }

                Resource.Loading -> Unit
            }
        }
    }

    // ——— Status: facts about the paper, so no confirmation is asked for ———

    fun setStatus(row: ChequeRow, status: String) {
        viewModelScope.launch {
            val result = repository.setChequeStatus(
                id = row.id,
                status = status,
                onDate = SimpleDate.today().toApi(),
            )
            when (result) {
                is Resource.Success -> {
                    _uiState.update { it.copy(message = result.data) }
                    load()
                }

                is Resource.Error -> _uiState.update {
                    it.copy(
                        message = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }

                Resource.Loading -> Unit
            }
        }
    }

    // ——— The bounce: the one thing here that posts ———

    fun askDishonour(row: ChequeRow) {
        _uiState.update {
            it.copy(
                dishonourFor = row,
                plan = null,
                dishonourOn = SimpleDate.today(),
                dishonourReason = "",
                dishonourCharge = "",
                dishonourChargeCoa4Id = null,
            )
        }
        loadPlan()
    }

    fun onDishonourOn(date: SimpleDate) = _uiState.update { it.copy(dishonourOn = date) }
    fun onDishonourReason(value: String) = _uiState.update { it.copy(dishonourReason = value) }

    fun onDishonourCharge(value: String) {
        _uiState.update { it.copy(dishonourCharge = value) }
        loadPlan()
    }

    fun onDishonourChargeHead(id: Long) {
        _uiState.update { it.copy(dishonourChargeCoa4Id = id) }
        loadPlan()
    }

    /** Re-reads the legs whenever the charge changes: the plan IS the answer. */
    private fun loadPlan() {
        val state = _uiState.value
        val row = state.dishonourFor ?: return
        _uiState.update { it.copy(isPlanLoading = true) }
        viewModelScope.launch {
            val result = repository.fetchDishonourPlan(
                id = row.id,
                charge = state.dishonourCharge,
                chargeCoa4Id = state.dishonourChargeCoa4Id,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isPlanLoading = false, plan = result.data)
                }

                is Resource.Error -> _uiState.update {
                    it.copy(
                        isPlanLoading = false,
                        message = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }

                Resource.Loading -> Unit
            }
        }
    }

    fun cancelDishonour() = _uiState.update { it.copy(dishonourFor = null, plan = null) }

    fun confirmDishonour() {
        val state = _uiState.value
        val row = state.dishonourFor ?: return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = repository.dishonourCheque(
                id = row.id,
                onDate = state.dishonourOn.toApi(),
                reason = state.dishonourReason,
                charge = state.dishonourCharge,
                chargeCoa4Id = state.dishonourChargeCoa4Id,
            )
            when (result) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            dishonourFor = null,
                            plan = null,
                            message = result.data,
                        )
                    }
                    load()
                }

                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }

                Resource.Loading -> Unit
            }
        }
    }

    // ——— Delete ———

    fun askDelete(row: ChequeRow) = _uiState.update { it.copy(pendingDelete = row) }
    fun cancelDelete() = _uiState.update { it.copy(pendingDelete = null) }

    fun confirmDelete() {
        val row = _uiState.value.pendingDelete ?: return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = repository.deleteCheque(row.id)
            when (result) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(isSaving = false, pendingDelete = null, message = result.data)
                    }
                    load()
                }

                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        pendingDelete = null,
                        message = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }

                Resource.Loading -> Unit
            }
        }
    }

    fun dismissMessage() = _uiState.update { it.copy(message = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                ChequeRegisterViewModel(
                    repository = AccountsRepository.get(context.applicationContext),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@Composable
fun ChequeRegisterScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChequeRegisterViewModel = viewModel(
        factory = ChequeRegisterViewModel.provideFactory(LocalContext.current),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val today = remember { SimpleDate.today().toApi() }

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    AuthenticatedShell(
        title = "Cheque Register",
        currentRoute = Routes.TRANSACTIONS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        if (state.form != null) {
            ChequeForm(state = state, viewModel = viewModel, context = context)
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                DueStrip(state)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DIRECTIONS.forEach { (value, label) ->
                        FilterChip(
                            selected = state.direction == value,
                            onClick = { viewModel.onDirection(value) },
                            label = { Text(label) },
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    STATUSES.forEach { (value, label) ->
                        FilterChip(
                            selected = state.status == value,
                            onClick = { viewModel.onStatus(value) },
                            label = { Text(label) },
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppTextField(
                        value = state.search,
                        onValueChange = viewModel::onSearchChange,
                        label = "Cheque no, bank or name",
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        text = "Search",
                        onClick = viewModel::search,
                        isLoading = state.isLoading,
                        compact = true,
                    )
                    AddButton(text = "Add", onClick = viewModel::openNew, compact = true)
                }

                if (state.note.isNotBlank()) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                        ScreenNote(state.note)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                when {
                    state.isLoading && state.rows.isEmpty() -> CentredBox {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
                    }

                    state.error != null -> CentredBox {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = state.error.orEmpty(),
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(12.dp))
                            PrimaryButton(
                                text = "Retry",
                                onClick = viewModel::search,
                                compact = true,
                            )
                        }
                    }

                    else -> Box(modifier = Modifier.weight(1f)) {
                        ReportTable(
                            columns = chequeColumns(
                                today = today,
                                onStatus = viewModel::setStatus,
                                onBounce = viewModel::askDishonour,
                                onEdit = viewModel::openEdit,
                                onDelete = viewModel::askDelete,
                            ),
                            data = state.rows,
                            noDataMessage = "No cheques on this filter.",
                        )
                    }
                }

                if (state.lastPage > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LinkButton(
                            text = "Previous",
                            onClick = { viewModel.goToPage(state.currentPage - 1) },
                            enabled = state.currentPage > 1,
                        )
                        Text(
                            text = "Page ${state.currentPage} of ${state.lastPage}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                        )
                        LinkButton(
                            text = "Next",
                            onClick = { viewModel.goToPage(state.currentPage + 1) },
                            enabled = state.currentPage < state.lastPage,
                        )
                    }
                }
            }
        }

        state.dishonourFor?.let { row ->
            DishonourDialog(state = state, row = row, viewModel = viewModel, context = context)
        }

        state.pendingDelete?.let { row ->
            AlertDialog(
                onDismissRequest = viewModel::cancelDelete,
                title = { Text("Delete this cheque?") },
                text = {
                    Text(
                        "Cheque ${row.chequeNo.ifBlank { "—" }} for " +
                            "${AmountFormat.format(row.amount)} will be removed from the " +
                            "register. Nothing is posted or unposted by this.",
                    )
                },
                confirmButton = {
                    PrimaryButton(
                        text = "Delete",
                        onClick = viewModel::confirmDelete,
                        enabled = !state.isSaving,
                        isLoading = state.isSaving,
                        compact = true,
                    )
                },
                dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::cancelDelete) },
            )
        }

        state.message?.let { message ->
            AlertDialog(
                onDismissRequest = viewModel::dismissMessage,
                text = { Text(message) },
                confirmButton = {
                    PrimaryButton(text = "OK", onClick = viewModel::dismissMessage, compact = true)
                },
            )
        }
    }
}

/** "3 received due · 2 issued due", pinned where it is seen before anything else. */
@Composable
private fun DueStrip(state: ChequeRegisterUiState) {
    val received = state.receivedDue
    val issued = state.issuedDue
    if (received == null && issued == null) return
    SummaryTile(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = listOfNotNull(
                received?.let { "${it.count} received due" },
                issued?.let { "${it.count} issued due" },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = AppFontWeight.SemiBold,
        )
        Text(
            text = listOfNotNull(
                received?.let { "in: ${AmountFormat.format(it.total)}" },
                issued?.let { "out: ${AmountFormat.format(it.total)}" },
            ).joinToString("   "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.appColors.textMuted,
        )
    }
}

// ---------------------------------------------------------------------------
// The add / edit form
// ---------------------------------------------------------------------------

@Composable
private fun ChequeForm(
    state: ChequeRegisterUiState,
    viewModel: ChequeRegisterViewModel,
    context: Context,
) {
    val form = state.form ?: return
    ScrollingBody {
        Text(
            text = if (form.id == null) "New cheque" else "Edit cheque",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = AppFontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("received" to "Received", "issued" to "Issued").forEach { (value, label) ->
                FilterChip(
                    selected = form.direction == value,
                    onClick = { viewModel.onForm { it.copy(direction = value) } },
                    label = { Text(label) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        AppTextField(
            value = form.chequeNo,
            onValueChange = { v -> viewModel.onForm { it.copy(chequeNo = v) } },
            label = "Cheque number",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppTextField(
                value = form.bankName,
                onValueChange = { v -> viewModel.onForm { it.copy(bankName = v) } },
                label = "Bank",
                modifier = Modifier.weight(1f),
            )
            AppTextField(
                value = form.branchName,
                onValueChange = { v -> viewModel.onForm { it.copy(branchName = v) } },
                label = "Its branch",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PickerField(
                label = "Dated",
                value = form.chequeDate.toDisplay(),
                trailingIcon = Icons.Filled.DateRange,
                modifier = Modifier.weight(1f),
                onClick = {
                    pickAccountsDate(context, form.chequeDate) { d ->
                        viewModel.onForm { it.copy(chequeDate = d) }
                    }
                },
            )
            PickerField(
                label = "Taken on",
                value = form.onDate.toDisplay(),
                trailingIcon = Icons.Filled.DateRange,
                modifier = Modifier.weight(1f),
                onClick = {
                    pickAccountsDate(context, form.onDate) { d ->
                        viewModel.onForm { it.copy(onDate = d) }
                    }
                },
            )
        }
        Spacer(Modifier.height(10.dp))
        AppSelectDropdown(
            label = "Whose cheque",
            options = state.partyHeads.map { SelectorOption(it.id.toString(), it.name, it.groupName) },
            selected = state.partyHeads.firstOrNull { it.id == form.partyCoa4Id }
                ?.let { SelectorOption(it.id.toString(), it.name, it.groupName) },
            onSelected = { option ->
                viewModel.onForm { it.copy(partyCoa4Id = option.id.toLongOrNull()) }
            },
            placeholder = "Pick a party head",
        )
        Spacer(Modifier.height(6.dp))
        AppTextField(
            value = form.partyName,
            onValueChange = { v -> viewModel.onForm { it.copy(partyName = v) } },
            label = "…or simply type a name",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        AppSelectDropdown(
            label = "Where it sits",
            options = state.bankHeads.map { SelectorOption(it.id.toString(), it.name, it.groupName) },
            selected = state.bankHeads.firstOrNull { it.id == form.accountCoa4Id }
                ?.let { SelectorOption(it.id.toString(), it.name, it.groupName) },
            onSelected = { option ->
                viewModel.onForm { it.copy(accountCoa4Id = option.id.toLongOrNull()) }
            },
            placeholder = "Pick a bank account",
        )
        Spacer(Modifier.height(10.dp))
        AppTextField(
            value = form.amount,
            onValueChange = { v -> viewModel.onForm { it.copy(amount = v) } },
            label = "Amount",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        AppTextField(
            value = form.note,
            onValueChange = { v -> viewModel.onForm { it.copy(note = v) } },
            label = "Note",
            multiline = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(
                text = "Save",
                onClick = viewModel::saveForm,
                enabled = form.isValid && !state.isSaving,
                isLoading = state.isSaving,
            )
            SecondaryButton(text = "Cancel", onClick = viewModel::closeForm)
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ---------------------------------------------------------------------------
// The bounce
// ---------------------------------------------------------------------------

@Composable
private fun DishonourDialog(
    state: ChequeRegisterUiState,
    row: ChequeRow,
    viewModel: ChequeRegisterViewModel,
    context: Context,
) {
    AlertDialog(
        onDismissRequest = viewModel::cancelDishonour,
        title = { Text("Bounce cheque ${row.chequeNo.ifBlank { "—" }}?") },
        text = {
            // The body outgrows the screen once the legs are listed.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "This POSTS a voucher. The receipt is turned around leg for leg — " +
                        "the debt goes back on the party and the money comes off the bank.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))
                PickerField(
                    label = "Bounced on",
                    value = state.dishonourOn.toDisplay(),
                    trailingIcon = Icons.Filled.DateRange,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        pickAccountsDate(context, state.dishonourOn, viewModel::onDishonourOn)
                    },
                )
                Spacer(Modifier.height(8.dp))
                AppTextField(
                    value = state.dishonourReason,
                    onValueChange = viewModel::onDishonourReason,
                    label = "What the bank said",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                AppTextField(
                    value = state.dishonourCharge,
                    onValueChange = viewModel::onDishonourCharge,
                    label = "The bank's charge, if any",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.dishonourCharge.trim().toDoubleOrNull()?.let { it > 0.0 } == true) {
                    Spacer(Modifier.height(8.dp))
                    AppSelectDropdown(
                        label = "Charge goes to",
                        options = state.expenseHeads.map {
                            SelectorOption(it.id.toString(), it.name, it.groupName)
                        },
                        selected = state.expenseHeads
                            .firstOrNull { it.id == state.dishonourChargeCoa4Id }
                            ?.let { SelectorOption(it.id.toString(), it.name, it.groupName) },
                        onSelected = { option ->
                            option.id.toLongOrNull()?.let(viewModel::onDishonourChargeHead)
                        },
                        placeholder = "Pick an expense head",
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "What will be posted",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = AppFontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                when {
                    state.isPlanLoading -> Text(
                        text = "Working out the legs…",
                        style = MaterialTheme.typography.labelSmall,
                    )

                    state.plan == null || state.plan.legs.isEmpty() -> Text(
                        text = "No legs could be worked out for this cheque.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.appColors.warning,
                    )

                    else -> state.plan.legs.forEach { leg ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = leg.head.ifBlank { "Head ${leg.coa4Id}" },
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = if (leg.debit > 0.0) {
                                    "Dr ${AmountFormat.format(leg.debit)}"
                                } else {
                                    "Cr ${AmountFormat.format(leg.credit)}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = AppFontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            PrimaryButton(
                text = "Bounce it",
                onClick = viewModel::confirmDishonour,
                enabled = !state.isSaving && state.plan != null && state.plan.legs.isNotEmpty(),
                isLoading = state.isSaving,
                compact = true,
            )
        },
        dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::cancelDishonour) },
    )
}

// ---------------------------------------------------------------------------
// Table
// ---------------------------------------------------------------------------

private fun chequeColumns(
    today: String,
    onStatus: (ChequeRow, String) -> Unit,
    onBounce: (ChequeRow) -> Unit,
    onEdit: (ChequeRow) -> Unit,
    onDelete: (ChequeRow) -> Unit,
): List<ReportColumn<ChequeRow>> = listOf(
    ReportColumn("CHEQUE", ReportColWidth.Fixed(150.dp)) { r, _ ->
        ReportTableCell.Slot { StackedCell(r.chequeNo.ifBlank { "-" }, chequeBank(r)) }
    },
    ReportColumn("WHOSE", ReportColWidth.Fixed(190.dp)) { r, _ ->
        ReportTableCell.Slot {
            StackedCell(
                r.whose.ifBlank { "-" },
                // Which way the paper went, and the voucher it belongs to —
                // a cheque entered without one is a fact worth reading.
                buildString {
                    append(if (r.direction == "issued") "issued" else "received")
                    append(" · ")
                    append(r.vrNo.ifBlank { "no voucher named" })
                },
            )
        }
    },
    ReportColumn("DATED", ReportColWidth.Fixed(150.dp)) { r, _ ->
        ReportTableCell.Slot {
            StackedCell(
                r.chequeDate.ifBlank { "-" },
                when {
                    r.isOpen && r.chequeDate.isNotBlank() && r.chequeDate <= today ->
                        "its day has come"

                    r.chequeDate > today -> "post-dated"
                    else -> ""
                },
            )
        }
    },
    ReportColumn("AMOUNT", ReportColWidth.Fixed(112.dp), TextAlign.End) { r, _ ->
        cellText(AmountFormat.formatOrDash(r.amount), align = TextAlign.End)
    },
    ReportColumn("WHERE IT STANDS", ReportColWidth.Fixed(170.dp)) { r, _ ->
        ReportTableCell.Slot { StatusCell(r) }
    },
    ReportColumn("", ReportColWidth.Fixed(56.dp), TextAlign.Center) { r, _ ->
        ReportTableCell.Slot {
            RowActions(
                row = r,
                onStatus = onStatus,
                onBounce = onBounce,
                onEdit = onEdit,
                onDelete = onDelete,
            )
        }
    },
)

private fun chequeBank(row: ChequeRow): String =
    listOf(row.bankName, row.branchName).filter { it.isNotBlank() }.joinToString(", ")

@Composable
private fun StackedCell(title: String, sub: String) {
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = AppFontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (sub.isNotBlank()) {
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.appColors.textOnScreenMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StatusCell(row: ChequeRow) {
    val colour: Color = when (row.status) {
        "dishonoured" -> MaterialTheme.appColors.danger
        "cleared" -> MaterialTheme.appColors.success
        "cancelled" -> MaterialTheme.appColors.textOnScreenMuted
        else -> MaterialTheme.colorScheme.onBackground
    }
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
        Text(
            text = statusLabel(row.status),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = AppFontWeight.SemiBold,
            color = colour,
        )
        if (row.returnReason.isNotBlank()) {
            Text(
                text = row.returnReason,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.appColors.danger,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RowActions(
    row: ChequeRow,
    onStatus: (ChequeRow, String) -> Unit,
    onBounce: (ChequeRow) -> Unit,
    onEdit: (ChequeRow) -> Unit,
    onDelete: (ChequeRow) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "Actions",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SETTABLE_STATUSES.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    enabled = row.status != value,
                    onClick = {
                        expanded = false
                        onStatus(row, value)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Bounce…") },
                enabled = row.status != "dishonoured",
                onClick = {
                    expanded = false
                    onBounce(row)
                },
            )
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = {
                    expanded = false
                    onEdit(row)
                },
            )
            DropdownMenuItem(
                // A bounced cheque posted a voucher; deleting the paper would
                // leave that voucher explaining nothing. The server refuses it
                // too — this only saves the round trip.
                text = { Text("Delete") },
                enabled = row.status != "dishonoured",
                onClick = {
                    expanded = false
                    onDelete(row)
                },
            )
        }
    }
}
