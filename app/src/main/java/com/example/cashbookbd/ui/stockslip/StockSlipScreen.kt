package com.example.cashbookbd.ui.stockslip

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.example.cashbookbd.core.UriParts
import com.example.cashbookbd.core.VoucherAttachment
import com.example.cashbookbd.core.VoucherImages
import com.example.cashbookbd.core.jArr
import com.example.cashbookbd.core.jAsArr
import com.example.cashbookbd.core.jAsObj
import com.example.cashbookbd.core.jBool
import com.example.cashbookbd.core.jInt
import com.example.cashbookbd.core.jLong
import com.example.cashbookbd.core.jNum
import com.example.cashbookbd.core.jObj
import com.example.cashbookbd.core.jObjs
import com.example.cashbookbd.core.jObjects
import com.example.cashbookbd.core.jPayload
import com.example.cashbookbd.core.jStr
import com.example.cashbookbd.data.remote.WebApi
import com.example.cashbookbd.data.repository.RequisitionItemOption
import com.example.cashbookbd.data.repository.RequisitionRepository
import com.example.cashbookbd.data.repository.SelectorRepository
import com.example.cashbookbd.data.repository.StockShortageRow
import com.example.cashbookbd.data.repository.StockShortageWarning
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.report.ReportSelectorSource
import com.example.cashbookbd.session.Permissions
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SearchableSelectDropdown
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.components.StockShortageDialog
import com.example.cashbookbd.ui.components.VoucherAttachmentsCell
import com.example.cashbookbd.ui.components.VoucherImageViewerDialog
import com.example.cashbookbd.ui.reports.LogoutOnExpiry
import com.example.cashbookbd.ui.reports.ReportLoading
import com.example.cashbookbd.ui.reports.ReportNote
import com.example.cashbookbd.ui.reports.SnackOnMessage
import com.example.cashbookbd.ui.reports.kitMoney
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One reason of the shop's own short list. */
data class SlipReason(
    val id: String,
    val name: String,
    /** The reason owes a name (a piece swapped back to a customer). */
    val needsParty: Boolean,
    val sort: Int,
    val active: Boolean,
)

/** Where a Product In line's rate came from; NONE opens the box. */
enum class RateSource { PENDING, LAST_PURCHASE, MASTER, NONE }

data class SlipLine(
    val key: Long,
    val productId: String,
    val productName: String,
    val unit: String,
    val quantity: String,
    val note: String,
    val rate: String = "",
    val rateSource: RateSource = RateSource.PENDING,
) {
    val value: Double get() = (quantity.toDoubleOrNull() ?: 0.0) * (rate.toDoubleOrNull() ?: 0.0)
}

data class SlipListRow(
    val id: Long,
    val mainTrxId: Long,
    val number: String,
    val date: String,
    val reason: String,
    val warehouse: String,
    val party: String,
    val itemCount: Int,
    val totalQty: Double,
    val totalValue: Double,
    val voucherImage: String,
    val branchPad: String,
    val approved: Boolean,
)

data class StockSlipUiState(
    val kind: StockSlipKind,
    val canCreate: Boolean = false,
    val canApprove: Boolean = false,
    val reasons: List<SlipReason> = emptyList(),
    val warehouses: List<SelectorOption> = emptyList(),
    val reasonId: String = "",
    val warehouse: SelectorOption? = null,
    val party: SelectorOption? = null,
    val note: String = "",
    val items: List<SlipLine> = emptyList(),
    // The line being typed.
    val lineProduct: SelectorOption? = null,
    val lineUnit: String = "",
    val lineQty: String = "",
    val lineNote: String = "",
    val lineRate: String = "",
    val lineRateSource: RateSource = RateSource.PENDING,
    val rateLoading: Boolean = false,
    val editingLineKey: Long? = null,
    /** The saved slip open for changing: its id and number. */
    val editingId: Long? = null,
    val editingNo: String = "",
    val photos: List<Uri> = emptyList(),
    val isSaving: Boolean = false,
    val shortage: StockShortageWarning? = null,
    // The list.
    val list: List<SlipListRow> = emptyList(),
    val listLoading: Boolean = false,
    val search: String = "",
    val approval: SelectorOption = APPROVAL_FILTERS.first(),
    val listReason: SelectorOption = EVERY_REASON,
    val page: Int = 1,
    val lastPage: Int = 1,
    val busyId: Long? = null,
    val askApprove: Pair<SlipListRow, Boolean>? = null,
    val askDelete: SlipListRow? = null,
    /** Bumped to clear the searchable boxes after a reset. */
    val resetKey: Int = 0,
    val message: String? = null,
    val sessionExpired: Boolean = false,
) {
    val selectedReason: SlipReason? get() = reasons.firstOrNull { it.id == reasonId }
    val needsParty: Boolean get() = selectedReason?.needsParty == true
}

val APPROVAL_FILTERS = listOf(
    SelectorOption("all", "All"),
    SelectorOption("pending", "Pending only"),
    SelectorOption("approved", "Approved only"),
)
val EVERY_REASON = SelectorOption("", "Every reason")

/** The shop's default: the first reason still offered, else the first at all. */
internal fun defaultReasonId(reasons: List<SlipReason>): String =
    (reasons.firstOrNull { it.active } ?: reasons.firstOrNull())?.id.orEmpty()

internal suspend fun WebApi.loadReasons(kind: StockSlipKind): Resource<List<SlipReason>> =
    when (val r = get("${kind.base}/reason/list")) {
        is Resource.Success -> {
            val payload = r.data.get("data").jAsObj()?.get("data")
            val rows = payload.jAsArr() ?: payload.jAsObj()?.get("data").jAsArr() ?: r.data.get("data").jAsArr() ?: JsonArray()
            Resource.Success(
                rows.jObjects().map {
                    SlipReason(
                        id = it.jStr("id"),
                        name = it.jStr("name"),
                        needsParty = it.jBool("needs_party"),
                        sort = it.jInt("sort") ?: 0,
                        active = it.jBool("is_active"),
                    )
                },
            )
        }
        is Resource.Error -> r
        Resource.Loading -> Resource.Loading
    }

class StockSlipViewModel(
    private val kind: StockSlipKind,
    private val api: WebApi,
    private val selectors: SelectorRepository,
    private val requisition: RequisitionRepository,
    private val appContext: Context,
    canCreate: Boolean,
    canApprove: Boolean,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StockSlipUiState(kind = kind, canCreate = canCreate, canApprove = canApprove))
    val uiState: StateFlow<StockSlipUiState> = _uiState.asStateFlow()

    /** The product the newest rate lookup is for. */
    private var rateFor = ""

    init {
        viewModelScope.launch {
            (selectors.fetch(ReportSelectorSource.WAREHOUSE) as? Resource.Success)?.let { r ->
                _uiState.update { it.copy(warehouses = r.data) }
            }
        }
        loadReasons()
        loadList()
    }

    private fun loadReasons() {
        viewModelScope.launch {
            when (val r = api.loadReasons(kind)) {
                // Open a new slip on the shop's own default reason; a reason
                // already chosen is left exactly as it is.
                is Resource.Success -> _uiState.update {
                    it.copy(reasons = r.data, reasonId = it.reasonId.ifBlank { defaultReasonId(r.data) })
                }
                is Resource.Error -> _uiState.update { it.copy(sessionExpired = r.isUnauthorized) }
                Resource.Loading -> Unit
            }
        }
    }

    // ---- Form ---------------------------------------------------------------

    fun onReason(o: SelectorOption) = _uiState.update { it.copy(reasonId = o.id) }
    fun onWarehouse(o: SelectorOption) = _uiState.update { it.copy(warehouse = o.takeIf { w -> w.id.isNotBlank() }) }
    fun onParty(o: SelectorOption) = _uiState.update { it.copy(party = o) }
    fun onNote(v: String) = _uiState.update { it.copy(note = v) }
    fun onLineQty(v: String) = _uiState.update { it.copy(lineQty = v.filter { c -> c.isDigit() || c == '.' }) }
    fun onLineNote(v: String) = _uiState.update { it.copy(lineNote = v) }
    fun onLineRate(v: String) = _uiState.update { it.copy(lineRate = v.filter { c -> c.isDigit() || c == '.' }) }
    fun onPhotos(uris: List<Uri>) = _uiState.update { it.copy(photos = uris) }

    suspend fun searchProducts(q: String): Resource<List<SelectorOption>> =
        when (val r = requisition.searchItems(q)) {
            is Resource.Success -> Resource.Success(r.data.map { it.toOption() })
            is Resource.Error -> r
            Resource.Loading -> Resource.Loading
        }

    private fun RequisitionItemOption.toOption() = SelectorOption(id, name, unit.takeIf { it.isNotBlank() })

    /** Party lookup for the reasons that ask for one: name, mobile or code. */
    suspend fun searchParties(q: String): Resource<List<SelectorOption>> =
        when (val r = api.get("${kind.base}/party", mapOf("q" to q.trim()))) {
            is Resource.Success -> {
                val payload = r.data.get("data").jAsObj()?.get("data") ?: r.data.get("data")
                Resource.Success(
                    (payload.jAsArr() ?: JsonArray()).jObjects().map {
                        SelectorOption(
                            it.jStr("id"), it.jStr("name"),
                            listOf(it.jStr("mobile"), it.jStr("idfr_code")).filter { s -> s.isNotBlank() }.joinToString(" · ").ifBlank { null },
                        )
                    },
                )
            }
            is Resource.Error -> r
            Resource.Loading -> Resource.Loading
        }

    fun onProduct(o: SelectorOption) {
        // Cleared until the lookup answers, so a figure from the previous
        // product can never be read as this one's.
        _uiState.update { it.copy(lineProduct = o, lineUnit = o.sublabel.orEmpty(), lineRate = "", lineRateSource = RateSource.PENDING) }
        if (kind.priced) loadRate(o.id)
    }

    /**
     * What this product should be received at. `none` opens the box; anything
     * else fills it and locks it. A lookup that fails reads as `none` — the
     * server still applies the book rate if it has one.
     */
    private fun loadRate(productId: String) {
        rateFor = productId
        _uiState.update { it.copy(rateLoading = true) }
        viewModelScope.launch {
            val (rate, source) = fetchRate(productId)
            if (rateFor != productId) return@launch
            _uiState.update { it.copy(rateLoading = false, lineRate = rate, lineRateSource = source) }
        }
    }

    private suspend fun fetchRate(productId: String): Pair<String, RateSource> =
        when (val r = api.get("${kind.base}/rate/$productId")) {
            is Resource.Success -> {
                val p = r.data.jPayload()
                when (p.jStr("source")) {
                    "last_purchase" -> trimNumber(p.jNum("rate")) to RateSource.LAST_PURCHASE
                    "master" -> trimNumber(p.jNum("rate")) to RateSource.MASTER
                    else -> trimNumber(p.jNum("rate")) to RateSource.NONE
                }
            }
            else -> "" to RateSource.NONE
        }

    private fun trimNumber(v: Double): String = when {
        v == 0.0 -> ""
        v % 1.0 == 0.0 -> v.toLong().toString()
        else -> v.toString()
    }

    fun clearLine() {
        rateFor = ""
        _uiState.update {
            it.copy(
                lineProduct = null, lineUnit = "", lineQty = "", lineNote = "", lineRate = "",
                lineRateSource = RateSource.PENDING, rateLoading = false, editingLineKey = null,
                resetKey = it.resetKey + 1,
            )
        }
    }

    private fun validateLine(): Boolean {
        val s = _uiState.value
        val product = s.lineProduct
        val error = when {
            product == null -> "Please select product"
            (s.lineQty.toDoubleOrNull() ?: 0.0) <= 0 -> "Please enter valid quantity"
            kind.priced && s.lineRateSource == RateSource.PENDING -> "Wait for the rate to load"
            kind.priced && s.lineRateSource == RateSource.NONE && s.lineRate.isNotBlank() &&
                (s.lineRate.toDoubleOrNull() ?: -1.0) < 0 -> "The rate has to be a number, and not a negative one"
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(message = error) }
            return false
        }
        // One rate per product: the server keys the clerk's rates by product.
        if (kind.priced && s.lineRateSource == RateSource.NONE) {
            val other = s.items.firstOrNull { it.productId == product!!.id && it.key != s.editingLineKey }
            if (other != null && (other.rate.toDoubleOrNull() ?: 0.0) != (s.lineRate.toDoubleOrNull() ?: 0.0)) {
                _uiState.update {
                    it.copy(message = "${product!!.label} is already on this receipt at ৳${kitMoney(other.rate.toDoubleOrNull() ?: 0.0)}. Use the same rate.")
                }
                return false
            }
        }
        return true
    }

    private fun currentLine(key: Long): SlipLine {
        val s = _uiState.value
        return SlipLine(
            key = key,
            productId = s.lineProduct!!.id,
            productName = s.lineProduct.label,
            unit = s.lineUnit,
            quantity = s.lineQty,
            note = s.lineNote,
            rate = s.lineRate,
            rateSource = s.lineRateSource,
        )
    }

    fun addOrUpdateLine() {
        if (!validateLine()) return
        val s = _uiState.value
        val editing = s.editingLineKey
        _uiState.update {
            it.copy(
                items = if (editing == null) {
                    it.items + currentLine(System.nanoTime())
                } else {
                    it.items.map { line -> if (line.key == editing) currentLine(editing) else line }
                },
            )
        }
        clearLine()
    }

    fun editLine(line: SlipLine) = _uiState.update {
        it.copy(
            lineProduct = SelectorOption(line.productId, line.productName, line.unit.ifBlank { null }),
            lineUnit = line.unit, lineQty = line.quantity, lineNote = line.note,
            lineRate = line.rate, lineRateSource = line.rateSource, editingLineKey = line.key,
            resetKey = it.resetKey + 1,
        )
    }

    fun deleteLine(line: SlipLine) {
        _uiState.update { it.copy(items = it.items.filterNot { l -> l.key == line.key }) }
        if (_uiState.value.editingLineKey == line.key) clearLine()
    }

    fun resetForm() {
        clearLine()
        _uiState.update {
            it.copy(
                reasonId = defaultReasonId(it.reasons), warehouse = null, party = null, note = "",
                items = emptyList(), editingId = null, editingNo = "", photos = emptyList(),
            )
        }
    }

    /**
     * Opens a saved slip in the form. Everything the save sends is filled back
     * from the slip's own details; on Product In each line's rate is asked
     * again, because whether the box is open is a question about the books today.
     */
    fun editSlip(row: SlipListRow) {
        viewModelScope.launch {
            when (val r = api.get("${kind.base}/details/${row.id}")) {
                is Resource.Success -> {
                    val data = r.data.jPayload()
                    val master = data.jObj("master") ?: JsonObject()
                    if (master.jBool("is_approved")) {
                        _uiState.update {
                            it.copy(message = "${master.jStr(kind.numberKey)} is approved. Take the approval off first, then change it.")
                        }
                        return@launch
                    }
                    val details = data.jObjs("details")
                    val lines = details.mapIndexed { i, line ->
                        async {
                            var rate = trimNumber(line.jNum("rate"))
                            var source = RateSource.NONE
                            if (kind.priced) {
                                val (bookRate, bookSource) = fetchRate(line.jStr("product_id"))
                                if (bookSource != RateSource.NONE) {
                                    rate = bookRate
                                    source = bookSource
                                }
                            }
                            SlipLine(
                                key = System.nanoTime() + i,
                                productId = line.jStr("product_id"),
                                productName = line.jStr("product_name"),
                                unit = line.jStr("unit_name"),
                                quantity = trimNumber(line.jNum("quantity")).ifBlank { "0" },
                                note = line.jStr("note"),
                                rate = rate,
                                rateSource = source,
                            )
                        }
                    }.awaitAll()
                    clearLine()
                    _uiState.update { s ->
                        s.copy(
                            warehouse = master.jStr("warehouse_id").takeIf { it.isNotBlank() && it != "0" }
                                ?.let { id -> s.warehouses.firstOrNull { it.id == id } ?: SelectorOption(id, id) },
                            reasonId = master.jStr("reason_id"),
                            note = master.jStr("note"),
                            items = lines,
                            party = master.jStr("party_id").takeIf { it.isNotBlank() && it != "0" }
                                ?.let { SelectorOption(it, master.jStr("party_name")) },
                            editingId = master.jLong("id"),
                            editingNo = master.jStr(kind.numberKey),
                            photos = emptyList(),
                        )
                    }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(message = r.message.ifBlank { "Could not open the ${kind.noun}" }, sessionExpired = r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun save() {
        val s = _uiState.value
        val error = when {
            s.reasonId.isBlank() -> "Please pick a reason"
            s.needsParty && s.party == null -> kind.partyRequired
            s.items.isEmpty() -> "Please add at least one product"
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(message = error) }
            return
        }
        submit(allowNegative = false)
    }

    fun continueShortage() {
        _uiState.update { it.copy(shortage = null) }
        submit(allowNegative = true)
    }

    fun dismissShortage() = _uiState.update { it.copy(shortage = null) }

    private fun buildPayload(allowNegative: Boolean): JsonObject {
        val s = _uiState.value
        return JsonObject().apply {
            s.warehouse?.id?.toLongOrNull()?.let { addProperty(kind.warehouseKey, it) } ?: add(kind.warehouseKey, JsonNull.INSTANCE)
            addProperty("reason_id", s.reasonId.toLongOrNull() ?: 0L)
            // Only while the reason still asks for one.
            val party = s.party?.id?.toLongOrNull()?.takeIf { s.needsParty }
            if (party != null) addProperty("party_id", party) else add("party_id", JsonNull.INSTANCE)
            if (s.note.isNotBlank()) addProperty("note", s.note) else add("note", JsonNull.INSTANCE)
            if (kind.checksShortage) addProperty("allow_negative", allowNegative)
            if (kind.priced) {
                // Only the lines the books could not price, and only where the
                // clerk actually answered.
                val rates = JsonObject()
                s.items.filter { it.rateSource == RateSource.NONE && it.rate.isNotBlank() }
                    .forEach { rates.addProperty(it.productId, it.rate.toDoubleOrNull() ?: 0.0) }
                add("rates", rates)
            }
            add("items", JsonArray().apply {
                s.items.forEach { line ->
                    add(JsonObject().apply {
                        addProperty("product_id", line.productId.toLongOrNull() ?: 0L)
                        addProperty("quantity", line.quantity.toDoubleOrNull() ?: 0.0)
                        if (line.note.isNotBlank()) addProperty("note", line.note) else add("note", JsonNull.INSTANCE)
                    })
                }
            })
        }
    }

    private fun submit(allowNegative: Boolean) {
        val s = _uiState.value
        if (s.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        val nameOf = s.items.associate { it.productId to it.productName }
        val path = s.editingId?.let { "${kind.base}/update/$it" } ?: "${kind.base}/store"
        viewModelScope.launch {
            when (val r = api.postEnvelope(path, buildPayload(allowNegative))) {
                is Resource.Success -> {
                    val body = r.data
                    val ok = body.jBool("success")
                    val shortages = body.jObjs("shortages")
                    when {
                        // A shortage is success:false WITH a list — asked once.
                        kind.checksShortage && !ok && shortages.isNotEmpty() -> _uiState.update {
                            it.copy(
                                isSaving = false,
                                shortage = StockShortageWarning(
                                    rows = shortages.map { x -> StockShortageRow(x.jStr("name"), x.jNum("available"), x.jNum("requested")) },
                                    shortages = emptyList(),
                                    message = body.jStr("message").ifBlank { "Not enough stock on the book." },
                                    blocked = false,
                                ),
                            )
                        }
                        !ok -> _uiState.update {
                            it.copy(isSaving = false, message = WebApi.messageOf(body).ifBlank { "Failed to save ${kind.noun}" })
                        }
                        else -> {
                            val saved = body.jObj("data")?.jObj("data") ?: body.jObj("data") ?: JsonObject()
                            var msg = body.jStr("message").ifBlank {
                                if (s.editingId != null) "${kind.noun.replaceFirstChar { it.uppercase() }} updated" else "${kind.noun.replaceFirstChar { it.uppercase() }} saved"
                            }
                            // ৳0 lines are said out loud: they raise the quantity
                            // and not the value, and read as profit when sold.
                            if (kind.priced) {
                                val unpriced = saved.jArr("unpriced").mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }
                                if (unpriced.isNotEmpty()) {
                                    msg += "\n${unpriced.size} line(s) went in at ৳0 -- no purchase price on the books: " +
                                        unpriced.joinToString(", ") { id -> nameOf[id] ?: "#$id" }
                                }
                            }
                            // Photos go on once the slip has a voucher to hang them on.
                            val mainTrxId = saved.jLong("main_trx_id") ?: 0L
                            if (kind.photos && s.photos.isNotEmpty() && mainTrxId > 0) {
                                msg += "\n" + uploadPhotos(mainTrxId, s.photos)
                            }
                            _uiState.update { it.copy(isSaving = false, message = msg) }
                            resetForm()
                            loadList()
                        }
                    }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isSaving = false, message = r.message, sessionExpired = r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    /** The Image Upload screen's own endpoint; a failure is said, never thrown. */
    private suspend fun uploadPhotos(mainTrxId: Long, uris: List<Uri>): String {
        val parts = withContext(Dispatchers.IO) { uris.mapNotNull { UriParts.part(appContext, it, "images[]") } }
        if (parts.isEmpty()) return "The photos could not be attached"
        return when (val r = api.multipart("admin/voucher/upload/$mainTrxId", mapOf("voucher_id" to UriParts.text(mainTrxId.toString())), parts)) {
            is Resource.Success -> "${parts.size} photo(s) attached"
            is Resource.Error -> r.message.ifBlank { "The photos could not be attached" }
            Resource.Loading -> ""
        }
    }

    // ---- List ---------------------------------------------------------------

    fun onSearch(v: String) = _uiState.update { it.copy(search = v) }
    fun onApprovalFilter(o: SelectorOption) { _uiState.update { it.copy(approval = o, page = 1) }; loadList() }
    fun onListReason(o: SelectorOption) { _uiState.update { it.copy(listReason = o, page = 1) }; loadList() }
    fun searchList() { _uiState.update { it.copy(page = 1) }; loadList() }
    fun onPage(p: Int) { _uiState.update { it.copy(page = p) }; loadList() }

    fun loadList() {
        val s = _uiState.value
        _uiState.update { it.copy(listLoading = true) }
        viewModelScope.launch {
            val params = buildMap {
                put("page", s.page.toString())
                put("perPage", "10")
                put("search", s.search)
                put("approval", s.approval.id)
                s.listReason.id.takeIf { it.isNotBlank() }?.let { put("reason_id", it) }
            }
            when (val r = api.get("${kind.base}/list", params)) {
                is Resource.Success -> {
                    val root = r.data.get("data").jAsObj()?.get("data") ?: r.data.get("data")
                    val pager = root.jAsObj()
                    val rows = root.jAsArr() ?: pager?.get("data").jAsArr() ?: JsonArray()
                    _uiState.update {
                        it.copy(
                            listLoading = false,
                            lastPage = pager?.jInt("last_page") ?: 1,
                            list = rows.jObjects().map { o ->
                                SlipListRow(
                                    id = o.jLong("id") ?: 0L,
                                    mainTrxId = o.jLong("main_trx_id") ?: 0L,
                                    number = o.jStr(kind.numberKey),
                                    date = o.jStr(kind.dateKey),
                                    reason = o.jStr("reason_name"),
                                    warehouse = o.jStr("warehouse_name"),
                                    party = o.jStr("party_name"),
                                    itemCount = o.jInt("item_count") ?: 0,
                                    totalQty = o.jNum("total_qty"),
                                    totalValue = o.jNum("total_value"),
                                    voucherImage = o.jStr("voucher_image"),
                                    branchPad = o.jStr("branch_pad"),
                                    approved = o.jBool("is_approved"),
                                )
                            },
                        )
                    }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(listLoading = false, list = emptyList(), message = r.message, sessionExpired = r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun askApprove(row: SlipListRow, approve: Boolean) = _uiState.update { it.copy(askApprove = row to approve) }
    fun askDelete(row: SlipListRow) = _uiState.update { it.copy(askDelete = row) }
    fun dismissAsks() = _uiState.update { it.copy(askApprove = null, askDelete = null) }

    /** The Approval Center's own endpoint: the stock already moved at save. */
    fun confirmApproval() {
        val (row, approve) = _uiState.value.askApprove ?: return
        val verb = if (approve) "Approve" else "Take the approval back off"
        _uiState.update { it.copy(askApprove = null, busyId = row.id) }
        viewModelScope.launch {
            val body = JsonObject().apply {
                addProperty("type", "voucher")
                addProperty("action", if (approve) "approve" else "remove")
                add("ids", JsonArray().apply { add(row.mainTrxId) })
                addProperty("remarks", "$verb from the ${kind.title.lowercase()} list")
            }
            val r = api.post("approval-center/action", body)
            _uiState.update {
                it.copy(
                    busyId = null,
                    message = when (r) {
                        is Resource.Success -> r.data.jStr("message").ifBlank { "$verb done" }
                        is Resource.Error -> r.message.ifBlank { "$verb failed" }
                        Resource.Loading -> null
                    },
                    sessionExpired = (r as? Resource.Error)?.isUnauthorized == true,
                )
            }
            loadList()
        }
    }

    fun confirmDelete() {
        val row = _uiState.value.askDelete ?: return
        _uiState.update { it.copy(askDelete = null, busyId = row.id) }
        viewModelScope.launch {
            val r = api.post("${kind.base}/destroy/${row.id}")
            _uiState.update {
                it.copy(
                    busyId = null,
                    message = when (r) {
                        is Resource.Success -> r.data.jStr("message").ifBlank { "Deleted" }
                        is Resource.Error -> r.message.ifBlank { "Delete failed" }
                        Resource.Loading -> null
                    },
                    sessionExpired = (r as? Resource.Error)?.isUnauthorized == true,
                )
            }
            loadList()
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context, kind: StockSlipKind) = viewModelFactory {
            initializer {
                val app = context.applicationContext
                val permissions = ServiceLocator.provideSessionManager(app).state.value.settings?.permissions
                StockSlipViewModel(
                    kind = kind,
                    api = ServiceLocator.provideWebApi(app),
                    selectors = ServiceLocator.provideSelectorRepository(app),
                    requisition = ServiceLocator.provideRequisitionRepository(app),
                    appContext = app,
                    canCreate = Permissions.has(permissions, kind.createPermission),
                    canApprove = Permissions.has(permissions, kind.approvePermission),
                )
            }
        }
    }
}

@Composable
fun StockSlipScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    kind: StockSlipKind,
    viewModel: StockSlipViewModel = viewModel(
        key = "stock-slip-${kind.name}",
        factory = StockSlipViewModel.provideFactory(LocalContext.current, kind),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val settings = remember { ServiceLocator.provideSessionManager(context).state.value.settings }
    var viewing by remember { mutableStateOf<VoucherAttachment?>(null) }
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        viewModel.onPhotos(uris)
    }
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)
    SnackOnMessage(state.message, snackbar, viewModel::onMessageShown)

    val reasonOptions = state.reasons.map { SelectorOption(it.id, it.name) }

    AuthenticatedShell(title = kind.title, currentRoute = Routes.BRANCH_TRANSFER, navController = navController, onLogout = onLogout) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.editingId != null) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.appColors.warning, MaterialTheme.shapes.small)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Editing ${state.editingNo} — Update saves the change and moves the stock with it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.appColors.warning,
                                modifier = Modifier.weight(1f),
                            )
                            LinkButton(text = "Cancel edit", onClick = viewModel::resetForm)
                        }
                    }
                }
                item {
                    AppSelectDropdown(
                        label = "Reason",
                        options = reasonOptions,
                        selected = reasonOptions.firstOrNull { it.id == state.reasonId },
                        onSelected = viewModel::onReason,
                        placeholder = kind.reasonPlaceholder,
                    )
                }
                item {
                    AppSelectDropdown(
                        label = kind.warehouseLabel,
                        options = listOf(SelectorOption("", "Select warehouse")) + state.warehouses,
                        selected = state.warehouse,
                        onSelected = viewModel::onWarehouse,
                        placeholder = "Select warehouse",
                    )
                }
                // Only the reasons that owe a name show a picker.
                if (state.needsParty) {
                    item {
                        key(state.editingId) {
                            SearchableSelectDropdown(
                                selected = state.party,
                                onSelected = viewModel::onParty,
                                search = viewModel::searchParties,
                                label = kind.partyLabel,
                                placeholder = "Search a customer by name, mobile or code",
                                emptyText = "No customer found",
                            )
                        }
                    }
                }
                item { AppTextField(state.note, viewModel::onNote, label = "Anything worth writing down", caption = "Note", modifier = Modifier.fillMaxWidth()) }
                if (kind.photos) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryButton(
                                text = if (state.editingId != null) "Photos (added to the ones on the slip)" else "Photos",
                                onClick = { pickPhotos.launch("image/*") },
                            )
                            if (state.photos.isNotEmpty()) {
                                Text(
                                    "${state.photos.size} file(s) will be attached on ${if (state.editingId != null) "Update" else "Save"}.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.appColors.textOnScreenMuted,
                                )
                            }
                        }
                    }
                }
                item { LineEditor(state, viewModel) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton(
                            text = if (state.editingLineKey != null) "Update Item" else "Add Item",
                            onClick = viewModel::addOrUpdateLine,
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(text = "Clear Item", onClick = viewModel::clearLine, modifier = Modifier.weight(1f))
                    }
                }
                if (state.items.isEmpty()) {
                    item { ReportNote("No product added") }
                } else {
                    itemsIndexed(state.items, key = { _, l -> l.key }) { i, line -> SlipLineCard(kind, i + 1, line, viewModel::editLine, viewModel::deleteLine) }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton(
                            text = if (state.isSaving) "Saving..." else if (state.editingId != null) "Update" else "Save",
                            onClick = viewModel::save,
                            isLoading = state.isSaving,
                            enabled = state.canCreate && !state.isSaving,
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(
                            text = if (state.editingId != null) "Cancel Edit" else "Reset All",
                            onClick = viewModel::resetForm,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // ---- The list ----
                item {
                    Text(
                        kind.listTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = AppFontWeight.SemiBold,
                        color = MaterialTheme.appColors.textOnScreen,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppSelectDropdown("Approval", APPROVAL_FILTERS, state.approval, viewModel::onApprovalFilter, Modifier.weight(1f))
                        AppSelectDropdown("Reason", listOf(EVERY_REASON) + reasonOptions, state.listReason, viewModel::onListReason, Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                        AppTextField(state.search, viewModel::onSearch, label = "Search", caption = "Search", modifier = Modifier.weight(1f))
                        PrimaryButton(text = "Search", onClick = viewModel::searchList)
                    }
                }
                when {
                    state.listLoading && state.list.isEmpty() -> item { ReportLoading() }
                    state.list.isEmpty() -> item { ReportNote("No data found") }
                    else -> itemsIndexed(state.list, key = { _, r -> r.id }) { i, row ->
                        SlipListCard(
                            kind = kind,
                            sl = (state.page - 1) * 10 + i + 1,
                            row = row,
                            canApprove = state.canApprove,
                            canChange = state.canCreate,
                            busy = state.busyId == row.id,
                            isLocalEnv = settings?.isLocalEnv == true,
                            onApprove = { viewModel.askApprove(row, !row.approved) },
                            onEdit = { viewModel.editSlip(row) },
                            onDelete = { viewModel.askDelete(row) },
                            onOpenPhoto = { viewing = it },
                        )
                    }
                }
                if (state.lastPage > 1) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            LinkButton(text = "Previous", enabled = state.page > 1, onClick = { viewModel.onPage(state.page - 1) })
                            Text("Page ${state.page} of ${state.lastPage}", style = MaterialTheme.typography.labelMedium)
                            LinkButton(text = "Next", enabled = state.page < state.lastPage, onClick = { viewModel.onPage(state.page + 1) })
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }

    viewing?.let { VoucherImageViewerDialog(it, settings?.isLocalEnv == true) { viewing = null } }
    state.shortage?.let {
        StockShortageDialog(warning = it, action = "write it off", onCancel = viewModel::dismissShortage, onContinue = viewModel::continueShortage)
    }
    state.askApprove?.let { (row, approve) ->
        AlertDialog(
            onDismissRequest = viewModel::dismissAsks,
            title = { Text(if (approve) "Confirm Approval" else "Remove Approval") },
            text = {
                Text(
                    (if (approve) "Are you sure you want to approve ${kind.noun} " else "Take the approval back off ${kind.noun} ") +
                        "${row.number} ?\n\n${kind.approvedNote}",
                )
            },
            confirmButton = { LinkButton(text = if (approve) "Approve" else "Remove Approval", onClick = viewModel::confirmApproval) },
            dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::dismissAsks) },
        )
    }
    state.askDelete?.let { row ->
        AlertDialog(
            onDismissRequest = viewModel::dismissAsks,
            title = { Text("Confirm Deletion") },
            text = { Text("Are you sure you want to delete ${kind.noun} ${row.number} ?") },
            confirmButton = { LinkButton(text = "Delete", onClick = viewModel::confirmDelete, color = MaterialTheme.colorScheme.error) },
            dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::dismissAsks) },
        )
    }
}

@Composable
private fun LineEditor(state: StockSlipUiState, viewModel: StockSlipViewModel) {
    val kind = state.kind
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            key(state.resetKey) {
                SearchableSelectDropdown(
                    selected = state.lineProduct,
                    onSelected = viewModel::onProduct,
                    search = viewModel::searchProducts,
                    label = "Select Product",
                    emptyText = "No product found",
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField(
                    state.lineQty, viewModel::onLineQty, label = "0",
                    caption = "Quantity" + if (state.lineUnit.isNotBlank()) " (${state.lineUnit})" else "",
                    keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f),
                )
                if (kind.priced) {
                    // Locked where the books hold a rate, and the lock is real:
                    // the server consults the clerk's figure last.
                    AppTextField(
                        state.lineRate, viewModel::onLineRate,
                        label = if (state.lineRateSource == RateSource.NONE) "0" else "",
                        caption = "Rate",
                        enabled = state.lineRateSource == RateSource.NONE,
                        keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f),
                    )
                }
            }
            if (kind.priced) {
                val hint = when (state.lineRateSource) {
                    RateSource.PENDING -> if (state.rateLoading) "Looking up the rate…" else ""
                    RateSource.NONE -> "No purchase price on the books — type one, or leave blank for ৳0"
                    RateSource.MASTER -> "Product catalogue price"
                    RateSource.LAST_PURCHASE -> "Last purchase rate"
                }
                if (hint.isNotBlank()) Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textMuted)
            }
            AppTextField(state.lineNote, viewModel::onLineNote, label = "Optional", caption = "Line Note", modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SlipLineCard(kind: StockSlipKind, sl: Int, line: SlipLine, onEdit: (SlipLine) -> Unit, onDelete: (SlipLine) -> Unit) {
    // A ৳0 receipt line is called out before Save: it adds quantity and no value.
    val zeroRated = kind.priced && line.value <= 0
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("$sl. ${line.productName}", fontWeight = AppFontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                Text(
                    "Qty: ${kitMoney(line.quantity.toDoubleOrNull() ?: 0.0)} ${line.unit}".trim(),
                    style = MaterialTheme.typography.labelSmall,
                )
                if (kind.priced) {
                    Text(
                        "Rate: ${kitMoney(line.rate.toDoubleOrNull() ?: 0.0)} · Value: ${kitMoney(line.value)}" +
                            if (zeroRated) " — no rate on this line, it adds quantity and no value" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (zeroRated) MaterialTheme.appColors.warning else MaterialTheme.appColors.textMuted,
                        fontWeight = if (zeroRated) AppFontWeight.SemiBold else null,
                    )
                }
                Text("Note: ${line.note.ifBlank { "-" }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textMuted)
            }
            LinkButton(text = "Edit", onClick = { onEdit(line) })
            LinkButton(text = "Delete", onClick = { onDelete(line) }, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SlipListCard(
    kind: StockSlipKind,
    sl: Int,
    row: SlipListRow,
    canApprove: Boolean,
    canChange: Boolean,
    busy: Boolean,
    isLocalEnv: Boolean,
    onApprove: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenPhoto: (VoucherAttachment) -> Unit,
) {
    val muted = MaterialTheme.appColors.textMuted
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$sl. ${row.number.ifBlank { "-" }}", fontWeight = AppFontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    if (row.approved) "Approved" else "Pending",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = AppFontWeight.SemiBold,
                    color = if (row.approved) MaterialTheme.appColors.success else MaterialTheme.appColors.warning,
                )
            }
            Text("${row.date.ifBlank { "-" }} · ${row.reason.ifBlank { "-" }}", style = MaterialTheme.typography.labelSmall, color = muted)
            Text("Warehouse: ${row.warehouse.ifBlank { "-" }} · ${kind.partyLabel}: ${row.party.ifBlank { "-" }}", style = MaterialTheme.typography.labelSmall, color = muted)
            Text(
                "Items: ${row.itemCount} · Total Qty: ${kitMoney(row.totalQty)}" +
                    if (kind.priced) " · Value: ${kitMoney(row.totalValue)}" else "",
                style = MaterialTheme.typography.labelSmall,
            )
            if (kind.photos) {
                val attachments = VoucherImages.attachments(row.voucherImage, row.branchPad.ifBlank { null })
                if (attachments.isNotEmpty()) VoucherAttachmentsCell(attachments, isLocalEnv, onOpenPhoto)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (busy) {
                    ReportLoading()
                } else {
                    if (canApprove) {
                        LinkButton(
                            text = if (row.approved) "Remove Approval" else "Approve",
                            onClick = onApprove,
                            color = if (row.approved) MaterialTheme.appColors.warning else MaterialTheme.appColors.success,
                        )
                    }
                    // Edit and Delete: the slip's own permission, and not once signed off.
                    if (canChange && !row.approved) {
                        LinkButton(text = "Edit", onClick = onEdit)
                        LinkButton(text = "Delete", onClick = onDelete, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
