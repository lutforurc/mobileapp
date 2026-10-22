package com.example.cashbookbd.ui.companyscheme

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.core.jInt
import com.example.cashbookbd.core.jLong
import com.example.cashbookbd.core.jNum
import com.example.cashbookbd.core.jNumOrNull
import com.example.cashbookbd.core.jObj
import com.example.cashbookbd.core.jObjs
import com.example.cashbookbd.core.jPayload
import com.example.cashbookbd.core.jRows
import com.example.cashbookbd.core.jStr
import com.example.cashbookbd.data.remote.WebApi
import com.example.cashbookbd.data.repository.LedgerRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.ParityRoutes
import com.example.cashbookbd.session.Permissions
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.FilterActions
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SearchableLedgerDropdown
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.DateField
import com.example.cashbookbd.ui.reports.DateRangeFields
import com.example.cashbookbd.ui.reports.LogoutOnExpiry
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportFooterCell
import com.example.cashbookbd.ui.reports.ReportLoading
import com.example.cashbookbd.ui.reports.ReportNote
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.SnackOnMessage
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.reports.kitMoney
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToLong

/*
 * Company scheme (web 3b830761 … 862c112b): what each brand still owes on the
 * IMEIs sold under its scheme, and what the brands paid. The sale itself is
 * written on Invoice → Sales; the brand's statement is the ordinary Ledger of
 * its scheme account. The papers stay on the web.
 */

private val ALL_BRANCH = SelectorOption("0", "All Branch")

private fun dmy(value: String): String = SimpleDate.fromApi(value.take(10))?.toDisplay() ?: value.ifBlank { "-" }

/** The web's money(): a nought is a dash. */
private fun schemeMoney(v: Double): String = if (v == 0.0) "-" else kitMoney(v)

private suspend fun ReportRepository.branchOptions(): List<SelectorOption> =
    (getBranches() as? Resource.Success)?.data?.branches.orEmpty().map { SelectorOption(it.id.toString(), it.name) }

// ---- Receivable ---------------------------------------------------------------

data class SchemeRow(
    val id: Long,
    val mainTrxId: Long,
    val imei: String,
    val partyName: String,
    val invoiceNo: String,
    val saleDate: String,
    val productName: String,
    val buyerName: String,
    val buyerMobile: String,
    val salePrice: Double?,
    val amount: Double,
    val paid: Double,
    val balance: Double,
    val dueDate: String,
    val daysOverdue: Int,
)

data class SchemeSummary(
    val partyId: Long,
    val partyName: String,
    val count: Int,
    val open: Double,
    val overdue: Double,
    val ledger: Double,
    val difference: Double,
)

data class SchemeReconcile(val ledger: Double, val open: Double, val difference: Double)

private val STATUSES = listOf(
    SelectorOption("open", "Open (not fully paid)"),
    SelectorOption("overdue", "Overdue"),
    SelectorOption("paid", "Paid"),
    SelectorOption("all", "All"),
)
private val METHODS = listOf(SelectorOption("cash", "Cash"), SelectorOption("bank", "Bank"))

data class SchemeReceivableUiState(
    val canReceive: Boolean = false,
    val canMark: Boolean = false,
    val branches: List<SelectorOption> = listOf(ALL_BRANCH),
    val branch: SelectorOption = ALL_BRANCH,
    val party: LedgerDropdownItem? = null,
    val status: SelectorOption = STATUSES.first(),
    val search: String = "",
    val isLoading: Boolean = false,
    val rows: List<SchemeRow>? = null,
    val totalCount: Int = 0,
    val totalAmount: Double = 0.0,
    val totalPaid: Double = 0.0,
    val totalBalance: Double = 0.0,
    val summary: List<SchemeSummary>? = null,
    val reconcile: SchemeReconcile? = null,
    /** receivable id → amount typed against it */
    val picked: Map<Long, String> = emptyMap(),
    val fillAmount: String = "",
    val banks: List<SelectorOption> = emptyList(),
    val method: SelectorOption = METHODS.first(),
    val bank: SelectorOption? = null,
    val remarks: String = "",
    val isReceiving: Boolean = false,
    val markDate: SimpleDate = SimpleDate.today(),
    val isMarking: Boolean = false,
    val resetKey: Int = 0,
    val message: String? = null,
    val sessionExpired: Boolean = false,
) {
    /** Ticks and the receive panel: a company chosen, and not the Paid list. */
    val showPick: Boolean get() = canReceive && party != null && status.id != "paid"
    val pickedTotal: Double get() = picked.values.sumOf { it.toDoubleOrNull() ?: 0.0 }
}

/**
 * Company scheme receivables: what each brand still owes, IMEI by IMEI.
 * Ticking rows and pressing Receive writes one cash or bank receipt for the
 * brand, spread over the ticked IMEIs. With no company chosen, every brand
 * stands on one line against its ledger.
 */
class SchemeReceivableViewModel(
    private val api: WebApi,
    private val reports: ReportRepository,
    private val ledgers: LedgerRepository,
    canReceive: Boolean,
    canMark: Boolean,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SchemeReceivableUiState(canReceive = canReceive, canMark = canMark))
    val uiState: StateFlow<SchemeReceivableUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val list = reports.branchOptions()
            _uiState.update { it.copy(branches = listOf(ALL_BRANCH) + list) }
        }
        // The bank heads (CoA L3 2).
        viewModelScope.launch {
            (api.get("coal3/l4-list/2") as? Resource.Success)?.let { r ->
                _uiState.update { it.copy(banks = r.data.jRows().map { b -> SelectorOption(b.jStr("id"), b.jStr("name")) }) }
            }
        }
    }

    fun onBranch(o: SelectorOption) = _uiState.update { it.copy(branch = o) }
    fun onParty(p: LedgerDropdownItem?) = _uiState.update { it.copy(party = p) }
    fun onStatus(o: SelectorOption) = _uiState.update { it.copy(status = o) }
    fun onSearch(v: String) = _uiState.update { it.copy(search = v) }
    fun onFillAmount(v: String) = _uiState.update { it.copy(fillAmount = v.filter { c -> c.isDigit() || c == '.' }) }
    fun onMethod(o: SelectorOption) = _uiState.update { it.copy(method = o) }
    fun onBank(o: SelectorOption) = _uiState.update { it.copy(bank = o) }
    fun onRemarks(v: String) = _uiState.update { it.copy(remarks = v) }
    fun onMarkDate(d: SimpleDate) = _uiState.update { it.copy(markDate = d) }

    suspend fun searchCompanies(q: String) = ledgers.searchLedgers(q, acType = "3")

    /** Opens a brand from the summary; press Apply to read it. */
    fun openCompany(s: SchemeSummary) = _uiState.update {
        it.copy(party = LedgerDropdownItem(s.partyId.toInt(), s.partyName, null), resetKey = it.resetKey + 1)
    }

    fun togglePick(row: SchemeRow, checked: Boolean) = _uiState.update {
        it.copy(picked = if (checked) it.picked + (row.id to plain(row.balance)) else it.picked - row.id)
    }

    fun onPickAmount(id: Long, v: String) = _uiState.update { it.copy(picked = it.picked + (id to v.filter { c -> c.isDigit() || c == '.' })) }

    private fun plain(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

    fun reset() = _uiState.update {
        it.copy(
            party = null, branch = ALL_BRANCH, status = STATUSES.first(), search = "", rows = null,
            summary = null, reconcile = null, picked = emptyMap(), fillAmount = "", resetKey = it.resetKey + 1,
        )
    }

    fun load() {
        val s = _uiState.value
        _uiState.update { it.copy(isLoading = true, picked = emptyMap()) }
        val partyId = s.party?.id?.toLong() ?: 0L
        val branchId = s.branch.id.toLongOrNull() ?: 0L
        viewModelScope.launch {
            val body = JsonObject().apply {
                if (partyId > 0) addProperty("party_coa4_id", partyId)
                if (branchId > 0) addProperty("branch_id", branchId)
                addProperty("status", s.status.id)
                s.search.trim().takeIf { it.isNotEmpty() }?.let { addProperty("search", it) }
            }
            when (val r = api.post("company-scheme/receivables", body)) {
                is Resource.Success -> {
                    val data = r.data.jPayload()
                    val totals = data.jObj("totals")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            rows = data.jObjs("rows").map { o ->
                                SchemeRow(
                                    id = o.jLong("id") ?: 0L,
                                    mainTrxId = o.jLong("main_trx_id") ?: 0L,
                                    imei = o.jStr("imei"),
                                    partyName = o.jStr("party_name"),
                                    invoiceNo = o.jStr("invoice_no"),
                                    saleDate = o.jStr("sale_date"),
                                    productName = o.jStr("product_name"),
                                    buyerName = o.jStr("buyer_name"),
                                    buyerMobile = o.jStr("buyer_mobile"),
                                    salePrice = o.jNumOrNull("sale_price"),
                                    amount = o.jNum("amount"),
                                    paid = o.jNum("paid"),
                                    balance = o.jNum("balance"),
                                    dueDate = o.jStr("due_date"),
                                    daysOverdue = o.jInt("days_overdue") ?: 0,
                                )
                            },
                            totalCount = totals?.jInt("count") ?: 0,
                            totalAmount = totals?.jNum("amount") ?: 0.0,
                            totalPaid = totals?.jNum("paid") ?: 0.0,
                            totalBalance = totals?.jNum("balance") ?: 0.0,
                        )
                    }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, rows = null, message = r.message.ifBlank { "The list could not be loaded." }, sessionExpired = r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
        // The brand's ledger against its open IMEIs: they should agree to the
        // paisa. Both follow the branch filter, as the Ledger report reads one.
        viewModelScope.launch {
            val scope = if (branchId > 0) mapOf("branch_id" to branchId.toString()) else emptyMap()
            if (partyId > 0) {
                val rec = (api.get("company-scheme/reconcile/$partyId", scope) as? Resource.Success)?.data?.jPayload()
                _uiState.update {
                    it.copy(reconcile = rec?.let { d -> SchemeReconcile(d.jNum("ledger_balance"), d.jNum("open_total"), d.jNum("difference")) })
                }
            } else {
                val sum = (api.get("company-scheme/summary", scope) as? Resource.Success)?.data?.jPayload()
                _uiState.update {
                    it.copy(
                        reconcile = null,
                        summary = sum?.jObjs("rows")?.map { o ->
                            SchemeSummary(
                                o.jLong("party_coa4_id") ?: 0L, o.jStr("party_name"), o.jInt("count") ?: 0,
                                o.jNum("open_total"), o.jNum("overdue_total"), o.jNum("ledger_balance"), o.jNum("difference"),
                            )
                        },
                    )
                }
            }
        }
    }

    /**
     * Spread a lump sum over the listed IMEIs in the order they stand — the
     * API sorts by due date, so the oldest due fills first — and stop when it
     * runs out. The clerk then corrects any row the statement disagrees on.
     */
    fun fill() {
        val s = _uiState.value
        var left = ((s.fillAmount.toDoubleOrNull() ?: 0.0) * 100).roundToLong() / 100.0
        if (left <= 0) {
            _uiState.update { it.copy(message = "Enter the amount the brand paid.") }
            return
        }
        val next = linkedMapOf<Long, String>()
        for (row in s.rows.orEmpty()) {
            if (left <= 0) break
            if (row.balance <= 0) continue
            val take = min(row.balance, left)
            next[row.id] = plain(take)
            left = ((left - take) * 100).roundToLong() / 100.0
        }
        _uiState.update {
            it.copy(picked = next, message = if (left > 0) "${kitMoney(left)} is more than what is open here." else null)
        }
    }

    fun receive() {
        val s = _uiState.value
        val party = s.party
        val error = when {
            party == null -> "Select the brand's scheme account first."
            s.picked.isEmpty() -> "Tick at least one IMEI."
            s.method.id == "bank" && s.bank == null -> "Select the bank account."
            s.picked.values.any { (it.toDoubleOrNull() ?: 0.0) <= 0 } -> "Every ticked IMEI needs an amount above 0."
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(message = error) }
            return
        }
        _uiState.update { it.copy(isReceiving = true) }
        viewModelScope.launch {
            val body = JsonObject().apply {
                addProperty("party_coa4_id", party!!.id)
                addProperty("method", s.method.id)
                if (s.method.id == "bank") addProperty("bank_coa4_id", s.bank!!.id.toLongOrNull() ?: 0L)
                addProperty("remarks", s.remarks)
                add("allocations", JsonArray().apply {
                    s.picked.forEach { (id, amount) ->
                        add(JsonObject().apply {
                            addProperty("receivable_id", id)
                            addProperty("amount", amount.toDoubleOrNull() ?: 0.0)
                        })
                    }
                })
            }
            when (val r = api.post("company-scheme/receive", body)) {
                is Resource.Success -> {
                    val vr = r.data.jPayload().jStr("vr_no")
                    _uiState.update {
                        it.copy(
                            isReceiving = false, remarks = "", fillAmount = "",
                            message = r.data.jStr("message").ifBlank { "Received." } + if (vr.isNotBlank()) " ($vr)" else "",
                        )
                    }
                    load()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isReceiving = false, message = r.message.ifBlank { "Not received." }, sessionExpired = r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    /** Change the due date on the ticked rows. No posting moves. */
    fun mark() {
        val s = _uiState.value
        if (s.picked.isEmpty()) {
            _uiState.update { it.copy(message = "Tick at least one IMEI.") }
            return
        }
        _uiState.update { it.copy(isMarking = true) }
        viewModelScope.launch {
            val body = JsonObject().apply {
                add("receivable_ids", JsonArray().apply { s.picked.keys.forEach { add(it) } })
                addProperty("due_date", s.markDate.toApi())
            }
            when (val r = api.post("company-scheme/mark", body)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isMarking = false, message = r.data.jStr("message").ifBlank { "Updated." }) }
                    load()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isMarking = false, message = r.message.ifBlank { "Not updated." }, sessionExpired = r.isUnauthorized)
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
                val permissions = ServiceLocator.provideSessionManager(app).state.value.settings?.permissions
                SchemeReceivableViewModel(
                    api = ServiceLocator.provideWebApi(app),
                    reports = ServiceLocator.provideReportRepository(app),
                    ledgers = ServiceLocator.provideLedgerRepository(app),
                    canReceive = Permissions.has(permissions, "company.scheme.receive"),
                    canMark = Permissions.has(permissions, "company.scheme.mark"),
                )
            }
        }
    }
}

@Composable
fun CompanySchemeReceivableScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    viewModel: SchemeReceivableViewModel = viewModel(factory = SchemeReceivableViewModel.provideFactory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)
    SnackOnMessage(state.message, snackbar, viewModel::onMessageShown)
    val muted = MaterialTheme.appColors.textMuted

    AuthenticatedShell(title = "Company Scheme Receivable", currentRoute = ParityRoutes.COMPANY_SCHEME, navController = navController, onLogout = onLogout) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    key(state.resetKey) {
                        SearchableLedgerDropdown(
                            selectedLedger = state.party,
                            onLedgerSelected = viewModel::onParty,
                            searchLedgers = viewModel::searchCompanies,
                            label = "Company (scheme account)",
                            placeholder = "All companies",
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppSelectDropdown("Select Branch", state.branches, state.branch, viewModel::onBranch, Modifier.weight(1f))
                        AppSelectDropdown("Status", STATUSES, state.status, viewModel::onStatus, Modifier.weight(1f))
                    }
                }
                item { AppTextField(state.search, viewModel::onSearch, label = "IMEI / Mobile / Invoice", caption = "IMEI / Mobile / Invoice", modifier = Modifier.fillMaxWidth()) }
                item { FilterActions(onApply = viewModel::load, onReset = viewModel::reset, isLoading = state.isLoading, modifier = Modifier.fillMaxWidth()) }

                // Every brand on one line while no company is chosen.
                val summary = state.summary
                if (state.party == null && !summary.isNullOrEmpty()) {
                    item {
                        val danger = MaterialTheme.appColors.danger
                        val warning = MaterialTheme.appColors.warning
                        val link = MaterialTheme.appColors.textLink
                        ReportTable(
                            columns = listOf(
                                ReportColumn<SchemeSummary>("COMPANY", ReportColWidth.Fixed(150.dp)) { r, _ -> cellText(r.partyName, maxLines = 2) },
                                ReportColumn<SchemeSummary>("IMEIS", ReportColWidth.Fixed(60.dp), TextAlign.End) { r, _ -> cellText(r.count.toString()) },
                                ReportColumn<SchemeSummary>("OPEN", ReportColWidth.Fixed(96.dp), TextAlign.End) { r, _ -> cellText(schemeMoney(r.open)) },
                                ReportColumn<SchemeSummary>("OVERDUE", ReportColWidth.Fixed(96.dp), TextAlign.End) { r, _ ->
                                    cellText(schemeMoney(r.overdue), color = danger)
                                },
                                ReportColumn<SchemeSummary>("LEDGER", ReportColWidth.Fixed(96.dp), TextAlign.End) { r, _ -> cellText(schemeMoney(r.ledger)) },
                                ReportColumn<SchemeSummary>("DIFFERENCE", ReportColWidth.Fixed(100.dp), TextAlign.End) { r, _ ->
                                    if (abs(r.difference) >= 0.01) cellText(schemeMoney(r.difference), bold = true, color = warning) else cellText("0")
                                },
                                ReportColumn<SchemeSummary>("", ReportColWidth.Fixed(70.dp), TextAlign.Center) { r, _ ->
                                    com.example.cashbookbd.ui.reports.ReportTableCell.Slot {
                                        Text(
                                            "Open",
                                            modifier = Modifier.clickable { viewModel.openCompany(r) }.padding(8.dp),
                                            color = link,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                },
                            ),
                            data = summary,
                            scrollable = false,
                        )
                    }
                    item {
                        Text(
                            "${if (state.branch.id != "0") "${state.branch.label} only" else "All branches"}. Tap Open beside a company, then press Apply.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.appColors.textOnScreenMuted,
                        )
                    }
                }
                state.reconcile?.let { rec ->
                    item {
                        val agrees = abs(rec.difference) < 0.01
                        val tone = if (agrees) MaterialTheme.appColors.success else MaterialTheme.appColors.warning
                        Text(
                            "Ledger balance ${kitMoney(rec.ledger)} · Open IMEIs ${kitMoney(rec.open)} · Difference ${kitMoney(rec.difference)}" +
                                if (agrees) "" else " -- something reached this account outside Company Scheme (an ordinary voucher, a scheme invoice edited from Electronics Sales, or a paid scheme sale deleted).",
                            modifier = Modifier.fillMaxWidth().background(tone.copy(alpha = 0.12f), MaterialTheme.shapes.small).padding(8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = tone,
                        )
                    }
                }

                val rows = state.rows
                when {
                    state.isLoading && rows == null -> item { ReportLoading() }
                    rows == null -> item { ReportNote("Choose a company, then press Apply.") }
                    rows.isEmpty() -> item { ReportNote("Nothing here.") }
                    else -> {
                        items(rows, key = { it.id }) { row ->
                            Card(Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                                    if (state.showPick) {
                                        if (row.balance > 0) {
                                            Checkbox(checked = state.picked.containsKey(row.id), onCheckedChange = { viewModel.togglePick(row, it) })
                                        }
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("${row.invoiceNo} · ${dmy(row.saleDate)}", fontWeight = AppFontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                        Text("${row.productName.ifBlank { "-" }} · ${row.imei}", style = MaterialTheme.typography.labelSmall)
                                        Text(
                                            listOf(row.buyerName, row.buyerMobile).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "-" },
                                            style = MaterialTheme.typography.labelSmall, color = muted,
                                        )
                                        if (state.party == null) Text("Company: ${row.partyName.ifBlank { "-" }}", style = MaterialTheme.typography.labelSmall, color = muted)
                                        Text(
                                            "Sale Price ${row.salePrice?.let(::schemeMoney) ?: "-"} · Receivable ${schemeMoney(row.amount)} · Received ${schemeMoney(row.paid)}",
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Text("Balance ${schemeMoney(row.balance)}", style = MaterialTheme.typography.labelSmall, fontWeight = AppFontWeight.Bold)
                                            Text("Due ${dmy(row.dueDate)}", style = MaterialTheme.typography.labelSmall)
                                            if (row.daysOverdue > 0) {
                                                Text("${row.daysOverdue} d overdue", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.danger)
                                            }
                                        }
                                        if (state.showPick && state.picked.containsKey(row.id)) {
                                            AppTextField(
                                                value = state.picked[row.id].orEmpty(),
                                                onValueChange = { v -> viewModel.onPickAmount(row.id, v) },
                                                label = "0", caption = "Receive Now",
                                                keyboardType = KeyboardType.Decimal,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            ReportTable(
                                columns = listOf(
                                    ReportColumn<Unit>("TOTAL (${state.totalCount})", ReportColWidth.Weight(0.25f)) { _, _ -> cellText("") },
                                    ReportColumn<Unit>("RECEIVABLE", ReportColWidth.Weight(0.25f), TextAlign.End) { _, _ -> cellText(schemeMoney(state.totalAmount), bold = true) },
                                    ReportColumn<Unit>("RECEIVED", ReportColWidth.Weight(0.25f), TextAlign.End) { _, _ -> cellText(schemeMoney(state.totalPaid), bold = true) },
                                    ReportColumn<Unit>("BALANCE", ReportColWidth.Weight(0.25f), TextAlign.End) { _, _ -> cellText(schemeMoney(state.totalBalance), bold = true) },
                                ),
                                data = listOf(Unit),
                                scrollable = false,
                            )
                        }
                        if (state.showPick) {
                            item { ReceivePanel(state, viewModel) }
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun ReceivePanel(state: SchemeReceivableUiState, viewModel: SchemeReceivableViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // A lump sum: fill the ticks oldest-due first, then correct by hand.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            AppTextField(
                state.fillAmount, viewModel::onFillAmount,
                label = "Total, to spread oldest due first", caption = "Amount the brand paid",
                keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f),
            )
            SecondaryButton(text = "Fill oldest first", onClick = viewModel::fill)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppSelectDropdown("Received In", METHODS, state.method, viewModel::onMethod, Modifier.weight(1f))
            if (state.method.id == "bank") {
                AppSelectDropdown("Bank Account", state.banks, state.bank, viewModel::onBank, Modifier.weight(1f), placeholder = "Select Bank Account")
            }
        }
        AppTextField(state.remarks, viewModel::onRemarks, label = "Cheque no, reference...", caption = "Remarks", modifier = Modifier.fillMaxWidth())
        PrimaryButton(
            text = "Receive " + if (state.picked.isNotEmpty()) kitMoney(state.pickedTotal) else "",
            onClick = viewModel::receive,
            isLoading = state.isReceiving,
            modifier = Modifier.fillMaxWidth(),
        )
        // Extend the ticked rows' due dates without editing the invoice.
        if (state.canMark) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                DateField("Date for ticked IMEIs", state.markDate, viewModel::onMarkDate, Modifier.weight(1f))
                SecondaryButton(text = "Set Due Date", onClick = viewModel::mark, isLoading = state.isMarking)
            }
        }
    }
}

// ---- Receipts -------------------------------------------------------------------

data class SchemeReceiptLine(
    val first: Boolean,
    val sl: Int,
    val vrNo: String,
    val vrDate: String,
    val method: String,
    val partyName: String,
    val voucherAmount: Double,
    val imei: String,
    val productName: String,
    val invoiceNo: String,
    val amount: Double,
)

data class SchemeReceiptsUiState(
    val branches: List<SelectorOption> = listOf(ALL_BRANCH),
    val branch: SelectorOption = ALL_BRANCH,
    val party: LedgerDropdownItem? = null,
    val startDate: SimpleDate = SimpleDate.today(),
    val endDate: SimpleDate = SimpleDate.today(),
    val isLoading: Boolean = false,
    val lines: List<SchemeReceiptLine>? = null,
    val total: Double = 0.0,
    val resetKey: Int = 0,
    val message: String? = null,
    val sessionExpired: Boolean = false,
)

/** What the brands paid, day by day: each receipt voucher and the IMEIs it paid for. */
class SchemeReceiptsViewModel(
    private val api: WebApi,
    private val reports: ReportRepository,
    private val ledgers: LedgerRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SchemeReceiptsUiState())
    val uiState: StateFlow<SchemeReceiptsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val list = reports.branchOptions()
            _uiState.update { it.copy(branches = listOf(ALL_BRANCH) + list) }
        }
    }

    fun onBranch(o: SelectorOption) = _uiState.update { it.copy(branch = o) }
    fun onParty(p: LedgerDropdownItem?) = _uiState.update { it.copy(party = p) }
    fun onStart(d: SimpleDate) = _uiState.update { it.copy(startDate = d) }
    fun onEnd(d: SimpleDate) = _uiState.update { it.copy(endDate = d) }
    suspend fun searchCompanies(q: String) = ledgers.searchLedgers(q, acType = "3")

    fun reset() = _uiState.update {
        it.copy(
            party = null, branch = ALL_BRANCH, startDate = SimpleDate.today(), endDate = SimpleDate.today(),
            lines = null, total = 0.0, resetKey = it.resetKey + 1,
        )
    }

    fun load() {
        val s = _uiState.value
        if (s.startDate.toApi() > s.endDate.toApi()) {
            _uiState.update { it.copy(message = "The start date cannot be after the end date.") }
            return
        }
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val body = JsonObject().apply {
                addProperty("from_date", s.startDate.toApi())
                addProperty("to_date", s.endDate.toApi())
                s.party?.id?.let { addProperty("party_coa4_id", it) }
                s.branch.id.toLongOrNull()?.takeIf { it > 0 }?.let { addProperty("branch_id", it) }
            }
            when (val r = api.post("company-scheme/receipts", body)) {
                is Resource.Success -> {
                    val data = r.data.jPayload()
                    // One row per IMEI; a voucher's date, number and company
                    // stand on its first IMEI only.
                    val lines = data.jObjs("rows").flatMapIndexed { vi, receipt ->
                        receipt.jObjs("lines").mapIndexed { li, line ->
                            SchemeReceiptLine(
                                first = li == 0, sl = vi + 1,
                                vrNo = receipt.jStr("vr_no"), vrDate = receipt.jStr("vr_date"),
                                method = receipt.jStr("method"), partyName = receipt.jStr("party_name"),
                                voucherAmount = receipt.jNum("amount"),
                                imei = line.jStr("imei"), productName = line.jStr("product_name"),
                                invoiceNo = line.jStr("invoice_no"), amount = line.jNum("amount"),
                            )
                        }
                    }
                    _uiState.update { it.copy(isLoading = false, lines = lines, total = data.jNum("total")) }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, lines = null, message = r.message.ifBlank { "The list could not be loaded." }, sessionExpired = r.isUnauthorized)
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
                SchemeReceiptsViewModel(
                    ServiceLocator.provideWebApi(app), ServiceLocator.provideReportRepository(app), ServiceLocator.provideLedgerRepository(app),
                )
            }
        }
    }
}

@Composable
fun CompanySchemeReceiptsScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    viewModel: SchemeReceiptsViewModel = viewModel(factory = SchemeReceiptsViewModel.provideFactory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)
    SnackOnMessage(state.message, snackbar, viewModel::onMessageShown)

    AuthenticatedShell(title = "Company Scheme Receipts", currentRoute = ParityRoutes.COMPANY_SCHEME, navController = navController, onLogout = onLogout) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    key(state.resetKey) {
                        SearchableLedgerDropdown(
                            selectedLedger = state.party, onLedgerSelected = viewModel::onParty, searchLedgers = viewModel::searchCompanies,
                            label = "Company (scheme account)", placeholder = "All companies",
                        )
                    }
                }
                item { AppSelectDropdown("Select Branch", state.branches, state.branch, viewModel::onBranch) }
                item { DateRangeFields(state.startDate, state.endDate, viewModel::onStart, viewModel::onEnd) }
                item { FilterActions(onApply = viewModel::load, onReset = viewModel::reset, isLoading = state.isLoading, modifier = Modifier.fillMaxWidth()) }
                val lines = state.lines
                if (lines != null) {
                    item {
                        Text(
                            "${state.startDate.toDisplay()} — ${state.endDate.toDisplay()}",
                            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.appColors.textOnScreen,
                        )
                    }
                }
                item {
                    val muted = MaterialTheme.appColors.textMuted
                    ReportTable(
                        columns = listOf(
                            ReportColumn<SchemeReceiptLine>("DATE", ReportColWidth.Fixed(90.dp), TextAlign.Center) { r, _ -> cellText(if (r.first) dmy(r.vrDate) else "") },
                            ReportColumn<SchemeReceiptLine>("VOUCHER", ReportColWidth.Fixed(130.dp)) { r, _ ->
                                if (!r.first) cellText("") else com.example.cashbookbd.ui.reports.ReportTableCell.Slot {
                                    Column(Modifier.padding(8.dp)) {
                                        Text(r.vrNo, style = MaterialTheme.typography.bodySmall)
                                        Text("${r.method.uppercase()} · ${kitMoney(r.voucherAmount)}", style = MaterialTheme.typography.labelSmall, color = muted)
                                    }
                                }
                            },
                            ReportColumn<SchemeReceiptLine>("COMPANY", ReportColWidth.Fixed(140.dp)) { r, _ -> cellText(if (r.first) r.partyName else "", maxLines = 2) },
                            ReportColumn<SchemeReceiptLine>("IMEI", ReportColWidth.Fixed(140.dp)) { r, _ -> cellText(r.imei, bold = true) },
                            ReportColumn<SchemeReceiptLine>("PRODUCT", ReportColWidth.Fixed(150.dp)) { r, _ -> cellText(r.productName, maxLines = 2) },
                            ReportColumn<SchemeReceiptLine>("INVOICE", ReportColWidth.Fixed(120.dp)) { r, _ -> cellText(r.invoiceNo) },
                            ReportColumn<SchemeReceiptLine>("AMOUNT", ReportColWidth.Fixed(100.dp), TextAlign.End) { r, _ -> cellText(kitMoney(r.amount)) },
                        ),
                        data = lines.orEmpty(),
                        scrollable = false,
                        noDataMessage = if (lines != null) "No receipt in this range." else "Choose a date range, then press Apply.",
                        footerRows = if (lines.isNullOrEmpty()) emptyList() else listOf(
                            listOf(
                                ReportFooterCell(cellText("Total", bold = true), colSpan = 6),
                                ReportFooterCell(cellText(kitMoney(state.total), bold = true)),
                            ),
                        ),
                    )
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

// ---- The section's home -----------------------------------------------------------

/** The web's two-entry Company Scheme group. */
@Composable
fun CompanySchemeHomeScreen(navController: NavHostController, onLogout: () -> Unit) {
    AuthenticatedShell(title = "Company Scheme", currentRoute = ParityRoutes.COMPANY_SCHEME, navController = navController, onLogout = onLogout) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { ParityMenuRow("Receivable") { navController.navigate(ParityRoutes.COMPANY_SCHEME_RECEIVABLE) } }
            item { ParityMenuRow("Receipts") { navController.navigate(ParityRoutes.COMPANY_SCHEME_RECEIPTS) } }
        }
    }
}

/** One tappable row of a section's home, the Reports home's own look. */
@Composable
fun ParityMenuRow(title: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            androidx.compose.material3.Icon(
                imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
