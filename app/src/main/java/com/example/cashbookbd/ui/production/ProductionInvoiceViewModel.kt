package com.example.cashbookbd.ui.production

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.LedgerRepository
import com.example.cashbookbd.data.repository.ProductionRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.production.model.ProductionInvoiceLine
import com.example.cashbookbd.ui.production.model.ProductionInvoiceMode
import com.example.cashbookbd.ui.production.model.ProductionShortage
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InvoiceLine(
    val localId: Long,
    val productId: String,
    val productName: String,
    val unit: String,
    val qty: String,
    val price: String,
    val free: Boolean = false,
    val schemeId: Long? = null,
)

data class ProductionInvoiceUiState(
    val mode: ProductionInvoiceMode,
    val partyId: String = "",
    val partyName: String = "",
    val billNo: String = "",
    val billDate: String = "",
    val notes: String = "",
    val discount: String = "",
    val payment: String = "",
    val lines: List<InvoiceLine> = emptyList(),

    val draft: EntryProduct? = null,
    val draftQty: String = "",
    val draftPrice: String = "",
    val draftFree: Boolean = false,
    val editingLineId: Long? = null,

    val searchNo: String = "",
    val isSearching: Boolean = false,
    val editingMtmId: String? = null,
    val editingVrNo: String = "",
    val isSaving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val sessionExpired: Boolean = false,
    val canWrite: Boolean = false,
    val shortage: ProductionShortage? = null,
) {
    val total: Double
        get() = lines.sumOf { (it.qty.toDoubleOrNull() ?: 0.0) * (it.price.toDoubleOrNull() ?: 0.0) }

    val net: Double get() = (total - (discount.toDoubleOrNull() ?: 0.0)).coerceAtLeast(0.0)

    /** A cash party (account 17) is always paid in full and the box is locked. */
    val isCashParty: Boolean get() = partyId == "17"

    val effectivePayment: String
        get() = if (isCashParty) plainAmount(net) else payment

    val partyLabel: String
        get() = when (mode) {
            ProductionInvoiceMode.PURCHASE -> "Supplier"
            else -> "Customer"
        }

    val paymentLabel: String
        get() = when (mode) {
            ProductionInvoiceMode.PURCHASE -> "Paid (net ${plainAmount(net)})"
            ProductionInvoiceMode.SALE -> "Received (net ${plainAmount(net)})"
            ProductionInvoiceMode.SALES_RETURN -> "Cash Payment (refund ${plainAmount(net)})"
        }

    val saveLabel: String get() = if (editingMtmId != null) "Update" else "Save"

    val isReturn: Boolean get() = mode == ProductionInvoiceMode.SALES_RETURN
}

class ProductionInvoiceViewModel(
    private val mode: ProductionInvoiceMode,
    private val repository: ProductionRepository,
    private val ledgerRepository: LedgerRepository,
    transactionDate: String?,
    canWrite: Boolean,
) : ViewModel() {

    private val today = SimpleDate.fromDisplay(transactionDate) ?: SimpleDate.today()

    private val _uiState = MutableStateFlow(
        ProductionInvoiceUiState(
            mode = mode,
            billDate = today.toApi(),
            canWrite = canWrite,
        )
    )
    val uiState: StateFlow<ProductionInvoiceUiState> = _uiState.asStateFlow()

    suspend fun searchParties(query: String): Resource<List<LedgerDropdownItem>> =
        ledgerRepository.searchLedgers(query, acType = "3")

    fun onParty(item: LedgerDropdownItem) = _uiState.update {
        it.copy(partyId = item.id.toString(), partyName = item.name, payment = "0")
    }

    fun onBillNo(v: String) = _uiState.update { it.copy(billNo = v.take(255)) }
    fun onBillDate(date: SimpleDate) = _uiState.update { it.copy(billDate = date.toApi()) }
    fun onNotes(v: String) = _uiState.update { it.copy(notes = v.take(500)) }
    fun onDiscount(v: String) = _uiState.update { it.copy(discount = num(v)) }
    fun onPayment(v: String) = _uiState.update { it.copy(payment = num(v)) }
    fun onSearchNo(v: String) = _uiState.update { it.copy(searchNo = v) }

    fun onDraftProduct(product: EntryProduct?) = _uiState.update { state ->
        val price = if (mode == ProductionInvoiceMode.PURCHASE) {
            product?.price
        } else {
            product?.price
        }
        state.copy(
            draft = product,
            // A free line stays at 0 however the price arrives.
            draftPrice = if (state.draftFree) "0" else price?.let { plain(it) } ?: "",
        )
    }

    fun onDraftQty(v: String) = _uiState.update { it.copy(draftQty = num(v)) }
    fun onDraftPrice(v: String) = _uiState.update { it.copy(draftPrice = num(v)) }
    fun onDraftFree(on: Boolean) = _uiState.update {
        it.copy(draftFree = on, draftPrice = if (on) "0" else it.draftPrice)
    }

    fun addLine() {
        val state = _uiState.value
        val product = state.draft
        if (product == null) return fail("Pick a product")
        if ((state.draftQty.toDoubleOrNull() ?: 0.0) <= 0) return fail("Enter a quantity")
        if (!state.isReturn && !state.draftFree && (state.draftPrice.toDoubleOrNull() ?: 0.0) <= 0) {
            return fail("Enter a price, or tick Free")
        }
        if (state.isReturn && (state.draftPrice.toDoubleOrNull() ?: -1.0) < 0) {
            return fail("Enter the refund price")
        }

        val line = InvoiceLine(
            localId = state.editingLineId ?: System.nanoTime(),
            productId = product.id,
            productName = product.label,
            unit = product.unit,
            qty = state.draftQty,
            price = if (state.draftFree) "0" else state.draftPrice,
            free = state.draftFree,
            schemeId = state.lines.firstOrNull { it.localId == state.editingLineId }?.schemeId,
        )

        _uiState.update {
            it.copy(
                lines = if (it.editingLineId != null) {
                    it.lines.map { row -> if (row.localId == it.editingLineId) line else row }
                } else {
                    it.lines + line
                },
                draft = null,
                draftQty = "",
                draftPrice = "",
                draftFree = false,
                editingLineId = null,
                error = null,
            )
        }

        // A sale earns the scheme's free goods on its own; a purchase simply has
        // them ticked in by hand and a return has none.
        if (mode == ProductionInvoiceMode.SALE && !line.free) {
            matchScheme(line)
        }
    }

    private fun matchScheme(line: InvoiceLine) {
        viewModelScope.launch {
            when (val result = repository.matchScheme(
                productId = line.productId,
                qty = line.qty,
                date = _uiState.value.billDate,
            )) {
                is Resource.Success -> {
                    val match = result.data
                    if (match.schemeId == null || match.freeQty <= 0) return@launch
                    val freeProduct = match.freeProductId ?: line.productId
                    val same = freeProduct == line.productId
                    _uiState.update { state ->
                        state.copy(
                            lines = state.lines + InvoiceLine(
                                localId = System.nanoTime(),
                                productId = freeProduct,
                                productName = if (same) line.productName else "Product #$freeProduct",
                                unit = if (same) line.unit else "",
                                qty = plain(match.freeQty),
                                price = "0",
                                free = true,
                                schemeId = match.schemeId,
                            ),
                            message = "${match.name}: ${plain(match.freeQty)} free added",
                        )
                    }
                }
                is Resource.Error -> Unit
                Resource.Loading -> Unit
            }
        }
    }

    fun editLine(id: Long) = _uiState.update { state ->
        val row = state.lines.firstOrNull { it.localId == id } ?: return@update state
        state.copy(
            draft = EntryProduct(row.productId, row.productName, row.unit, row.price.toDoubleOrNull()),
            draftQty = row.qty,
            draftPrice = row.price,
            draftFree = row.free,
            editingLineId = id,
        )
    }

    fun removeLine(id: Long) = _uiState.update {
        it.copy(lines = it.lines.filterNot { row -> row.localId == id })
    }

    fun cancelLineEdit() = _uiState.update {
        it.copy(draft = null, draftQty = "", draftPrice = "", draftFree = false, editingLineId = null)
    }

    fun save() {
        val state = _uiState.value
        if (!state.canWrite || state.isSaving) return
        val problem = when {
            state.partyId.isBlank() -> "Select the ${state.partyLabel.lowercase()}."
            state.lines.isEmpty() -> if (state.isReturn) "Add at least one returned item." else
                "Add at least one ${if (mode == ProductionInvoiceMode.PURCHASE) "purchase" else "sale"} item."
            state.isReturn && state.billNo.isBlank() ->
                "The original sale invoice is required — the cost is read from it."
            !state.isReturn && state.lines.none { !it.free } ->
                "A free line needs a paid line on the same invoice."
            (state.effectivePayment.toDoubleOrNull() ?: 0.0) < 0 -> "Enter the amount (0 if none)."
            (state.effectivePayment.toDoubleOrNull() ?: 0.0) > state.net ->
                "The amount cannot be more than the net."
            else -> null
        }
        if (problem != null) return fail(problem)

        send(state, allowNegative = false)
    }

    private fun send(state: ProductionInvoiceUiState, allowNegative: Boolean) {
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val payload = buildPayload(state).apply {
                if (allowNegative) addProperty("allow_negative", true)
            }
            when (val result = repository.saveInvoice(mode, payload, state.editingMtmId != null)) {
                is Resource.Success -> {
                    val body = result.data
                    val success = body.get("success")?.takeIf { it.isJsonPrimitive }?.asBoolean
                    val stockShortage = body.get("stock_shortage")?.takeIf { it.isJsonPrimitive }?.asBoolean
                    if (stockShortage == true) {
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                shortage = ProductionShortage(
                                    message = body.str("message").ifBlank { "Not enough stock." },
                                    blocked = body.get("stock_blocked")?.takeIf { p -> p.isJsonPrimitive }?.asBoolean == true,
                                ),
                            )
                        }
                        return@launch
                    }
                    if (success == false) {
                        _uiState.update { it.copy(isSaving = false, error = body.str("message").ifBlank { "Could not save" }) }
                        return@launch
                    }
                    val vrNo = body.dataData().str("vr_no")
                    val saved = when (mode) {
                        ProductionInvoiceMode.PURCHASE -> "Purchase saved"
                        ProductionInvoiceMode.SALE -> "Sale saved"
                        ProductionInvoiceMode.SALES_RETURN -> "Return saved"
                    }
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            message = body.str("message").ifBlank {
                                if (vrNo.isBlank()) saved else "$saved — $vrNo"
                            },
                        )
                    }
                    reset()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun continueAfterShortage() {
        val state = _uiState.value
        if (state.shortage?.blocked == true) return
        _uiState.update { it.copy(shortage = null) }
        send(state, allowNegative = true)
    }

    fun dismissShortage() = _uiState.update { it.copy(shortage = null) }

    fun find() {
        val no = _uiState.value.searchNo.trim()
        if (no.isBlank()) return fail("Enter the Vr. No.")
        _uiState.update { it.copy(isSearching = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.findInvoice(mode, no)) {
                is Resource.Success -> {
                    val found = result.data
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            partyId = found.account,
                            partyName = found.accountName,
                            billNo = found.invoiceNo,
                            billDate = found.invoiceDate.ifBlank { today.toApi() },
                            notes = found.notes,
                            discount = if (found.discount > 0) plain(found.discount) else "",
                            payment = plain(found.netPayment),
                            lines = found.lines.map { row ->
                                InvoiceLine(
                                    localId = if (row.id != 0L) row.id else System.nanoTime(),
                                    productId = row.productId,
                                    productName = row.productName,
                                    unit = row.unit,
                                    qty = plain(row.qty),
                                    price = plain(row.price),
                                    free = row.free,
                                    schemeId = row.schemeId,
                                )
                            },
                            editingMtmId = found.mtmId,
                            editingVrNo = found.vrNo.ifBlank { no },
                            message = "Editing ${found.vrNo.ifBlank { no }}",
                            error = null,
                        )
                    }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSearching = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun reset() = _uiState.update {
        it.copy(
            partyId = "", partyName = "", billNo = "", billDate = today.toApi(), notes = "",
            discount = "", payment = "", lines = emptyList(), draft = null, draftQty = "",
            draftPrice = "", draftFree = false, editingLineId = null, searchNo = "",
            editingMtmId = null, editingVrNo = "", error = null, shortage = null,
        )
    }

    private fun buildPayload(state: ProductionInvoiceUiState): JsonObject {
        val payment = state.effectivePayment.toDoubleOrNull() ?: 0.0
        return JsonObject().apply {
            when (mode) {
                ProductionInvoiceMode.PURCHASE -> {
                    addProperty("account", state.partyId)
                    addProperty("accountName", state.partyName)
                    add("invoice_date", state.billDate.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE)
                    add("invoice_no", state.billNo.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE)
                    addProperty("discountAmt", state.discount.toDoubleOrNull() ?: 0.0)
                    addProperty("paymentAmt", payment)
                }
                ProductionInvoiceMode.SALE -> {
                    addProperty("account", state.partyId)
                    addProperty("accountName", state.partyName)
                    addProperty("discountAmt", state.discount.toDoubleOrNull() ?: 0.0)
                    addProperty("receivedAmt", payment)
                }
                ProductionInvoiceMode.SALES_RETURN -> {
                    addProperty("supplier_id", state.partyId)
                    addProperty("sales_invoice_number", state.billNo)
                    addProperty("sales_invoice_date", state.billDate)
                    addProperty("total", state.total)
                    addProperty("discount", state.discount.toDoubleOrNull() ?: 0.0)
                    addProperty("netpayment", payment)
                }
            }
            add("notes", state.notes.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE)
            add("table_data", JsonArray().apply {
                state.lines.forEach { row ->
                    add(JsonObject().apply {
                        if (state.isReturn) {
                            addProperty("code", row.productId.toLongOrNull() ?: 0L)
                        } else {
                            addProperty("product", row.productId.toLongOrNull() ?: 0L)
                        }
                        addProperty("qty", row.qty.toDoubleOrNull() ?: 0.0)
                        addProperty("price", row.price.toDoubleOrNull() ?: 0.0)
                        if (!state.isReturn) {
                            add("godown", JsonNull.INSTANCE)
                            addProperty("free", row.free)
                        }
                        if (mode == ProductionInvoiceMode.SALE) {
                            add("scheme_id", row.schemeId?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE)
                        }
                    })
                }
            })
            state.editingMtmId?.let { addProperty("mtmId", it) }
        }
    }

    private fun fail(message: String) = _uiState.update { it.copy(error = message) }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onErrorShown() = _uiState.update { it.copy(error = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    private fun num(value: String): String = value.filter { it.isDigit() || it == '.' }

    private fun plain(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    companion object {
        fun provideFactory(context: Context, mode: ProductionInvoiceMode) = viewModelFactory {
            initializer {
                val app = context.applicationContext
                val session = ServiceLocator.provideSessionManager(app)
                ProductionInvoiceViewModel(
                    mode = mode,
                    repository = ServiceLocator.provideProductionRepository(app),
                    ledgerRepository = ServiceLocator.provideLedgerRepository(app),
                    transactionDate = session.state.value.settings?.transactionDate,
                    canWrite = com.example.cashbookbd.session.Permissions.has(
                        session.permissions,
                        if (mode == ProductionInvoiceMode.SALES_RETURN) {
                            "production.sales.return.create"
                        } else {
                            "production.create"
                        },
                    ),
                )
            }
        }
    }
}

private fun JsonObject.dataData(): JsonObject =
    jObj("data")?.jObj("data") ?: jObj("data") ?: JsonObject()

/** A number as form text: "95.0" → "95", "2.50" → "2.5". */
private fun plainAmount(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

private fun JsonObject.str(key: String): String =
    get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

private fun JsonObject.jObj(key: String): JsonObject? =
    get(key)?.takeIf { it.isJsonObject }?.asJsonObject
