package com.example.cashbookbd.ui.accounts

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
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.AccountsRepository
import com.example.cashbookbd.data.repository.AuditEvent
import com.example.cashbookbd.data.repository.AuditTrailView
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.FilterActions
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.PickerField
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
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
 * Audit Trail — **who changed which voucher, and when.**
 *
 * Reading only, and behind a permission of its own: the value of a trail is
 * that the people whose work it records cannot decide who sees it.
 *
 * Two kinds of row arrive together. A `trail` row is a recorded change and
 * carries its fields, old value beside new. A `voucher` row was found on the
 * voucher itself — it knows only who and when, and says so, rather than
 * pretending to a detail it never had.
 */

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

data class AuditTrailUiState(
    val isBranchesLoading: Boolean = false,
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val from: SimpleDate = SimpleDate.today().plusDays(-30),
    val to: SimpleDate = SimpleDate.today(),
    val userId: Long? = null,
    val action: String = "",
    val voucherNo: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val view: AuditTrailView? = null,
    val sessionExpired: Boolean = false,
) {
    val selectedUser get() = view?.users?.firstOrNull { it.id == userId }
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class AuditTrailViewModel(
    private val repository: AccountsRepository,
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuditTrailUiState())
    val uiState: StateFlow<AuditTrailUiState> = _uiState.asStateFlow()

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

    fun onBranch(branch: BranchOption) = _uiState.update { it.copy(selectedBranch = branch) }
    fun onFrom(date: SimpleDate) = _uiState.update { it.copy(from = date) }
    fun onTo(date: SimpleDate) = _uiState.update { it.copy(to = date) }
    fun onUser(id: Long?) = _uiState.update { it.copy(userId = id) }
    fun onAction(value: String) = _uiState.update { it.copy(action = value) }
    fun onVoucherNo(value: String) = _uiState.update { it.copy(voucherNo = value) }

    fun reset() {
        _uiState.update {
            it.copy(
                from = SimpleDate.today().plusDays(-30),
                to = SimpleDate.today(),
                userId = null,
                action = "",
                voucherNo = "",
            )
        }
        load()
    }

    fun load() {
        val state = _uiState.value
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = repository.fetchAuditTrail(
                from = state.from.toApi(),
                to = state.to.toApi(),
                userId = state.userId,
                action = state.action,
                voucherNo = state.voucherNo,
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

    fun dismissMessage() = _uiState.update { it.copy(message = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                AuditTrailViewModel(
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
fun AuditTrailScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuditTrailViewModel = viewModel(
        factory = AuditTrailViewModel.provideFactory(LocalContext.current),
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
        title = "Audit Trail",
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PickerField(
                        label = "From",
                        value = state.from.toDisplay(),
                        trailingIcon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f),
                        onClick = { pickAccountsDate(context, state.from, viewModel::onFrom) },
                    )
                    PickerField(
                        label = "To",
                        value = state.to.toDisplay(),
                        trailingIcon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f),
                        onClick = { pickAccountsDate(context, state.to, viewModel::onTo) },
                    )
                }
                Spacer(Modifier.height(10.dp))
                AppSelectDropdown(
                    label = "Who",
                    options = state.view?.users.orEmpty().map {
                        SelectorOption(it.id.toString(), it.name)
                    },
                    selected = state.selectedUser?.let { SelectorOption(it.id.toString(), it.name) },
                    onSelected = { option -> viewModel.onUser(option.id.toLongOrNull()) },
                    placeholder = "Everybody",
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTextField(
                        value = state.action,
                        onValueChange = viewModel::onAction,
                        label = "What (action)",
                        modifier = Modifier.weight(1f),
                    )
                    AppTextField(
                        value = state.voucherNo,
                        onValueChange = viewModel::onVoucherNo,
                        label = "Voucher no",
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
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
                Spacer(Modifier.height(12.dp))
                FilterActions(
                    onApply = viewModel::load,
                    onReset = viewModel::reset,
                    canApply = !state.isLoading,
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
                        PrimaryButton(text = "Retry", onClick = viewModel::load, compact = true)
                    }
                }

                else -> Box(modifier = Modifier.weight(1f)) {
                    ReportTable(
                        columns = auditColumns,
                        data = state.view?.events.orEmpty(),
                        noDataMessage = "Nothing was recorded in this window.",
                    )
                }
            }
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

private val auditColumns: List<ReportColumn<AuditEvent>> = listOf(
    ReportColumn("WHEN", ReportColWidth.Fixed(140.dp)) { e, _ ->
        cellText(e.at.ifBlank { "-" }, maxLines = 2)
    },
    ReportColumn("WHO", ReportColWidth.Fixed(130.dp)) { e, _ ->
        cellText(e.user.ifBlank { "-" }, maxLines = 2)
    },
    ReportColumn("WHAT", ReportColWidth.Fixed(120.dp)) { e, _ ->
        cellText(e.action.ifBlank { "-" }, maxLines = 2)
    },
    ReportColumn("VOUCHER", ReportColWidth.Fixed(140.dp)) { e, _ ->
        ReportTableCell.Slot { VoucherCell(e) }
    },
    ReportColumn("WHAT CHANGED", ReportColWidth.Fixed(280.dp)) { e, _ ->
        ReportTableCell.Slot { ChangesCell(e) }
    },
)

@Composable
private fun VoucherCell(event: AuditEvent) {
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
        Text(
            text = event.vrNo.ifBlank { "-" },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = AppFontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (event.vrDate.isNotBlank()) {
            Text(
                text = SimpleDate.fromApi(event.vrDate)?.toDisplay() ?: event.vrDate,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.appColors.textOnScreenMuted,
            )
        }
    }
}

/**
 * One line per field: "field: old → new". A row that came off the voucher has
 * no fields at all and says so — an empty cell would read as "nothing changed",
 * which is a different and untrue statement.
 */
@Composable
private fun ChangesCell(event: AuditEvent) {
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
        if (event.changes.isEmpty()) {
            Text(
                text = if (event.isFromVoucher) {
                    "only who and when — this one was found on the voucher"
                } else {
                    "-"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.appColors.textOnScreenMuted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            event.changes.forEach { change ->
                Text(
                    text = "${change.field}: ${change.old.ifBlank { "—" }} → " +
                        change.new.ifBlank { "—" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
