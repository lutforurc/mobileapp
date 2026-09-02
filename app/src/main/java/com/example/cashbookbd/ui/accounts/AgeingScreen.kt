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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.cashbookbd.data.repository.AccountsRepository
import com.example.cashbookbd.data.repository.AgeingRow
import com.example.cashbookbd.data.repository.AgeingView
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
 * Ageing — who owes what, and for how long, both ways round.
 *
 * **Age counts from the day a bill fell due, not from the day it was raised**,
 * which is why a party's credit terms belong on this screen: without them every
 * invoice looks overdue the moment it is written. **A payment clears the oldest
 * bill still standing**, so money received does not simply sit against the
 * newest invoice and leave a two-year-old debt looking fresh.
 *
 * It posts nothing. The one thing it writes is a party's credit terms, and it
 * lives here because this is the screen on which somebody discovers they are
 * missing.
 */

private val SIDES = listOf(
    "receivable" to "Receivable",
    "payable" to "Payable",
)

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

data class AgeingUiState(
    val isBranchesLoading: Boolean = false,
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val asOn: SimpleDate = SimpleDate.today(),
    val side: String = "receivable",
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val view: AgeingView? = null,
    /** The party whose terms are being typed, and what has been typed. */
    val editing: AgeingRow? = null,
    val editingDays: String = "",
    val isSaving: Boolean = false,
    val sessionExpired: Boolean = false,
)

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class AgeingViewModel(
    private val repository: AccountsRepository,
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgeingUiState())
    val uiState: StateFlow<AgeingUiState> = _uiState.asStateFlow()

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

    fun onAsOn(date: SimpleDate) {
        _uiState.update { it.copy(asOn = date) }
        load()
    }

    fun onSide(side: String) {
        _uiState.update { it.copy(side = side) }
        load()
    }

    fun load() {
        val state = _uiState.value
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = repository.fetchAgeing(
                asOn = state.asOn.toApi(),
                side = state.side,
                branchId = state.selectedBranch?.id,
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

    fun startEdit(row: AgeingRow) = _uiState.update {
        it.copy(editing = row, editingDays = row.creditDays?.toString().orEmpty())
    }

    fun onEditingDays(value: String) = _uiState.update {
        it.copy(editingDays = value.filter(Char::isDigit).take(3))
    }

    fun cancelEdit() = _uiState.update { it.copy(editing = null, editingDays = "") }

    /** Blank clears the terms — that party pays cash. 0…365 otherwise. */
    fun saveEdit() {
        val state = _uiState.value
        val row = state.editing ?: return
        val days = state.editingDays.trim().toIntOrNull()
        if (days != null && days !in 0..365) {
            _uiState.update { it.copy(message = "Credit days must be between 0 and 365.") }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = repository.saveAgeingTerms(coa4Id = row.coa4Id, creditDays = days)
            when (result) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSaving = false, editing = null, editingDays = "") }
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
                AgeingViewModel(
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
fun AgeingScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AgeingViewModel = viewModel(
        factory = AgeingViewModel.provideFactory(LocalContext.current),
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
        title = "Ageing",
        currentRoute = Routes.REPORTS,
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SIDES.forEach { (value, label) ->
                        FilterChip(
                            selected = state.side == value,
                            onClick = { viewModel.onSide(value) },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PickerField(
                        label = "As on",
                        value = state.asOn.toDisplay(),
                        trailingIcon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f),
                        onClick = { pickAccountsDate(context, state.asOn, viewModel::onAsOn) },
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        AppSelectDropdown(
                            label = "Branch",
                            options = state.branches.map {
                                SelectorOption(it.id.toString(), it.name)
                            },
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
                    val view = state.view
                    ReportTable(
                        columns = ageingColumns(view?.buckets.orEmpty(), viewModel::startEdit),
                        data = view?.rows.orEmpty(),
                        footerRows = ageingFooter(view),
                        noDataMessage = "Nobody owes anything on this side.",
                    )
                }
            }
        }

        state.editing?.let { row ->
            AlertDialog(
                onDismissRequest = viewModel::cancelEdit,
                title = { Text(row.name.ifBlank { row.partyName.ifBlank { "Terms" } }) },
                text = {
                    Column {
                        AppTextField(
                            value = state.editingDays,
                            onValueChange = viewModel::onEditingDays,
                            label = "Credit days (0–365)",
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Leave it empty for cash — every bill falls due the day it " +
                                "is raised.",
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

/**
 * The bucket columns are built from the server's own keys — they carry an EN
 * DASH ("0–30"), and a re-typed hyphen would quietly read every bucket as zero.
 */
private fun ageingColumns(
    buckets: List<String>,
    onEditTerms: (AgeingRow) -> Unit,
): List<ReportColumn<AgeingRow>> {
    val oldest = buckets.lastOrNull()
    return buildList {
        add(
            ReportColumn<AgeingRow>("CUSTOMER/SUPPLIER", ReportColWidth.Fixed(210.dp)) { r, _ ->
                ReportTableCell.Slot { PartyBlock(r) }
            },
        )
        add(
            ReportColumn<AgeingRow>("TERMS", ReportColWidth.Fixed(90.dp), TextAlign.Center) { r, _ ->
                ReportTableCell.Slot { TermsCell(r, onEditTerms) }
            },
        )
        add(
            ReportColumn<AgeingRow>("NOT YET DUE", ReportColWidth.Fixed(110.dp), TextAlign.End) { r, _ ->
                cellText(AmountFormat.formatOrDash(r.notDue), align = TextAlign.End)
            },
        )
        buckets.forEach { key ->
            add(
                ReportColumn<AgeingRow>(key, ReportColWidth.Fixed(104.dp), TextAlign.End) { r, _ ->
                    val value = r.buckets[key] ?: 0.0
                    ReportTableCell.Slot { BucketCell(value, danger = key == oldest && value > 0.0) }
                },
            )
        }
        add(
            ReportColumn<AgeingRow>("OWED", ReportColWidth.Fixed(120.dp), TextAlign.End) { r, _ ->
                cellText(AmountFormat.formatOrDash(r.outstanding), align = TextAlign.End, bold = true)
            },
        )
    }
}

private fun ageingFooter(view: AgeingView?): List<List<ReportFooterCell>> {
    if (view == null || view.rows.isEmpty()) return emptyList()
    val totals = view.totals
    return listOf(
        buildList {
            add(ReportFooterCell(cellText("Total", bold = true)))
            add(ReportFooterCell(ReportTableCell.Empty))
            add(
                ReportFooterCell(
                    cellText(AmountFormat.formatOrDash(totals.notDue), TextAlign.End, bold = true),
                ),
            )
            view.buckets.forEach { key ->
                add(
                    ReportFooterCell(
                        cellText(
                            AmountFormat.formatOrDash(totals.buckets[key] ?: 0.0),
                            TextAlign.End,
                            bold = true,
                        ),
                    ),
                )
            }
            add(
                ReportFooterCell(
                    cellText(
                        AmountFormat.formatOrDash(totals.outstanding),
                        TextAlign.End,
                        bold = true,
                    ),
                ),
            )
        },
    )
}

/** The name, the number to ring, and how long the oldest bill has stood. */
@Composable
private fun PartyBlock(row: AgeingRow) {
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
        Text(
            text = row.partyName.ifBlank { row.name }.ifBlank { "-" },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = AppFontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (row.mobile.isNotBlank()) {
            Text(
                text = row.mobile,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.appColors.textOnScreenMuted,
                maxLines = 1,
            )
        }
        Text(
            text = if (row.oldestDue.isNotBlank() && row.oldestDays > 0) {
                "oldest fell due ${SimpleDate.fromApi(row.oldestDue)?.toDisplay() ?: row.oldestDue}" +
                    " — ${row.oldestDays} day(s) ago"
            } else {
                "nothing overdue"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (row.oldestDays > 0) {
                MaterialTheme.appColors.warning
            } else {
                MaterialTheme.appColors.textOnScreenMuted
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Blank terms read as "cash", never as an empty cell somebody must interpret. */
@Composable
private fun TermsCell(row: AgeingRow, onEdit: (AgeingRow) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit(row) }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = row.creditDays?.let { "$it d" } ?: "cash",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = AppFontWeight.SemiBold,
            color = MaterialTheme.appColors.textLink,
            textAlign = TextAlign.Center,
        )
    }
}

/** The oldest bucket is painted in danger the moment anything lands in it. */
@Composable
private fun BucketCell(value: Double, danger: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            text = AmountFormat.formatOrDash(value),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (danger) AppFontWeight.Bold else AppFontWeight.Normal,
            color = if (danger) MaterialTheme.appColors.danger else Color.Unspecified,
            textAlign = TextAlign.End,
        )
    }
}
