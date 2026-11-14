package com.example.cashbookbd.ui.production

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.ProductionRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.reports.model.SimpleDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Drives Production Register: the branch's month-to-date window and its batches. */
class ProductionRegisterViewModel(
    private val productionRepository: ProductionRepository,
    transactionDate: String?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductionRegisterUiState())
    val uiState: StateFlow<ProductionRegisterUiState> = _uiState.asStateFlow()

    private var appliedFrom: String = ""
    private var appliedTo: String = ""

    init {
        val today = SimpleDate.fromDisplay(transactionDate) ?: SimpleDate.today()
        _uiState.update {
            it.copy(
                // The web opens on the month so far (branchDate.ts).
                fromDate = today.copy(day = 1),
                toDate = today,
            )
        }
        run()
    }

    fun onFrom(date: SimpleDate) = _uiState.update { it.copy(fromDate = date) }

    fun onTo(date: SimpleDate) = _uiState.update { it.copy(toDate = date) }

    fun run() {
        val state = _uiState.value
        val from = state.fromDate ?: return
        val to = state.toDate ?: return
        if (state.isLoading) return
        if (to.toApi() < from.toApi()) {
            _uiState.update { it.copy(error = "To date cannot be earlier than From date.") }
            return
        }

        appliedFrom = from.toApi()
        appliedTo = to.toApi()
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            when (val result = productionRepository.fetchRegister(appliedFrom, appliedTo)) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        report = result.data,
                        appliedRange = "${from.toDisplay()} — ${to.toDisplay()}",
                    )
                }

                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.message,
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
                val app = context.applicationContext
                ProductionRegisterViewModel(
                    productionRepository = ServiceLocator.provideProductionRepository(app),
                    transactionDate = ServiceLocator.provideSessionManager(app)
                        .state.value.settings?.transactionDate,
                )
            }
        }
    }
}

/** Loads one batch's costing detail for the register's Detail action. */
class ProductionCostingViewModel(
    private val productionRepository: ProductionRepository,
    private val batchId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductionCostingUiState())
    val uiState: StateFlow<ProductionCostingUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = productionRepository.fetchCosting(batchId)) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, error = null, detail = result.data)
                }

                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }

                Resource.Loading -> Unit
            }
        }
    }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context, batchId: Long) = viewModelFactory {
            initializer {
                ProductionCostingViewModel(
                    productionRepository = ServiceLocator.provideProductionRepository(context.applicationContext),
                    batchId = batchId,
                )
            }
        }
    }
}
