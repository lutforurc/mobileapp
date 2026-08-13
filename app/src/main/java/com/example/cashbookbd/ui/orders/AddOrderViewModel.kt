package com.example.cashbookbd.ui.orders

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.LedgerRepository
import com.example.cashbookbd.data.repository.OrderHeader
import com.example.cashbookbd.data.repository.OrderProductLine
import com.example.cashbookbd.data.repository.OrderRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.data.repository.SelectorRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.report.ReportSelectorSource
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the Create Order form: the header pickers, the single-product fields
 * or the multi-product pending batch (per the branch's `multi_product_order`
 * setting), and the store POST via [OrderRepository]. A successful save hands
 * the backend's confirmation to [AddOrderUiState.savedMessage] — the screen
 * passes it to the Orders list and pops back.
 */
class AddOrderViewModel(
    isMultiProduct: Boolean,
    private val orderRepository: OrderRepository,
    private val reportRepository: ReportRepository,
    private val ledgerRepository: LedgerRepository,
    private val selectorRepository: SelectorRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddOrderUiState(isMultiProduct = isMultiProduct))
    val uiState: StateFlow<AddOrderUiState> = _uiState.asStateFlow()

    init {
        loadBranches()
    }

    /**
     * The user's protected branches; the first is their own, so it's the
     * default. The DDL's business transaction date becomes the default Order
     * Date (Last Delivery follows at +30 days until the user picks their own).
     */
    private fun loadBranches() {
        _uiState.update { it.copy(isBranchesLoading = true, branchesError = null) }
        viewModelScope.launch {
            when (val result = reportRepository.getBranches()) {
                is Resource.Success -> {
                    val options = result.data.branches
                        .map { SelectorOption(id = it.id.toString(), label = it.name) }
                    _uiState.update { state ->
                        val orderDate = result.data.transactionDate ?: state.orderDate
                        state.copy(
                            isBranchesLoading = false,
                            branches = options,
                            selectedBranch = state.selectedBranch ?: options.firstOrNull(),
                            orderDate = orderDate,
                            lastDeliveryDate = if (state.lastDeliveryTouched) {
                                state.lastDeliveryDate
                            } else {
                                orderDate.plusDays(DEFAULT_DELIVERY_DAYS)
                            },
                        )
                    }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isBranchesLoading = false,
                        branchesError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    // ---- Header ----

    fun onBranchSelected(option: SelectorOption) = _uiState.update { it.copy(selectedBranch = option) }

    /** Changing the type invalidates the reference order (it must be the opposite type). */
    fun onOrderTypeSelected(option: SelectorOption) =
        _uiState.update { it.copy(orderType = option, referenceOrder = null) }

    fun onOrderForSelected(item: LedgerDropdownItem) = _uiState.update { it.copy(orderFor = item) }

    fun onOrderNumberChange(value: String) = _uiState.update { it.copy(orderNumber = value) }

    fun onDeliveryLocationChange(value: String) = _uiState.update { it.copy(deliveryLocation = value) }

    /** Last Delivery keeps tracking Order Date + 30 until the user sets it. */
    fun onOrderDateChange(date: SimpleDate) = _uiState.update {
        it.copy(
            orderDate = date,
            lastDeliveryDate = if (it.lastDeliveryTouched) {
                it.lastDeliveryDate
            } else {
                date.plusDays(DEFAULT_DELIVERY_DAYS)
            },
        )
    }

    fun onLastDeliveryDateChange(date: SimpleDate) =
        _uiState.update { it.copy(lastDeliveryDate = date, lastDeliveryTouched = true) }

    fun onReferenceOrderSelected(option: SelectorOption) = _uiState.update { it.copy(referenceOrder = option) }

    fun onNotesChange(value: String) = _uiState.update { it.copy(notes = value) }

    fun onStatusSelected(option: SelectorOption) = _uiState.update { it.copy(status = option) }

    // ---- Searches (each dropdown owns its own debounce) ----

    suspend fun searchLedgers(query: String): Resource<List<LedgerDropdownItem>> =
        ledgerRepository.searchLedgers(query, acType = "")

    suspend fun searchProducts(query: String): Resource<List<SelectorOption>> =
        selectorRepository.fetch(ReportSelectorSource.PRODUCT, query)

    /** Searches orders of the OPPOSITE type; empty for a Stock order. */
    suspend fun searchReferenceOrders(query: String): Resource<List<SelectorOption>> {
        val oppositeType = _uiState.value.oppositeOrderType
        if (oppositeType.isEmpty()) return Resource.Success(emptyList())
        return orderRepository.searchReferenceOrders(query, oppositeType)
    }

    // ---- Single-product fields ----

    fun onProductSelected(option: SelectorOption) = _uiState.update { it.copy(product = option) }

    fun onOrderRateChange(value: String) =
        _uiState.update { it.copy(orderRate = value.decimalOnly()).withContractAuto() }

    fun onTotalOrderChange(value: String) =
        _uiState.update { it.copy(totalOrder = value.decimalOnly()).withContractAuto() }

    fun onContractQtyChange(value: String) = _uiState.update { it.copy(contractQty = value.decimalOnly()) }

    /**
     * The web's auto-calculation: contract order qty = total qty × rate,
     * refreshed whenever either side changes (a typed override lasts only until
     * then, exactly like the web form). Blank while either side is blank, so an
     * order raised before the figure is agreed stays uncommitted.
     */
    private fun AddOrderUiState.withContractAuto(): AddOrderUiState {
        val product = (totalOrder.toDoubleOrNull() ?: 0.0) * (orderRate.toDoubleOrNull() ?: 0.0)
        return copy(
            contractQty = when {
                product <= 0.0 -> ""
                product % 1.0 == 0.0 -> product.toLong().toString()
                else -> product.toString()
            }
        )
    }

    // ---- Multi-product line entry ----

    fun onLineProductSelected(option: SelectorOption) = _uiState.update { it.copy(lineProduct = option) }

    fun onLineQtyChange(value: String) = _uiState.update { it.copy(lineQty = value.decimalOnly()) }

    fun onLineRateChange(value: String) = _uiState.update { it.copy(lineRate = value.decimalOnly()) }

    fun onLineContractQtyChange(value: String) =
        _uiState.update { it.copy(lineContractQty = value.decimalOnly()) }

    /**
     * Adds the current entry as a pending line and clears the entry fields.
     * Zero/blank quantity is allowed (an order may precede the agreed figure);
     * the same product twice is not, mirroring the web and the server.
     */
    fun addLine() {
        val state = _uiState.value
        val product = state.lineProduct ?: return
        if (state.lines.any { it.productId == product.id }) {
            _uiState.update {
                it.copy(message = "The same product cannot be added twice on one order.", isError = true)
            }
            return
        }
        _uiState.update {
            it.copy(
                lines = it.lines + OrderProductLine(
                    productId = product.id,
                    productName = product.label,
                    qty = it.lineQty.trim(),
                    rate = it.lineRate.trim(),
                    contractQty = it.lineContractQty.trim(),
                ),
                lineProduct = null,
                lineQty = "",
                lineRate = "",
                lineContractQty = "",
                message = null,
                isError = false,
            )
        }
    }

    /** Loads a pending line back into the entry (and removes it from the batch). */
    fun editLine(index: Int) {
        _uiState.update { state ->
            val line = state.lines.getOrNull(index) ?: return@update state
            state.copy(
                lineProduct = SelectorOption(id = line.productId, label = line.productName),
                lineQty = line.qty,
                lineRate = line.rate,
                lineContractQty = line.contractQty,
                lines = state.lines.filterIndexed { i, _ -> i != index },
            )
        }
    }

    fun removeLine(index: Int) {
        _uiState.update {
            if (index !in it.lines.indices) it
            else it.copy(lines = it.lines.filterIndexed { i, _ -> i != index })
        }
    }

    // ---- Save ----

    /** ⚠️ Posts a REAL order — guarded by [AddOrderUiState.canSave], never re-fired automatically. */
    fun submit() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(isSubmitting = true, message = null, isError = false) }
        viewModelScope.launch {
            val multi = state.isMultiProduct
            val header = OrderHeader(
                branchId = state.selectedBranch?.id.orEmpty(),
                orderFor = state.orderFor?.id?.toString().orEmpty(),
                // In multi mode the products travel in items[]; the flat
                // product fields go as empty strings, like the web.
                productId = if (multi) "" else state.product?.id.orEmpty(),
                orderNumber = state.orderNumber.trim(),
                refOrderId = state.referenceOrder?.id,
                deliveryLocation = state.deliveryLocation.trim(),
                orderDate = state.orderDate.toApi(),
                lastDeliveryDate = state.lastDeliveryDate.toApi(),
                orderRate = if (multi) "" else state.orderRate.trim(),
                contractOrderQty = if (multi) null else state.contractQty.trim().ifEmpty { null },
                totalOrder = if (multi) "" else state.totalOrder.trim(),
                orderType = state.orderType.id,
                status = state.status.id,
                notes = state.notes.trim(),
            )
            val result = orderRepository.submitOrder(
                header = header,
                items = if (multi) state.lines else null,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isSubmitting = false, savedMessage = result.data)
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
    }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    private fun String.decimalOnly(): String =
        filterIndexed { i, c -> c.isDigit() || (c == '.' && !take(i).contains('.')) }

    companion object {
        /** The default Last Delivery Date offset: order date + 30 days. */
        private const val DEFAULT_DELIVERY_DAYS = 30

        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                AddOrderViewModel(
                    // The branch's multi_product_order meta, loaded with the
                    // session settings.
                    isMultiProduct = ServiceLocator.provideSessionManager(appContext)
                        .state.value.settings?.multiProductOrder == true,
                    orderRepository = ServiceLocator.provideOrderRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                    ledgerRepository = ServiceLocator.provideLedgerRepository(appContext),
                    selectorRepository = ServiceLocator.provideSelectorRepository(appContext),
                )
            }
        }
    }
}
