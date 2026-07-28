package com.example.cashbookbd.ui.customer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.CoaRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.reports.model.SelectorOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the Add/Edit CoA Level 3 form: loads the L2/source dropdowns, prefills
 * the stored model when [coaId] is set, and saves via store/update.
 */
class AddCoaL3ViewModel(
    private val coaId: String?,
    private val repository: CoaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddCoaL3UiState(isEdit = coaId != null))
    val uiState: StateFlow<AddCoaL3UiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isFormLoading = true, formError = null) }
        viewModelScope.launch {
            when (val options = repository.loadFormOptions()) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            l2Options = options.data.l2Options,
                            sources = options.data.sources,
                            // Source is required server-side, so the first is preselected.
                            selectedSource = it.selectedSource ?: options.data.sources.firstOrNull(),
                        )
                    }
                    if (coaId != null) {
                        prefill(coaId)
                    } else {
                        _uiState.update { it.copy(isFormLoading = false) }
                    }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isFormLoading = false,
                        formError = options.message,
                        sessionExpired = it.sessionExpired || options.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    private suspend fun prefill(id: String) {
        when (val details = repository.loadCoaL3(id)) {
            is Resource.Success -> _uiState.update { state ->
                state.copy(
                    isFormLoading = false,
                    name = details.data.name.take(COA_L3_NAME_MAX),
                    selectedL2 = state.l2Options.firstOrNull { it.id == details.data.l2Id },
                    selectedSource = details.data.sourceId
                        ?.let { sourceId -> state.sources.firstOrNull { it.id == sourceId } }
                        ?: state.selectedSource,
                )
            }
            is Resource.Error -> _uiState.update {
                it.copy(
                    isFormLoading = false,
                    formError = details.message,
                    sessionExpired = it.sessionExpired || details.isUnauthorized,
                )
            }
            Resource.Loading -> Unit
        }
    }

    fun onL2Selected(option: SelectorOption) = _uiState.update { it.copy(selectedL2 = option) }
    fun onSourceSelected(option: SelectorOption) = _uiState.update { it.copy(selectedSource = option) }
    fun onName(value: String) = _uiState.update { it.copy(name = value.take(COA_L3_NAME_MAX)) }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        val l2 = state.selectedL2 ?: return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = repository.saveCoaL3(
                coaId = coaId,
                l2Id = l2.id,
                sourceId = if (state.sources.isEmpty()) null else state.selectedSource?.id,
                name = state.name,
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
        fun provideFactory(context: Context, coaId: String?) = viewModelFactory {
            initializer {
                AddCoaL3ViewModel(
                    coaId = coaId,
                    repository = ServiceLocator.provideCoaRepository(context.applicationContext),
                )
            }
        }
    }
}
