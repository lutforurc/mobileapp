package com.example.cashbookbd.ui.reports

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.example.cashbookbd.core.jNum
import com.example.cashbookbd.core.jObj
import com.example.cashbookbd.core.jObjs
import com.example.cashbookbd.core.jPayload
import com.example.cashbookbd.core.jStr
import com.example.cashbookbd.data.remote.WebApi
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.FilterActions
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DabEntry(val sl: String, val description: String, val amount: Double)
data class DabSection(val key: String, val title: String, val rows: List<DabEntry>, val total: Double)
data class DabBalanceRow(val sl: String, val name: String, val accountNumber: String, val details: String, val balance: Double)
data class DabBalanceTable(val rows: List<DabBalanceRow>, val total: Double)

data class DailyAccountBook(
    val opening: Double,
    val receipt: List<DabSection>,
    val payment: List<DabSection>,
    val totalReceipt: Double,
    val totalPayment: Double,
    val banks: DabBalanceTable,
    val mobiles: DabBalanceTable,
    val closingReceivable: Double,
    val closingPayable: Double,
    val closing: Double,
)

private val EVERY_CASH = SelectorOption("", "Every cash account")

data class DailyAccountBookUiState(
    val branch: BranchPick = BranchPick(),
    val startDate: SimpleDate? = null,
    val endDate: SimpleDate? = null,
    val cashAccounts: List<SelectorOption> = listOf(EVERY_CASH),
    val cashAccount: SelectorOption = EVERY_CASH,
    val isLoading: Boolean = false,
    val error: String? = null,
    val report: DailyAccountBook? = null,
    val message: String? = null,
    val sessionExpired: Boolean = false,
)

/**
 * The day's cash book, both sides on one page (DailyAccountBook.tsx).
 *
 * The two sides are never added together: receipts and payments are two
 * questions about the same till, and the closing figure is Opening + Receipt
 * − Payment as the server writes it. A section with no entries is not drawn
 * at all, and a side whose every section is empty goes with its banner.
 */
class DailyAccountBookViewModel(
    private val api: WebApi,
    private val reportRepository: ReportRepository,
    private val ownBranchId: Long?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DailyAccountBookUiState())
    val uiState: StateFlow<DailyAccountBookUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            when (val result = reportRepository.loadBranchPick(ownBranchId)) {
                is Resource.Success -> _uiState.update {
                    it.copy(branch = result.data, startDate = result.data.trxDate, endDate = result.data.trxDate)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(branch = it.branch.copy(loading = false), error = result.message, sessionExpired = result.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onBranch(id: String) = _uiState.update { it.copy(branch = it.branch.pick(id)) }
    fun onStart(d: SimpleDate) = _uiState.update { it.copy(startDate = d) }
    fun onEnd(d: SimpleDate) = _uiState.update { it.copy(endDate = d) }
    fun onCash(o: SelectorOption) = _uiState.update { it.copy(cashAccount = o) }

    fun reset() = _uiState.update {
        it.copy(
            startDate = it.branch.trxDate ?: it.startDate,
            endDate = it.branch.trxDate ?: it.endDate,
            cashAccount = EVERY_CASH, report = null, error = null,
        )
    }

    fun apply() {
        val s = _uiState.value
        val branch = s.branch.selectedId
        if (branch == null) {
            _uiState.update { it.copy(message = "Choose a branch first.") }
            return
        }
        val from = s.startDate
        val to = s.endDate
        if (from == null || to == null) {
            _uiState.update { it.copy(message = "Choose a date range first.") }
            return
        }
        if (s.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val params = buildMap {
                put("branch_id", branch.toString())
                put("start_date", from.toApi())
                put("end_date", to.toApi())
                s.cashAccount.id.takeIf { it.isNotBlank() }?.let { put("cash_account_id", it) }
            }
            when (val result = api.get("reports/daily-account-book", params)) {
                is Resource.Success -> {
                    val data = result.data.jPayload()
                    val accounts = data.jObjs("cash_accounts").map { SelectorOption(it.jStr("id"), it.jStr("name")) }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            report = data.toBook(),
                            cashAccounts = listOf(EVERY_CASH) + accounts,
                        )
                    }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, report = null, error = result.message, sessionExpired = result.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    private fun JsonObject.toBook(): DailyAccountBook {
        fun sections(side: String) = jObj("sections")?.jObjs(side).orEmpty().map { s ->
            DabSection(
                key = s.jStr("key"),
                title = s.jStr("title"),
                rows = s.jObjs("rows").map { r -> DabEntry(r.jStr("sl"), r.jStr("description"), r.jNum("amount")) },
                total = s.jNum("total"),
            )
        }
        fun balances(key: String): DabBalanceTable {
            val t = jObj(key)
            return DabBalanceTable(
                rows = t?.jObjs("rows").orEmpty().map { r ->
                    DabBalanceRow(r.jStr("sl"), r.jStr("name"), r.jStr("account_number"), r.jStr("details"), r.jNum("balance"))
                },
                total = t?.jNum("total") ?: 0.0,
            )
        }
        return DailyAccountBook(
            opening = jNum("opening"),
            receipt = sections("receipt"),
            payment = sections("payment"),
            totalReceipt = jObj("totals")?.jNum("receipt") ?: 0.0,
            totalPayment = jObj("totals")?.jNum("payment") ?: 0.0,
            banks = balances("banks"),
            mobiles = balances("mobiles"),
            closingReceivable = jNum("closing_receivable"),
            closingPayable = jNum("closing_payable"),
            closing = jNum("closing"),
        )
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val app = context.applicationContext
                DailyAccountBookViewModel(
                    api = ServiceLocator.provideWebApi(app),
                    reportRepository = ServiceLocator.provideReportRepository(app),
                    ownBranchId = ServiceLocator.provideSessionManager(app).state.value.settings?.branchId,
                )
            }
        }
    }
}

/** The web's money(): blank for nought. */
private fun dabMoney(v: Double) = if (v == 0.0) "" else kitMoney(v)

@Composable
fun DailyAccountBookScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    viewModel: DailyAccountBookViewModel = viewModel(factory = DailyAccountBookViewModel.provideFactory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)
    SnackOnMessage(state.message, snackbar, viewModel::onMessageShown)

    AuthenticatedShell(title = "Daily Account Book", currentRoute = Routes.REPORTS, navController = navController, onLogout = onLogout) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { BranchField(state.branch, viewModel::onBranch) }
                item { DateRangeFields(state.startDate, state.endDate, viewModel::onStart, viewModel::onEnd) }
                // Narrows the till the book is about.
                item { AppSelectDropdown("Cash Account", state.cashAccounts, state.cashAccount, viewModel::onCash) }
                item {
                    FilterActions(onApply = viewModel::apply, onReset = viewModel::reset, isLoading = state.isLoading, modifier = Modifier.fillMaxWidth())
                }
                if (state.isLoading) item { ReportLoading() }
                state.error?.takeIf { !state.isLoading }?.let { item { ReportError(it) } }

                val book = state.report
                if (book != null) {
                    item { TotalBox("Opening Balance", dabMoney(book.opening)) }
                    val receipt = book.receipt.filter { it.rows.isNotEmpty() }
                    val payment = book.payment.filter { it.rows.isNotEmpty() }
                    if (receipt.isNotEmpty()) {
                        item { SideBanner("RECEIPT") }
                        receipt.forEach { section -> item(key = "r-${section.key}") { SectionBlock(section) } }
                    }
                    if (payment.isNotEmpty()) {
                        item { SideBanner("PAYMENT") }
                        payment.forEach { section -> item(key = "p-${section.key}") { SectionBlock(section) } }
                    }
                    item { TotalBox("Total Receipt Amount", dabMoney(book.totalReceipt)) }
                    item { TotalBox("Total Payment Amount", dabMoney(book.totalPayment)) }
                    if (receipt.isEmpty() && payment.isEmpty()) item { ReportNote("No cash movement in that period.") }
                    if (book.banks.rows.isNotEmpty()) item { BalanceBlock("Closing Bank Balance", book.banks) }
                    if (book.mobiles.rows.isNotEmpty()) item { BalanceBlock("Mobile Bank Balance", book.mobiles) }
                    item { TotalBox("Closing Receivable Amount", dabMoney(book.closingReceivable)) }
                    item { TotalBox("Closing Payable Amount", dabMoney(book.closingPayable)) }
                    item { TotalBox("Closing Balance", dabMoney(book.closing), emphasis = true) }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun SideBanner(text: String) {
    Box(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small).padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontWeight = AppFontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun TotalBox(label: String, value: String, emphasis: Boolean = false) {
    val tone = if (emphasis) MaterialTheme.appColors.success else MaterialTheme.colorScheme.outline
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, tone, MaterialTheme.shapes.small)
            .background(if (emphasis) tone.copy(alpha = 0.10f) else Color.Transparent, MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = AppFontWeight.SemiBold)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = AppFontWeight.Bold)
    }
}

@Composable
private fun BlockTitle(title: String) {
    Text(
        title,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = AppFontWeight.Bold,
    )
}

@Composable
private fun SectionBlock(section: DabSection) {
    Column(Modifier.fillMaxWidth()) {
        BlockTitle(section.title)
        ReportTable(
            columns = listOf(
                ReportColumn<DabEntry>("SL", ReportColWidth.Weight(0.12f), TextAlign.Center) { r, _ -> cellText(r.sl) },
                ReportColumn<DabEntry>("DESCRIPTION", ReportColWidth.Weight(0.58f)) { r, _ -> cellText(r.description, maxLines = 4) },
                ReportColumn<DabEntry>("AMOUNT", ReportColWidth.Weight(0.30f), TextAlign.End) { r, _ -> cellText(dabMoney(r.amount)) },
            ),
            data = section.rows,
            scrollable = false,
            footerRows = listOf(
                listOf(
                    ReportFooterCell(cellText("Total", align = TextAlign.End, bold = true), colSpan = 2),
                    ReportFooterCell(cellText(dabMoney(section.total), bold = true)),
                ),
            ),
        )
    }
}

@Composable
private fun BalanceBlock(title: String, table: DabBalanceTable) {
    Column(Modifier.fillMaxWidth()) {
        BlockTitle(title)
        ReportTable(
            columns = listOf(
                ReportColumn<DabBalanceRow>("SL", ReportColWidth.Fixed(44.dp), TextAlign.Center) { r, _ -> cellText(r.sl) },
                ReportColumn<DabBalanceRow>("ACCOUNT NAME", ReportColWidth.Fixed(150.dp)) { r, _ -> cellText(r.name, maxLines = 2) },
                ReportColumn<DabBalanceRow>("ACCOUNT NUMBER", ReportColWidth.Fixed(130.dp)) { r, _ -> cellText(r.accountNumber, maxLines = 2) },
                ReportColumn<DabBalanceRow>("DETAILS", ReportColWidth.Fixed(140.dp)) { r, _ -> cellText(r.details, maxLines = 2) },
                ReportColumn<DabBalanceRow>("BALANCE", ReportColWidth.Fixed(100.dp), TextAlign.End) { r, _ -> cellText(dabMoney(r.balance)) },
            ),
            data = table.rows,
            scrollable = false,
            footerRows = listOf(
                listOf(
                    ReportFooterCell(cellText("Total", align = TextAlign.End, bold = true), colSpan = 4),
                    ReportFooterCell(cellText(dabMoney(table.total), bold = true)),
                ),
            ),
        )
    }
}
