package com.example.cashbookbd.ui.invoice

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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.core.jAsArr
import com.example.cashbookbd.core.jAsObj
import com.example.cashbookbd.core.jNum
import com.example.cashbookbd.core.jObjects
import com.example.cashbookbd.core.jPayload
import com.example.cashbookbd.core.jStr
import com.example.cashbookbd.data.remote.WebApi
import com.example.cashbookbd.data.repository.LedgerRepository
import com.example.cashbookbd.data.repository.StockShortageWarning
import com.example.cashbookbd.data.repository.TransactionRepository
import com.example.cashbookbd.data.repository.parseStockShortage
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SearchableLedgerDropdown
import com.example.cashbookbd.ui.components.SearchableSelectDropdown
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.components.StockShortageDialog
import com.example.cashbookbd.ui.reports.DateField
import com.example.cashbookbd.ui.reports.LogoutOnExpiry
import com.example.cashbookbd.ui.reports.SnackOnMessage
import com.example.cashbookbd.ui.reports.kitMoney
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.ScreenGutter
import com.example.cashbookbd.ui.theme.appColors
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The cash head: a cash sale is settled in full and carries no balance. */
private const val CASH_HEAD = "17"

/** Tiles and Sanitary's own Sales and Purchase invoices (web 6db6ffe6 / 2c8b7254). */
enum class TilesInvoiceKind(
    val title: String,
    val endpoint: String,
    val partyLabel: String,
    val amountLabel: String,
    /** A sale or a purchase return: the party ends up owing more. */
    val partyOwes: Boolean,
    /** Lines are filled with the sales price (else the cost). */
    val salesPriced: Boolean,
    val isReturn: Boolean = false,
    /** A return names the invoice it reverses ("sales"/"purchase"). */
    val returnPrefix: String = "",
) {
    // A tiles sale posts through the Electronics store; the purchase through Trading's.
    SALES("Sales Invoice", "electronics/sales/store", "Select Customer", "Received Amount", partyOwes = true, salesPriced = true),
    PURCHASE("Purchase Invoice", "trading/purchase/api-store", "Select Supplier", "Payment Amount", partyOwes = false, salesPriced = false),
    // Web bbe848f7: the two returns of the tiles shop, with the four manual figures.
    SALES_RETURN("Sales Return", "sales-return/api-store", "Select Customer", "Payment Amount", partyOwes = false, salesPriced = true, isReturn = true, returnPrefix = "sales"),
    PURCHASE_RETURN("Purchase Return", "purchase-return/api-store", "Select Supplier", "Received Amount", partyOwes = true, salesPriced = false, isReturn = true, returnPrefix = "purchase"),
}

/** One product as the dropdown carries it (label_3 cost, label_4 sale, label_5 unit). */
data class TilesProduct(
    val id: String,
    val name: String,
    val code: String,
    val unit: String,
    val purchasePrice: Double,
    val salesPrice: Double,
    val measurement: String,
    val measurementUnit: String,
    val packageSize: String,
    val packageUnit: String,
)

data class TilesLine(
    val product: TilesProduct,
    val qty: Double,
    val price: Double,
    val warehouseId: String,
    val warehouseName: String,
) {
    /** "CODE - NAME" when the product has a code, the bare name when not — the paper's shape. */
    val label: String get() = if (product.code.isNotBlank()) "${product.code} - ${product.name}" else product.name
}

data class TilesInvoiceUiState(
    val kind: TilesInvoiceKind = TilesInvoiceKind.SALES,
    val branch: CurrentBranchInfo? = null,
    val party: LedgerDropdownItem? = null,
    val previousBalance: Double = 0.0,
    val amount: String = "",
    val amountManual: Boolean = false,
    val discount: String = "",
    val notes: String = "",
    val serviceCharge: String = "",
    val tdsAmount: String = "",
    val transportationAmt: String = "",
    val serviceLabels: Map<Int, String> = emptyMap(),
    val manualVoucherNo: String = "",
    val manualVoucherDate: SimpleDate? = null,
    val manualChallanNo: String = "",
    val manualChallanDate: SimpleDate? = null,
    /** "1" this invoice takes the stock; "0" a delivery will. */
    val deductsStock: String = "1",
    val referrerId: String = "",
    /** A return's original invoice number and date. */
    val invoiceNo: String = "",
    val invoiceDate: SimpleDate? = null,
    val trackedProducts: List<SelectorOption> = emptyList(),
    val trackedProduct: SelectorOption? = null,
    val warehouses: List<TilesWarehouse> = emptyList(),
    // The line being written.
    val product: TilesProduct? = null,
    val qty: String = "",
    val price: String = "",
    val warehouse: TilesWarehouse? = null,
    val editIndex: Int? = null,
    val lines: List<TilesLine> = emptyList(),
    val isSaving: Boolean = false,
    val shortage: StockShortageWarning? = null,
    val message: String? = null,
    val sessionExpired: Boolean = false,
) {
    val isSales: Boolean get() = kind == TilesInvoiceKind.SALES
    val isCash: Boolean get() = party?.id?.toString() == CASH_HEAD

    /** A tiles branch without "Have Warehouse" keeps one godown, picked for it. */
    val singleWarehouse: Boolean get() = branch?.isTilesAndSanitary == true && branch.haveWarehouse.not()

    val linesTotal: Double get() = lines.sumOf { it.qty * it.price }

    private fun n(s: String) = s.toDoubleOrNull() ?: 0.0

    /** Sales: the lines plus service charge, TDS and transport; purchase: the lines alone. */
    val billAmount: Double
        get() = linesTotal + if (isSales) n(serviceCharge) + n(tdsAmount) + n(transportationAmt) else 0.0

    /** What a cash party settles: the bill net of discount, never below nought. */
    val payable: String get() = Math.max(0.0, billAmount - n(discount)).let { Math.round(it).toString() }

    /**
     * Sales, Purchase Return:  Total Tk. = Previous Balance + Current Invoice − Discount − Amount
     * Purchase, Sales Return:  Total Tk. = Previous Balance − Current Invoice + Discount + Amount
     * (the party's ledger term by term, debit − credit; a supplier we owe stands negative).
     */
    val totalTk: Double
        get() = if (kind.partyOwes) previousBalance + billAmount - n(discount) - n(amount)
        else previousBalance - billAmount + n(discount) + n(amount)
}

class TilesInvoiceViewModel(
    private val kind: TilesInvoiceKind,
    private val api: WebApi,
    private val ledgers: LedgerRepository,
    private val transactions: TransactionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TilesInvoiceUiState(kind = kind))
    val uiState: StateFlow<TilesInvoiceUiState> = _uiState.asStateFlow()
    private var productCache: Map<String, TilesProduct> = emptyMap()

    init {
        viewModelScope.launch {
            val branch = CurrentBranchCache.load(api)
            val warehouses = loadTilesWarehouses(api)
            _uiState.update { s ->
                val mine = warehouses.filter { it.branchId == branch?.id }
                val default = mine.firstOrNull { it.isDefault } ?: mine.firstOrNull()
                s.copy(branch = branch, warehouses = warehouses, warehouse = if (s.copy(branch = branch).singleWarehouse) default else s.warehouse)
            }
        }
        if (kind == TilesInvoiceKind.SALES) {
            viewModelScope.launch {
                // The three extra-charge heads' own names (coa4 42 / 41 / 198).
                val body = (api.post("settings/service-list", JsonObject()) as? Resource.Success)?.data
                val list = body?.get("data").jAsArr() ?: body?.get("data").jAsObj()?.get("data").jAsArr()
                val labels = list?.jObjects().orEmpty().mapNotNull { o -> o.jStr("id").toIntOrNull()?.let { it to o.jStr("name") } }.toMap()
                _uiState.update { it.copy(serviceLabels = labels) }
            }
        }
    }

    suspend fun searchParties(q: String) = ledgers.searchLedgers(q, acType = "3")

    suspend fun searchProducts(q: String): Resource<List<SelectorOption>> {
        if (q.isBlank()) return Resource.Success(emptyList())
        return when (val r = api.get("product/ddl/list", mapOf("q" to q.trim()), emptyOnNotFound = true)) {
            is Resource.Success -> {
                val body = r.data
                val arr = body.get("data").jAsArr() ?: body.get("data").jAsObj()?.get("data").jAsArr()
                val products = arr?.jObjects().orEmpty().map { o ->
                    TilesProduct(
                        id = o.jStr("value"), name = o.jStr("label"), code = o.jStr("code"), unit = o.jStr("label_5"),
                        purchasePrice = o.jNum("label_3"), salesPrice = o.jNum("label_4"),
                        measurement = o.jStr("measurement"), measurementUnit = o.jStr("measurement_unit"),
                        packageSize = o.jStr("package_size"), packageUnit = o.jStr("package_unit"),
                    )
                }
                productCache = products.associateBy { it.id }
                Resource.Success(products.map { SelectorOption(it.id, it.name, it.unit) })
            }
            is Resource.Error -> r
            Resource.Loading -> Resource.Loading
        }
    }

    fun onParty(item: LedgerDropdownItem?) {
        _uiState.update {
            it.copy(
                party = item, previousBalance = 0.0, amountManual = false,
                // Cleared for anyone but cash: a credit bill has taken nothing yet.
                amount = "", trackedProduct = null,
            )
        }
        syncCashAmount()
        val id = item?.id?.toString() ?: return
        if (id != CASH_HEAD) {
            viewModelScope.launch {
                // The party's own balance WITH its sign (debit − credit).
                val r = api.get("trading/cash/received/previous-balance", mapOf("account" to id, "exclude_mtm_id" to "", "allow_negative" to "1"))
                val bal = (r as? Resource.Success)?.data?.jPayload()?.jNum("balance") ?: 0.0
                _uiState.update { s -> if (s.party?.id?.toString() == id) s.copy(previousBalance = bal) else s }
            }
            if (kind == TilesInvoiceKind.PURCHASE) {
                viewModelScope.launch {
                    val products = transactions.fetchTrackedProducts("purchase", id).map { (pid, name) -> SelectorOption(pid, name) }
                    _uiState.update { it.copy(trackedProducts = products) }
                }
            }
        }
    }

    /** A cash party's amount is forced onto the payable and the box is shut. */
    private fun syncCashAmount() = _uiState.update { s -> if (s.isCash) s.copy(amount = s.payable) else s }

    fun onAmount(v: String) = _uiState.update { if (it.isCash) it else it.copy(amount = v.decimalOnly(), amountManual = true) }
    fun onDiscount(v: String) { _uiState.update { it.copy(discount = v.decimalOnly()) }; syncCashAmount() }
    fun onNotes(v: String) = _uiState.update { it.copy(notes = v) }
    fun onServiceCharge(v: String) { _uiState.update { it.copy(serviceCharge = v.decimalOnly()) }; syncCashAmount() }
    fun onTds(v: String) { _uiState.update { it.copy(tdsAmount = v.decimalOnly()) }; syncCashAmount() }
    fun onTransport(v: String) { _uiState.update { it.copy(transportationAmt = v.decimalOnly()) }; syncCashAmount() }
    fun onManualVoucherNo(v: String) = _uiState.update { it.copy(manualVoucherNo = v) }
    fun onManualVoucherDate(d: SimpleDate) = _uiState.update { it.copy(manualVoucherDate = d) }
    fun onManualChallanNo(v: String) = _uiState.update { it.copy(manualChallanNo = v) }
    fun onManualChallanDate(d: SimpleDate) = _uiState.update { it.copy(manualChallanDate = d) }
    fun onDeductOnDelivery(on: Boolean) = _uiState.update { it.copy(deductsStock = if (on) "0" else "1") }
    fun onReferrer(id: String) = _uiState.update { it.copy(referrerId = id) }
    fun onInvoiceNo(v: String) = _uiState.update { it.copy(invoiceNo = v) }
    fun onInvoiceDate(d: SimpleDate) = _uiState.update { it.copy(invoiceDate = d) }
    fun onTracked(o: SelectorOption) = _uiState.update { it.copy(trackedProduct = o.takeIf { t -> t.id.isNotBlank() }) }

    fun onProduct(option: SelectorOption) {
        val p = productCache[option.id] ?: return
        // A sale (and its return) fills the sales price; a purchase the cost.
        // A product with no price yet leaves the box empty, not 0.
        val price = if (kind.salesPriced) p.salesPrice else p.purchasePrice
        _uiState.update { it.copy(product = p, price = if (price > 0) plain(price) else "") }
    }

    fun onQty(v: String) = _uiState.update { it.copy(qty = v.decimalOnly()) }
    fun onPrice(v: String) = _uiState.update { it.copy(price = v.decimalOnly()) }
    fun onWarehouse(o: SelectorOption) = _uiState.update { s -> s.copy(warehouse = s.warehouses.firstOrNull { it.id == o.id }) }

    /**
     * One line per product (a second line of it is a slip at the counter), and
     * on a sale nothing goes out under what it cost.
     */
    fun addOrUpdateLine() {
        val s = _uiState.value
        val p = s.product
        val qty = s.qty.toDoubleOrNull() ?: 0.0
        val price = s.price.toDoubleOrNull() ?: 0.0
        val problem = when {
            p == null -> "Please select a product."
            qty <= 0 -> "Please enter a valid quantity."
            price <= 0 -> "Please enter a valid price."
            s.lines.withIndex().any { (i, l) -> i != s.editIndex && l.product.id == p.id } -> "This product is already added."
            s.isSales && p.purchasePrice > 0 && price < p.purchasePrice ->
                "Sales price cannot be below the purchase price (${plain(p.purchasePrice)})."
            else -> null
        }
        if (problem != null || p == null) {
            _uiState.update { it.copy(message = problem) }
            return
        }
        val line = TilesLine(p, qty, price, s.warehouse?.id.orEmpty(), s.warehouse?.name.orEmpty())
        _uiState.update {
            it.copy(
                lines = if (it.editIndex != null) it.lines.mapIndexed { i, l -> if (i == it.editIndex) line else l } else it.lines + line,
                product = null, qty = "", price = "", editIndex = null,
            )
        }
        syncCashAmount()
    }

    fun editLine(index: Int) = _uiState.update { s ->
        val l = s.lines.getOrNull(index) ?: return@update s
        s.copy(product = l.product, qty = plain(l.qty), price = plain(l.price), editIndex = index,
            warehouse = s.warehouses.firstOrNull { it.id == l.warehouseId } ?: s.warehouse)
    }

    fun removeLine(index: Int) {
        _uiState.update { it.copy(lines = it.lines.filterIndexed { i, _ -> i != index }, editIndex = null) }
        syncCashAmount()
    }

    fun reset() = _uiState.update {
        TilesInvoiceUiState(
            kind = kind, branch = it.branch, warehouses = it.warehouses, serviceLabels = it.serviceLabels,
            // One store all day: the warehouse stays between bills.
            warehouse = it.warehouse,
        )
    }

    fun save(allowNegative: Boolean = false) {
        val s = _uiState.value
        val party = s.party
        val problem = when {
            party == null -> "Please select an account."
            (s.discount.toDoubleOrNull() ?: 0.0) < 0 -> "Discount amount must be 0 or greater."
            s.lines.isEmpty() -> if (s.isSales) "Please add some products." else "Please add products information!"
            else -> null
        }
        if (problem != null || party == null) {
            _uiState.update { it.copy(message = problem) }
            return
        }
        _uiState.update { it.copy(isSaving = true, shortage = null) }
        viewModelScope.launch {
            when (val r = api.postEnvelope(kind.endpoint, buildPayload(s, party, allowNegative))) {
                is Resource.Success -> {
                    val body = r.data
                    val shortage = parseStockShortage(body)
                    when {
                        shortage != null -> _uiState.update { it.copy(isSaving = false, shortage = shortage) }
                        body.get("success")?.let { it.isJsonPrimitive && !it.asBoolean } == true ->
                            _uiState.update { it.copy(isSaving = false, message = WebApi.messageOf(body).ifBlank { "The invoice could not be saved." }) }
                        else -> {
                            val vr = body.jPayload().jStr("vr_no")
                            reset()
                            _uiState.update {
                                it.copy(isSaving = false, message = if (vr.isNotBlank()) "Voucher No.: $vr" else body.jStr("message").ifBlank { "Invoice saved." })
                            }
                        }
                    }
                }
                is Resource.Error -> _uiState.update { it.copy(isSaving = false, message = r.message, sessionExpired = r.isUnauthorized) }
                Resource.Loading -> Unit
            }
        }
    }

    private fun buildPayload(s: TilesInvoiceUiState, party: LedgerDropdownItem, allowNegative: Boolean): JsonObject =
        if (kind.isReturn) returnPayload(s, party) else invoicePayload(s, party, allowNegative)

    /** The return writers' body: table_data of {code, qty, price, godown}. */
    private fun returnPayload(s: TilesInvoiceUiState, party: LedgerDropdownItem) = JsonObject().apply {
        addProperty("supplier_id", party.id.toString())
        addProperty("${kind.returnPrefix}_invoice_number", s.invoiceNo)
        addProperty("${kind.returnPrefix}_invoice_date", s.invoiceDate?.toApi().orEmpty())
        addProperty("total", s.linesTotal)
        addProperty("discount", s.discount.toDoubleOrNull() ?: 0.0)
        // A whole return on credit is normal: an untouched box saves as 0.
        addProperty("netpayment", s.amount.ifBlank { "0" })
        addProperty("notes", s.notes)
        addProperty("manual_voucher_no", s.manualVoucherNo)
        addProperty("manual_voucher_date", s.manualVoucherDate?.toApi().orEmpty())
        addProperty("manual_challan_no", s.manualChallanNo)
        addProperty("manual_challan_date", s.manualChallanDate?.toApi().orEmpty())
        add(
            "table_data",
            JsonArray().apply {
                s.lines.forEach { l ->
                    add(
                        JsonObject().apply {
                            l.product.id.toLongOrNull()?.let { addProperty("code", it) } ?: addProperty("code", l.product.id)
                            addProperty("qty", l.qty)
                            addProperty("price", l.price)
                            addProperty("godown", l.warehouseId)
                        },
                    )
                }
            },
        )
    }

    private fun invoicePayload(s: TilesInvoiceUiState, party: LedgerDropdownItem, allowNegative: Boolean) = JsonObject().apply {
        addProperty("mtmId", "")
        addProperty("account", party.id.toString())
        addProperty("accountName", party.name)
        // An untouched amount saves as 0: a whole bill on credit is normal here.
        val amount = s.amount.ifBlank { "0" }
        if (s.isSales) {
            addProperty("receivedAmt", amount)
            addProperty("discountAmt", s.discount.toDoubleOrNull() ?: 0.0)
            addProperty("serviceCharge", s.serviceCharge.toDoubleOrNull() ?: 0.0)
            addProperty("tdsAmount", s.tdsAmount.toDoubleOrNull() ?: 0.0)
            addProperty("transportationAmt", s.transportationAmt.toDoubleOrNull() ?: 0.0)
            addProperty("invoice_deducts_stock", s.deductsStock)
            addProperty("referrer_id", s.referrerId)
        } else {
            addProperty("paymentAmt", amount)
            addProperty("discountAmt", s.discount.toDoubleOrNull() ?: 0.0)
            addProperty("purchaseOrderNumber", "")
            addProperty("purchaseOrderText", "")
            addProperty("invoice_no", "")
            addProperty("invoice_date", "")
            s.trackedProduct?.id?.toLongOrNull()?.let { addProperty("trackedProductId", it) } ?: add("trackedProductId", JsonNull.INSTANCE)
        }
        addProperty("notes", s.notes)
        addProperty("manual_voucher_no", s.manualVoucherNo)
        addProperty("manual_voucher_date", s.manualVoucherDate?.toApi().orEmpty())
        addProperty("manual_challan_no", s.manualChallanNo)
        addProperty("manual_challan_date", s.manualChallanDate?.toApi().orEmpty())
        if (allowNegative) addProperty("allow_negative", true)
        add(
            "products",
            JsonArray().apply {
                s.lines.forEachIndexed { i, l ->
                    add(
                        JsonObject().apply {
                            addProperty("id", System.currentTimeMillis() + i)
                            l.product.id.toLongOrNull()?.let { addProperty("product", it) } ?: addProperty("product", l.product.id)
                            addProperty("product_name", l.product.name)
                            addProperty("product_code", l.product.code)
                            addProperty("serial_no", "")
                            addProperty("unit", l.product.unit)
                            addProperty("qty", l.qty)
                            addProperty("price", l.price)
                            addProperty("warehouse", l.warehouseId)
                            if (s.isSales) {
                                l.product.measurement.takeIf { it.isNotBlank() }?.let { addProperty("measurement", it) }
                                l.product.measurementUnit.takeIf { it.isNotBlank() }?.let { addProperty("measurement_unit", it) }
                                l.product.packageSize.takeIf { it.isNotBlank() }?.let { addProperty("package_size", it) }
                                l.product.packageUnit.takeIf { it.isNotBlank() }?.let { addProperty("package_unit", it) }
                            }
                        },
                    )
                }
            },
        )
    }

    fun dismissShortage() = _uiState.update { it.copy(shortage = null) }
    fun confirmShortage() = save(allowNegative = true)
    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context, kind: TilesInvoiceKind) = viewModelFactory {
            initializer {
                val app = context.applicationContext
                TilesInvoiceViewModel(
                    kind, ServiceLocator.provideWebApi(app), ServiceLocator.provideLedgerRepository(app),
                    ServiceLocator.provideTransactionRepository(app),
                )
            }
        }
    }
}

private fun String.decimalOnly(): String = filterIndexed { i, c -> c.isDigit() || (c == '.' && !take(i).contains('.')) }
private fun plain(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

@Composable
fun TilesInvoiceScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    kind: TilesInvoiceKind,
    viewModel: TilesInvoiceViewModel = viewModel(
        key = "tiles-${kind.name}",
        factory = TilesInvoiceViewModel.provideFactory(LocalContext.current, kind),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showReferrer by remember { mutableStateOf(false) }
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)
    SnackOnMessage(state.message, snackbar, viewModel::onMessageShown)

    state.shortage?.let {
        StockShortageDialog(warning = it, action = "sell", onCancel = viewModel::dismissShortage, onContinue = viewModel::confirmShortage)
    }
    if (showReferrer) {
        ReferrerPickerDialog(state.referrerId, viewModel::onReferrer) { showReferrer = false }
    }

    AuthenticatedShell(title = kind.title, currentRoute = Routes.INVOICES, navController = navController, onLogout = onLogout) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ScreenGutter, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        SearchableLedgerDropdown(
                            selectedLedger = state.party,
                            onLedgerSelected = viewModel::onParty,
                            searchLedgers = viewModel::searchParties,
                            label = kind.partyLabel,
                        )
                    }
                    // Who recommended the sale: the switch alone, uncaptioned —
                    // the name lives in the popup. It always opens the popup.
                    if (state.isSales) {
                        Switch(checked = state.referrerId.isNotBlank(), onCheckedChange = { showReferrer = true }, modifier = Modifier.padding(start = 8.dp))
                    }
                }

                val branch = state.branch
                if (kind.isReturn) {
                    val which = if (kind.returnPrefix == "sales") "Sales" else "Purchase"
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppTextField(state.invoiceNo, viewModel::onInvoiceNo, label = "$which Invoice Number", modifier = Modifier.weight(1f))
                        DateField("$which Invoice Date", state.invoiceDate, viewModel::onInvoiceDate, Modifier.weight(1f))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextField(state.manualVoucherNo, viewModel::onManualVoucherNo, label = branch?.label("manual_voucher_no_label", "Memo No.") ?: "Memo No.", modifier = Modifier.weight(1f))
                    DateField(branch?.label("manual_voucher_date_label", "Memo Date") ?: "Memo Date", state.manualVoucherDate, viewModel::onManualVoucherDate, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextField(state.manualChallanNo, viewModel::onManualChallanNo, label = branch?.label("manual_challan_no_label", "Challan No") ?: "Challan No", modifier = Modifier.weight(1f))
                    DateField(branch?.label("manual_challan_date_label", "Challan Date") ?: "Challan Date", state.manualChallanDate, viewModel::onManualChallanDate, Modifier.weight(1f))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextField(
                        state.amount, viewModel::onAmount, label = kind.amountLabel, keyboardType = KeyboardType.Decimal,
                        enabled = !state.isCash, modifier = Modifier.weight(1f),
                    )
                    AppTextField(state.discount, viewModel::onDiscount, label = "Discount Amount", keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f))
                }
                AppTextField(state.notes, viewModel::onNotes, label = "Notes", modifier = Modifier.fillMaxWidth())

                if (state.isSales) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppTextField(state.serviceCharge, viewModel::onServiceCharge, label = state.serviceLabels[42] ?: "Service Charge", keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f))
                        AppTextField(state.tdsAmount, viewModel::onTds, label = state.serviceLabels[41] ?: "TDS", keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f))
                    }
                    AppTextField(state.transportationAmt, viewModel::onTransport, label = state.serviceLabels[198] ?: "Transportation", keyboardType = KeyboardType.Decimal, modifier = Modifier.fillMaxWidth())
                    // Offered only where the branch deducts stock on delivery.
                    if (branch?.deductStockOnDelivery == true || state.deductsStock == "0") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Deduct stock on delivery", fontWeight = AppFontWeight.SemiBold)
                                Text(
                                    "On: the stock leaves the godown when the delivery is posted. Off: this invoice takes the stock now, as it always has.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.appColors.textOnScreenMuted,
                                )
                            }
                            Switch(checked = state.deductsStock == "0", onCheckedChange = viewModel::onDeductOnDelivery)
                        }
                    }
                } else if (kind == TilesInvoiceKind.PURCHASE && !state.isCash && state.trackedProducts.isNotEmpty()) {
                    AppSelectDropdown(
                        "Product (optional)", listOf(SelectorOption("", "Not applicable")) + state.trackedProducts,
                        state.trackedProduct, viewModel::onTracked,
                    )
                }

                // ---- The line being written ----
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SearchableSelectDropdown(
                            selected = state.product?.let { SelectorOption(it.id, it.name, it.unit) },
                            onSelected = viewModel::onProduct,
                            search = viewModel::searchProducts,
                            label = "Select Product",
                            placeholder = "Search product",
                            emptyText = "No product found",
                        )
                        if (!state.singleWarehouse) {
                            val options = state.warehouses.map { SelectorOption(it.id, it.name) }
                            AppSelectDropdown("Select Warehouse", options, state.warehouse?.let { SelectorOption(it.id, it.name) }, viewModel::onWarehouse)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppTextField(state.qty, viewModel::onQty, label = "Quantity" + (state.product?.unit?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""), keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f))
                            AppTextField(state.price, viewModel::onPrice, label = if (state.isSales) "Enter Price" else "Price", keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f))
                        }
                        val lineTotal = (state.qty.toDoubleOrNull() ?: 0.0) * (state.price.toDoubleOrNull() ?: 0.0)
                        Text("Line total: ${kitMoney(lineTotal)}", style = MaterialTheme.typography.labelSmall)
                        SecondaryButton(text = if (state.editIndex != null) "Update" else "Add New", onClick = viewModel::addOrUpdateLine)
                    }
                }

                // ---- The lines ----
                state.lines.forEachIndexed { i, l ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${i + 1}. ${l.label}", fontWeight = AppFontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                if (l.product.measurement.isNotBlank()) {
                                    Text(
                                        listOf(l.product.measurement, l.product.measurementUnit).filter { it.isNotBlank() }.joinToString(" ") +
                                            (if (l.product.packageSize.isNotBlank()) " · ${l.product.packageSize} ${l.product.packageUnit}".trimEnd() else ""),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textOnScreenMuted,
                                    )
                                }
                                Text(
                                    "${plain(l.qty)} ${l.product.unit} × ${plain(l.price)} = ${kitMoney(l.qty * l.price)}" +
                                        (if (l.warehouseName.isNotBlank() && !state.singleWarehouse) " · ${l.warehouseName}" else ""),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            LinkButton(text = "Edit", onClick = { viewModel.editLine(i) })
                            LinkButton(text = "Delete", onClick = { viewModel.removeLine(i) }, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                if (state.lines.isNotEmpty()) {
                    // The foot sums the Quantity column beside the money (web 182ed65e).
                    Text(
                        text = "Total   Qty ${plain(state.lines.sumOf { it.qty })}",
                        fontWeight = AppFontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                // The purchase reads nought until a supplier is picked: a running
                // balance needs someone to run against.
                val has = state.isSales || state.party != null
                TilesTotalPanel(
                    terms = listOf(
                        "Previous Balance" to if (has) state.previousBalance else 0.0,
                        "Current Invoice" to if (has) state.billAmount else 0.0,
                        "Discount" to if (has) (state.discount.toDoubleOrNull() ?: 0.0) else 0.0,
                        kind.amountLabel to if (has) (state.amount.toDoubleOrNull() ?: 0.0) else 0.0,
                    ),
                    total = if (has) state.totalTk else 0.0,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(text = if (state.isSaving) "Saving..." else "Save", onClick = { viewModel.save() }, isLoading = state.isSaving, modifier = Modifier.weight(1f))
                    SecondaryButton(text = "Reset", onClick = viewModel::reset, modifier = Modifier.weight(1f))
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}
