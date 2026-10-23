package com.example.cashbookbd.ui.products

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.cashbookbd.core.jAsObj
import com.example.cashbookbd.core.jBool
import com.example.cashbookbd.core.jNum
import com.example.cashbookbd.core.jNumOrNull
import com.example.cashbookbd.core.jObjects
import com.example.cashbookbd.core.jPayload
import com.example.cashbookbd.core.jAsArr
import com.example.cashbookbd.core.jStr
import com.example.cashbookbd.data.remote.WebApi
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.data.repository.SelectorRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.report.ReportSelectorSource
import com.example.cashbookbd.ui.components.FilterActions
import com.example.cashbookbd.ui.components.SearchableSelectDropdown
import com.example.cashbookbd.ui.reports.BranchField
import com.example.cashbookbd.ui.reports.BranchPick
import com.example.cashbookbd.ui.reports.DateRangeFields
import com.example.cashbookbd.ui.reports.LogoutOnExpiry
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportError
import com.example.cashbookbd.ui.reports.ReportFooterCell
import com.example.cashbookbd.ui.reports.ReportLoading
import com.example.cashbookbd.ui.reports.ReportNote
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.reports.kitMoney
import com.example.cashbookbd.ui.reports.kitMoneyDash
import com.example.cashbookbd.ui.reports.loadBranchPick
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One line of `reports/product-statement`: an Opening row or an invoice line. */
data class StockStatementLine(
    val sl: String,
    val productName: String,
    val type: String,
    val invoiceNo: String,
    val invoiceDate: String,
    val isOpening: Boolean,
    val qty: Double,
    val purchasePrice: Double,
    val salesPrice: Double,
    val amount: Double,
    val stock: Double,
)

data class ProductStockStatementUiState(
    val branch: BranchPick = BranchPick(),
    val product: SelectorOption? = null,
    val startDate: SimpleDate? = null,
    val endDate: SimpleDate? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val rows: List<StockStatementLine> = emptyList(),
    val closing: Double? = null,
    val hasRun: Boolean = false,
    val sessionExpired: Boolean = false,
)

/**
 * Products → Product Statement (web 87782eb7): Opening row per product, then
 * every invoice line with Qty, both prices, Amount and a running Stock. A
 * blank product means every product. The window opens on the first of the
 * business month through the business day, like the web.
 */
class ProductStockStatementViewModel(
    private val api: WebApi,
    private val reportRepository: ReportRepository,
    private val selectorRepository: SelectorRepository,
    private val ownBranchId: Long?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductStockStatementUiState())
    val uiState: StateFlow<ProductStockStatementUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            when (val result = reportRepository.loadBranchPick(ownBranchId)) {
                is Resource.Success -> _uiState.update {
                    val day = result.data.trxDate ?: SimpleDate.today()
                    it.copy(branch = result.data, startDate = day.copy(day = 1), endDate = day)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(branch = it.branch.copy(loading = false), error = result.message, sessionExpired = result.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onBranch(id: String) = _uiState.update { it.copy(branch = it.branch.pick(id)) }
    fun onProduct(o: SelectorOption) = _uiState.update { it.copy(product = o) }
    fun onStart(d: SimpleDate) = _uiState.update { it.copy(startDate = d) }
    fun onEnd(d: SimpleDate) = _uiState.update { it.copy(endDate = d) }

    suspend fun searchProducts(q: String) = selectorRepository.fetch(ReportSelectorSource.PRODUCT, q)

    /** The web's Reset only folds the filter panel; the filters and sheet stay. */
    fun reset() = Unit

    fun apply() {
        val s = _uiState.value
        val branchId = s.branch.selectedId
        val start = s.startDate
        val end = s.endDate
        if (branchId == null || start == null || end == null) {
            _uiState.update { it.copy(error = "Please select branch and date range.") }
            return
        }
        if (s.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val params = buildMap {
                put("branch_id", branchId.toString())
                s.product?.id?.takeIf { it.isNotBlank() }?.let { put("product_id", it) }
                put("startdate", start.toDisplay())
                put("enddate", end.toDisplay())
            }
            when (val result = api.get("reports/product-statement", params)) {
                is Resource.Success -> {
                    val raw = result.data.jPayload().jAsObj()
                    val rows = raw?.get("details").jAsArr()?.jObjects().orEmpty().map { o ->
                        StockStatementLine(
                            sl = o.jStr("sl"),
                            productName = o.jStr("product_name"),
                            type = o.jStr("type"),
                            invoiceNo = o.jStr("invoice_no"),
                            invoiceDate = o.jStr("invoice_date"),
                            isOpening = o.jBool("is_opening") == true,
                            qty = o.jNum("qty"),
                            purchasePrice = o.jNum("purchase_price"),
                            salesPrice = o.jNum("sales_price"),
                            amount = o.jNum("amount"),
                            stock = o.jNum("stock"),
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false, hasRun = true, rows = rows,
                            closing = raw?.get("totals").jAsObj()?.jNumOrNull("closing"),
                        )
                    }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, hasRun = true, rows = emptyList(), closing = null, error = result.message, sessionExpired = result.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val app = context.applicationContext
                ProductStockStatementViewModel(
                    api = ServiceLocator.provideWebApi(app),
                    reportRepository = ServiceLocator.provideReportRepository(app),
                    selectorRepository = ServiceLocator.provideSelectorRepository(app),
                    ownBranchId = ServiceLocator.provideSessionManager(app).state.value.settings?.branchId,
                )
            }
        }
    }
}

/** dd/MM/yyyy from the API's yyyy-MM-dd (or as it came). */
private fun displayDate(value: String): String =
    if (value.isBlank()) "" else SimpleDate.fromApi(value.take(10))?.toDisplay() ?: value

@Composable
fun ProductStockStatementScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    viewModel: ProductStockStatementViewModel = viewModel(factory = ProductStockStatementViewModel.provideFactory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)

    AuthenticatedShell(
        title = "Product Statement",
        currentRoute = Routes.PRODUCTS,
        navController = navController,
        onLogout = onLogout,
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BranchField(state.branch, viewModel::onBranch)
                SearchableSelectDropdown(
                    selected = state.product,
                    onSelected = viewModel::onProduct,
                    search = viewModel::searchProducts,
                    label = "Select Product",
                    placeholder = "All Products",
                    emptyText = "No product found",
                )
                DateRangeFields(state.startDate, state.endDate, viewModel::onStart, viewModel::onEnd)
                FilterActions(
                    onApply = viewModel::apply,
                    onReset = viewModel::reset,
                    isLoading = state.isLoading,
                    canApply = state.branch.selected != null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            when {
                state.error != null -> ReportError(state.error!!, viewModel::apply)
                state.isLoading && !state.hasRun -> ReportLoading()
                state.hasRun -> StockStatementTable(state.rows, state.closing)
                else -> ReportNote("Choose your filters, then tap Apply.")
            }
        }
    }
}

@Composable
private fun StockStatementTable(rows: List<StockStatementLine>, closing: Double?) {
    val negative = MaterialTheme.colorScheme.error
    val columns = listOf(
        ReportColumn<StockStatementLine>("SL. NO.", ReportColWidth.Fixed(56.dp), TextAlign.Center) { r, _ -> cellText(r.sl.ifBlank { "-" }) },
        ReportColumn<StockStatementLine>("NAME", ReportColWidth.Fixed(170.dp)) { r, _ -> cellText(r.productName.ifBlank { "-" }, maxLines = 3) },
        ReportColumn<StockStatementLine>("TYPE", ReportColWidth.Fixed(90.dp)) { r, _ -> cellText(r.type.ifBlank { "-" }) },
        ReportColumn<StockStatementLine>("INV. NO.", ReportColWidth.Fixed(110.dp)) { r, _ ->
            cellText(if (r.isOpening) "Opening" else r.invoiceNo.ifBlank { "-" }, bold = r.isOpening)
        },
        ReportColumn<StockStatementLine>("INV. DATE", ReportColWidth.Fixed(90.dp)) { r, _ -> cellText(displayDate(r.invoiceDate)) },
        ReportColumn<StockStatementLine>("QTY", ReportColWidth.Fixed(80.dp), TextAlign.End) { r, _ ->
            cellText(kitMoney(r.qty), color = if (r.qty < 0) negative else androidx.compose.ui.graphics.Color.Unspecified)
        },
        ReportColumn<StockStatementLine>("PURCHASE PRICE", ReportColWidth.Fixed(100.dp), TextAlign.End) { r, _ -> cellText(kitMoney(r.purchasePrice)) },
        ReportColumn<StockStatementLine>("SALES PRICE", ReportColWidth.Fixed(100.dp), TextAlign.End) { r, _ -> cellText(kitMoney(r.salesPrice)) },
        ReportColumn<StockStatementLine>("AMOUNT", ReportColWidth.Fixed(110.dp), TextAlign.End) { r, _ -> cellText(kitMoney(r.amount)) },
        ReportColumn<StockStatementLine>("STOCK", ReportColWidth.Fixed(90.dp), TextAlign.End) { r, _ -> cellText(kitMoney(r.stock)) },
    )
    if (rows.isEmpty()) {
        ReportNote("No data found")
        return
    }
    val netQty = rows.sumOf { it.qty }
    val totalAmount = rows.sumOf { it.amount }
    ReportTable(
        columns = columns,
        data = rows,
        footerRows = listOf(
            listOf(
                ReportFooterCell(cellText("Total", align = TextAlign.End, bold = true), colSpan = 5),
                ReportFooterCell(cellText(kitMoney(netQty), bold = true)),
                ReportFooterCell(cellText("-")),
                ReportFooterCell(cellText("-")),
                ReportFooterCell(cellText(kitMoneyDash(totalAmount), bold = true)),
                ReportFooterCell(closing?.let { cellText(kitMoney(it), bold = true) } ?: ReportTableCell.Empty),
            ),
        ),
    )
}
