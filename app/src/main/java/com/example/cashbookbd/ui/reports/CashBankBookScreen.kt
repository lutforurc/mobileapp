package com.example.cashbookbd.ui.reports

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.example.cashbookbd.data.repository.CashBankBookRepository
import com.example.cashbookbd.data.repository.CashBankBookView
import com.example.cashbookbd.data.repository.CashBankRow
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.accounts.pickAccountsDate
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val ALL_BANKS = SelectorOption("", "All Banks")

data class CashBankBookUiState(
    val isBranchesLoading: Boolean = false,
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val startDate: SimpleDate = SimpleDate.today(),
    val endDate: SimpleDate = SimpleDate.today(),
    val selectedBank: SelectorOption = ALL_BANKS,
    val isLoading: Boolean = false,
    val error: String? = null,
    val report: CashBankBookView? = null,
    val hasApplied: Boolean = false,
    val sessionExpired: Boolean = false,
)

/**
 * The cash book with a bank column beside the cash one — the paper double-
 * column book. The two columns are never added together on screen: a
 * contra fills a cell in each on the same row, marked (C), and each
 * account's own two sides are what have to come to the same figure.
 */
class CashBankBookViewModel(
    private val repository: CashBankBookRepository,
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CashBankBookUiState())
    val uiState: StateFlow<CashBankBookUiState> = _uiState.asStateFlow()

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
                    it.copy(isBranchesLoading = false, sessionExpired = it.sessionExpired || result.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onBranch(branch: BranchOption) = _uiState.update { it.copy(selectedBranch = branch) }
    fun onStartDate(date: SimpleDate) = _uiState.update { it.copy(startDate = date) }
    fun onEndDate(date: SimpleDate) = _uiState.update { it.copy(endDate = date) }
    fun onBank(option: SelectorOption) = _uiState.update { it.copy(selectedBank = option) }

    fun apply() {
        val state = _uiState.value
        val branch = state.selectedBranch ?: return
        if (state.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = repository.fetch(
                branchId = branch.id.toString(),
                from = state.startDate.toApi(),
                to = state.endDate.toApi(),
                bankAccountId = state.selectedBank.id.toLongOrNull(),
            )
            when (result) {
                is Resource.Success -> _uiState.update { it.copy(isLoading = false, hasApplied = true, report = result.data) }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, error = result.message, sessionExpired = it.sessionExpired || result.isUnauthorized)
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
                CashBankBookViewModel(
                    repository = ServiceLocator.provideCashBankBookRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CashBankBookScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CashBankBookViewModel = viewModel(factory = CashBankBookViewModel.provideFactory(LocalContext.current)),
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
        title = "Cash & Bank Book",
        currentRoute = Routes.HOTEL,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                AppSelectDropdown(
                    label = "Branch",
                    options = state.branches.map { SelectorOption(it.id.toString(), it.name) },
                    selected = state.selectedBranch?.let { SelectorOption(it.id.toString(), it.name) },
                    onSelected = { option ->
                        state.branches.firstOrNull { it.id.toString() == option.id }?.let(viewModel::onBranch)
                    },
                    placeholder = if (state.isBranchesLoading) "Loading branches…" else "Pick a branch",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PickerField(
                        label = "From",
                        value = state.startDate.toDisplay(),
                        trailingIcon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f),
                        onClick = { pickAccountsDate(context, state.startDate, viewModel::onStartDate) },
                    )
                    PickerField(
                        label = "To",
                        value = state.endDate.toDisplay(),
                        trailingIcon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f),
                        onClick = { pickAccountsDate(context, state.endDate, viewModel::onEndDate) },
                    )
                }
            }
            if (state.report != null || state.hasApplied) {
                item {
                    AppSelectDropdown(
                        label = "Bank Account",
                        options = listOf(ALL_BANKS) + state.report?.banks.orEmpty(),
                        selected = state.selectedBank,
                        onSelected = viewModel::onBank,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                PrimaryButton(
                    text = "Apply",
                    onClick = viewModel::apply,
                    enabled = state.selectedBranch != null && !state.isLoading,
                    isLoading = state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.error != null && state.report == null) {
                item {
                    Text(state.error!!, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }

            state.report?.let { report ->
                if (!report.isBalanced) {
                    item {
                        Text(
                            text = "This book does not foot — read it with care. " +
                                (if (!report.cashBalanced) "Cash column is off. " else "") +
                                (if (!report.bankBalanced) "Bank column is off." else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appColors.danger,
                        )
                    }
                }
                item {
                    CashBankMoneyRow(
                        label = "Balance B/D",
                        debitCash = report.opening.cashDebit,
                        debitBank = report.opening.bankDebit,
                        creditCash = report.opening.cashCredit,
                        creditBank = report.opening.bankCredit,
                        bold = true,
                    )
                }
                if (report.rows.isEmpty()) {
                    item {
                        Text(
                            "Nothing moved in this range.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appColors.textOnScreenMuted,
                        )
                    }
                }
                items(report.rows.size, key = { report.rows[it].mtmId }) { index ->
                    CashBankVoucherRow(report.rows[index])
                }
                item { HorizontalDivider() }
                item {
                    CashBankMoneyRow(
                        label = "Balance C/D",
                        debitCash = report.closing.cashDebit,
                        debitBank = report.closing.bankDebit,
                        creditCash = report.closing.cashCredit,
                        creditBank = report.closing.bankCredit,
                        bold = true,
                    )
                }
                item {
                    CashBankMoneyRow(
                        label = "Total",
                        debitCash = report.totals.debitCash,
                        debitBank = report.totals.debitBank,
                        creditCash = report.totals.creditCash,
                        creditBank = report.totals.creditBank,
                        bold = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun CashBankVoucherRow(row: CashBankRow) {
    val muted = MaterialTheme.appColors.textMuted
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = SimpleDate.fromApi(row.vrDate)?.toDisplay() ?: row.vrDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                )
                Text(row.vrNo, style = MaterialTheme.typography.labelSmall, fontWeight = AppFontWeight.SemiBold)
                if (row.isContra) {
                    Text("(C)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.info)
                }
            }
            Text(
                text = listOfNotNull(
                    row.description.takeIf { it.isNotBlank() },
                    row.bankName.takeIf { it.isNotBlank() }?.let { "→ ($it)" },
                ).joinToString(" "),
                style = MaterialTheme.typography.bodySmall,
            )
            if (row.note.isNotBlank() && row.note != "Contra") {
                Text(row.note, style = MaterialTheme.typography.labelSmall, color = muted)
            }
            CashBankMoneyLine(row.debitCash, row.debitBank, row.creditCash, row.creditBank)
        }
    }
}

@Composable
private fun CashBankMoneyRow(
    label: String,
    debitCash: Double,
    debitBank: Double,
    creditCash: Double,
    creditBank: Double,
    bold: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) AppFontWeight.Bold else AppFontWeight.SemiBold,
        )
        CashBankMoneyLine(debitCash, debitBank, creditCash, creditBank, bold = bold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CashBankMoneyLine(
    debitCash: Double,
    debitBank: Double,
    creditCash: Double,
    creditBank: Double,
    bold: Boolean = false,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (debitCash != 0.0) MoneyTag("Dr Cash", debitCash, bold)
        if (debitBank != 0.0) MoneyTag("Dr Bank", debitBank, bold)
        if (creditCash != 0.0) MoneyTag("Cr Cash", creditCash, bold)
        if (creditBank != 0.0) MoneyTag("Cr Bank", creditBank, bold)
    }
}

@Composable
private fun MoneyTag(label: String, value: Double, bold: Boolean) {
    Row {
        Text("$label: ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textMuted)
        Text(
            AmountFormat.format(value),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (bold) AppFontWeight.SemiBold else null,
        )
    }
}
