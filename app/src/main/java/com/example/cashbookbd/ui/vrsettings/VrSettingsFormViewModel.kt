package com.example.cashbookbd.ui.vrsettings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.DashboardRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.data.repository.VrSettingsRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.vrsettings.VrKind
import com.example.cashbookbd.vrsettings.VrSettingsForms
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives a VR-settings action form (resolved from [settingKey]): a voucher-no
 * delete (with a force-confirm step), an installment delete, or a voucher
 * date-change (branch + type + date range).
 */
class VrSettingsFormViewModel(
    settingKey: String,
    private val repository: VrSettingsRepository,
    private val reportRepository: ReportRepository,
    private val dashboardRepository: DashboardRepository,
) : ViewModel() {

    private val spec = VrSettingsForms.byKey(settingKey)

    private val _uiState = MutableStateFlow(
        VrSettingsFormUiState(
            title = spec?.title ?: "VR Settings",
            isSupported = spec != null,
            kind = spec?.kind,
            actionLabel = spec?.actionLabel ?: "Submit",
        )
    )
    val uiState: StateFlow<VrSettingsFormUiState> = _uiState.asStateFlow()

    init {
        if (spec?.kind == VrKind.DATE_CHANGE) {
            applyDashboardDate()
            loadBranches()
        }
    }

    private fun applyDashboardDate() {
        viewModelScope.launch {
            val dashboard = dashboardRepository.getCachedDashboard()
                ?: (dashboardRepository.getDashboard() as? Resource.Success)?.data
            val trDate = SimpleDate.fromDisplay(dashboard?.transactionDate) ?: return@launch
            _uiState.update { it.copy(presentDate = trDate, changeDate = trDate) }
        }
    }

    private fun loadBranches() {
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

    fun onVoucherNoChange(value: String) =
        _uiState.update { it.copy(voucherNo = value, requiresConfirmation = false, message = null) }

    fun onBranchSelected(branch: BranchOption) = _uiState.update { it.copy(selectedBranch = branch) }
    fun onVoucherTypeChange(value: String) = _uiState.update { it.copy(voucherType = value) }
    fun onPresentDate(date: SimpleDate) = _uiState.update { it.copy(presentDate = date) }
    fun onChangeDate(date: SimpleDate) = _uiState.update { it.copy(changeDate = date) }
    fun onStartVoucher(value: String) = _uiState.update { it.copy(startVoucher = value) }
    fun onEndVoucher(value: String) = _uiState.update { it.copy(endVoucher = value) }

    fun submit() {
        val currentSpec = spec ?: return
        val state = _uiState.value
        if (!state.canSubmit) return
        _uiState.update { it.copy(isSubmitting = true, message = null, isError = false) }

        viewModelScope.launch {
            when (currentSpec.kind) {
                VrKind.VOUCHER_DELETE -> runVoucherDelete(state.voucherNo.trim(), confirm = false)
                VrKind.INSTALLMENT_DELETE -> {
                    applyResult(repository.deleteInstallment(state.voucherNo.trim()), clearVoucher = true)
                }
                VrKind.DATE_CHANGE -> {
                    val result = repository.changeVoucherDate(
                        branchId = state.selectedBranch!!.id,
                        voucherType = state.voucherType,
                        presentDate = state.presentDate.toApi(),
                        changeDate = state.changeDate.toApi(),
                        startVoucher = state.startVoucher.trim(),
                        endVoucher = state.endVoucher.trim(),
                    )
                    applyResult(result, clearVoucher = false)
                }
            }
        }
    }

    /** Re-submits a voucher delete with force-confirm (already-deleted voucher). */
    fun forceDelete() {
        val state = _uiState.value
        if (state.isSubmitting || state.voucherNo.isBlank()) return
        _uiState.update { it.copy(isSubmitting = true, message = null, isError = false) }
        viewModelScope.launch { runVoucherDelete(state.voucherNo.trim(), confirm = true) }
    }

    private suspend fun runVoucherDelete(voucherNo: String, confirm: Boolean) {
        when (val result = repository.deleteVoucher(voucherNo, confirm)) {
            is Resource.Success -> {
                val outcome = result.data
                if (outcome.requiresConfirmation) {
                    _uiState.update {
                        it.copy(isSubmitting = false, requiresConfirmation = true, message = outcome.message, isError = false)
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            requiresConfirmation = false,
                            message = outcome.message,
                            isError = false,
                            voucherNo = "",
                        )
                    }
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

    private fun applyResult(result: Resource<String>, clearVoucher: Boolean) {
        when (result) {
            is Resource.Success -> _uiState.update {
                it.copy(
                    isSubmitting = false,
                    message = result.data,
                    isError = false,
                    voucherNo = if (clearVoucher) "" else it.voucherNo,
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

    companion object {
        fun provideFactory(context: Context, settingKey: String) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                VrSettingsFormViewModel(
                    settingKey = settingKey,
                    repository = ServiceLocator.provideVrSettingsRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                    dashboardRepository = ServiceLocator.provideDashboardRepository(appContext),
                )
            }
        }
    }
}
