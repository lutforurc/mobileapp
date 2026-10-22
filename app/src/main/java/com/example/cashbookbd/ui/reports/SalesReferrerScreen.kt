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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.core.jLong
import com.example.cashbookbd.core.jNum
import com.example.cashbookbd.core.jObjs
import com.example.cashbookbd.core.jPayload
import com.example.cashbookbd.core.jStr
import com.example.cashbookbd.data.remote.WebApi
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.FilterActions
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReferrerRow(
    val referrerId: Long,
    val name: String,
    val mobile: String,
    val bills: Double,
    val total: Double,
    val discount: Double,
)

data class ReferrerBill(
    val id: String,
    val vrDate: String,
    val vrNo: String,
    val customer: String,
    val total: Double,
    val discount: Double,
)

/** One row of the referrer list itself (`referrers`). */
data class Referrer(
    val id: Long,
    val name: String,
    val mobile: String,
    val address: String,
    val active: Boolean,
)

data class SalesReferrerUiState(
    val branch: BranchPick = BranchPick(),
    val startDate: SimpleDate? = null,
    val endDate: SimpleDate? = null,
    val isLoading: Boolean = false,
    val rows: List<ReferrerRow>? = null,
    val openId: Long? = null,
    val bills: List<ReferrerBill> = emptyList(),
    val isLoadingBills: Boolean = false,
    // The referrer list (the web's ReferrerMasterModal).
    val showMaster: Boolean = false,
    val referrers: List<Referrer> = emptyList(),
    val isLoadingReferrers: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null,
    val sessionExpired: Boolean = false,
)

/**
 * Who recommended a sale, and what that came to — the referrers and their
 * totals, and the bills behind whichever one is opened (SalesReferrer.tsx).
 *
 * An account only: nothing was posted for this and nothing changed on the
 * bill. The boxes open on the first day of the financial year the business
 * date falls in, through the date itself — the Voucher Register's range.
 * Only bills somebody brought are counted; there is no "No referrer" row.
 */
class SalesReferrerViewModel(
    private val api: WebApi,
    private val reportRepository: ReportRepository,
    private val ownBranchId: Long?,
    private val fyStartMonth: Int,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SalesReferrerUiState())
    val uiState: StateFlow<SalesReferrerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            when (val result = reportRepository.loadBranchPick(ownBranchId)) {
                is Resource.Success -> _uiState.update {
                    val trx = result.data.trxDate
                    it.copy(branch = result.data, startDate = trx?.let(::yearStart), endDate = trx)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(branch = it.branch.copy(loading = false), message = result.message, sessionExpired = result.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    /** First day of the financial year [date] falls in. */
    private fun yearStart(date: SimpleDate): SimpleDate {
        val year = if (date.month >= fyStartMonth) date.year else date.year - 1
        return SimpleDate(year, fyStartMonth, 1)
    }

    fun onBranch(id: String) = _uiState.update { it.copy(branch = it.branch.pick(id)) }
    fun onStart(d: SimpleDate) = _uiState.update { it.copy(startDate = d) }
    fun onEnd(d: SimpleDate) = _uiState.update { it.copy(endDate = d) }

    fun reset() = _uiState.update {
        val trx = it.branch.trxDate
        it.copy(startDate = trx?.let(::yearStart), endDate = trx, rows = null, openId = null, bills = emptyList())
    }

    private fun rangeParams(): MutableMap<String, String> {
        val s = _uiState.value
        return buildMap {
            put("branch_id", s.branch.selectedId?.toString().orEmpty())
            s.startDate?.let { put("start_date", it.toApi()) }
            s.endDate?.let { put("end_date", it.toApi()) }
        }.toMutableMap()
    }

    fun apply() {
        val s = _uiState.value
        if (s.branch.selectedId == null) {
            _uiState.update { it.copy(message = "Choose a branch first.") }
            return
        }
        if (s.isLoading) return
        _uiState.update { it.copy(isLoading = true, openId = null, bills = emptyList()) }
        viewModelScope.launch {
            when (val result = api.get("reports/referrer-sales", rangeParams())) {
                is Resource.Success -> {
                    val data = result.data.jPayload()
                    val rows = data.jObjs("rows").map { o ->
                        ReferrerRow(
                            referrerId = o.jLong("referrer_id") ?: 0L,
                            name = o.jStr("name"),
                            mobile = o.jStr("mobile"),
                            bills = o.jNum("bills"),
                            total = o.jNum("total"),
                            discount = o.jNum("discount"),
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            rows = rows,
                            // The range actually counted, back into empty boxes.
                            startDate = it.startDate ?: SimpleDate.fromApi(data.jStr("from")),
                            endDate = it.endDate ?: SimpleDate.fromApi(data.jStr("to")),
                        )
                    }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, rows = null, message = result.message, sessionExpired = result.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    /** One referrer's bills at a time: two open lists is not a screen anyone reads. */
    fun toggle(row: ReferrerRow) {
        if (_uiState.value.openId == row.referrerId) {
            _uiState.update { it.copy(openId = null, bills = emptyList()) }
            return
        }
        _uiState.update { it.copy(openId = row.referrerId, bills = emptyList(), isLoadingBills = true) }
        viewModelScope.launch {
            val params = rangeParams().apply { put("referrer_id", row.referrerId.toString()) }
            when (val result = api.get("reports/referrer-sales/bills", params)) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoadingBills = false,
                        bills = result.data.jPayload().jObjs("rows").map { o ->
                            ReferrerBill(
                                id = o.jStr("id"),
                                vrDate = o.jStr("vr_date"),
                                vrNo = o.jStr("vr_no"),
                                customer = o.jStr("customer"),
                                total = o.jNum("total"),
                                discount = o.jNum("discount"),
                            )
                        },
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoadingBills = false, openId = null, message = result.message, sessionExpired = result.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    // ---- The referrer list --------------------------------------------------

    fun openMaster() {
        _uiState.update { it.copy(showMaster = true) }
        loadReferrers()
    }

    fun closeMaster() = _uiState.update { it.copy(showMaster = false) }

    private fun loadReferrers() {
        _uiState.update { it.copy(isLoadingReferrers = true) }
        viewModelScope.launch {
            when (val result = api.get("referrers")) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoadingReferrers = false,
                        referrers = result.data.jPayload().jObjs("rows").map { o ->
                            Referrer(
                                id = o.jLong("id") ?: 0L,
                                name = o.jStr("name"),
                                mobile = o.jStr("mobile"),
                                address = o.jStr("address"),
                                active = o.jStr("status") == "1",
                            )
                        },
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoadingReferrers = false, message = result.message, sessionExpired = result.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    /** Adds ([id] null) or updates one referrer. A name is required. */
    fun saveReferrer(id: Long?, name: String, mobile: String, address: String, onDone: () -> Unit) {
        if (name.isBlank()) {
            _uiState.update { it.copy(message = "A name is required.") }
            return
        }
        val body = mapOf<String, Any>("name" to name.trim(), "mobile" to mobile.trim(), "address" to address.trim())
        runMasterWrite(if (id == null) "Referrer added." else "Referrer updated.", onDone) {
            if (id == null) api.postMap("referrers", body) else api.put("referrers/$id", body)
        }
    }

    /**
     * Retires or reactivates. Nothing is ever deleted: last season's bills
     * name these rows, and a row taken away would leave them naming nobody.
     */
    fun toggleStatus(r: Referrer) =
        runMasterWrite("Referrer updated.", {}) {
            api.patch("referrers/${r.id}/status", mapOf("status" to if (r.active) 0 else 1))
        }

    private fun runMasterWrite(fallback: String, onDone: () -> Unit, block: suspend () -> Resource<JsonObject>) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = block()) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSaving = false, message = result.data.jStr("message").ifBlank { fallback }) }
                    onDone()
                    loadReferrers()
                    if (_uiState.value.rows != null) apply()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isSaving = false, message = result.message, sessionExpired = result.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val app = context.applicationContext
                val settings = ServiceLocator.provideSessionManager(app).state.value.settings
                SalesReferrerViewModel(
                    api = ServiceLocator.provideWebApi(app),
                    reportRepository = ServiceLocator.provideReportRepository(app),
                    ownBranchId = settings?.branchId,
                    fyStartMonth = settings?.fyStartMonth ?: 7,
                )
            }
        }
    }
}

@Composable
fun SalesReferrerScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    viewModel: SalesReferrerViewModel = viewModel(factory = SalesReferrerViewModel.provideFactory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)
    SnackOnMessage(state.message, snackbar, viewModel::onMessageShown)

    AuthenticatedShell(title = "Referer", currentRoute = Routes.REPORTS, navController = navController, onLogout = onLogout) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { BranchField(state.branch, viewModel::onBranch) }
                item { DateRangeFields(state.startDate, state.endDate, viewModel::onStart, viewModel::onEnd) }
                item {
                    FilterActions(
                        onApply = viewModel::apply,
                        onReset = viewModel::reset,
                        isLoading = state.isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // The list itself, kept here rather than on a screen of its own.
                item { SecondaryButton(text = "Referrers", onClick = viewModel::openMaster, modifier = Modifier.fillMaxWidth()) }

                val rows = state.rows
                when {
                    state.isLoading && rows == null -> item { ReportLoading() }
                    rows == null -> item { ReportNote("Choose a branch, then press Apply.") }
                    rows.isEmpty() -> item { ReportNote("No bills were brought by a referrer in this range.") }
                    else -> {
                        items(rows, key = { it.referrerId }) { row ->
                            ReferrerCard(
                                row = row,
                                open = state.openId == row.referrerId,
                                loadingBills = state.isLoadingBills,
                                bills = state.bills,
                                onClick = { viewModel.toggle(row) },
                            )
                        }
                        // Summed from the rows on screen: a second query could
                        // only disagree with the list above it.
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("Total", fontWeight = AppFontWeight.Bold)
                                    AmountLine("Bills", count(rows.sumOf { it.bills }))
                                    AmountLine("Bill Value", blankZero(rows.sumOf { it.total }))
                                    AmountLine("Discount", blankZero(rows.sumOf { it.discount }))
                                }
                            }
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }

    if (state.showMaster) {
        ReferrerMasterDialog(
            referrers = state.referrers,
            loading = state.isLoadingReferrers,
            saving = state.isSaving,
            onSave = viewModel::saveReferrer,
            onToggle = viewModel::toggleStatus,
            onClose = viewModel::closeMaster,
        )
    }
}

private fun blankZero(v: Double) = if (v == 0.0) "" else kitMoney(v)
private fun count(v: Double) = if (v == 0.0) "" else String.format(java.util.Locale.US, "%,d", v.toLong())

@Composable
private fun AmountLine(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.appColors.textMuted)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = if (bold) AppFontWeight.Bold else AppFontWeight.SemiBold)
    }
}

@Composable
private fun ReferrerCard(
    row: ReferrerRow,
    open: Boolean,
    loadingBills: Boolean,
    bills: List<ReferrerBill>,
    onClick: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.clickable(onClick = onClick).padding(12.dp)) {
            Text(
                row.name.ifBlank { "#${row.referrerId}" },
                fontWeight = if (open) AppFontWeight.Bold else AppFontWeight.SemiBold,
            )
            if (row.mobile.isNotBlank()) {
                Text(row.mobile, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textMuted)
            }
            AmountLine("Bills", count(row.bills))
            AmountLine("Bill Value", blankZero(row.total))
            AmountLine("Discount", blankZero(row.discount))
        }
        if (open) {
            HorizontalDivider()
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    loadingBills -> ReportLoading()
                    bills.isEmpty() -> Text("No bills under this referrer.", style = MaterialTheme.typography.labelSmall)
                    else -> bills.forEach { b ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${b.vrNo} · ${SimpleDate.fromApi(b.vrDate.take(10))?.toDisplay() ?: b.vrDate}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = AppFontWeight.SemiBold,
                                )
                                Text(b.customer, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textMuted)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(blankZero(b.total), style = MaterialTheme.typography.labelSmall)
                                if (b.discount != 0.0) {
                                    Text("Disc. ${kitMoney(b.discount)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textMuted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The referrer list the invoice popup picks from. A retired referrer drops off
 * the popup and stays on the report.
 */
@Composable
private fun ReferrerMasterDialog(
    referrers: List<Referrer>,
    loading: Boolean,
    saving: Boolean,
    onSave: (Long?, String, String, String, () -> Unit) -> Unit,
    onToggle: (Referrer) -> Unit,
    onClose: () -> Unit,
) {
    var editing by remember { mutableStateOf<Referrer?>(null) }
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    val clear = { editing = null; name = ""; mobile = ""; address = "" }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Sales Referrers") },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(
                        "The list the invoice popup picks from. A retired referrer keeps the bills they already brought.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.appColors.textMuted,
                    )
                }
                item { AppTextField(name, { name = it }, label = "Name", caption = if (editing != null) "Edit name" else "Name") }
                item { AppTextField(mobile, { mobile = it }, label = "Mobile", caption = "Mobile") }
                item { AppTextField(address, { address = it }, label = "Address", caption = "Address") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinkButton(
                            text = if (editing != null) "Save" else "Add",
                            enabled = !saving,
                            onClick = { onSave(editing?.id, name, mobile, address, clear) },
                        )
                        if (editing != null) LinkButton(text = "Cancel", onClick = clear)
                    }
                }
                if (loading) item { ReportLoading() }
                if (!loading && referrers.isEmpty()) item { Text("No referrers yet.", textAlign = TextAlign.Center) }
                items(referrers, key = { it.id }) { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, fontWeight = AppFontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            Text(
                                listOf(r.mobile, r.address).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.appColors.textMuted,
                            )
                            if (!r.active) {
                                Text("Retired", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        LinkButton(text = "Edit", onClick = {
                            editing = r; name = r.name; mobile = r.mobile; address = r.address
                        })
                        LinkButton(text = if (r.active) "Retire" else "Reactivate", enabled = !saving, onClick = { onToggle(r) })
                    }
                }
            }
        },
        confirmButton = { LinkButton(text = "Close", onClick = onClose) },
    )
}
