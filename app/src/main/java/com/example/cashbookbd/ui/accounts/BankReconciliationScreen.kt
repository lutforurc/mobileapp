package com.example.cashbookbd.ui.accounts

import android.app.DatePickerDialog
import android.content.Context
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import com.example.cashbookbd.data.repository.AccountHead
import com.example.cashbookbd.data.repository.AccountsRepository
import com.example.cashbookbd.data.repository.BankRecRow
import com.example.cashbookbd.data.repository.BankRecView
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
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
 * Bank Reconciliation — ticking the bank statement off against the books.
 *
 * **The difference is shown, never posted.** What the two sides cannot explain
 * — a charge, some interest, a cheque nobody entered — is put on the screen as
 * a figure and left there, so that somebody passes an ordinary voucher for it.
 * A reconciliation that wrote its own entries would hide the very thing it
 * exists to reveal, and the month would close on a lie that added up.
 *
 * Closing the month is refused while the difference is not zero. That refusal
 * is the exercise.
 */

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

data class BankRecUiState(
    val isAccountsLoading: Boolean = false,
    val accounts: List<AccountHead> = emptyList(),
    val note: String = "",
    val selectedAccountId: Long? = null,
    val statementDate: SimpleDate = SimpleDate.today(),
    val statementBalance: String = "",
    /** The clerk's working view: hide what the bank has already shown. */
    val onlyUncleared: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val view: BankRecView? = null,
    val isWorking: Boolean = false,
    val confirmClose: Boolean = false,
    val confirmReopen: Boolean = false,
    val sessionExpired: Boolean = false,
) {
    val selectedAccount: AccountHead? get() = accounts.firstOrNull { it.id == selectedAccountId }
    val canApply: Boolean get() = selectedAccountId != null && !isLoading
    val rows: List<BankRecRow>
        get() = view?.rows.orEmpty().filter { !onlyUncleared || !it.cleared }
    val isClosed: Boolean get() = view?.savedId != null
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class BankReconciliationViewModel(
    private val repository: AccountsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BankRecUiState())
    val uiState: StateFlow<BankRecUiState> = _uiState.asStateFlow()

    init {
        loadAccounts()
    }

    fun loadAccounts() {
        _uiState.update { it.copy(isAccountsLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.fetchBankAccounts()) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isAccountsLoading = false,
                        accounts = result.data.accounts,
                        note = result.data.note,
                        selectedAccountId = it.selectedAccountId
                            ?: result.data.accounts.firstOrNull()?.id,
                    )
                }

                is Resource.Error -> _uiState.update {
                    it.copy(
                        isAccountsLoading = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }

                Resource.Loading -> Unit
            }
        }
    }

    fun onAccount(id: Long) = _uiState.update { it.copy(selectedAccountId = id, view = null) }

    fun onStatementDate(date: SimpleDate) = _uiState.update { it.copy(statementDate = date) }

    fun onStatementBalance(value: String) = _uiState.update { it.copy(statementBalance = value) }

    fun onOnlyUncleared(value: Boolean) = _uiState.update { it.copy(onlyUncleared = value) }

    fun apply() = load(silent = false)

    private fun load(silent: Boolean) {
        val state = _uiState.value
        val coa4 = state.selectedAccountId ?: return
        if (!silent) _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = repository.fetchBankReconciliation(
                coa4Id = coa4,
                statementDate = state.statementDate.toApi(),
                statementBalance = state.statementBalance,
                branchId = null,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        view = result.data,
                        error = null,
                        // A closed month remembers what the bank said; show it
                        // rather than an empty box the clerk must fill again.
                        statementBalance = if (it.statementBalance.isBlank()) {
                            result.data.savedStatementBalance?.let { v -> AmountFormat.format(v) }
                                ?: it.statementBalance
                        } else {
                            it.statementBalance
                        },
                    )
                }

                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = if (silent) it.error else result.message,
                        message = if (silent) result.message else it.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }

                Resource.Loading -> Unit
            }
        }
    }

    /**
     * Ticks a leg off, or lets it go — optimistically.
     *
     * The tick is the one action here done dozens of times in a row, so the box
     * flips at once and is put back if the server refuses (a leg inside a closed
     * month is refused with the sentence that says so). A refreshed read follows
     * a success, because the difference above the table has just moved.
     */
    fun toggleTick(row: BankRecRow) {
        val state = _uiState.value
        val coa4 = state.selectedAccountId ?: return
        val next = !row.cleared
        setCleared(row.id, next)
        viewModelScope.launch {
            val result = repository.tickBankRows(
                ids = listOf(row.id),
                coa4Id = coa4,
                reconciledOn = if (next) state.statementDate.toApi() else null,
            )
            when (result) {
                is Resource.Success -> load(silent = true)
                is Resource.Error -> {
                    setCleared(row.id, !next)
                    _uiState.update {
                        it.copy(
                            message = result.message,
                            sessionExpired = it.sessionExpired || result.isUnauthorized,
                        )
                    }
                }

                Resource.Loading -> Unit
            }
        }
    }

    private fun setCleared(id: Long, cleared: Boolean) = _uiState.update { state ->
        val view = state.view ?: return@update state
        state.copy(
            view = view.copy(
                rows = view.rows.map { if (it.id == id) it.copy(cleared = cleared) else it },
            ),
        )
    }

    fun askClose() = _uiState.update { it.copy(confirmClose = true) }
    fun cancelClose() = _uiState.update { it.copy(confirmClose = false) }

    fun confirmClose() {
        val state = _uiState.value
        val coa4 = state.selectedAccountId ?: return
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            val result = repository.closeBankMonth(
                coa4Id = coa4,
                statementDate = state.statementDate.toApi(),
                statementBalance = state.statementBalance,
                branchId = null,
                note = "",
            )
            finishAction(result)
        }
    }

    fun askReopen() = _uiState.update { it.copy(confirmReopen = true) }
    fun cancelReopen() = _uiState.update { it.copy(confirmReopen = false) }

    fun confirmReopen() {
        val savedId = _uiState.value.view?.savedId ?: return
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch { finishAction(repository.reopenBankMonth(savedId)) }
    }

    private fun finishAction(result: Resource<String>) {
        when (result) {
            is Resource.Success -> {
                _uiState.update {
                    it.copy(
                        isWorking = false,
                        confirmClose = false,
                        confirmReopen = false,
                        message = result.data,
                    )
                }
                load(silent = true)
            }

            is Resource.Error -> _uiState.update {
                it.copy(
                    isWorking = false,
                    confirmClose = false,
                    confirmReopen = false,
                    message = result.message,
                    sessionExpired = it.sessionExpired || result.isUnauthorized,
                )
            }

            Resource.Loading -> Unit
        }
    }

    fun dismissMessage() = _uiState.update { it.copy(message = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                BankReconciliationViewModel(
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
fun BankReconciliationScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BankReconciliationViewModel = viewModel(
        factory = BankReconciliationViewModel.provideFactory(LocalContext.current),
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
        title = "Bank Reconciliation",
        currentRoute = Routes.TRANSACTIONS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (state.note.isNotBlank()) {
                    ScreenNote(state.note)
                    Spacer(Modifier.height(10.dp))
                }
                AppSelectDropdown(
                    label = "Bank account",
                    options = state.accounts.map {
                        SelectorOption(it.id.toString(), it.name, it.groupName)
                    },
                    selected = state.selectedAccount?.let {
                        SelectorOption(it.id.toString(), it.name, it.groupName)
                    },
                    onSelected = { option -> option.id.toLongOrNull()?.let(viewModel::onAccount) },
                    placeholder = if (state.isAccountsLoading) "Loading accounts…" else "Pick the bank",
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PickerField(
                        label = "Statement date",
                        value = state.statementDate.toDisplay(),
                        trailingIcon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            pickAccountsDate(context, state.statementDate, viewModel::onStatementDate)
                        },
                    )
                    AppTextField(
                        value = state.statementBalance,
                        onValueChange = viewModel::onStatementBalance,
                        label = "What the bank says",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = state.onlyUncleared,
                        onCheckedChange = viewModel::onOnlyUncleared,
                    )
                    Spacer(Modifier.height(0.dp))
                    Text(
                        text = "  Only what has not cleared",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(Modifier.height(10.dp))
                PrimaryButton(
                    text = "Show",
                    onClick = viewModel::apply,
                    enabled = state.canApply,
                    isLoading = state.isLoading,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            when {
                state.isLoading && state.view == null -> CentredBox {
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
                        PrimaryButton(text = "Retry", onClick = viewModel::apply, compact = true)
                    }
                }

                state.view == null -> CentredBox {
                    Text(
                        text = "Pick a bank and a statement date, then tap Show.",
                        color = MaterialTheme.appColors.textOnScreenMuted,
                        textAlign = TextAlign.Center,
                    )
                }

                else -> Column(modifier = Modifier.fillMaxSize()) {
                    ReconciliationSummary(
                        view = state.view!!,
                        isWorking = state.isWorking,
                        onClose = viewModel::askClose,
                        onReopen = viewModel::askReopen,
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        ReportTable(
                            columns = bankRecColumns(viewModel::toggleTick),
                            data = state.rows,
                            noDataMessage = "Nothing on this account up to that date.",
                        )
                    }
                }
            }
        }

        if (state.confirmClose) {
            AlertDialog(
                onDismissRequest = viewModel::cancelClose,
                title = { Text("Close this month?") },
                text = {
                    Text(
                        "The four figures are kept as they stand today and every tick is " +
                            "stamped to this statement. Nothing is posted. It can be opened " +
                            "again, which lets go of exactly these ticks.",
                    )
                },
                confirmButton = {
                    PrimaryButton(
                        text = "Close this month",
                        onClick = viewModel::confirmClose,
                        enabled = !state.isWorking,
                        isLoading = state.isWorking,
                        compact = true,
                    )
                },
                dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::cancelClose) },
            )
        }

        if (state.confirmReopen) {
            AlertDialog(
                onDismissRequest = viewModel::cancelReopen,
                title = { Text("Open it again?") },
                text = { Text("The ticks made in this month are let go. Nothing is posted.") },
                confirmButton = {
                    PrimaryButton(
                        text = "Open it again",
                        onClick = viewModel::confirmReopen,
                        enabled = !state.isWorking,
                        isLoading = state.isWorking,
                        compact = true,
                    )
                },
                dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::cancelReopen) },
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

/**
 * The five figures, read in the order a reconciliation is read out loud, then
 * the verdict. The difference is a fact on the screen and nothing more.
 */
@Composable
private fun ReconciliationSummary(
    view: BankRecView,
    isWorking: Boolean,
    onClose: () -> Unit,
    onReopen: () -> Unit,
) {
    val t = view.totals
    SummaryTile(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        SummaryLine("What the books say", t.bookBalance)
        SummaryLine("add — cheques written, not yet presented", t.unclearedOut)
        SummaryLine("less — paid in, not yet credited", t.unclearedIn)
        SummaryLine("so the bank should say", t.expectedBank, bold = true)
        SummaryLine("and the bank says", t.statementBalance, bold = true)
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (t.balanced) {
                if (view.savedId != null) "It agrees — closed and signed" else "It agrees"
            } else {
                "Out by ${AmountFormat.format(t.difference)} — nearly always a bank charge " +
                    "or interest. Post it, then come back."
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = AppFontWeight.SemiBold,
            color = if (t.balanced) {
                MaterialTheme.appColors.success
            } else {
                MaterialTheme.appColors.warning
            },
        )
        if (view.savedNote.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = view.savedNote,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.appColors.textMuted,
            )
        }
        Spacer(Modifier.height(10.dp))
        if (view.savedId != null) {
            PrimaryButton(
                text = "Open it again",
                onClick = onReopen,
                enabled = !isWorking,
                compact = true,
            )
        } else {
            PrimaryButton(
                text = "Close this month",
                onClick = onClose,
                // Refused by the server while it does not agree; refused here
                // too, so nobody is invited to try.
                enabled = t.balanced && !isWorking,
                compact = true,
            )
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: Double, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) AppFontWeight.SemiBold else AppFontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = AmountFormat.format(value),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) AppFontWeight.Bold else AppFontWeight.Normal,
        )
    }
}

// ---------------------------------------------------------------------------
// Table
// ---------------------------------------------------------------------------

private fun bankRecColumns(onTick: (BankRecRow) -> Unit): List<ReportColumn<BankRecRow>> = listOf(
    ReportColumn("DATE", ReportColWidth.Fixed(96.dp)) { r, _ ->
        cellText(r.vrDate.ifBlank { "-" })
    },
    ReportColumn("VOUCHER", ReportColWidth.Fixed(120.dp)) { r, _ ->
        cellText(r.vrNo.ifBlank { "-" }, maxLines = 2)
    },
    ReportColumn("WHAT IT WAS", ReportColWidth.Fixed(220.dp)) { r, _ ->
        cellText(r.what.ifBlank { "-" }, maxLines = 2)
    },
    ReportColumn("PAID IN", ReportColWidth.Fixed(104.dp), TextAlign.End) { r, _ ->
        cellText(AmountFormat.formatOrDash(r.debit), align = TextAlign.End)
    },
    ReportColumn("PAID OUT", ReportColWidth.Fixed(104.dp), TextAlign.End) { r, _ ->
        cellText(AmountFormat.formatOrDash(r.credit), align = TextAlign.End)
    },
    ReportColumn("ON THE STATEMENT", ReportColWidth.Fixed(120.dp), TextAlign.Center) { r, _ ->
        ReportTableCell.Slot {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Checkbox(checked = r.cleared, onCheckedChange = { onTick(r) })
            }
        }
    },
)

// ---------------------------------------------------------------------------
// Small shared pieces for the accounts screens
// ---------------------------------------------------------------------------

/** The server's own sentence about a screen, shown where the title is. */
@Composable
internal fun ScreenNote(note: String) {
    Text(
        text = note,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.appColors.textOnScreenMuted,
    )
}

@Composable
internal fun CentredBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** A scrolling column for the screens whose whole body is a form, not a table. */
@Composable
internal fun ScrollingBody(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) { content() }
}

/** The shared date picker every accounts screen opens. */
internal fun pickAccountsDate(
    context: Context,
    current: SimpleDate?,
    onPicked: (SimpleDate) -> Unit,
) {
    val start = current ?: SimpleDate.today()
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            onPicked(SimpleDate(year = year, month = month + 1, day = dayOfMonth))
        },
        start.year,
        start.month - 1,
        start.day,
    ).show()
}
