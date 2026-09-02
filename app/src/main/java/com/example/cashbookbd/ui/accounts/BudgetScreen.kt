package com.example.cashbookbd.ui.accounts

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
import com.example.cashbookbd.data.repository.AccountsRepository
import com.example.cashbookbd.data.repository.BudgetRow
import com.example.cashbookbd.data.repository.BudgetView
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.PickerField
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportFooterCell
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.cellText
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
 * Budget — **what was meant to be spent, against what was.**
 *
 * The actuals are never stored. They are read from the ledger on every request,
 * so an edited voucher moves this report at once and there is no second copy of
 * the truth to fall out of step. The only figure this screen writes is the
 * budget itself, one head at a time.
 *
 * "Should have spent" is the share of the year the months elapsed have earned —
 * it is what makes a mid-year overspend visible, instead of a head looking
 * comfortable in March and impossible in November.
 */

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

data class BudgetUiState(
    val isBranchesLoading: Boolean = false,
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val yearEnd: SimpleDate = SimpleDate.today(),
    val projectId: Long? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val view: BudgetView? = null,
    /** The head whose budget is being typed, and what has been typed. */
    val editing: BudgetRow? = null,
    val editingAmount: String = "",
    val isSaving: Boolean = false,
    val sessionExpired: Boolean = false,
) {
    val selectedProject get() = view?.projects?.firstOrNull { it.id == projectId }
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class BudgetViewModel(
    private val repository: AccountsRepository,
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    init {
        loadBranches()
        load()
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
        load()
    }

    fun onYearEnd(date: SimpleDate) {
        _uiState.update { it.copy(yearEnd = date) }
        load()
    }

    fun onProject(id: Long?) {
        _uiState.update { it.copy(projectId = id) }
        load()
    }

    fun load() {
        val state = _uiState.value
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = repository.fetchBudget(
                branchId = state.selectedBranch?.id,
                yearEnd = state.yearEnd.toApi(),
                projectId = state.projectId,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, view = result.data, error = null)
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

    fun startEdit(row: BudgetRow) = _uiState.update {
        it.copy(
            editing = row,
            editingAmount = if (row.budget == 0.0) "" else AmountFormat.format(row.budget),
        )
    }

    fun onEditingAmount(value: String) = _uiState.update { it.copy(editingAmount = value) }

    fun cancelEdit() = _uiState.update { it.copy(editing = null, editingAmount = "") }

    /** Blank or zero removes the row: "no budget" and "a budget of nothing" are one statement. */
    fun saveEdit() {
        val state = _uiState.value
        val row = state.editing ?: return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = repository.saveBudget(
                yearEnd = state.yearEnd.toApi(),
                coa4Id = row.coa4Id,
                amount = state.editingAmount,
                projectId = state.projectId,
                branchId = state.selectedBranch?.id,
                note = "",
            )
            when (result) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(isSaving = false, editing = null, editingAmount = "")
                    }
                    load()
                }

                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        editing = null,
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
                val appContext = context.applicationContext
                BudgetViewModel(
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
fun BudgetScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BudgetViewModel = viewModel(
        factory = BudgetViewModel.provideFactory(LocalContext.current),
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
        title = "Budget",
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
                state.view?.note?.takeIf { it.isNotBlank() }?.let {
                    ScreenNote(it)
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        AppSelectDropdown(
                            label = "Branch",
                            options = state.branches.map { SelectorOption(it.id.toString(), it.name) },
                            selected = state.selectedBranch?.let {
                                SelectorOption(it.id.toString(), it.name)
                            },
                            onSelected = { option ->
                                state.branches.firstOrNull { it.id.toString() == option.id }
                                    ?.let(viewModel::onBranch)
                            },
                            placeholder = if (state.isBranchesLoading) {
                                "Loading branches…"
                            } else {
                                "All branches"
                            },
                        )
                    }
                    PickerField(
                        label = "Year ends",
                        value = state.yearEnd.toDisplay(),
                        trailingIcon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f),
                        onClick = { pickAccountsDate(context, state.yearEnd, viewModel::onYearEnd) },
                    )
                }
                if (state.view?.projects.orEmpty().isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    AppSelectDropdown(
                        label = "Project",
                        options = state.view?.projects.orEmpty().map {
                            SelectorOption(it.id.toString(), it.name)
                        },
                        selected = state.selectedProject?.let {
                            SelectorOption(it.id.toString(), it.name)
                        },
                        onSelected = { option -> viewModel.onProject(option.id.toLongOrNull()) },
                        placeholder = "Every project",
                    )
                    if (state.projectId != null) {
                        LinkButton(text = "All projects", onClick = { viewModel.onProject(null) })
                    }
                }
                state.view?.let { view ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${view.yearStart} to ${view.yearEnd} · " +
                            "${view.monthsElapsed} month(s) gone · " +
                            "spending read up to ${view.actualsUpTo}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.appColors.textOnScreenMuted,
                    )
                }
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
                        PrimaryButton(text = "Retry", onClick = viewModel::load, compact = true)
                    }
                }

                else -> Box(modifier = Modifier.weight(1f)) {
                    ReportTable(
                        columns = budgetColumns(viewModel::startEdit),
                        data = state.view?.rows.orEmpty(),
                        footerRows = budgetFooter(state.view),
                        noDataMessage = "No expense heads to budget for.",
                    )
                }
            }
        }

        state.editing?.let { row ->
            AlertDialog(
                onDismissRequest = viewModel::cancelEdit,
                title = { Text(row.name.ifBlank { "Budget" }) },
                text = {
                    Column {
                        if (row.groupName.isNotBlank()) {
                            Text(
                                text = row.groupName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.appColors.textMuted,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        AppTextField(
                            value = state.editingAmount,
                            onValueChange = viewModel::onEditingAmount,
                            label = "Budget for the year",
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Leave it empty to take the budget off this head.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.appColors.textMuted,
                        )
                    }
                },
                confirmButton = {
                    PrimaryButton(
                        text = "Save",
                        onClick = viewModel::saveEdit,
                        enabled = !state.isSaving,
                        isLoading = state.isSaving,
                        compact = true,
                    )
                },
                dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::cancelEdit) },
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

// ---------------------------------------------------------------------------
// Table
// ---------------------------------------------------------------------------

private fun budgetColumns(onEdit: (BudgetRow) -> Unit): List<ReportColumn<BudgetRow>> = listOf(
    ReportColumn("HEAD", ReportColWidth.Fixed(200.dp)) { r, _ ->
        ReportTableCell.Slot { HeadCell(r) }
    },
    ReportColumn("BUDGET FOR THE YEAR", ReportColWidth.Fixed(130.dp), TextAlign.End) { r, _ ->
        ReportTableCell.Slot { EditableBudgetCell(r, onEdit) }
    },
    ReportColumn("SHOULD HAVE SPENT", ReportColWidth.Fixed(120.dp), TextAlign.End) { r, _ ->
        cellText(AmountFormat.formatOrDash(r.expected), align = TextAlign.End)
    },
    ReportColumn("ACTUALLY SPENT", ReportColWidth.Fixed(120.dp), TextAlign.End) { r, _ ->
        cellText(AmountFormat.formatOrDash(r.actual), align = TextAlign.End)
    },
    ReportColumn("LEFT FOR THE YEAR", ReportColWidth.Fixed(120.dp), TextAlign.End) { r, _ ->
        cellText(AmountFormat.formatOrDash(r.left), align = TextAlign.End)
    },
)

private fun budgetFooter(view: BudgetView?): List<List<ReportFooterCell>> {
    val totals = view?.totals ?: return emptyList()
    return listOf(
        listOf(
            ReportFooterCell(cellText("Total", bold = true)),
            ReportFooterCell(
                cellText(AmountFormat.formatOrDash(totals.budget), TextAlign.End, bold = true),
            ),
            ReportFooterCell(
                cellText(AmountFormat.formatOrDash(totals.expected), TextAlign.End, bold = true),
            ),
            ReportFooterCell(
                cellText(AmountFormat.formatOrDash(totals.actual), TextAlign.End, bold = true),
            ),
            ReportFooterCell(
                cellText(AmountFormat.formatOrDash(totals.left), TextAlign.End, bold = true),
            ),
        ),
    )
}

@Composable
private fun HeadCell(row: BudgetRow) {
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
        Text(
            text = row.name.ifBlank { "-" },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = AppFontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (row.groupName.isNotBlank()) {
            Text(
                text = row.groupName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.appColors.textOnScreenMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The one editable figure on the screen — everything else is read from the ledger. */
@Composable
private fun EditableBudgetCell(row: BudgetRow, onEdit: (BudgetRow) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit(row) }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            text = AmountFormat.formatOrDash(row.budget),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = AppFontWeight.SemiBold,
            color = MaterialTheme.appColors.textLink,
            textAlign = TextAlign.End,
        )
    }
}
