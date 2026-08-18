package com.example.cashbookbd.ui.inventory

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.BranchTransferHeader
import com.example.cashbookbd.data.repository.BranchTransferLine
import com.example.cashbookbd.data.repository.BranchTransferOutcome
import com.example.cashbookbd.data.repository.InventoryMovementRepository
import com.example.cashbookbd.data.repository.InventoryProduct
import com.example.cashbookbd.data.repository.MaterialIssueLine
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.data.repository.SelectorRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.inventory.InventoryFormKind
import com.example.cashbookbd.inventory.InventoryForms
import com.example.cashbookbd.report.ReportSelectorSource
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives one inventory-movement form (resolved from [formKey]): the transfer/
 * issue header, a running batch of product lines, and the store POST via
 * [InventoryMovementRepository]. The Branch Issue variant also owns the
 * stock-shortage confirm → `allow_negative` re-post flow.
 */
class InventoryMovementViewModel(
    formKey: String,
    private val inventoryMovementRepository: InventoryMovementRepository,
    private val reportRepository: ReportRepository,
    private val selectorRepository: SelectorRepository,
) : ViewModel() {

    private val spec = InventoryForms.byKey(formKey)

    /** Last product search results, so a picked option maps back to its unit/rate. */
    private var productCache: Map<String, InventoryProduct> = emptyMap()

    private val _uiState = MutableStateFlow(
        InventoryMovementUiState(
            title = spec?.title ?: "Inventory",
            kind = spec?.kind ?: InventoryFormKind.BRANCH_ISSUE,
            isSupported = spec != null,
            dateLabel = spec?.dateLabel ?: "Date",
            fromLabel = spec?.fromLabel?.ifBlank { null } ?: "From Branch",
            toLabel = spec?.toLabel?.ifBlank { null } ?: "To Branch",
        )
    )
    val uiState: StateFlow<InventoryMovementUiState> = _uiState.asStateFlow()

    init {
        when (spec?.kind) {
            InventoryFormKind.BRANCH_ISSUE, InventoryFormKind.BRANCH_RECEIVE -> loadBranches()
            InventoryFormKind.MATERIAL_ISSUE -> {
                loadWarehouses()
                loadProjects()
            }
            null -> Unit
        }
    }

    /**
     * Loads both branch lists: the user's protected branches (this side of the
     * transfer) and every branch (the other side). Issue also takes its default
     * Challan Date from the protected DDL's business transaction date.
     */
    private fun loadBranches() {
        val kind = spec?.kind ?: return
        _uiState.update { it.copy(isBranchesLoading = true, branchesError = null) }
        viewModelScope.launch {
            // Own company only: a transfer between two companies' branches is
            // not a transfer, and offering one is how it gets posted by mistake.
            val protectedResult = reportRepository.getBranches(ownCompany = true)
            if (protectedResult is Resource.Error) {
                _uiState.update {
                    it.copy(
                        isBranchesLoading = false,
                        branchesError = protectedResult.message,
                        sessionExpired = it.sessionExpired || protectedResult.isUnauthorized,
                    )
                }
                return@launch
            }
            val allResult = inventoryMovementRepository.getAllBranches()
            if (allResult is Resource.Error) {
                _uiState.update {
                    it.copy(
                        isBranchesLoading = false,
                        branchesError = allResult.message,
                        sessionExpired = it.sessionExpired || allResult.isUnauthorized,
                    )
                }
                return@launch
            }

            val branchList = (protectedResult as Resource.Success).data
            val protectedOptions = branchList.branches
                .map { SelectorOption(id = it.id.toString(), label = it.name) }
            val allOptions = (allResult as Resource.Success).data
                .map { SelectorOption(id = it.id.toString(), label = it.name) }

            _uiState.update { state ->
                when (kind) {
                    InventoryFormKind.BRANCH_RECEIVE -> {
                        // Receive Branch = protected; From = Self Branch + every branch.
                        val fromOptions = listOf(SELF_BRANCH_OPTION) + allOptions
                        state.copy(
                            isBranchesLoading = false,
                            fromOptions = fromOptions,
                            toOptions = protectedOptions,
                            fromBranch = state.fromBranch ?: fromOptions.firstOrNull(),
                            toBranch = state.toBranch ?: protectedOptions.firstOrNull(),
                        )
                    }
                    else -> {
                        // Issue: From = protected; To = every branch, defaulting to
                        // the first that isn't the From side.
                        val from = state.fromBranch ?: protectedOptions.firstOrNull()
                        state.copy(
                            isBranchesLoading = false,
                            fromOptions = protectedOptions,
                            toOptions = allOptions,
                            fromBranch = from,
                            toBranch = state.toBranch
                                ?: allOptions.firstOrNull { it.id != from?.id }
                                ?: allOptions.firstOrNull(),
                            // The backend's current business date is the default
                            // Challan Date, like the web form.
                            date = branchList.transactionDate ?: state.date,
                        )
                    }
                }
            }
        }
    }

    private fun loadWarehouses() {
        viewModelScope.launch {
            val result = selectorRepository.fetch(ReportSelectorSource.WAREHOUSE)
            if (result is Resource.Success) {
                // Prepend "Not Applicable" (empty id) so the optional warehouse
                // can stay/become unset, as on the web.
                val options = listOf(SelectorOption(id = "", label = "Not Applicable")) + result.data
                _uiState.update { it.copy(warehouses = options) }
            }
        }
    }

    private fun loadProjects() {
        viewModelScope.launch {
            when (val result = inventoryMovementRepository.getProjects()) {
                is Resource.Success -> _uiState.update { it.copy(projects = result.data) }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        message = result.message,
                        isError = true,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    // ---- Header ----

    fun onDateChange(date: SimpleDate) = _uiState.update { it.copy(date = date) }

    fun onFromBranchSelected(option: SelectorOption) = _uiState.update { it.copy(fromBranch = option) }

    fun onToBranchSelected(option: SelectorOption) = _uiState.update { it.copy(toBranch = option) }

    fun onChallanNumberChange(value: String) = _uiState.update { it.copy(challanNumber = value) }

    fun onReceiverNameChange(value: String) = _uiState.update { it.copy(receiverName = value) }

    /** Digits only, capped at the validator's 32-character limit. */
    fun onReceiverMobileChange(value: String) =
        _uiState.update { it.copy(receiverMobile = value.filter(Char::isDigit).take(32)) }

    fun onDriverNameChange(value: String) = _uiState.update { it.copy(driverName = value) }
    fun onDriverMobileChange(value: String) = _uiState.update { it.copy(driverMobile = value) }

    fun onNoteChange(value: String) = _uiState.update { it.copy(note = value) }

    fun onWarehouseSelected(option: SelectorOption) = _uiState.update { it.copy(selectedWarehouse = option) }

    fun onProjectSelected(option: SelectorOption) = _uiState.update { it.copy(selectedProject = option) }

    fun onReceivedByChange(value: String) = _uiState.update { it.copy(receivedBy = value) }

    // ---- Line entry ----

    /** Product search that caches the full rows so a pick can read unit/rate. */
    suspend fun searchProducts(query: String): Resource<List<SelectorOption>> =
        when (val result = inventoryMovementRepository.searchProducts(query)) {
            is Resource.Success -> {
                productCache = result.data.associateBy { it.id }
                Resource.Success(
                    result.data.map { SelectorOption(id = it.id, label = it.name, sublabel = it.unit) }
                )
            }
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }

    fun onProductSelected(option: SelectorOption) {
        val product = productCache[option.id] ?: return
        _uiState.update { it.copy(product = product) }
    }

    fun onQtyChange(value: String) = _uiState.update { it.copy(qty = value.decimalOnly()) }

    fun onDamagedQtyChange(value: String) = _uiState.update { it.copy(damagedQty = value.decimalOnly()) }

    fun onShortQtyChange(value: String) = _uiState.update { it.copy(shortQty = value.decimalOnly()) }

    fun onBuildingChange(value: String) = _uiState.update { it.copy(building = value) }

    fun onWorkItemChange(value: String) = _uiState.update { it.copy(workItem = value) }

    fun onLineNoteChange(value: String) = _uiState.update { it.copy(lineNote = value) }

    /** Adds the current entry as a pending line and clears the entry fields. */
    fun addLine() {
        val state = _uiState.value
        val product = state.product ?: return
        val qty = state.qty.toDoubleOrNull() ?: return
        if (qty <= 0) return
        _uiState.update {
            if (it.isMaterial) {
                it.copy(
                    materialLines = it.materialLines + MaterialIssueLine(
                        product = product,
                        qty = qty,
                        building = it.building.trim(),
                        workItem = it.workItem.trim(),
                        note = it.lineNote.trim(),
                    ),
                    product = null,
                    qty = "",
                    building = "",
                    workItem = "",
                    lineNote = "",
                )
            } else {
                it.copy(
                    transferLines = it.transferLines + BranchTransferLine(
                        product = product,
                        qty = qty,
                        damagedQty = it.damagedQty.toDoubleOrNull() ?: 0.0,
                        shortQty = it.shortQty.toDoubleOrNull() ?: 0.0,
                    ),
                    product = null,
                    qty = "",
                    damagedQty = "",
                    shortQty = "",
                )
            }
        }
    }

    /** Loads a pending line back into the entry (and removes it from the batch). */
    fun editLine(index: Int) {
        _uiState.update { state ->
            if (state.isMaterial) {
                val line = state.materialLines.getOrNull(index) ?: return@update state
                productCache = productCache + (line.product.id to line.product)
                state.copy(
                    product = line.product,
                    qty = line.qty.toPlainAmount(),
                    building = line.building,
                    workItem = line.workItem,
                    lineNote = line.note,
                    materialLines = state.materialLines.filterIndexed { i, _ -> i != index },
                )
            } else {
                val line = state.transferLines.getOrNull(index) ?: return@update state
                productCache = productCache + (line.product.id to line.product)
                state.copy(
                    product = line.product,
                    qty = line.qty.toPlainAmount(),
                    damagedQty = if (line.damagedQty > 0.0) line.damagedQty.toPlainAmount() else "",
                    shortQty = if (line.shortQty > 0.0) line.shortQty.toPlainAmount() else "",
                    transferLines = state.transferLines.filterIndexed { i, _ -> i != index },
                )
            }
        }
    }

    fun removeLine(index: Int) {
        _uiState.update {
            if (it.isMaterial) {
                if (index !in it.materialLines.indices) it
                else it.copy(materialLines = it.materialLines.filterIndexed { i, _ -> i != index })
            } else {
                if (index !in it.transferLines.indices) it
                else it.copy(transferLines = it.transferLines.filterIndexed { i, _ -> i != index })
            }
        }
    }

    // ---- Save ----

    fun submit() = doSubmit(allowNegative = false)

    /** "Transfer anyway" after a stock shortage: re-posts with `allow_negative`. */
    fun confirmShortage() {
        _uiState.update { it.copy(shortages = null) }
        doSubmit(allowNegative = true)
    }

    fun dismissShortage() = _uiState.update { it.copy(shortages = null) }

    private fun doSubmit(allowNegative: Boolean) {
        val currentSpec = spec ?: return
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(isSubmitting = true, message = null, isError = false) }
        viewModelScope.launch {
            if (state.isMaterial) {
                submitMaterialIssue(currentSpec.endpoint, state)
            } else {
                submitBranchTransfer(currentSpec.endpoint, state, allowNegative)
            }
        }
    }

    private suspend fun submitBranchTransfer(
        endpoint: String,
        state: InventoryMovementUiState,
        allowNegative: Boolean,
    ) {
        val toBranchId = state.toBranch?.id?.toLongOrNull()
        if (toBranchId == null) {
            _uiState.update { it.copy(isSubmitting = false, message = "Select a branch.", isError = true) }
            return
        }
        val result = inventoryMovementRepository.submitBranchTransfer(
            endpoint = endpoint,
            header = BranchTransferHeader(
                // "Self Branch" has an empty id → null from_branch_id.
                fromBranchId = state.fromBranch?.id?.toLongOrNull(),
                toBranchId = toBranchId,
                challanNumber = state.challanNumber.trim(),
                challanDate = state.date.toApi(),
                receiverName = state.receiverName.trim(),
                receiverMobile = state.receiverMobile.trim(),
                note = state.note.trim(),
                driverName = state.driverName.trim(),
                driverMobile = state.driverMobile.trim(),
            ),
            lines = state.transferLines,
            allowNegative = allowNegative,
        )
        when (result) {
            is Resource.Success -> when (val outcome = result.data) {
                is BranchTransferOutcome.Saved -> _uiState.update {
                    // The batch clears; the header selections stay for the next one.
                    it.copy(
                        isSubmitting = false,
                        message = outcome.vrNo.takeIf { vr -> vr.isNotBlank() }
                            ?.let { vr -> "Voucher: $vr" }
                            ?: outcome.message
                            ?: "Transfer saved successfully.",
                        isError = false,
                        transferLines = emptyList(),
                        product = null,
                        qty = "",
                        damagedQty = "",
                        shortQty = "",
                    )
                }
                is BranchTransferOutcome.StockShortage -> _uiState.update {
                    it.copy(isSubmitting = false, shortages = outcome.warning)
                }
            }
            is Resource.Error -> _uiState.update {
                it.copy(
                    isSubmitting = false,
                    message = result.message,
                    isError = true,
                    sessionExpired = it.sessionExpired || result.isUnauthorized,
                )
            }
            Resource.Loading -> Unit
        }
    }

    private suspend fun submitMaterialIssue(endpoint: String, state: InventoryMovementUiState) {
        val projectId = state.selectedProject?.id?.toLongOrNull()
        if (projectId == null) {
            _uiState.update { it.copy(isSubmitting = false, message = "Select a project.", isError = true) }
            return
        }
        val result = inventoryMovementRepository.submitMaterialIssue(
            endpoint = endpoint,
            issueDate = state.date.toApi(),
            fromWarehouseId = state.selectedWarehouse?.id?.toLongOrNull(),
            projectId = projectId,
            receivedBy = state.receivedBy.trim(),
            note = state.note.trim(),
            lines = state.materialLines,
        )
        when (result) {
            is Resource.Success -> _uiState.update {
                it.copy(
                    isSubmitting = false,
                    message = result.data,
                    isError = false,
                    materialLines = emptyList(),
                    product = null,
                    qty = "",
                    building = "",
                    workItem = "",
                    lineNote = "",
                )
            }
            is Resource.Error -> _uiState.update {
                it.copy(
                    isSubmitting = false,
                    message = result.message,
                    isError = true,
                    sessionExpired = it.sessionExpired || result.isUnauthorized,
                )
            }
            Resource.Loading -> Unit
        }
    }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    private fun String.decimalOnly(): String =
        filterIndexed { i, c -> c.isDigit() || (c == '.' && !take(i).contains('.')) }

    /** "12.0" → "12", "12.5" stays — for refilling the qty fields on edit. */
    private fun Double.toPlainAmount(): String =
        if (this % 1.0 == 0.0) toLong().toString() else toString()

    companion object {
        fun provideFactory(context: Context, formKey: String) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                InventoryMovementViewModel(
                    formKey = formKey,
                    inventoryMovementRepository = ServiceLocator.provideInventoryMovementRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                    selectorRepository = ServiceLocator.provideSelectorRepository(appContext),
                )
            }
        }
    }
}
