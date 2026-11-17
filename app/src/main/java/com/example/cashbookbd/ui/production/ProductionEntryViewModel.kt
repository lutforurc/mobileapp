package com.example.cashbookbd.ui.production

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.ProductionRepository
import com.example.cashbookbd.data.repository.RecipeRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.production.ProductionMenu
import com.example.cashbookbd.ui.components.AppDateField
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SearchableSelectDropdown
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.production.model.ProductionAllocatableRow
import com.example.cashbookbd.ui.production.model.ProductionBatchRow
import com.example.cashbookbd.ui.production.model.RecipeListRow
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/** A product as the entry screen's search boxes hand it over. */
data class EntryProduct(val id: String, val label: String, val unit: String, val price: Double?)

data class EntryInputLine(
    val localId: Long,
    val productId: String,
    val productName: String,
    val unit: String,
    val qty: String,
    val godownId: String = "",
)

data class EntryOutputLine(
    val localId: Long,
    val productId: String,
    val productName: String,
    val unit: String,
    val qty: String,
    val price: String,
    val share: String = "",
    val expiry: String = "",
    val godownId: String = "",
)

data class RecipeLineLocal(
    val productId: String,
    val productName: String,
    val unit: String,
    val baseQty: Double,
)

data class RecipeCheckLocal(
    val id: String,
    val name: String,
    val version: String,
    val yieldQty: Double,
    val outputProductId: String,
    val outputProductName: String,
    val unit: String,
    val salesPrice: String,
    val lossBasis: String,
    val lossTolerancePct: Double,
    val filledQty: Double,
    val lines: List<RecipeLineLocal>,
)

data class ProductionEntryUiState(
    val date: String = "",
    val batchRef: String = "",
    val notes: String = "",
    val inputs: List<EntryInputLine> = emptyList(),
    val outputs: List<EntryOutputLine> = emptyList(),
    val lossActual: String = "",
    val lossTolerance: String = "",
    val lossBasis: String = "kg",
    val lossReason: String = "",
    val allocations: Map<Long, String> = emptyMap(),

    val recipes: List<RecipeListRow> = emptyList(),
    val recipeId: String = "",
    val recipeQty: String = "",
    val recipeName: String = "",
    val recipeVersion: String = "",
    val check: RecipeCheckLocal? = null,

    val batches: List<ProductionBatchRow> = emptyList(),
    val allocatable: List<ProductionAllocatableRow> = emptyList(),
    val stock: Map<String, Double> = emptyMap(),

    val tab: Int = 0,
    val editingBatchId: Long? = null,
    val editingBatchRef: String = "",

    val inputDraft: EntryProduct? = null,
    val inputDraftQty: String = "",
    val editingInputId: Long? = null,

    val outputDraft: EntryProduct? = null,
    val outputDraftQty: String = "",
    val outputDraftExpiry: String = "",
    val outputDraftPrice: String = "",
    val editingOutputId: Long? = null,

    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val sessionExpired: Boolean = false,
    val canWrite: Boolean = false,
    val confirmReverseId: Long? = null,
    val confirmDeleteId: Long? = null,
) {
    val autoShares: List<Double>
        get() {
            val weights = outputs.map { (it.qty.toDoubleOrNull() ?: 0.0) * (it.price.toDoubleOrNull() ?: 0.0) }
            val sum = weights.sum()
            return weights.map { if (sum > 0) it / sum * 100.0 else 0.0 }
        }

    val sharesTyped: Boolean get() = outputs.any { it.share.isNotBlank() }

    val shareTotal: Double
        get() = if (sharesTyped) outputs.sumOf { it.share.toDoubleOrNull() ?: 0.0 } else 100.0

    val recipeFactor: Double
        get() {
            val check = check ?: return 1.0
            val target = recipeQty.toDoubleOrNull() ?: check.yieldQty
            return if (check.yieldQty > 0) target / check.yieldQty else 1.0
        }

    val recipeQtyChanged: Boolean
        get() {
            val check = check ?: return false
            val target = recipeQty.toDoubleOrNull() ?: check.yieldQty
            return target != check.filledQty
        }

    val allocationTotal: Double
        get() = allocations.values.sumOf { it.toDoubleOrNull() ?: 0.0 }

    val headerLabel: String
        get() = if (editingBatchId != null) {
            "Editing draft ${editingBatchRef.ifBlank { "#$editingBatchId" }}"
        } else {
            "New batch"
        }
}

class ProductionEntryViewModel(
    private val repository: ProductionRepository,
    private val recipeRepository: RecipeRepository,
    transactionDate: String?,
    canWrite: Boolean,
) : ViewModel() {

    private val today = SimpleDate.fromDisplay(transactionDate) ?: SimpleDate.today()

    private val _uiState = MutableStateFlow(
        ProductionEntryUiState(date = today.toApi(), canWrite = canWrite),
    )
    val uiState: StateFlow<ProductionEntryUiState> = _uiState.asStateFlow()

    init {
        loadBatches()
        loadAllocatable(today.toApi())
        loadRecipes(today.toApi())
    }

    fun onTab(index: Int) = _uiState.update { it.copy(tab = index) }

    fun onDate(date: SimpleDate) {
        _uiState.update { it.copy(date = date.toApi()) }
        loadAllocatable(date.toApi())
        loadRecipes(date.toApi())
        reloadStock()
    }

    fun onBatchRef(v: String) = _uiState.update { it.copy(batchRef = v.take(255)) }
    fun onNotes(v: String) = _uiState.update { it.copy(notes = v.take(500)) }
    fun onLossActual(v: String) = _uiState.update { it.copy(lossActual = num(v)) }
    fun onLossTolerance(v: String) = _uiState.update { it.copy(lossTolerance = num(v)) }
    fun onLossBasis(v: String) = _uiState.update { it.copy(lossBasis = v) }
    fun onLossReason(v: String) = _uiState.update { it.copy(lossReason = v.take(255)) }

    fun loadBatches() {
        viewModelScope.launch {
            when (val result = repository.fetchBatches()) {
                is Resource.Success -> _uiState.update { it.copy(batches = result.data) }
                is Resource.Error -> _uiState.update {
                    it.copy(error = result.message, sessionExpired = it.sessionExpired || result.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    private fun loadAllocatable(date: String) {
        viewModelScope.launch {
            when (val result = repository.fetchAllocatable(date)) {
                is Resource.Success -> _uiState.update { it.copy(allocatable = result.data) }
                is Resource.Error -> Unit
                Resource.Loading -> Unit
            }
        }
    }

    private fun loadRecipes(date: String) {
        viewModelScope.launch {
            when (val result = recipeRepository.fetchRecipes(date)) {
                is Resource.Success -> _uiState.update { it.copy(recipes = result.data) }
                is Resource.Error -> Unit
                Resource.Loading -> Unit
            }
        }
    }

    private fun reloadStock() {
        val state = _uiState.value
        val ids = (state.inputs.map { it.productId } + state.check?.lines?.map { it.productId }.orEmpty())
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(",")
        if (ids.isBlank()) {
            _uiState.update { it.copy(stock = emptyMap()) }
            return
        }
        viewModelScope.launch {
            when (val result = repository.fetchStock(ids, state.date)) {
                is Resource.Success -> _uiState.update { it.copy(stock = result.data) }
                is Resource.Error -> Unit
                Resource.Loading -> Unit
            }
        }
    }

    // ---- Inputs ---------------------------------------------------------

    fun onInputProduct(product: EntryProduct?) =
        _uiState.update { it.copy(inputDraft = product, inputDraftQty = "") }

    fun onInputQty(v: String) = _uiState.update { it.copy(inputDraftQty = num(v)) }

    fun addOrUpdateInput() {
        val state = _uiState.value
        val product = state.inputDraft
        if (product == null) {
            _uiState.update { it.copy(error = "Pick a product.") }
            return
        }
        if ((state.inputDraftQty.toDoubleOrNull() ?: 0.0) <= 0) {
            _uiState.update { it.copy(error = "Enter a quantity.") }
            return
        }
        val line = EntryInputLine(
            localId = state.editingInputId ?: System.nanoTime(),
            productId = product.id,
            productName = product.label,
            unit = product.unit,
            qty = state.inputDraftQty,
        )
        _uiState.update {
            it.copy(
                inputs = if (it.editingInputId != null) {
                    it.inputs.map { row -> if (row.localId == it.editingInputId) line else row }
                } else {
                    it.inputs + line
                },
                inputDraft = null,
                inputDraftQty = "",
                editingInputId = null,
                error = null,
            )
        }
        reloadStock()
    }

    fun editInput(id: Long) = _uiState.update { state ->
        val row = state.inputs.firstOrNull { it.localId == id } ?: return@update state
        state.copy(
            inputDraft = EntryProduct(row.productId, row.productName, row.unit, null),
            inputDraftQty = row.qty,
            editingInputId = id,
        )
    }

    fun removeInput(id: Long) {
        _uiState.update { it.copy(inputs = it.inputs.filterNot { row -> row.localId == id }) }
        reloadStock()
    }

    // ---- Outputs --------------------------------------------------------

    fun onOutputProduct(product: EntryProduct?) = _uiState.update {
        it.copy(
            outputDraft = product,
            outputDraftPrice = product?.price?.let { p ->
                if (p % 1.0 == 0.0) p.toLong().toString() else p.toString()
            } ?: "",
        )
    }

    fun onOutputQty(v: String) = _uiState.update { it.copy(outputDraftQty = num(v)) }
    fun onOutputPrice(v: String) = _uiState.update { it.copy(outputDraftPrice = num(v)) }
    fun onOutputExpiry(date: SimpleDate) = _uiState.update { it.copy(outputDraftExpiry = date.toApi()) }
    fun clearOutputExpiry() = _uiState.update { it.copy(outputDraftExpiry = "") }

    fun addOrUpdateOutput() {
        val state = _uiState.value
        val product = state.outputDraft
        if (product == null) {
            _uiState.update { it.copy(error = "Pick the product made.") }
            return
        }
        if ((state.outputDraftQty.toDoubleOrNull() ?: 0.0) <= 0) {
            _uiState.update { it.copy(error = "Enter the good output quantity.") }
            return
        }
        if (state.outputDraftExpiry.isNotBlank() && state.outputDraftExpiry < state.date) {
            _uiState.update { it.copy(error = "The expiry cannot be before the production date.") }
            return
        }
        if (state.outputs.any { it.productId == product.id && it.localId != state.editingOutputId }) {
            _uiState.update { it.copy(error = "This product is already an output.") }
            return
        }
        val existing = state.outputs.firstOrNull { it.localId == state.editingOutputId }
        val line = EntryOutputLine(
            localId = state.editingOutputId ?: System.nanoTime(),
            productId = product.id,
            productName = product.label,
            unit = product.unit,
            qty = state.outputDraftQty,
            price = state.outputDraftPrice,
            share = existing?.share.orEmpty(),
            expiry = state.outputDraftExpiry,
        )
        _uiState.update {
            it.copy(
                outputs = if (it.editingOutputId != null) {
                    it.outputs.map { row -> if (row.localId == it.editingOutputId) line else row }
                } else {
                    it.outputs + line
                },
                outputDraft = null,
                outputDraftQty = "",
                outputDraftExpiry = "",
                outputDraftPrice = "",
                editingOutputId = null,
                error = null,
            )
        }
    }

    fun editOutput(id: Long) = _uiState.update { state ->
        val row = state.outputs.firstOrNull { it.localId == id } ?: return@update state
        state.copy(
            outputDraft = EntryProduct(row.productId, row.productName, row.unit, row.price.toDoubleOrNull()),
            outputDraftQty = row.qty,
            outputDraftPrice = row.price,
            outputDraftExpiry = row.expiry,
            editingOutputId = id,
        )
    }

    fun removeOutput(id: Long) = _uiState.update {
        it.copy(outputs = it.outputs.filterNot { row -> row.localId == id })
    }

    /** A typed share replaces the automatic one; the others take theirs. */
    fun onShare(id: Long, value: String) = _uiState.update { state ->
        val auto = state.autoShares
        state.copy(
            outputs = state.outputs.mapIndexed { index, row ->
                if (row.localId == id) {
                    row.copy(share = value)
                } else if (row.share.isBlank()) {
                    row.copy(share = String.format(Locale.US, "%.2f", auto.getOrElse(index) { 0.0 }))
                } else {
                    row
                }
            }
        )
    }

    fun clearShares() = _uiState.update { state ->
        state.copy(outputs = state.outputs.map { it.copy(share = "") })
    }

    // ---- Recipes --------------------------------------------------------

    fun chooseRecipe(id: String) {
        if (id.isBlank()) {
            _uiState.update {
                it.copy(recipeId = "", recipeQty = "", recipeName = "", recipeVersion = "", check = null)
            }
            return
        }
        viewModelScope.launch {
            when (val result = recipeRepository.fetchRecipe(id.toLongOrNull() ?: return@launch)) {
                is Resource.Success -> applyRecipe(
                    id = result.data.recipe.id?.toString().orEmpty(),
                    name = result.data.recipe.name,
                    version = result.data.recipe.version,
                    yieldQty = result.data.recipe.yieldQty.toDoubleOrNull() ?: 0.0,
                    outputProductId = result.data.recipe.outputProductId,
                    outputProductName = result.data.recipe.outputProductName,
                    unit = result.data.recipe.unit,
                    salesPrice = result.data.recipe.salesPrice,
                    lossBasis = result.data.recipe.lossBasis,
                    lossTolerancePct = result.data.recipe.lossTolerancePct.toDoubleOrNull() ?: 0.0,
                    lines = result.data.lines.map {
                        RecipeLineLocal(it.productId, it.productName, it.unit, it.qty.toDoubleOrNull() ?: 0.0)
                    },
                    target = result.data.recipe.yieldQty.toDoubleOrNull() ?: 0.0,
                )
                is Resource.Error -> _uiState.update { it.copy(error = result.message) }
                Resource.Loading -> Unit
            }
        }
    }

    fun onRecipeQty(v: String) = _uiState.update { it.copy(recipeQty = num(v)) }

    fun refillRecipe() {
        val state = _uiState.value
        val check = state.check
        if (check == null) {
            _uiState.update { it.copy(error = "Choose a recipe first, then press Fill.") }
            return
        }
        applyRecipe(
            id = check.id,
            name = check.name,
            version = check.version,
            yieldQty = check.yieldQty,
            outputProductId = check.outputProductId,
            outputProductName = check.outputProductName,
            unit = check.unit,
            salesPrice = check.salesPrice,
            lossBasis = check.lossBasis,
            lossTolerancePct = check.lossTolerancePct,
            lines = check.lines,
            target = state.recipeQty.toDoubleOrNull() ?: check.yieldQty,
        )
    }

    private fun applyRecipe(
        id: String,
        name: String,
        version: String,
        yieldQty: Double,
        outputProductId: String,
        outputProductName: String,
        unit: String,
        salesPrice: String,
        lossBasis: String,
        lossTolerancePct: Double,
        lines: List<RecipeLineLocal>,
        target: Double,
    ) {
        if (yieldQty <= 0) {
            _uiState.update { it.copy(error = "This recipe has no yield, so its quantities cannot be scaled.") }
            return
        }
        val targetQty = if (target > 0) round2(target) else round2(yieldQty)
        val factor = targetQty / yieldQty

        _uiState.update { state ->
            val existing = state.outputs.firstOrNull { it.productId == outputProductId }
            val outputs = if (existing != null) {
                state.outputs.map {
                    if (it.productId == outputProductId) it.copy(qty = plain(targetQty)) else it
                }
            } else {
                state.outputs + EntryOutputLine(
                    localId = System.nanoTime(),
                    productId = outputProductId,
                    productName = outputProductName,
                    unit = unit,
                    qty = plain(targetQty),
                    price = salesPrice,
                )
            }
            state.copy(
                recipeId = id,
                recipeQty = plain(targetQty),
                recipeName = name,
                recipeVersion = version,
                lossTolerance = plain(lossTolerancePct),
                lossBasis = lossBasis.ifBlank { state.lossBasis },
                inputs = lines.map {
                    EntryInputLine(
                        localId = System.nanoTime() + it.productId.hashCode(),
                        productId = it.productId,
                        productName = it.productName,
                        unit = it.unit,
                        qty = plain(round2(it.baseQty * factor)),
                    )
                },
                outputs = outputs,
                check = RecipeCheckLocal(
                    id = id,
                    name = name,
                    version = version,
                    yieldQty = yieldQty,
                    outputProductId = outputProductId,
                    outputProductName = outputProductName,
                    unit = unit,
                    salesPrice = salesPrice,
                    lossBasis = lossBasis,
                    lossTolerancePct = lossTolerancePct,
                    filledQty = targetQty,
                    lines = lines,
                ),
                message = "Filled from $name" + if (version.isNotBlank()) " v$version" else "",
                error = null,
            )
        }
        reloadStock()
    }

    // ---- Save / batches -------------------------------------------------

    fun save() {
        val state = _uiState.value
        if (!state.canWrite || state.isSaving) return
        if (state.recipeQtyChanged) {
            _uiState.update { it.copy(tab = 0, error = "The make quantity changed. Press Fill to apply it before saving.") }
            return
        }
        val problem = when {
            state.outputs.isEmpty() -> "Add the product this batch makes." to 1
            state.outputs.size > 1 && state.sharesTyped && kotlin.math.abs(state.shareTotal - 100) > 0.01 ->
                "The cost shares add up to ${String.format(Locale.US, "%.2f", state.shareTotal)}%, not 100%." to 1
            state.inputs.isEmpty() -> "Add at least one input." to 0
            else -> null
        }
        if (problem != null) {
            _uiState.update { it.copy(tab = problem.second, error = problem.first) }
            return
        }

        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val body = JsonObject().apply {
                addProperty("idempotency_key", System.currentTimeMillis().toString())
                addProperty("production_date", state.date)
                add("batch_ref", state.batchRef.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE)
                add("recipe_id", state.recipeId.takeIf { it.isNotBlank() }?.toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE)
                add("recipe_qty", state.recipeQty.takeIf { it.isNotBlank() }?.toDoubleOrNull()?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE)
                add("outputs", JsonArray().apply {
                    state.outputs.forEach { row ->
                        add(JsonObject().apply {
                            addProperty("product_id", row.productId.toLongOrNull() ?: 0L)
                            add("godown_id", JsonNull.INSTANCE)
                            addProperty("qty", row.qty.toDoubleOrNull() ?: 0.0)
                            add(
                                "share_pct",
                                if (state.sharesTyped && state.outputs.size > 1) {
                                    JsonPrimitive(row.share.toDoubleOrNull() ?: 0.0)
                                } else {
                                    JsonNull.INSTANCE
                                },
                            )
                            add("expiry_date", row.expiry.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE)
                        })
                    }
                })
                add("inputs", JsonArray().apply {
                    state.inputs.forEach { row ->
                        add(JsonObject().apply {
                            addProperty("product_id", row.productId.toLongOrNull() ?: 0L)
                            add("godown_id", JsonNull.INSTANCE)
                            addProperty("base_qty", row.qty.toDoubleOrNull() ?: 0.0)
                        })
                    }
                })
                add("loss", JsonObject().apply {
                    addProperty("basis", state.lossBasis)
                    addProperty("actual", state.lossActual.toDoubleOrNull() ?: 0.0)
                    addProperty("tolerance_pct", state.lossTolerance.toDoubleOrNull() ?: 0.0)
                    add("reason", state.lossReason.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE)
                })
                add("allocations", JsonArray().apply {
                    state.allocations.forEach { (sourceId, amount) ->
                        val value = amount.toDoubleOrNull() ?: 0.0
                        if (value > 0) {
                            add(JsonObject().apply {
                                addProperty("source_acc_detail_id", sourceId)
                                addProperty("amount", value)
                            })
                        }
                    }
                })
            }
            when (val result = repository.saveBatch(state.editingBatchId, body)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSaving = false, message = result.data) }
                    resetForm()
                    loadBatches()
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

    fun resetForm() = _uiState.update {
        it.copy(
            date = today.toApi(), batchRef = "", notes = "", inputs = emptyList(), outputs = emptyList(),
            lossActual = "", lossTolerance = "", lossBasis = "kg", lossReason = "",
            allocations = emptyMap(), recipeId = "", recipeQty = "", recipeName = "", recipeVersion = "",
            check = null, editingBatchId = null, editingBatchRef = "",
            inputDraft = null, inputDraftQty = "", editingInputId = null,
            outputDraft = null, outputDraftQty = "", outputDraftExpiry = "", outputDraftPrice = "",
            editingOutputId = null, error = null, tab = 0,
        )
    }

    fun openBatch(id: Long) {
        viewModelScope.launch {
            when (val result = repository.fetchBatchDetails(id)) {
                is Resource.Success -> {
                    val d = result.data
                    val typed = d.outputs.size > 1 && d.outputs.all { it.sharePct != null }
                    _uiState.update {
                        it.copy(
                            date = d.productionDate.ifBlank { today.toApi() },
                            batchRef = d.batchRef,
                            notes = d.notes,
                            outputs = d.outputs.map { row ->
                                EntryOutputLine(
                                    localId = System.nanoTime() + (row.productId ?: 0L),
                                    productId = row.productId?.toString().orEmpty(),
                                    productName = row.productName,
                                    unit = row.unit,
                                    qty = plain(row.qty),
                                    price = row.salesPrice?.let { p -> plain(p) }.orEmpty(),
                                    share = if (typed) plain(row.sharePct ?: 0.0) else "",
                                    expiry = row.expiryDate,
                                )
                            },
                            inputs = d.inputs.map { row ->
                                EntryInputLine(
                                    localId = System.nanoTime() + (row.productId ?: 0L),
                                    productId = row.productId?.toString().orEmpty(),
                                    productName = row.productName,
                                    unit = row.unit,
                                    qty = plain(row.baseQty),
                                )
                            },
                            lossActual = if (d.lossActual != 0.0) plain(d.lossActual) else "",
                            lossTolerance = if (d.lossTolerancePct != 0.0) plain(d.lossTolerancePct) else "",
                            lossBasis = d.lossBasis.ifBlank { "kg" },
                            lossReason = d.lossReason,
                            allocations = d.allocations.mapValues { plain(it.value) },
                            recipeId = d.recipe?.recipeId?.toString() ?: d.recipeId?.toString().orEmpty(),
                            recipeQty = d.recipeQty?.let { plain(it) }.orEmpty(),
                            recipeName = d.recipe?.name.orEmpty(),
                            recipeVersion = d.recipe?.version.orEmpty(),
                            check = d.recipe?.let { snap ->
                                RecipeCheckLocal(
                                    id = snap.recipeId?.toString().orEmpty(),
                                    name = snap.name,
                                    version = snap.version,
                                    yieldQty = snap.yieldQty,
                                    outputProductId = snap.outputProductId?.toString().orEmpty(),
                                    outputProductName = d.outputs.firstOrNull {
                                        it.productId == snap.outputProductId
                                    }?.productName.orEmpty(),
                                    unit = snap.unit,
                                    salesPrice = snap.salesPrice?.let { plain(it) }.orEmpty(),
                                    lossBasis = snap.lossBasis,
                                    lossTolerancePct = snap.lossTolerancePct,
                                    filledQty = d.recipeQty ?: snap.targetQty,
                                    lines = snap.lines.map {
                                        RecipeLineLocal(
                                            it.productId?.toString().orEmpty(),
                                            it.productName,
                                            it.unit,
                                            it.baseQty,
                                        )
                                    },
                                )
                            },
                            editingBatchId = d.id,
                            editingBatchRef = d.batchRef,
                            tab = 0,
                            error = null,
                            message = "Opened ${d.batchRef.ifBlank { "#${d.id}" }}",
                        )
                    }
                    reloadStock()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(error = result.message, sessionExpired = it.sessionExpired || result.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onAllocation(row: ProductionAllocatableRow, value: String) = _uiState.update {
        it.copy(allocations = it.allocations + (row.sourceAccDetailId to num(value)))
    }

    fun post(id: Long) = batchAction { repository.postBatch(id) }
    fun onReverseRequested(id: Long) = _uiState.update { it.copy(confirmReverseId = id) }
    fun onReverseDismissed() = _uiState.update { it.copy(confirmReverseId = null) }
    fun onDeleteRequested(id: Long) = _uiState.update { it.copy(confirmDeleteId = id) }
    fun onDeleteDismissed() = _uiState.update { it.copy(confirmDeleteId = null) }

    fun reverse() {
        val id = _uiState.value.confirmReverseId ?: return
        _uiState.update { it.copy(confirmReverseId = null) }
        batchAction { repository.reverseBatch(id) }
    }

    fun deleteDraft() {
        val id = _uiState.value.confirmDeleteId ?: return
        _uiState.update { it.copy(confirmDeleteId = null) }
        batchAction { repository.destroyBatch(id) }
    }

    private fun batchAction(block: suspend () -> Resource<String>) {
        viewModelScope.launch {
            when (val result = block()) {
                is Resource.Success -> {
                    _uiState.update { it.copy(message = result.data) }
                    loadBatches()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(error = result.message, sessionExpired = it.sessionExpired || result.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onErrorShown() = _uiState.update { it.copy(error = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    private fun num(value: String): String =
        value.filter { it.isDigit() || it == '.' }

    private fun plain(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    private fun round2(value: Double): Double = Math.round(value * 100.0) / 100.0

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val app = context.applicationContext
                val session = ServiceLocator.provideSessionManager(app)
                ProductionEntryViewModel(
                    repository = ServiceLocator.provideProductionRepository(app),
                    recipeRepository = ServiceLocator.provideRecipeRepository(app),
                    transactionDate = session.state.value.settings?.transactionDate,
                    canWrite = com.example.cashbookbd.session.Permissions.has(
                        session.permissions,
                        "production.create",
                    ),
                )
            }
        }
    }
}
