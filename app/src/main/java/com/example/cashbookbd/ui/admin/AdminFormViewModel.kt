package com.example.cashbookbd.ui.admin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.admin.AdminForms
import com.example.cashbookbd.admin.AdminKind
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.AdminRepository
import com.example.cashbookbd.data.repository.DashboardRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Drives an admin action form (resolved from [adminKey]): day close, voucher
 * approval (date range), approval remove (voucher no) or change voucher type
 * (branch + type + voucher no).
 */
class AdminFormViewModel(
    adminKey: String,
    private val repository: AdminRepository,
    private val reportRepository: ReportRepository,
    private val dashboardRepository: DashboardRepository,
) : ViewModel() {

    private val spec = AdminForms.byKey(adminKey)

    private val _uiState = MutableStateFlow(
        AdminFormUiState(
            title = spec?.title ?: "Admin",
            isSupported = spec != null,
            kind = spec?.kind,
            actionLabel = spec?.actionLabel ?: "Submit",
        )
    )
    val uiState: StateFlow<AdminFormUiState> = _uiState.asStateFlow()

    init {
        when (spec?.kind) {
            AdminKind.DAY_CLOSE, AdminKind.VOUCHER_APPROVAL -> applyDashboardDate()
            AdminKind.CHANGE_VOUCHER_TYPE -> {
                loadBranches()
                loadVoucherTypes()
            }
            else -> Unit
        }
    }

    private fun applyDashboardDate() {
        viewModelScope.launch {
            val dashboard = dashboardRepository.getCachedDashboard()
                ?: (dashboardRepository.getDashboard() as? Resource.Success)?.data
            val trDate = SimpleDate.fromDisplay(dashboard?.transactionDate) ?: return@launch
            _uiState.update {
                it.copy(
                    currentDate = trDate,
                    nextDate = trDate.plusDays(1),
                    startDate = trDate,
                    endDate = trDate,
                )
            }
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

    private fun loadVoucherTypes() {
        _uiState.update { it.copy(isTypesLoading = true) }
        viewModelScope.launch {
            when (val result = repository.fetchVoucherTypes()) {
                is Resource.Success -> _uiState.update {
                    it.copy(isTypesLoading = false, voucherTypes = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isTypesLoading = false,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onVoucherNoChange(value: String) = _uiState.update { it.copy(voucherNo = value, message = null) }
    fun onBranchSelected(branch: BranchOption) = _uiState.update { it.copy(selectedBranch = branch) }
    fun onTypeSelected(type: SelectorOption) = _uiState.update { it.copy(selectedType = type) }
    fun onStartDate(date: SimpleDate) = _uiState.update { it.copy(startDate = date) }
    fun onEndDate(date: SimpleDate) = _uiState.update { it.copy(endDate = date) }

    fun submit() {
        val currentSpec = spec ?: return
        val state = _uiState.value
        if (!state.canSubmit) return
        _uiState.update { it.copy(isSubmitting = true, message = null, isError = false) }

        viewModelScope.launch {
            val result = when (currentSpec.kind) {
                AdminKind.DAY_CLOSE ->
                    repository.dayClose(state.currentDate.toDisplay(), state.nextDate.toDisplay())
                AdminKind.VOUCHER_APPROVAL ->
                    repository.approveVouchers(state.startDate.toApi(), state.endDate.toApi())
                AdminKind.APPROVAL_REMOVE ->
                    repository.removeApproval(state.voucherNo.trim())
                AdminKind.CHANGE_VOUCHER_TYPE ->
                    repository.changeVoucherType(
                        branchId = state.selectedBranch!!.id,
                        voucherType = state.selectedType!!.id,
                        voucherNumber = state.voucherNo.trim(),
                    )
            }
            applyResult(result, currentSpec.kind)
        }
    }

    private fun applyResult(result: Resource<String>, kind: AdminKind) {
        when (result) {
            is Resource.Success -> _uiState.update {
                it.copy(
                    isSubmitting = false,
                    message = result.data,
                    isError = false,
                    // Clear the voucher input on the voucher-based actions.
                    voucherNo = if (kind == AdminKind.APPROVAL_REMOVE || kind == AdminKind.CHANGE_VOUCHER_TYPE) {
                        ""
                    } else {
                        it.voucherNo
                    },
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

    private fun SimpleDate.plusDays(days: Int): SimpleDate {
        val c = Calendar.getInstance()
        c.set(year, month - 1, day)
        c.add(Calendar.DAY_OF_MONTH, days)
        return SimpleDate(
            year = c.get(Calendar.YEAR),
            month = c.get(Calendar.MONTH) + 1,
            day = c.get(Calendar.DAY_OF_MONTH),
        )
    }

    companion object {
        fun provideFactory(context: Context, adminKey: String) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                AdminFormViewModel(
                    adminKey = adminKey,
                    repository = ServiceLocator.provideAdminRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                    dashboardRepository = ServiceLocator.provideDashboardRepository(appContext),
                )
            }
        }
    }
}
