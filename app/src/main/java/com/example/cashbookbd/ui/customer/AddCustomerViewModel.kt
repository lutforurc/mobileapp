package com.example.cashbookbd.ui.customer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.CustomerRepository
import com.example.cashbookbd.data.repository.NewCustomer
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.reports.model.SelectorOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Backs the (essential) Add Customer form: collects the fields and saves them. */
class AddCustomerViewModel(
    private val repository: CustomerRepository,
    settings: com.example.cashbookbd.session.Settings?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AddCustomerUiState(
            showSex = settings?.needCustomerSex == true,
            showArea = settings?.needCustomerArea == true,
            showOpening = settings?.openingOngoing == true,
        )
    )
    val uiState: StateFlow<AddCustomerUiState> = _uiState.asStateFlow()

    init {
        if (_uiState.value.showArea) loadAreas()
    }

    private fun loadAreas() {
        _uiState.update { it.copy(isAreasLoading = true) }
        viewModelScope.launch {
            when (val result = repository.fetchAreas()) {
                is Resource.Success -> _uiState.update { state ->
                    state.copy(
                        isAreasLoading = false,
                        areas = result.data.map { area ->
                            SelectorOption(
                                id = area.id,
                                label = area.name,
                                sublabel = listOf(area.thana, area.district)
                                    .filter { it.isNotBlank() }
                                    .joinToString(", ")
                                    .ifBlank { null },
                            )
                        },
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isAreasLoading = false,
                        error = it.error ?: result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onType(option: SelectorOption) = _uiState.update { it.copy(type = option) }
    fun onSex(option: SelectorOption) = _uiState.update { it.copy(sex = option) }
    fun onArea(option: SelectorOption) = _uiState.update { it.copy(area = option) }
    fun onName(value: String) = _uiState.update { it.copy(name = value) }
    fun onAddress(value: String) = _uiState.update { it.copy(address = value) }
    fun onMobile(value: String) = _uiState.update { it.copy(mobile = value) }
    fun onLedgerPage(value: String) = _uiState.update { it.copy(ledgerPage = value) }
    fun onNationalId(value: String) = _uiState.update { it.copy(nationalId = value) }
    fun onOpeningBalance(value: String) = _uiState.update { it.copy(openingBalance = value) }

    fun save() {
        val state = _uiState.value
        val type = state.type ?: return
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = repository.storeCustomer(
                NewCustomer(
                    typeId = type.id,
                    name = state.name,
                    address = state.address,
                    mobile = state.mobile,
                    ledgerPage = state.ledgerPage,
                    nationalId = state.nationalId,
                    sex = state.sex?.id.orEmpty(),
                    areaId = state.area?.id.orEmpty(),
                    // Never sent from a branch that is not keying openings, even
                    // if something had left a value behind in the field.
                    openingBalance = if (state.showOpening) state.openingBalance else "",
                )
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isSaving = false, savedMessage = result.data)
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

    fun onErrorShown() = _uiState.update { it.copy(error = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                AddCustomerViewModel(
                    repository = ServiceLocator.provideCustomerRepository(appContext),
                    settings = ServiceLocator.provideSessionManager(appContext).state.value.settings,
                )
            }
        }
    }
}
