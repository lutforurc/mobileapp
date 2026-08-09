package com.example.cashbookbd.ui.realestate

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.ProjectCostRepository
import com.example.cashbookbd.data.repository.ProjectPurchaseLine
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.reports.model.SelectorOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectPurchaseUiState(
    val projects: List<SelectorOption> = emptyList(),
    val buildings: List<SelectorOption> = emptyList(),
    val isLoadingDdls: Boolean = false,
    val ddlError: String? = null,

    // Invoice header.
    val supplier: LedgerDropdownItem? = null,
    val invoiceNo: String = "",
    /** `YYYY-MM-DD`, or blank. */
    val invoiceDate: String = "",
    val vehicleNo: String = "",
    val notes: String = "",
    val discount: String = "",
    val paid: String = "",

    // The product-line form. Project and building survive an Add, as on the
    // expense form — most lines of one invoice go to the same place.
    val projectId: String = "",
    val projectName: String = "",
    val buildingId: String = "",
    val buildingName: String = "",
    val productId: String = "",
    val productName: String = "",
    val qty: String = "",
    val price: String = "",

    val rows: List<ProjectPurchaseLine> = emptyList(),
    val editingRowKey: String? = null,

    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val editingVrNo: String? = null,
    val editingMtmId: String? = null,

    val isSaving: Boolean = false,
    val actionMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    val total: Double
        get() = rows.sumOf { (it.qty.toDoubleOrNull() ?: 0.0) * (it.price.toDoubleOrNull() ?: 0.0) }

    /** What is left to pay after the discount; never below zero. */
    val due: Double get() = maxOf(0.0, total - (discount.toDoubleOrNull() ?: 0.0))

    /** Supplier 17 is the cash account: a cash purchase is paid in full. */
    val isCashSupplier: Boolean get() = supplier?.id == ProjectCostRepository.CASH_COA4_ID

    val isRowEditing: Boolean get() = editingRowKey != null
}

/**
 * Backs the Project Purchase form — the construction purchase invoice with
 * Building in place of Warehouse. Each product row carries its project and
 * building; the server groups the rows and writes one Purchase ledger line
 * per building, so the report's sums come off the voucher itself.
 */
class ProjectPurchaseViewModel(
    private val repository: ProjectCostRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectPurchaseUiState())
    val uiState: StateFlow<ProjectPurchaseUiState> = _uiState.asStateFlow()

    private var lineCounter = 0

    /** Last search's price per product id — picking a product pre-fills Price. */
    private var productPrices: Map<Int, Double?> = emptyMap()

    init {
        loadDdls()
    }

    fun loadDdls() {
        _uiState.update { it.copy(isLoadingDdls = true, ddlError = null) }
        viewModelScope.launch {
            when (val projects = repository.projectsDdl()) {
                is Resource.Success -> _uiState.update { it.copy(isLoadingDdls = false, projects = projects.data) }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoadingDdls = false,
                        ddlError = projects.message,
                        sessionExpired = it.sessionExpired || projects.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    // ---- Header ----

    fun onSupplierSelected(item: LedgerDropdownItem) = _uiState.update {
        it.copy(supplier = item, paid = if (item.id == ProjectCostRepository.CASH_COA4_ID) formatPlain(it.due) else it.paid)
    }

    fun onInvoiceNo(value: String) = _uiState.update { it.copy(invoiceNo = value) }
    fun onInvoiceDate(value: String) = _uiState.update { it.copy(invoiceDate = value) }
    fun onVehicleNo(value: String) = _uiState.update { it.copy(vehicleNo = value) }
    fun onNotes(value: String) = _uiState.update { it.copy(notes = value) }

    fun onDiscount(value: String) = _uiState.update {
        it.copy(discount = value).syncCashPaid()
    }

    fun onPaid(value: String) = _uiState.update { it.copy(paid = value) }
    fun onSearchQuery(value: String) = _uiState.update { it.copy(searchQuery = value) }

    // ---- Product line ----

    fun onProjectSelected(option: SelectorOption) {
        _uiState.update {
            it.copy(projectId = option.id, projectName = option.label, buildingId = "", buildingName = "", buildings = emptyList())
        }
        loadBuildings(option.id.toIntOrNull() ?: return)
    }

    private fun loadBuildings(projectId: Int) {
        viewModelScope.launch {
            when (val result = repository.buildingsDdl(projectId)) {
                is Resource.Success -> _uiState.update { state ->
                    if (state.projectId.toIntOrNull() == projectId) state.copy(buildings = result.data) else state
                }
                is Resource.Error -> _uiState.update {
                    it.copy(sessionExpired = it.sessionExpired || result.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onBuildingSelected(option: SelectorOption) = _uiState.update {
        it.copy(buildingId = option.id, buildingName = if (option.id.isBlank()) "" else option.label)
    }

    /** Product search for the picker; remembers each hit's last purchase rate. */
    suspend fun searchProducts(query: String): Resource<List<SelectorOption>> =
        when (val result = repository.searchProducts(query)) {
            is Resource.Success -> {
                productPrices = productPrices + result.data.associate { it.id to it.price }
                Resource.Success(result.data.map { SelectorOption(id = it.id.toString(), label = it.name) })
            }
            is Resource.Error -> Resource.Error(result.message, result.isUnauthorized)
            Resource.Loading -> Resource.Loading
        }

    fun onProductSelected(option: SelectorOption) = _uiState.update {
        val price = option.id.toIntOrNull()?.let { id -> productPrices[id] }
        it.copy(
            productId = option.id,
            productName = option.label,
            // The last known rate, overridable — the clerk knows better.
            price = price?.let(::formatPlain) ?: it.price,
        )
    }

    fun onQty(value: String) = _uiState.update { it.copy(qty = value) }
    fun onPrice(value: String) = _uiState.update { it.copy(price = value) }

    fun saveLine() {
        val state = _uiState.value
        val message = when {
            state.projectId.isBlank() -> "Choose the project this material is for."
            state.productId.isBlank() -> "Choose a product."
            (state.qty.trim().toDoubleOrNull() ?: 0.0) <= 0.0 -> "Enter a quantity greater than zero."
            else -> null
        }
        if (message != null) {
            _uiState.update { it.copy(actionMessage = message) }
            return
        }
        val line = ProjectPurchaseLine(
            key = state.editingRowKey ?: "new-${lineCounter++}",
            productId = state.productId.toIntOrNull() ?: return,
            productName = state.productName,
            qty = state.qty.trim(),
            price = state.price.trim().ifBlank { "0" },
            projectId = state.projectId.toIntOrNull() ?: return,
            projectName = state.projectName,
            buildingId = state.buildingId.toIntOrNull(),
            buildingName = state.buildingName,
        )
        _uiState.update {
            it.copy(
                rows = if (it.editingRowKey == null) {
                    it.rows + line
                } else {
                    it.rows.map { row -> if (row.key == it.editingRowKey) line else row }
                },
                editingRowKey = null,
                productId = "",
                productName = "",
                qty = "",
                price = "",
            ).syncCashPaid()
        }
    }

    fun editRow(key: String) {
        val row = _uiState.value.rows.firstOrNull { it.key == key } ?: return
        _uiState.update {
            it.copy(
                editingRowKey = key,
                projectId = row.projectId.toString(),
                projectName = row.projectName,
                buildingId = row.buildingId?.toString().orEmpty(),
                buildingName = row.buildingName,
                productId = row.productId.toString(),
                productName = row.productName,
                qty = row.qty,
                price = row.price,
            )
        }
        loadBuildings(row.projectId)
    }

    fun cancelRowEdit() = _uiState.update {
        it.copy(editingRowKey = null, productId = "", productName = "", qty = "", price = "")
    }

    fun removeRow(key: String) {
        val wasEditing = _uiState.value.editingRowKey == key
        _uiState.update { it.copy(rows = it.rows.filter { row -> row.key != key }).syncCashPaid() }
        if (wasEditing) cancelRowEdit()
    }

    /** Clears everything client-side; no server call. */
    fun resetAll() {
        productPrices = emptyMap()
        _uiState.update {
            ProjectPurchaseUiState(
                projects = it.projects,
                isLoadingDdls = it.isLoadingDdls,
            )
        }
    }

    // ---- Save / search ----

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return
        val message = when {
            state.supplier == null -> "Choose a supplier."
            state.rows.isEmpty() -> "Add at least one product before saving."
            state.isRowEditing -> "Finish the line you are editing first — Save Line, or Cancel."
            else -> null
        }
        if (message != null) {
            _uiState.update { it.copy(actionMessage = message) }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = repository.purchaseSave(
                supplier = state.supplier!!.id,
                invoiceNo = state.invoiceNo,
                invoiceDate = state.invoiceDate,
                vehicleNo = state.vehicleNo,
                notes = state.notes,
                discount = state.discount.toDoubleOrNull() ?: 0.0,
                paid = state.paid.toDoubleOrNull() ?: 0.0,
                products = state.rows,
                mtmId = state.editingMtmId,
            )
            when (result) {
                is Resource.Success -> {
                    val kept = _uiState.value
                    productPrices = emptyMap()
                    _uiState.update {
                        ProjectPurchaseUiState(
                            projects = kept.projects,
                            actionMessage = result.data,
                        )
                    }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        actionMessage = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onSearch() {
        val vrNo = _uiState.value.searchQuery.trim()
        if (vrNo.isEmpty()) {
            _uiState.update { it.copy(actionMessage = "Type a voucher number to search.") }
            return
        }
        if (_uiState.value.isSearching) return
        _uiState.update { it.copy(isSearching = true) }
        viewModelScope.launch {
            when (val result = repository.purchaseEdit(vrNo)) {
                is Resource.Success -> {
                    val voucher = result.data
                    val projectNames = _uiState.value.projects
                        .mapNotNull { o -> o.id.toIntOrNull()?.let { it to o.label } }.toMap()
                    val buildingNames = buildingNames(
                        voucher.products.filter { it.buildingId != null }.map { it.projectId },
                    )
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            supplier = LedgerDropdownItem(voucher.supplier, voucher.supplierName, null),
                            invoiceNo = voucher.invoiceNo,
                            invoiceDate = voucher.invoiceDate,
                            vehicleNo = voucher.vehicleNo,
                            notes = voucher.notes,
                            discount = voucher.discount,
                            paid = voucher.paid,
                            rows = voucher.products.map { row ->
                                row.copy(
                                    projectName = projectNames[row.projectId].orEmpty(),
                                    buildingName = row.buildingId
                                        ?.let { id -> buildingNames[row.projectId]?.get(id) }.orEmpty(),
                                )
                            },
                            editingVrNo = voucher.vrNo,
                            editingMtmId = voucher.mtmId,
                            editingRowKey = null,
                            actionMessage = "Voucher ${voucher.vrNo} loaded",
                        )
                    }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSearching = false,
                        actionMessage = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    private suspend fun buildingNames(projectIds: List<Int>): Map<Int, Map<Int, String>> {
        val result = mutableMapOf<Int, Map<Int, String>>()
        projectIds.distinct().forEach { projectId ->
            val ddl = repository.buildingsDdl(projectId)
            if (ddl is Resource.Success) {
                result[projectId] = ddl.data
                    .mapNotNull { o -> o.id.toIntOrNull()?.let { it to o.label } }
                    .toMap()
            }
        }
        return result
    }

    fun onActionMessageShown() = _uiState.update { it.copy(actionMessage = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    /** A cash purchase is paid in full — the Paid box follows the due figure. */
    private fun ProjectPurchaseUiState.syncCashPaid(): ProjectPurchaseUiState =
        if (isCashSupplier) copy(paid = formatPlain(due)) else this

    companion object {
        /** A plain decimal without the ".0" tail; large figures stay plain too. */
        private fun formatPlain(value: Double): String =
            java.math.BigDecimal(value.toString()).stripTrailingZeros().toPlainString()

        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                ProjectPurchaseViewModel(
                    repository = ServiceLocator.provideProjectCostRepository(context.applicationContext),
                )
            }
        }
    }
}
