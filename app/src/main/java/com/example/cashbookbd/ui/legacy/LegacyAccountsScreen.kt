package com.example.cashbookbd.ui.legacy

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.core.jInt
import com.example.cashbookbd.core.jNum
import com.example.cashbookbd.core.jNumOrNull
import com.example.cashbookbd.core.jObj
import com.example.cashbookbd.core.jObjs
import com.example.cashbookbd.core.jPayload
import com.example.cashbookbd.core.jStr
import com.example.cashbookbd.data.remote.WebApi
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.reports.LogoutOnExpiry
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportLoading
import com.example.cashbookbd.ui.reports.ReportNote
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.SnackOnMessage
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.reports.kitMoney
import com.example.cashbookbd.ui.reports.kitMoneyDash
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

data class LegacyAccountRow(
    val legacyId: String,
    val name: String,
    val number: String,
    val type: String,
    val mobile: String,
    val details: String,
    val entries: Int,
    val balance: Double,
)

data class LegacyLedgerRow(val date: String, val particulars: String, val debit: Double, val credit: Double, val balance: Double)

data class LegacyAccountCard(
    val name: String,
    val subtitle: String,
    val legacyId: String,
    val added: String,
    val closing: Double,
    val entries: Int,
    val rows: List<LegacyLedgerRow>,
)

data class LegacyAccountsUiState(
    val isLoading: Boolean = true,
    val rows: List<LegacyAccountRow> = emptyList(),
    val negative: Double = 0.0,
    val positive: Double = 0.0,
    val net: Double = 0.0,
    val hasTotals: Boolean = false,
    val opening: Boolean = false,
    val account: LegacyAccountCard? = null,
    val message: String? = null,
    val sessionExpired: Boolean = false,
)

/**
 * The old shop's own cash and bank accounts (LegacyAccounts.tsx). A backup,
 * not a ledger of ours: no form, no save, and not one taka of it reaches the
 * new books — they are the other leg of the openings already taken.
 */
class LegacyAccountsViewModel(private val api: WebApi) : ViewModel() {
    private val _uiState = MutableStateFlow(LegacyAccountsUiState())
    val uiState: StateFlow<LegacyAccountsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            when (val r = api.get("legacy/accounts")) {
                is Resource.Success -> {
                    val d = r.data.jPayload()
                    val totals = d.jObj("totals")
                    val negative = abs(totals?.jNum("negative") ?: 0.0)
                    val positive = totals?.jNum("positive") ?: 0.0
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            rows = d.jObjs("rows").map { o ->
                                LegacyAccountRow(
                                    o.jStr("legacy_id"), o.jStr("name"), o.jStr("account_number"), o.jStr("type"),
                                    o.jStr("mobile"), o.jStr("details"), o.jInt("entries") ?: 0, o.jNum("balance"),
                                )
                            },
                            hasTotals = totals != null,
                            negative = negative,
                            positive = positive,
                            net = totals?.jNumOrNull("net") ?: (positive - negative),
                        )
                    }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, message = r.message.ifBlank { "Could not read the old accounts" }, sessionExpired = r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun open(row: LegacyAccountRow) {
        _uiState.update { it.copy(opening = true) }
        viewModelScope.launch {
            when (val r = api.get("legacy/account/${java.net.URLEncoder.encode(row.legacyId, "UTF-8")}")) {
                is Resource.Success -> {
                    val d = r.data.jPayload()
                    val a = d.jObj("account")
                    _uiState.update {
                        it.copy(
                            opening = false,
                            account = LegacyAccountCard(
                                name = a?.jStr("name").orEmpty(),
                                subtitle = listOf("type", "account_number", "mobile", "details").map { k -> a?.jStr(k).orEmpty() }
                                    .filter { s -> s.isNotBlank() }.joinToString(" — "),
                                legacyId = a?.jStr("legacy_id").orEmpty(),
                                added = a?.jStr("added").orEmpty(),
                                closing = d.jObj("summary")?.jNum("closing") ?: 0.0,
                                entries = d.jObj("summary")?.jInt("entries") ?: 0,
                                rows = d.jObjs("rows").map { o ->
                                    LegacyLedgerRow(o.jStr("doc_date"), o.jStr("particulars"), o.jNum("debit"), o.jNum("credit"), o.jNum("balance"))
                                },
                            ),
                        )
                    }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(opening = false, message = r.message.ifBlank { "Could not open that account" }, sessionExpired = r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun close() = _uiState.update { it.copy(account = null) }
    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer { LegacyAccountsViewModel(ServiceLocator.provideWebApi(context.applicationContext)) }
        }
    }
}

/** A nought keeps its figure in a total; every other value takes the app's format. */
private fun totalFigure(v: Double) = if (v == 0.0) "0" else kitMoney(v)
private fun dmy(v: String) = SimpleDate.fromApi(v.take(10))?.toDisplay() ?: v.ifBlank { "—" }

@Composable
fun LegacyAccountsScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    viewModel: LegacyAccountsViewModel = viewModel(factory = LegacyAccountsViewModel.provideFactory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)
    SnackOnMessage(state.message, snackbar, viewModel::onMessageShown)
    val muted = MaterialTheme.appColors.textMuted
    val onScreenMuted = MaterialTheme.appColors.textOnScreenMuted

    androidx.activity.compose.BackHandler(enabled = state.account != null) { viewModel.close() }

    AuthenticatedShell(title = "Old Record - Information", currentRoute = Routes.LEGACY_HOME, navController = navController, onLogout = onLogout) {
        Box(Modifier.fillMaxSize()) {
            val account = state.account
            if (account != null) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { LinkButton(text = "← সব অ্যাকাউন্ট", onClick = viewModel::close) }
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(account.name, fontWeight = AppFontWeight.SemiBold)
                                if (account.subtitle.isNotBlank()) Text(account.subtitle, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "পুরনো সিস্টেমের আইডি ${account.legacyId}" + if (account.added.isNotBlank()) " — যোগ করা ${dmy(account.added)}" else "",
                                    style = MaterialTheme.typography.labelSmall, color = muted,
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("পুরনো সিস্টেমের ব্যালেন্স", style = MaterialTheme.typography.labelSmall, color = muted)
                                    Text(kitMoney(account.closing), fontWeight = AppFontWeight.Bold)
                                }
                                Text("${account.entries} টা লেনদেন", style = MaterialTheme.typography.labelSmall, color = muted)
                                Text(
                                    "পুরনো সিস্টেমের নিজের অ্যাকাউন্ট — এখানে কোনো হিসাব হয় না, নতুন খাতায় কিছু পোস্ট হয় না।",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                    item {
                        ReportTable(
                            columns = listOf(
                                ReportColumn<LegacyLedgerRow>("ক্রমিক নং", ReportColWidth.Fixed(64.dp), TextAlign.Center) { _, i -> cellText((i + 1).toString()) },
                                ReportColumn<LegacyLedgerRow>("তারিখ", ReportColWidth.Fixed(92.dp), TextAlign.Center) { r, _ -> cellText(if (r.date.isBlank()) "—" else dmy(r.date)) },
                                ReportColumn<LegacyLedgerRow>("বিবরণ", ReportColWidth.Fixed(200.dp)) { r, _ -> cellText(r.particulars, maxLines = 3) },
                                ReportColumn<LegacyLedgerRow>("ডেবিট", ReportColWidth.Fixed(96.dp), TextAlign.End) { r, _ -> cellText(kitMoneyDash(r.debit)) },
                                ReportColumn<LegacyLedgerRow>("ক্রেডিট", ReportColWidth.Fixed(96.dp), TextAlign.End) { r, _ -> cellText(kitMoneyDash(r.credit)) },
                                ReportColumn<LegacyLedgerRow>("ব্যালেন্স", ReportColWidth.Fixed(110.dp), TextAlign.End) { r, _ -> cellText(kitMoneyDash(r.balance)) },
                            ),
                            data = account.rows,
                            scrollable = false,
                            noDataMessage = "এই অ্যাকাউন্টের কোনো লেনদেন পাওয়া যায়নি",
                        )
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("পুরনো ERP-র নিজস্ব অ্যাকাউন্ট", fontWeight = AppFontWeight.SemiBold, color = MaterialTheme.appColors.textOnScreen)
                            Text(
                                "পুরনো সিস্টেমের নিজের ক্যাশ ও ব্যাংক অ্যাকাউন্ট — শুধু ব্যাকআপ, কোনো ওপেনিং নয়। সারিতে ট্যাপ করলে লেজার খুলবে।",
                                style = MaterialTheme.typography.bodySmall, color = onScreenMuted,
                            )
                        }
                    }
                    if (state.hasTotals) {
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    "(−) ব্যালেন্স — মোট" to state.negative,
                                    "(+) ব্যালেন্স — মোট" to state.positive,
                                    "নিট ((+) − (−))" to state.net,
                                ).forEach { (label, v) ->
                                    Card(Modifier.weight(1f)) {
                                        Column(Modifier.padding(8.dp)) {
                                            Text(label, style = MaterialTheme.typography.labelSmall, color = muted)
                                            Text(totalFigure(v), fontWeight = AppFontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    when {
                        state.isLoading -> item { ReportLoading() }
                        state.rows.isEmpty() -> item { ReportNote("পুরনো সিস্টেমের কোনো অ্যাকাউন্ট আনা হয়নি") }
                        else -> itemsIndexed(state.rows, key = { _, r -> r.legacyId }) { i, row ->
                            Card(Modifier.fillMaxWidth().clickable(enabled = !state.opening) { viewModel.open(row) }) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("${i + 1}. ${row.name}", fontWeight = AppFontWeight.SemiBold, modifier = Modifier.weight(1f))
                                        Text(kitMoneyDash(row.balance), fontWeight = AppFontWeight.SemiBold)
                                    }
                                    Text(
                                        "নম্বর: ${row.number.ifBlank { "—" }} · ধরন: ${row.type.ifBlank { "—" }} · মোবাইল: ${row.mobile.ifBlank { "—" }}",
                                        style = MaterialTheme.typography.labelSmall, color = muted,
                                    )
                                    Text("বিবরণ: ${row.details.ifBlank { "—" }} · লেনদেন: ${row.entries}", style = MaterialTheme.typography.labelSmall, color = muted)
                                }
                            }
                        }
                    }
                    if (state.opening) item { ReportLoading() }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}
