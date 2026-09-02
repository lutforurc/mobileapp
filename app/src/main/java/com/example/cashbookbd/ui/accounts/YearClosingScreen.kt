package com.example.cashbookbd.ui.accounts

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.cashbookbd.data.repository.AccountsRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.data.repository.YearClosingRow
import com.example.cashbookbd.data.repository.YearClosingView
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SummaryTile
import com.example.cashbookbd.ui.reports.PickerField
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

/**
 * Year Closing — **every profit-and-loss head emptied into capital**, so the new
 * year starts from nothing.
 *
 * The heaviest act in the accounts, so it is shown leg by leg before it is
 * done: the plan is a read, and running it is a separate, confirmed act that
 * posts one real voucher. Undoing is a CONTRA voucher, never a deletion, and
 * the most recent closing must be reversed before an older one can be.
 */

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

data class YearClosingUiState(
    val isBranchesLoading: Boolean = false,
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val yearEnd: SimpleDate = SimpleDate.today(),
    val capitalCoa4Id: Long? = null,
    val note: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val view: YearClosingView? = null,
    val isWorking: Boolean = false,
    val confirmRun: Boolean = false,
    val confirmReverse: YearClosingRow? = null,
    val sessionExpired: Boolean = false,
) {
    val capitalHead get() = view?.capitalHeads?.firstOrNull { it.id == capitalCoa4Id }

    /** Nothing to close is a valid answer, and the button must say so. */
    val canRun: Boolean
        get() = capitalCoa4Id != null &&
            view?.plan?.legs.orEmpty().isNotEmpty() &&
            view?.already == null &&
            !isWorking
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class YearClosingViewModel(
    private val repository: AccountsRepository,
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(YearClosingUiState())
    val uiState: StateFlow<YearClosingUiState> = _uiState.asStateFlow()

    init {
        loadBranches()
        loadPlan()
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
                    it.copy(
                        isBranchesLoading = false,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }

                Resource.Loading -> Unit
            }
        }
    }

    fun onBranch(branch: BranchOption) {
        _uiState.update { it.copy(selectedBranch = branch) }
        loadPlan()
    }

    fun onYearEnd(date: SimpleDate) {
        _uiState.update { it.copy(yearEnd = date) }
        loadPlan()
    }

    fun onCapitalHead(id: Long) {
        _uiState.update { it.copy(capitalCoa4Id = id) }
        loadPlan()
    }

    fun loadPlan() {
        val state = _uiState.value
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = repository.fetchYearClosingPlan(
                yearEnd = state.yearEnd.toApi(),
                branchId = state.selectedBranch?.id,
                capitalCoa4Id = state.capitalCoa4Id,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        view = result.data,
                        note = result.data.note,
                        error = null,
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

    fun askRun() = _uiState.update { it.copy(confirmRun = true) }
    fun cancelRun() = _uiState.update { it.copy(confirmRun = false) }

    fun confirmRun() {
        val state = _uiState.value
        val capital = state.capitalCoa4Id ?: return
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            finish(
                repository.runYearClosing(
                    yearEnd = state.yearEnd.toApi(),
                    capitalCoa4Id = capital,
                    branchId = state.selectedBranch?.id,
                    note = "",
                ),
            )
        }
    }

    fun askReverse(row: YearClosingRow) = _uiState.update { it.copy(confirmReverse = row) }
    fun cancelReverse() = _uiState.update { it.copy(confirmReverse = null) }

    fun confirmReverse() {
        val row = _uiState.value.confirmReverse ?: return
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch { finish(repository.reverseYearClosing(row.id)) }
    }

    private fun finish(result: Resource<String>) {
        when (result) {
            is Resource.Success -> {
                _uiState.update {
                    it.copy(
                        isWorking = false,
                        confirmRun = false,
                        confirmReverse = null,
                        message = result.data,
                    )
                }
                loadPlan()
            }

            is Resource.Error -> _uiState.update {
                it.copy(
                    isWorking = false,
                    confirmRun = false,
                    confirmReverse = null,
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
                val appContext = context.applicationContext
                YearClosingViewModel(
                    repository = AccountsRepository.get(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@Composable
fun YearClosingScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: YearClosingViewModel = viewModel(
        factory = YearClosingViewModel.provideFactory(LocalContext.current),
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
        title = "Year Closing",
        currentRoute = Routes.TRANSACTIONS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (state.note.isNotBlank()) {
                ScreenNote(state.note)
                Spacer(Modifier.height(10.dp))
            }

            PickerField(
                label = "Year ends on",
                value = state.yearEnd.toDisplay(),
                trailingIcon = Icons.Filled.DateRange,
                modifier = Modifier.fillMaxWidth(),
                onClick = { pickAccountsDate(context, state.yearEnd, viewModel::onYearEnd) },
            )
            Spacer(Modifier.height(10.dp))
            AppSelectDropdown(
                label = "Branch",
                options = state.branches.map { SelectorOption(it.id.toString(), it.name) },
                selected = state.selectedBranch?.let { SelectorOption(it.id.toString(), it.name) },
                onSelected = { option ->
                    state.branches.firstOrNull { it.id.toString() == option.id }
                        ?.let(viewModel::onBranch)
                },
                placeholder = if (state.isBranchesLoading) "Loading branches…" else "All branches",
            )
            Spacer(Modifier.height(10.dp))
            AppSelectDropdown(
                label = "Profit goes to",
                options = state.view?.capitalHeads.orEmpty().map {
                    SelectorOption(it.id.toString(), it.name, it.groupName)
                },
                selected = state.capitalHead?.let {
                    SelectorOption(it.id.toString(), it.name, it.groupName)
                },
                onSelected = { option -> option.id.toLongOrNull()?.let(viewModel::onCapitalHead) },
                placeholder = "Pick the capital head",
            )

            Spacer(Modifier.height(14.dp))

            when {
                state.isLoading && state.view == null -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground) }

                state.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.error.orEmpty(),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(text = "Retry", onClick = viewModel::loadPlan, compact = true)
                }

                state.view != null -> PlanBody(
                    state = state,
                    view = state.view!!,
                    onRun = viewModel::askRun,
                    onReverse = viewModel::askReverse,
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        if (state.confirmRun) {
            val plan = state.view?.plan
            AlertDialog(
                onDismissRequest = viewModel::cancelRun,
                title = { Text("Close the year?") },
                text = {
                    Text(
                        "This POSTS one voucher. " +
                            "${plan?.headsClosed ?: 0} profit-and-loss heads are emptied into " +
                            "${state.capitalHead?.name ?: "capital"}, leaving " +
                            "${AmountFormat.format(plan?.profit ?: 0.0)} in capital. It can be " +
                            "reversed, but only with a contra voucher — never deleted.",
                    )
                },
                confirmButton = {
                    PrimaryButton(
                        text = "Close the year",
                        onClick = viewModel::confirmRun,
                        enabled = !state.isWorking,
                        isLoading = state.isWorking,
                        compact = true,
                    )
                },
                dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::cancelRun) },
            )
        }

        state.confirmReverse?.let { row ->
            AlertDialog(
                onDismissRequest = viewModel::cancelReverse,
                title = { Text("Reverse the closing of ${row.yearEnd}?") },
                text = {
                    Text(
                        "This POSTS a contra voucher that undoes the closing leg for leg. " +
                            "The original stays where it is — nothing is deleted. Only the " +
                            "most recent closing may be reversed.",
                    )
                },
                confirmButton = {
                    PrimaryButton(
                        text = "Reverse it",
                        onClick = viewModel::confirmReverse,
                        enabled = !state.isWorking,
                        isLoading = state.isWorking,
                        compact = true,
                    )
                },
                dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::cancelReverse) },
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

@Composable
private fun PlanBody(
    state: YearClosingUiState,
    view: YearClosingView,
    onRun: () -> Unit,
    onReverse: (YearClosingRow) -> Unit,
) {
    val plan = view.plan

    SummaryTile(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "The year ${plan.yearStart} to ${plan.yearEnd}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.appColors.textMuted,
        )
        Spacer(Modifier.height(6.dp))
        FigureLine("Income", plan.incomeTotal)
        FigureLine("Expense", plan.expenseTotal)
        FigureLine(
            label = if (plan.profit >= 0) "Profit" else "Loss",
            value = if (plan.profit >= 0) plan.profit else -plan.profit,
            bold = true,
            colour = if (plan.profit >= 0) {
                MaterialTheme.appColors.success
            } else {
                MaterialTheme.appColors.danger
            },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${plan.headsClosed} head(s) would be emptied",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.appColors.textMuted,
        )
    }

    Spacer(Modifier.height(12.dp))

    if (view.already != null) {
        SummaryTile(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "This year is already closed.",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = AppFontWeight.SemiBold,
                color = MaterialTheme.appColors.success,
            )
            Text(
                text = "Voucher ${view.already.vrNo.ifBlank { "—" }} · " +
                    AmountFormat.format(view.already.profit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.appColors.textMuted,
            )
            Spacer(Modifier.height(8.dp))
            PrimaryButton(
                text = "Reverse it",
                onClick = { onReverse(view.already) },
                enabled = !state.isWorking,
                compact = true,
            )
        }
    } else {
        Text(
            text = "What would be posted",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = AppFontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        if (plan.legs.isEmpty()) {
            Text(
                text = "Nothing to close for this year — no profit-and-loss head has a balance.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.textOnScreenMuted,
            )
        } else {
            // EVERY leg, not a sample: this is the whole of what will be posted,
            // and a summary would be exactly the thing worth hiding.
            SummaryTile(modifier = Modifier.fillMaxWidth()) {
                plan.legs.forEach { leg ->
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
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            text = "Close the year",
            onClick = onRun,
            enabled = state.canRun,
            isLoading = state.isWorking,
        )
        if (state.capitalCoa4Id == null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Pick the capital head the profit goes to first.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.appColors.textOnScreenMuted,
            )
        }
    }

    if (view.history.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Years already closed",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = AppFontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        view.history.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    text = row.yearEnd,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = row.vrNo.ifBlank { "—" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.appColors.textOnScreenMuted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = AmountFormat.format(row.profit),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = AppFontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

@Composable
private fun FigureLine(
    label: String,
    value: Double,
    bold: Boolean = false,
    colour: androidx.compose.ui.graphics.Color? = null,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
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
            color = colour ?: androidx.compose.ui.graphics.Color.Unspecified,
        )
    }
}
