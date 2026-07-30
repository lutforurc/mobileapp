package com.example.cashbookbd.ui.orders

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.OrderReportsRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.reports.model.SelectorOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the Average Price report: the protected-branch dropdown (defaulting
 * to the user's own — the first the DDL returns), the order type-ahead, the
 * Purchase/Sales report-type select, and `POST invoice/order/avg-price` via
 * [OrderReportsRepository].
 */
class AveragePriceViewModel(
    private val reportRepository: ReportRepository,
    private val orderReportsRepository: OrderReportsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AveragePriceUiState())
    val uiState: StateFlow<AveragePriceUiState> = _uiState.asStateFlow()

    init {
        loadBranches()
    }

    /** The user's protected branches; the first is their own, so it's the default. */
    fun loadBranches() {
        _uiState.update { it.copy(isBranchesLoading = true, branchesError = null) }
        viewModelScope.launch {
            when (val result = reportRepository.getBranches()) {
                is Resource.Success -> _uiState.update { state ->
                    val options = result.data.branches
                        .map { SelectorOption(id = it.id.toString(), label = it.name) }
                    state.copy(
                        isBranchesLoading = false,
                        branches = options,
                        selectedBranch = state.selectedBranch ?: options.firstOrNull(),
                    )
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

    fun onBranchSelected(option: SelectorOption) =
        _uiState.update { it.copy(selectedBranch = option) }

    fun onOrderSelected(option: SelectorOption) =
        _uiState.update { it.copy(selectedOrder = option) }

    fun onReportTypeSelected(option: SelectorOption) =
        _uiState.update { it.copy(selectedReportType = option) }

    /** The order type-ahead; the dropdown owns its own debounce. */
    suspend fun searchOrders(query: String): Resource<List<SelectorOption>> =
        orderReportsRepository.searchOrders(query)

    fun reset() {
        _uiState.update { state ->
            state.copy(
                selectedBranch = state.branches.firstOrNull(),
                selectedOrder = null,
                selectedReportType = AveragePriceUiState.REPORT_TYPES.first(),
                report = null,
                reportError = null,
            )
        }
    }

    fun runReport() {
        val state = _uiState.value
        val branch = state.selectedBranch ?: return
        val order = state.selectedOrder ?: return
        if (state.isReportLoading) return

        _uiState.update { it.copy(isReportLoading = true, reportError = null) }
        viewModelScope.launch {
            val result = orderReportsRepository.fetchAveragePrice(
                branchId = branch.id.toLongOrNull() ?: 0L,
                orderId = order.id.toIntOrNull() ?: 0,
                reportType = state.selectedReportType.id.toIntOrNull() ?: 1,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isReportLoading = false, report = result.data, reportError = null)
                }

                is Resource.Error -> _uiState.update {
                    it.copy(
                        isReportLoading = false,
                        report = null,
                        reportError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
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
                val appContext = context.applicationContext
                AveragePriceViewModel(
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                    orderReportsRepository = ServiceLocator.provideOrderReportsRepository(appContext),
                )
            }
        }
    }
}
