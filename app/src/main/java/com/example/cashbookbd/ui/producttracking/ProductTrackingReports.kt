package com.example.cashbookbd.ui.producttracking

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.example.cashbookbd.data.repository.LedgerRepository
import com.example.cashbookbd.data.repository.ProductStatement
import com.example.cashbookbd.data.repository.ProductTrackingRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.data.repository.TrackingFigures
import com.example.cashbookbd.data.repository.TrackingProductOption
import com.example.cashbookbd.data.repository.TrackingSummary
import com.example.cashbookbd.data.repository.TrackingUnmapped
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SearchableLedgerDropdown
import com.example.cashbookbd.ui.reports.PickerField
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.accents
import com.example.cashbookbd.ui.theme.appColors
import com.example.cashbookbd.ui.theme.asTint
import com.example.cashbookbd.ui.theme.muted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Both reports are memo statements: what has no product mapping is counted
// beside the figures, never hidden inside them.

private val ALL_BRANCHES = SelectorOption(id = "0", label = "All Branch")

/** The web's line_type → label table; unknown values pass through raw. */
private val LINE_TYPE_LABELS = mapOf(
    "sales_bill" to "Sales Bill",
    "sales_return" to "Sales Return",
    "purchase_bill" to "Purchase Bill",
    "purchase_return" to "Purchase Return",
    "cash_received" to "Cash Received",
    "cash_payment" to "Cash Payment",
    "bank_received" to "Bank Received",
    "bank_payment" to "Bank Payment",
    "journal_received" to "Journal (Received)",
    "journal_payment" to "Journal (Payment)",
)

// ---------------------------------------------------------------------------
// Product Financial Statement
// ---------------------------------------------------------------------------

data class ProductStatementUiState(
    val branches: List<SelectorOption> = listOf(ALL_BRANCHES),
    val branch: SelectorOption = ALL_BRANCHES,
    val party: LedgerDropdownItem? = null,
    val products: List<TrackingProductOption> = emptyList(),
    val product: TrackingProductOption? = null,
    val dateFrom: SimpleDate? = null,
    val dateTo: SimpleDate? = null,

    val isLoading: Boolean = false,
    val error: String? = null,
    val hasApplied: Boolean = false,
    val report: ProductStatement? = null,

    val message: String? = null,
    val sessionExpired: Boolean = false,
)

class ProductStatementViewModel(
    private val repository: ProductTrackingRepository,
    private val ledgerRepository: LedgerRepository,
    private val reportRepository: ReportRepository,
    /** A product handed over from the Summary screen's row tap, or null. */
    initialProductId: Long?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductStatementUiState())
    val uiState: StateFlow<ProductStatementUiState> = _uiState.asStateFlow()

    private val presetProductId = initialProductId

    init {
        loadBranches()
        loadProducts()
    }

    private fun loadBranches() {
        viewModelScope.launch {
            val result = reportRepository.getBranches()
            if (result is Resource.Success) {
                _uiState.update { state ->
                    state.copy(
                        branches = listOf(ALL_BRANCHES) + result.data.branches.map {
                            SelectorOption(id = it.id.toString(), label = it.name)
                        },
                    )
                }
            }
        }
    }

    /** The tracked products for the picked scope; refetched on scope change. */
    private fun loadProducts() {
        val state = _uiState.value
        viewModelScope.launch {
            val result = repository.fetchTrackedProducts(
                branchId = state.branch.id.toLongOrNull() ?: 0L,
                coa4Id = state.party?.id?.toLong() ?: 0L,
            )
            if (result is Resource.Success) {
                _uiState.update { s ->
                    s.copy(
                        products = result.data,
                        // The Summary screen's row tap lands pre-selected — an
                        // improvement over the web, which drops the parameter.
                        product = s.product
                            ?: presetProductId?.let { id -> result.data.firstOrNull { it.id == id } },
                    )
                }
            }
        }
    }

    fun onBranchSelected(option: SelectorOption) {
        _uiState.update { it.copy(branch = option) }
        loadProducts()
    }

    fun onPartySelected(party: LedgerDropdownItem?) {
        _uiState.update { it.copy(party = party) }
        loadProducts()
    }

    fun onProductSelected(option: SelectorOption) = _uiState.update { state ->
        state.copy(product = state.products.firstOrNull { it.id.toString() == option.id })
    }

    fun onDateFrom(date: SimpleDate) = _uiState.update { it.copy(dateFrom = date) }
    fun onDateTo(date: SimpleDate) = _uiState.update { it.copy(dateTo = date) }

    suspend fun searchParties(query: String): Resource<List<LedgerDropdownItem>> =
        ledgerRepository.searchLedgers(query)

    fun apply() {
        val state = _uiState.value
        val product = state.product ?: run {
            _uiState.update { it.copy(message = "Select a product.") }
            return
        }
        val from = state.dateFrom
        val to = state.dateTo
        if (from == null || to == null) {
            _uiState.update { it.copy(message = "Choose a date range.") }
            return
        }
        if (from.toApi() > to.toApi()) {
            _uiState.update { it.copy(message = "The start date cannot be after the end date.") }
            return
        }
        if (state.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = repository.fetchStatement(
                productId = product.id,
                branchId = state.branch.id.toLongOrNull() ?: 0L,
                coa4Id = state.party?.id?.toLong() ?: 0L,
                startDate = from.toApi(),
                endDate = to.toApi(),
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, hasApplied = true, report = result.data)
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

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context, initialProductId: Long?) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                ProductStatementViewModel(
                    repository = ServiceLocator.provideProductTrackingRepository(appContext),
                    ledgerRepository = ServiceLocator.provideLedgerRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                    initialProductId = initialProductId,
                )
            }
        }
    }
}

/** One product's memo ledger: bills, collections and the running balances. */
@Composable
fun ProductStatementScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    initialProductId: Long? = null,
    viewModel: ProductStatementViewModel = viewModel(
        key = "statement-${initialProductId ?: 0L}",
        factory = ProductStatementViewModel.provideFactory(LocalContext.current, initialProductId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onMessageShown()
    }

    AuthenticatedShell(
        title = "Product Statement",
        currentRoute = Routes.REPORTS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppSelectDropdown(
                    label = "Select Branch",
                    options = state.branches,
                    selected = state.branch,
                    onSelected = viewModel::onBranchSelected,
                    modifier = Modifier.fillMaxWidth(),
                )
                SearchableLedgerDropdown(
                    selectedLedger = state.party,
                    onLedgerSelected = viewModel::onPartySelected,
                    searchLedgers = viewModel::searchParties,
                    label = "Customer / Supplier",
                    placeholder = "All parties",
                )
                AppSelectDropdown(
                    label = "Select Product",
                    options = state.products.map {
                        SelectorOption(
                            id = it.id.toString(),
                            label = it.name + if (!it.isActive) " (inactive)" else "",
                        )
                    },
                    selected = state.product?.let {
                        SelectorOption(it.id.toString(), it.name + if (!it.isActive) " (inactive)" else "")
                    },
                    onSelected = viewModel::onProductSelected,
                    placeholder = "-- Select Product --",
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PickerField(
                        label = "Start Date",
                        value = state.dateFrom?.toDisplay().orEmpty(),
                        trailingIcon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f),
                        onClick = { pickDate(context, state.dateFrom, viewModel::onDateFrom) },
                    )
                    PickerField(
                        label = "End Date",
                        value = state.dateTo?.toDisplay().orEmpty(),
                        trailingIcon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f),
                        onClick = { pickDate(context, state.dateTo, viewModel::onDateTo) },
                    )
                }
                PrimaryButton(
                    text = "Apply",
                    onClick = viewModel::apply,
                    isLoading = state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )

                when {
                    state.error != null -> Text(state.error!!, color = MaterialTheme.colorScheme.onBackground)
                    !state.hasApplied -> Text(
                        text = "Pick a product and a date range, then tap Apply.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textOnScreenMuted,
                    )
                    state.report != null -> StatementBody(state.report!!)
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun StatementBody(report: ProductStatement) {
    val onScreen = MaterialTheme.colorScheme.onBackground

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(report.productName, style = MaterialTheme.typography.titleSmall, fontWeight = AppFontWeight.SemiBold)
            Text(
                text = listOf(report.partyName, report.branchName).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = onScreen.muted(),
            )
            Text(
                text = "${report.startDate} — ${report.endDate}",
                style = MaterialTheme.typography.labelSmall,
                color = onScreen.muted(),
            )
        }

        MemoNotice(report.notice, report.unmapped)

        // The two summary cards: what came in against what went out.
        SummaryCard(
            title = "Receivable (Sales)",
            lines = listOf(
                "Opening Receivable" to report.summary.openingReceivable,
                "Sales Bill" to report.summary.salesBill,
                "Sales Return" to -report.summary.salesReturn,
                "Cash Received" to -report.summary.cashReceived,
            ),
            closingLabel = "Closing Receivable",
            closing = report.summary.closingReceivable,
        )
        SummaryCard(
            title = "Payable (Purchase)",
            lines = listOf(
                "Opening Payable" to report.summary.openingPayable,
                "Purchase Bill" to report.summary.purchaseBill,
                "Purchase Return" to -report.summary.purchaseReturn,
                "Cash Payment" to -report.summary.cashPayment,
            ),
            closingLabel = "Closing Payable",
            closing = report.summary.closingPayable,
        )

        if (report.rows.isEmpty()) {
            Text(
                text = "No transactions in this range.",
                style = MaterialTheme.typography.bodySmall,
                color = onScreen.muted(),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
        } else {
            val hScroll = rememberScrollState()
            Column(modifier = Modifier.fillMaxWidth().horizontalScroll(hScroll)) {
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(vertical = 6.dp),
                ) {
                    StatementHeaderCell("Date", 84.dp)
                    StatementHeaderCell("Voucher", 110.dp)
                    StatementHeaderCell("Type", 110.dp)
                    StatementHeaderCell("Party", 130.dp)
                    StatementHeaderCell("Qty", 60.dp, TextAlign.End)
                    StatementHeaderCell("Rate", 70.dp, TextAlign.End)
                    StatementHeaderCell("Sales Bill", 90.dp, TextAlign.End)
                    StatementHeaderCell("Purchase Bill", 100.dp, TextAlign.End)
                    StatementHeaderCell("Received", 90.dp, TextAlign.End)
                    StatementHeaderCell("Payment", 90.dp, TextAlign.End)
                    StatementHeaderCell("Receivable", 96.dp, TextAlign.End)
                    StatementHeaderCell("Payable", 96.dp, TextAlign.End)
                }
                report.rows.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.appColors.gridLine)
                    Row(modifier = Modifier.padding(vertical = 5.dp)) {
                        StatementCell(row.vrDate, 84.dp)
                        StatementCell(row.vrNo.ifBlank { "-" }, 110.dp)
                        StatementCell(LINE_TYPE_LABELS[row.lineType] ?: row.lineType, 110.dp)
                        StatementCell(row.partyName.ifBlank { "-" }, 130.dp)
                        StatementCell(dashMoney(row.quantity), 60.dp, TextAlign.End)
                        StatementCell(dashMoney(row.rate), 70.dp, TextAlign.End)
                        StatementCell(dashMoney(row.salesBill - row.salesReturn), 90.dp, TextAlign.End)
                        StatementCell(dashMoney(row.purchaseBill - row.purchaseReturn), 100.dp, TextAlign.End)
                        StatementCell(dashMoney(row.cashReceived), 90.dp, TextAlign.End)
                        StatementCell(dashMoney(row.cashPayment), 90.dp, TextAlign.End)
                        // The running balances always show a figure, 0 included.
                        StatementCell(AmountFormat.format(row.receivableBalance), 96.dp, TextAlign.End)
                        StatementCell(AmountFormat.format(row.payableBalance), 96.dp, TextAlign.End)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatementHeaderCell(text: String, width: Dp, align: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = AppFontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onPrimary,
        textAlign = align,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width).padding(horizontal = 4.dp),
    )
}

@Composable
private fun StatementCell(text: String, width: Dp, align: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = align,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width).padding(horizontal = 4.dp),
    )
}

@Composable
private fun SummaryCard(
    title: String,
    lines: List<Pair<String, Double>>,
    closingLabel: String,
    closing: Double,
) {
    val onScreen = MaterialTheme.colorScheme.onBackground
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = AppFontWeight.SemiBold)
            lines.forEach { (label, amount) ->
                Row {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = onScreen.muted(), modifier = Modifier.weight(1f))
                    Text(AmountFormat.format(amount), style = MaterialTheme.typography.bodySmall, color = onScreen)
                }
            }
            HorizontalDivider(color = MaterialTheme.appColors.gridLine)
            Row {
                Text(closingLabel, style = MaterialTheme.typography.bodySmall, fontWeight = AppFontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    AmountFormat.format(closing),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = AppFontWeight.SemiBold,
                )
            }
        }
    }
}

/** The memo-statement warning, and what fell outside the mapping. */
@Composable
private fun MemoNotice(notice: String, unmapped: TrackingUnmapped) {
    val amber = MaterialTheme.accents.amber
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(amber.asTint(), RoundedCornerShape(6.dp))
            .padding(10.dp),
    ) {
        if (notice.isNotBlank()) {
            Text(notice, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground)
        }
        if (unmapped.rowsCount > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "This range holds ${unmapped.rowsCount} transaction(s) with no product — " +
                    "Received ${AmountFormat.format(unmapped.received)} and " +
                    "Payment ${AmountFormat.format(unmapped.payment)} — left out of these figures.",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = AppFontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Product Receivable / Payable (tracking summary)
// ---------------------------------------------------------------------------

data class TrackingSummaryUiState(
    val branches: List<SelectorOption> = listOf(ALL_BRANCHES),
    val branch: SelectorOption = ALL_BRANCHES,
    val party: LedgerDropdownItem? = null,
    val dateFrom: SimpleDate? = null,
    val dateTo: SimpleDate? = null,
    val includeInactive: Boolean = false,

    val isLoading: Boolean = false,
    val error: String? = null,
    val hasApplied: Boolean = false,
    val report: TrackingSummary? = null,

    val message: String? = null,
    val sessionExpired: Boolean = false,
)

class TrackingSummaryViewModel(
    private val repository: ProductTrackingRepository,
    private val ledgerRepository: LedgerRepository,
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrackingSummaryUiState())
    val uiState: StateFlow<TrackingSummaryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val result = reportRepository.getBranches()
            if (result is Resource.Success) {
                _uiState.update { state ->
                    state.copy(
                        branches = listOf(ALL_BRANCHES) + result.data.branches.map {
                            SelectorOption(id = it.id.toString(), label = it.name)
                        },
                    )
                }
            }
        }
    }

    fun onBranchSelected(option: SelectorOption) = _uiState.update { it.copy(branch = option) }
    fun onPartySelected(party: LedgerDropdownItem?) = _uiState.update { it.copy(party = party) }
    fun onDateFrom(date: SimpleDate) = _uiState.update { it.copy(dateFrom = date) }
    fun onDateTo(date: SimpleDate) = _uiState.update { it.copy(dateTo = date) }
    fun onIncludeInactive(on: Boolean) = _uiState.update { it.copy(includeInactive = on) }

    suspend fun searchParties(query: String): Resource<List<LedgerDropdownItem>> =
        ledgerRepository.searchLedgers(query)

    fun apply() {
        val state = _uiState.value
        val from = state.dateFrom
        val to = state.dateTo
        if (from == null || to == null) {
            _uiState.update { it.copy(message = "Choose a date range.") }
            return
        }
        if (from.toApi() > to.toApi()) {
            _uiState.update { it.copy(message = "The start date cannot be after the end date.") }
            return
        }
        if (state.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = repository.fetchSummary(
                branchId = state.branch.id.toLongOrNull() ?: 0L,
                coa4Id = state.party?.id?.toLong() ?: 0L,
                startDate = from.toApi(),
                endDate = to.toApi(),
                includeInactive = state.includeInactive,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, hasApplied = true, report = result.data)
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

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                TrackingSummaryViewModel(
                    repository = ServiceLocator.provideProductTrackingRepository(appContext),
                    ledgerRepository = ServiceLocator.provideLedgerRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                )
            }
        }
    }
}

/** Every tracked product's receivable and payable at once. */
@Composable
fun ProductTrackingSummaryScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrackingSummaryViewModel = viewModel(
        factory = TrackingSummaryViewModel.provideFactory(LocalContext.current)
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onMessageShown()
    }

    AuthenticatedShell(
        title = "Product Receivable / Payable",
        currentRoute = Routes.REPORTS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppSelectDropdown(
                    label = "Select Branch",
                    options = state.branches,
                    selected = state.branch,
                    onSelected = viewModel::onBranchSelected,
                    modifier = Modifier.fillMaxWidth(),
                )
                SearchableLedgerDropdown(
                    selectedLedger = state.party,
                    onLedgerSelected = viewModel::onPartySelected,
                    searchLedgers = viewModel::searchParties,
                    label = "Customer / Supplier",
                    placeholder = "All parties",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PickerField(
                        label = "Start Date",
                        value = state.dateFrom?.toDisplay().orEmpty(),
                        trailingIcon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f),
                        onClick = { pickDate(context, state.dateFrom, viewModel::onDateFrom) },
                    )
                    PickerField(
                        label = "End Date",
                        value = state.dateTo?.toDisplay().orEmpty(),
                        trailingIcon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f),
                        onClick = { pickDate(context, state.dateTo, viewModel::onDateTo) },
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable {
                        viewModel.onIncludeInactive(!state.includeInactive)
                    },
                ) {
                    Checkbox(checked = state.includeInactive, onCheckedChange = viewModel::onIncludeInactive)
                    Text(
                        text = "Show deactivated products too (to reconcile old figures)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                PrimaryButton(
                    text = "Apply",
                    onClick = viewModel::apply,
                    isLoading = state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )

                when {
                    state.error != null -> Text(state.error!!, color = MaterialTheme.colorScheme.onBackground)
                    !state.hasApplied -> Text(
                        text = "Choose a date range, then tap Apply.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textOnScreenMuted,
                    )
                    state.report != null -> SummaryBody(state.report!!) { productId ->
                        navController.navigate(Routes.productStatement(productId))
                    }
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun SummaryBody(report: TrackingSummary, onProduct: (Long) -> Unit) {
    val onScreen = MaterialTheme.colorScheme.onBackground

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = listOf(report.partyName, report.branchName).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = onScreen.muted(),
            )
            Text(
                text = "${report.startDate} — ${report.endDate}",
                style = MaterialTheme.typography.labelSmall,
                color = onScreen.muted(),
            )
        }

        MemoNotice(report.notice, report.unmapped)

        if (report.rows.isEmpty()) {
            Text(
                text = "No product is configured. Add one under Admin → Product Tracking.",
                style = MaterialTheme.typography.bodySmall,
                color = onScreen.muted(),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
            return@Column
        }

        val hScroll = rememberScrollState()
        Column(modifier = Modifier.fillMaxWidth().horizontalScroll(hScroll)) {
            // The web's two-level header: Product | Receivable ×5 | Payable ×5.
            Row(modifier = Modifier.background(MaterialTheme.colorScheme.primary).padding(vertical = 4.dp)) {
                StatementHeaderCell("", 140.dp)
                StatementHeaderCell("Receivable (Sales)", 88.dp * 5, TextAlign.Center)
                StatementHeaderCell("Payable (Purchase)", 88.dp * 5, TextAlign.Center)
            }
            Row(modifier = Modifier.background(MaterialTheme.colorScheme.primary).padding(bottom = 6.dp)) {
                StatementHeaderCell("Product", 140.dp)
                listOf("Opening", "Sales Bill", "Return", "Received", "Closing").forEach {
                    StatementHeaderCell(it, 88.dp, TextAlign.End)
                }
                listOf("Opening", "Purchase Bill", "Return", "Payment", "Closing").forEach {
                    StatementHeaderCell(it, 88.dp, TextAlign.End)
                }
            }
            report.rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.appColors.gridLine)
                Row(modifier = Modifier.padding(vertical = 5.dp)) {
                    // Tapping a product opens its full statement, pre-filtered
                    // — the web link, minus the dropped query parameter.
                    Text(
                        text = row.productName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .width(140.dp)
                            .padding(horizontal = 4.dp)
                            .clickable { onProduct(row.productId) },
                    )
                    FiguresCells(row.figures)
                }
            }
            report.totals?.let { totals ->
                HorizontalDivider(color = MaterialTheme.appColors.gridLine)
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary.asTint())
                        .padding(vertical = 5.dp),
                ) {
                    Text(
                        text = "Total",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = AppFontWeight.SemiBold,
                        modifier = Modifier.width(140.dp).padding(horizontal = 4.dp),
                    )
                    FiguresCells(totals, bold = true)
                }
            }
        }
    }
}

@Composable
private fun FiguresCells(figures: TrackingFigures, bold: Boolean = false) {
    val weight = if (bold) AppFontWeight.SemiBold else null
    listOf(
        figures.openingReceivable, figures.salesBill, figures.salesReturn,
        figures.cashReceived, figures.closingReceivable,
        figures.openingPayable, figures.purchaseBill, figures.purchaseReturn,
        figures.cashPayment, figures.closingPayable,
    ).forEachIndexed { index, value ->
        // The two Closing columns carry the weight even on plain rows.
        val closing = index == 4 || index == 9
        Text(
            text = dashMoney(value),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (closing) AppFontWeight.SemiBold else weight,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(88.dp).padding(horizontal = 4.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Shared bits
// ---------------------------------------------------------------------------

/** Report cells show zero as "-", per the app-wide report rule. */
private fun dashMoney(value: Double): String =
    if (value == 0.0) "-" else AmountFormat.format(value)

private fun pickDate(context: Context, initial: SimpleDate?, onPicked: (SimpleDate) -> Unit) {
    val start = initial ?: SimpleDate.today()
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            onPicked(SimpleDate(year = year, month = month + 1, day = dayOfMonth))
        },
        start.year,
        start.month - 1,
        start.day,
    ).show()
}
