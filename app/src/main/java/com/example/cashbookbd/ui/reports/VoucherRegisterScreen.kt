package com.example.cashbookbd.ui.reports

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.example.cashbookbd.data.repository.AdminRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.data.repository.VoucherRegisterGrand
import com.example.cashbookbd.data.repository.VoucherRegisterMonth
import com.example.cashbookbd.data.repository.VoucherRegisterRepository
import com.example.cashbookbd.data.repository.VoucherRegisterVoucherRow
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.accounts.pickAccountsDate
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.LinkButton
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

private val ALL_VOUCHER_TYPES = SelectorOption("", "All Voucher Types")

data class VoucherRegisterUiState(
    val isBranchesLoading: Boolean = false,
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val voucherTypes: List<SelectorOption> = listOf(ALL_VOUCHER_TYPES),
    val selectedVoucherType: SelectorOption = ALL_VOUCHER_TYPES,
    val startDate: SimpleDate? = null,
    val endDate: SimpleDate = SimpleDate.today(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val branchName: String = "",
    val months: List<VoucherRegisterMonth> = emptyList(),
    val grand: VoucherRegisterGrand? = null,
    val hasApplied: Boolean = false,
    /** The one expanded month, if any — only one open at a time. */
    val expandedMonth: String? = null,
    val isLoadingVouchers: Boolean = false,
    val vouchers: List<VoucherRegisterVoucherRow> = emptyList(),
    val sessionExpired: Boolean = false,
) {
    val showsStock: Boolean get() = months.any { it.purchaseQty != 0.0 || it.salesQty != 0.0 }
}

/**
 * Tally's Voucher Monthly Register — one row per month, blank where nothing
 * was written; tap a month with a count to see the vouchers behind it. Read
 * only: nothing here posts, edits or approves a voucher.
 */
class VoucherRegisterViewModel(
    private val repository: VoucherRegisterRepository,
    private val reportRepository: ReportRepository,
    private val adminRepository: AdminRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoucherRegisterUiState())
    val uiState: StateFlow<VoucherRegisterUiState> = _uiState.asStateFlow()

    init {
        loadBranches()
        loadVoucherTypes()
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

    private fun loadVoucherTypes() {
        viewModelScope.launch {
            when (val result = adminRepository.fetchVoucherTypes()) {
                is Resource.Success -> _uiState.update { it.copy(voucherTypes = listOf(ALL_VOUCHER_TYPES) + result.data) }
                else -> Unit
            }
        }
    }

    fun onBranch(branch: BranchOption) = _uiState.update { it.copy(selectedBranch = branch) }
    fun onVoucherType(option: SelectorOption) = _uiState.update { it.copy(selectedVoucherType = option) }
    fun onStartDate(date: SimpleDate) = _uiState.update { it.copy(startDate = date) }
    fun onEndDate(date: SimpleDate) = _uiState.update { it.copy(endDate = date) }

    fun apply() {
        val state = _uiState.value
        val branch = state.selectedBranch ?: return
        if (state.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null, expandedMonth = null, vouchers = emptyList()) }
        viewModelScope.launch {
            // Left blank, the server fills the company's own financial year.
            val from = state.startDate?.toApi() ?: ""
            val result = repository.fetchMonthly(
                branchId = branch.id.toString(),
                voucherTypeId = state.selectedVoucherType.id.toLongOrNull(),
                from = from,
                to = state.endDate.toApi(),
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        hasApplied = true,
                        branchName = result.data.branchName,
                        months = result.data.months,
                        grand = result.data.grand,
                        startDate = it.startDate ?: SimpleDate.fromApi(result.data.from),
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, error = result.message, sessionExpired = it.sessionExpired || result.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun toggleMonth(month: VoucherRegisterMonth) {
        val state = _uiState.value
        if (month.total == 0) return
        if (state.expandedMonth == month.month) {
            _uiState.update { it.copy(expandedMonth = null, vouchers = emptyList()) }
            return
        }
        val branch = state.selectedBranch ?: return
        _uiState.update { it.copy(expandedMonth = month.month, isLoadingVouchers = true, vouchers = emptyList()) }
        viewModelScope.launch {
            val result = repository.fetchVouchers(
                branchId = branch.id.toString(),
                voucherTypeId = state.selectedVoucherType.id.toLongOrNull(),
                month = month.month,
            )
            when (result) {
                is Resource.Success -> _uiState.update { it.copy(isLoadingVouchers = false, vouchers = result.data) }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoadingVouchers = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
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
                VoucherRegisterViewModel(
                    repository = ServiceLocator.provideVoucherRegisterRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                    adminRepository = ServiceLocator.provideAdminRepository(appContext),
                )
            }
        }
    }
}

@Composable
fun VoucherRegisterScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VoucherRegisterViewModel = viewModel(factory = VoucherRegisterViewModel.provideFactory(LocalContext.current)),
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
        title = "Voucher Register",
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
                AppSelectDropdown(
                    label = "Voucher Type",
                    options = state.voucherTypes,
                    selected = state.selectedVoucherType,
                    onSelected = viewModel::onVoucherType,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PickerField(
                        label = "From",
                        value = state.startDate?.toDisplay().orEmpty(),
                        trailingIcon = Icons.Filled.DateRange,
                        placeholder = "Financial year start",
                        modifier = Modifier.weight(1f),
                        onClick = { pickAccountsDate(context, state.startDate ?: state.endDate, viewModel::onStartDate) },
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
            item {
                PrimaryButton(
                    text = "Apply",
                    onClick = viewModel::apply,
                    enabled = state.selectedBranch != null && !state.isLoading,
                    isLoading = state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            when {
                state.error != null && !state.hasApplied -> item {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error!!, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                    }
                }

                state.hasApplied -> {
                    item {
                        Text(
                            text = "${state.branchName} · ${state.selectedVoucherType.label}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = AppFontWeight.SemiBold,
                        )
                    }
                    item { VoucherRegisterHeaderRow(showsStock = state.showsStock) }
                    if (state.months.all { it.total == 0 }) {
                        item {
                            Text(
                                "Nothing was written in this range.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.appColors.textOnScreenMuted,
                            )
                        }
                    }
                    state.months.forEachIndexed { index, month ->
                        item(key = "month-$index") {
                            Column {
                                VoucherRegisterMonthRow(
                                    month = month,
                                    showsStock = state.showsStock,
                                    expanded = state.expandedMonth == month.month,
                                    onClick = { viewModel.toggleMonth(month) },
                                )
                                if (state.expandedMonth == month.month) {
                                    VoucherRegisterDetail(isLoading = state.isLoadingVouchers, rows = state.vouchers)
                                }
                            }
                        }
                    }
                    state.grand?.let { grand ->
                        item {
                            HorizontalDivider()
                            VoucherRegisterGrandRow(grand, state.showsStock)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoucherRegisterHeaderRow(showsStock: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Month", style = MaterialTheme.typography.labelSmall, fontWeight = AppFontWeight.SemiBold, modifier = Modifier.weight(2f))
        Text("Total", style = MaterialTheme.typography.labelSmall, fontWeight = AppFontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        if (showsStock) {
            Text("Qty", style = MaterialTheme.typography.labelSmall, fontWeight = AppFontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
        Text("Amount", style = MaterialTheme.typography.labelSmall, fontWeight = AppFontWeight.SemiBold, modifier = Modifier.weight(2f), textAlign = TextAlign.End)
    }
}

@Composable
private fun VoucherRegisterMonthRow(
    month: VoucherRegisterMonth,
    showsStock: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val muted = MaterialTheme.appColors.textMuted
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let { if (month.total > 0) it.clickable(onClick = onClick) else it }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(2f)) {
                Text(month.label, style = MaterialTheme.typography.bodyMedium)
                if (month.cancelled > 0) {
                    Text("${month.cancelled} cancelled", style = MaterialTheme.typography.labelSmall, color = muted)
                }
            }
            Text(
                text = if (month.total == 0) "—" else month.total.toString(),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (showsStock) {
                val qty = month.purchaseQty + month.salesQty
                Text(
                    text = if (qty == 0.0) "—" else AmountFormat.format(qty, 0),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = if (month.amount == 0.0) "—" else AmountFormat.format(month.amount),
                modifier = Modifier.weight(2f),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = AppFontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun VoucherRegisterGrandRow(grand: VoucherRegisterGrand, showsStock: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Total", style = MaterialTheme.typography.bodyMedium, fontWeight = AppFontWeight.Bold, modifier = Modifier.weight(2f))
        Text(grand.total.toString(), modifier = Modifier.weight(1f), textAlign = TextAlign.End, fontWeight = AppFontWeight.Bold)
        if (showsStock) {
            Text(
                AmountFormat.format(grand.purchaseQty + grand.salesQty, 0),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
                fontWeight = AppFontWeight.Bold,
            )
        }
        Text(AmountFormat.format(grand.amount), modifier = Modifier.weight(2f), textAlign = TextAlign.End, fontWeight = AppFontWeight.Bold)
    }
}

@Composable
private fun VoucherRegisterDetail(isLoading: Boolean, rows: List<VoucherRegisterVoucherRow>) {
    val muted = MaterialTheme.appColors.textMuted
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (isLoading) {
            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (rows.isEmpty()) {
            Text("Nothing behind this month.", style = MaterialTheme.typography.labelSmall, color = muted)
        } else {
            rows.forEach { row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row {
                            Text(row.vrNo, style = MaterialTheme.typography.labelSmall, fontWeight = AppFontWeight.SemiBold)
                            if (row.isCancelled) {
                                Text(
                                    " · Cancelled",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.appColors.danger,
                                )
                            }
                        }
                        Text(
                            text = listOf(SimpleDate.fromApi(row.vrDate)?.toDisplay() ?: row.vrDate, row.particulars)
                                .filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = muted,
                        )
                    }
                    Text(AmountFormat.format(row.amount), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
