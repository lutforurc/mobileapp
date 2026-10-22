package com.example.cashbookbd.ui.reports

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.example.cashbookbd.core.jNum
import com.example.cashbookbd.core.jRows
import com.example.cashbookbd.core.jStr
import com.example.cashbookbd.data.remote.WebApi
import com.example.cashbookbd.data.repository.LedgerRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.data.repository.SelectorRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.report.ReportSelectorSource
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.FilterActions
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.components.SearchableLedgerDropdown
import com.example.cashbookbd.ui.components.SearchableSelectDropdown
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One invoice line, as `reports/customer-sales` answers it. */
data class CustomerSalesLine(
    val mtmId: String,
    val customerId: String,
    val customerName: String,
    val invoiceNo: String,
    val invoiceDate: String,
    val memoNo: String,
    val brand: String,
    val group: String,
    val category: String,
    val productName: String,
    val productCode: String,
    val productId: String,
    val quantity: Double,
    val rate: Double,
    val amount: Double,
) {
    /** "CODE - NAME", and only the name where the product has no code. */
    val productLabel: String
        get() = if (productCode.isNotBlank()) "$productCode - ${productName.ifBlank { "-" }}" else productName.ifBlank { "-" }
}

/** A row as the screen draws it: a customer band, an invoice band, or a line. */
sealed interface CustomerSalesRow {
    data class Customer(val name: String, val quantity: Double, val amount: Double) : CustomerSalesRow
    data class Invoice(
        val invoiceNo: String,
        val invoiceDate: String,
        val memoNo: String,
        val quantity: Double,
        val amount: Double,
    ) : CustomerSalesRow
    data class Item(val sl: Int, val line: CustomerSalesLine) : CustomerSalesRow
}

data class CustomerSalesBuilt(
    val rows: List<CustomerSalesRow> = emptyList(),
    val totalQuantity: Double = 0.0,
    val totalAmount: Double = 0.0,
)

/**
 * customerSalesGroups.ts: the flat lines filed Customer → Invoice → Brand →
 * Group → Category → Product. The customer and the invoice are heading rows,
 * not columns; the serial restarts at each invoice. The id leads and the name
 * is only the fallback, so two customers sharing a name stay two accounts.
 */
fun buildCustomerSales(lines: List<CustomerSalesLine>): CustomerSalesBuilt {
    val sorted = lines.sortedWith(
        compareBy<CustomerSalesLine> { it.customerName.lowercase() }
            .thenBy { it.mtmId.toLongOrNull() ?: 0L }
            .thenBy { it.brand.lowercase() }
            .thenBy { it.group.lowercase() }
            .thenBy { it.category.lowercase() }
            .thenBy { it.productName.lowercase() },
    )
    val byCustomer = LinkedHashMap<String, MutableList<CustomerSalesLine>>()
    sorted.forEach { line ->
        val key = if (line.customerId.isNotBlank()) "c:${line.customerId}" else "c:${line.customerName.trim()}"
        byCustomer.getOrPut(key) { mutableListOf() }.add(line)
    }
    val out = mutableListOf<CustomerSalesRow>()
    var totalQty = 0.0
    var totalAmount = 0.0
    byCustomer.values.forEach { customerLines ->
        val byInvoice = LinkedHashMap<String, MutableList<CustomerSalesLine>>()
        customerLines.forEach { line ->
            val key = if (line.mtmId.isNotBlank()) "i:${line.mtmId}" else "i:${line.invoiceNo}|${line.invoiceDate}"
            byInvoice.getOrPut(key) { mutableListOf() }.add(line)
        }
        val blocks = mutableListOf<CustomerSalesRow>()
        var cQty = 0.0
        var cAmount = 0.0
        byInvoice.values.forEach { invoiceLines ->
            val first = invoiceLines.first()
            val iQty = invoiceLines.sumOf { it.quantity }
            val iAmount = invoiceLines.sumOf { it.amount }
            blocks += CustomerSalesRow.Invoice(
                invoiceNo = first.invoiceNo.ifBlank { "-" },
                invoiceDate = first.invoiceDate.ifBlank { "-" },
                memoNo = first.memoNo,
                quantity = iQty,
                amount = iAmount,
            )
            invoiceLines.forEachIndexed { i, line -> blocks += CustomerSalesRow.Item(i + 1, line) }
            cQty += iQty
            cAmount += iAmount
        }
        out += CustomerSalesRow.Customer(customerLines.first().customerName.trim().ifBlank { "-" }, cQty, cAmount)
        out += blocks
        totalQty += cQty
        totalAmount += cAmount
    }
    return CustomerSalesBuilt(out, totalQty, totalAmount)
}

private val ALL_BRANDS = SelectorOption("", "All Brands")
private val ALL_GROUPS = SelectorOption("", "All Groups")
private val ALL_CATEGORIES = SelectorOption("", "All Categories")

data class CustomerSalesUiState(
    val branch: BranchPick = BranchPick(),
    val customer: LedgerDropdownItem? = null,
    val brands: List<SelectorOption> = listOf(ALL_BRANDS),
    val brand: SelectorOption = ALL_BRANDS,
    val groups: List<SelectorOption> = listOf(ALL_GROUPS),
    val group: SelectorOption = ALL_GROUPS,
    val categories: List<SelectorOption> = listOf(ALL_CATEGORIES),
    val category: SelectorOption = ALL_CATEGORIES,
    val product: SelectorOption? = null,
    val startDate: SimpleDate? = null,
    val endDate: SimpleDate? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val built: CustomerSalesBuilt = CustomerSalesBuilt(),
    val hasRun: Boolean = false,
    /** Bumped on Reset so the two search boxes clear their typed text. */
    val resetKey: Int = 0,
    val sessionExpired: Boolean = false,
)

class CustomerSalesViewModel(
    private val api: WebApi,
    private val reportRepository: ReportRepository,
    private val ledgerRepository: LedgerRepository,
    private val selectorRepository: SelectorRepository,
    private val ownBranchId: Long?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomerSalesUiState())
    val uiState: StateFlow<CustomerSalesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            when (val result = reportRepository.loadBranchPick(ownBranchId)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            branch = result.data,
                            startDate = result.data.trxDate ?: SimpleDate.today(),
                            endDate = result.data.trxDate ?: SimpleDate.today(),
                        )
                    }
                    // Opens on the branch's own transaction day rather than on
                    // an empty sheet, once.
                    apply()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(branch = it.branch.copy(loading = false), error = result.message, sessionExpired = result.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
        loadOptions(ReportSelectorSource.BRAND) { list -> _uiState.update { it.copy(brands = listOf(ALL_BRANDS) + list) } }
        loadOptions(ReportSelectorSource.PRODUCT_GROUP) { list -> _uiState.update { it.copy(groups = listOf(ALL_GROUPS) + list) } }
        loadOptions(ReportSelectorSource.CATEGORY) { list -> _uiState.update { it.copy(categories = listOf(ALL_CATEGORIES) + list) } }
    }

    private fun loadOptions(source: ReportSelectorSource, onLoaded: (List<SelectorOption>) -> Unit) {
        viewModelScope.launch {
            (selectorRepository.fetch(source) as? Resource.Success)?.let { onLoaded(it.data) }
        }
    }

    fun onBranch(id: String) = _uiState.update { it.copy(branch = it.branch.pick(id)) }
    fun onCustomer(item: LedgerDropdownItem?) = _uiState.update { it.copy(customer = item) }
    fun onBrand(o: SelectorOption) = _uiState.update { it.copy(brand = o) }
    fun onGroup(o: SelectorOption) = _uiState.update { it.copy(group = o) }
    fun onCategory(o: SelectorOption) = _uiState.update { it.copy(category = o) }
    fun onProduct(o: SelectorOption) = _uiState.update { it.copy(product = o) }
    fun onStart(d: SimpleDate) = _uiState.update { it.copy(startDate = d) }
    fun onEnd(d: SimpleDate) = _uiState.update { it.copy(endDate = d) }

    suspend fun searchCustomers(q: String) = ledgerRepository.searchLedgers(q, acType = "")
    suspend fun searchProducts(q: String) = selectorRepository.fetch(ReportSelectorSource.PRODUCT, q)

    /** The web's Reset: every filter back, the dates to the business day, the sheet cleared. */
    fun reset() = _uiState.update {
        it.copy(
            customer = null, brand = ALL_BRANDS, group = ALL_GROUPS, category = ALL_CATEGORIES,
            product = null, startDate = it.branch.trxDate ?: it.startDate,
            endDate = it.branch.trxDate ?: it.endDate, built = CustomerSalesBuilt(),
            hasRun = false, error = null, resetKey = it.resetKey + 1,
        )
    }

    fun apply() {
        val s = _uiState.value
        val branchId = s.branch.selectedId ?: return
        val start = s.startDate ?: return
        val end = s.endDate ?: return
        if (s.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            // Every filter travels, empty when unset — the server drops it.
            val params = mapOf(
                "branch_id" to branchId.toString(),
                "customer_id" to (s.customer?.id?.toString() ?: ""),
                "brand_id" to s.brand.id,
                "group_id" to s.group.id,
                "category_id" to s.category.id,
                "product_id" to (s.product?.id ?: ""),
                "startdate" to start.toApi(),
                "enddate" to end.toApi(),
            )
            when (val result = api.get("reports/customer-sales", params)) {
                is Resource.Success -> {
                    val lines = result.data.jRows().map { o ->
                        CustomerSalesLine(
                            mtmId = o.jStr("mtm_id"),
                            customerId = o.jStr("customer_id"),
                            customerName = o.jStr("customer_name"),
                            invoiceNo = o.jStr("invoice_no"),
                            invoiceDate = o.jStr("invoice_date"),
                            memoNo = o.jStr("manual_voucher_no"),
                            brand = o.jStr("brand_name"),
                            group = o.jStr("group_name"),
                            category = o.jStr("category_name"),
                            productName = o.jStr("product_name"),
                            productCode = o.jStr("product_code"),
                            productId = o.jStr("product_id"),
                            quantity = o.jNum("quantity"),
                            rate = o.jNum("rate"),
                            amount = o.jNum("amount"),
                        )
                    }
                    _uiState.update { it.copy(isLoading = false, hasRun = true, built = buildCustomerSales(lines)) }
                }
                // A failed read must not leave the previous filter's lines on
                // screen under a new heading.
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false, hasRun = true, built = CustomerSalesBuilt(),
                        error = result.message, sessionExpired = result.isUnauthorized,
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
                val app = context.applicationContext
                CustomerSalesViewModel(
                    api = ServiceLocator.provideWebApi(app),
                    reportRepository = ServiceLocator.provideReportRepository(app),
                    ledgerRepository = ServiceLocator.provideLedgerRepository(app),
                    selectorRepository = ServiceLocator.provideSelectorRepository(app),
                    ownBranchId = ServiceLocator.provideSessionManager(app).state.value.settings?.branchId,
                )
            }
        }
    }
}

@Composable
fun CustomerSalesScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    viewModel: CustomerSalesViewModel = viewModel(factory = CustomerSalesViewModel.provideFactory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)

    AuthenticatedShell(
        title = "Customer Sales Report",
        currentRoute = Routes.REPORTS,
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
                BranchField(state.branch, viewModel::onBranch, label = "Branch")
                androidx.compose.runtime.key(state.resetKey) {
                    SearchableLedgerDropdown(
                        selectedLedger = state.customer,
                        onLedgerSelected = viewModel::onCustomer,
                        searchLedgers = viewModel::searchCustomers,
                        label = "Customer",
                        placeholder = "All Customers",
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppSelectDropdown("Brand", state.brands, state.brand, viewModel::onBrand, Modifier.weight(1f))
                    AppSelectDropdown("Group", state.groups, state.group, viewModel::onGroup, Modifier.weight(1f))
                }
                AppSelectDropdown("Category", state.categories, state.category, viewModel::onCategory)
                androidx.compose.runtime.key(state.resetKey) {
                    SearchableSelectDropdown(
                        selected = state.product,
                        onSelected = viewModel::onProduct,
                        search = viewModel::searchProducts,
                        label = "Product",
                        placeholder = "All Products",
                        emptyText = "No product found",
                    )
                }
                DateRangeFields(state.startDate, state.endDate, viewModel::onStart, viewModel::onEnd, "From Date", "To Date")
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
                state.hasRun -> CustomerSalesTable(state.built)
                else -> ReportNote("Choose your filters, then tap Apply.")
            }
        }
    }
}

@Composable
private fun CustomerSalesTable(built: CustomerSalesBuilt) {
    val columns = listOf(
        ReportColumn<CustomerSalesRow>("SL", ReportColWidth.Fixed(48.dp), TextAlign.Center) { r, _ ->
            cellText((r as? CustomerSalesRow.Item)?.sl?.toString().orEmpty())
        },
        ReportColumn<CustomerSalesRow>("BRAND", ReportColWidth.Fixed(110.dp)) { r, _ ->
            cellText((r as? CustomerSalesRow.Item)?.line?.brand?.ifBlank { "-" }.orEmpty(), maxLines = 2)
        },
        ReportColumn<CustomerSalesRow>("GROUP", ReportColWidth.Fixed(110.dp)) { r, _ ->
            cellText((r as? CustomerSalesRow.Item)?.line?.group?.ifBlank { "-" }.orEmpty(), maxLines = 2)
        },
        ReportColumn<CustomerSalesRow>("CATEGORY", ReportColWidth.Fixed(110.dp)) { r, _ ->
            cellText((r as? CustomerSalesRow.Item)?.line?.category?.ifBlank { "-" }.orEmpty(), maxLines = 2)
        },
        ReportColumn<CustomerSalesRow>("PRODUCT", ReportColWidth.Fixed(190.dp)) { r, _ ->
            cellText((r as? CustomerSalesRow.Item)?.line?.productLabel.orEmpty(), maxLines = 3)
        },
        ReportColumn<CustomerSalesRow>("QTY", ReportColWidth.Fixed(80.dp), TextAlign.End) { r, _ ->
            cellText((r as? CustomerSalesRow.Item)?.line?.quantity?.let(::kitMoney).orEmpty())
        },
        ReportColumn<CustomerSalesRow>("RATE", ReportColWidth.Fixed(80.dp), TextAlign.End) { r, _ ->
            cellText((r as? CustomerSalesRow.Item)?.line?.rate?.let(::kitMoney).orEmpty())
        },
        ReportColumn<CustomerSalesRow>("AMOUNT", ReportColWidth.Fixed(100.dp), TextAlign.End) { r, _ ->
            cellText((r as? CustomerSalesRow.Item)?.line?.amount?.let(::kitMoney).orEmpty())
        },
    )
    val customerBg = MaterialTheme.colorScheme.secondaryContainer
    val invoiceBg = MaterialTheme.colorScheme.surfaceVariant
    ReportTable(
        columns = columns,
        data = built.rows,
        rowBackground = { r, _ ->
            when (r) {
                is CustomerSalesRow.Customer -> customerBg
                is CustomerSalesRow.Invoice -> invoiceBg
                else -> null
            }
        },
        bandRow = { r, _ ->
            when (r) {
                is CustomerSalesRow.Customer -> {
                    { BandLine(left = r.name, right = "Qty: ${kitMoney(r.quantity)} | Amount: ${kitMoney(r.amount)}", bold = true) }
                }
                is CustomerSalesRow.Invoice -> {
                    {
                        val left = buildString {
                            append("Invoice: ").append(r.invoiceNo)
                            if (r.invoiceDate != "-") append("   Date: ").append(r.invoiceDate)
                            if (r.memoNo.isNotBlank()) append("   Memo No.: ").append(r.memoNo)
                        }
                        BandLine(left = left, right = "Qty: ${kitMoney(r.quantity)} | Amount: ${kitMoney(r.amount)}", bold = false)
                    }
                }
                else -> null
            }
        },
        footerRows = if (built.rows.isEmpty()) emptyList() else listOf(
            listOf(
                ReportFooterCell(cellText("Grand Total", align = TextAlign.End, bold = true), colSpan = 5),
                ReportFooterCell(cellText(kitMoney(built.totalQuantity), bold = true)),
                ReportFooterCell(ReportTableCell.Empty),
                ReportFooterCell(cellText(kitMoney(built.totalAmount), bold = true)),
            ),
        ),
    )
}

/** A heading band: the name on the left, its subtotal on the right. */
@Composable
internal fun BandLine(left: String, right: String, bold: Boolean) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = left,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (bold) AppFontWeight.Bold else AppFontWeight.SemiBold,
            )
            Text(
                text = right,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = AppFontWeight.SemiBold,
            )
        }
    }
}
