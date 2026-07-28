package com.example.cashbookbd.ui.vrsettings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.data.repository.VoucherHistoryRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.reports.model.BranchOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the Voucher History screen: a branch + voucher-number filter over the
 * `history/information` audit trail.
 */
class VoucherHistoryViewModel(
    private val repository: VoucherHistoryRepository,
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoucherHistoryUiState())
    val uiState: StateFlow<VoucherHistoryUiState> = _uiState.asStateFlow()

    init {
        loadBranches()
    }

    fun loadBranches() {
        _uiState.update { it.copy(isBranchesLoading = true, branchesError = null) }
        viewModelScope.launch {
            when (val result = reportRepository.getBranches()) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isBranchesLoading = false,
                        branches = result.data.branches,
                        selectedBranch = it.selectedBranch ?: result.data.branches.firstOrNull(),
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

    fun onBranchSelected(branch: BranchOption) = _uiState.update { it.copy(selectedBranch = branch) }
    fun onVoucherNo(value: String) = _uiState.update { it.copy(voucherNo = value) }

    fun apply() {
        val state = _uiState.value
        if (!state.canApply) return
        val branch = state.selectedBranch ?: return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.loadHistory(branch.id, state.voucherNo)) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, result = result.data)
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
                VoucherHistoryViewModel(
                    repository = ServiceLocator.provideVoucherHistoryRepository(context.applicationContext),
                    reportRepository = ServiceLocator.provideReportRepository(context.applicationContext),
                )
            }
        }
    }
}
