package com.example.cashbookbd.ui.reports

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.CashFlowStatementRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.reports.model.SimpleDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the Cash Flow Statement: the branch and the year-to-date window the web
 * opens on, then one read of `reports/cash-flow-statement`.
 */
class CashFlowStatementViewModel(
    private val reportRepository: ReportRepository,
    private val cashFlowStatementRepository: CashFlowStatementRepository,
    private val ownBranchId: Long?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CashFlowStatementUiState())
    val uiState: StateFlow<CashFlowStatementUiState> = _uiState.asStateFlow()

    private var defaultDate: SimpleDate? = null

    init {
        viewModelScope.launch {
            when (val result = reportRepository.loadBranchPick(ownBranchId)) {
                is Resource.Success -> {
                    val trx = result.data.trxDate
                    defaultDate = trx
                    _uiState.update {
                        it.copy(
                            branch = result.data,
                            startDate = trx?.copy(month = 1, day = 1) ?: it.startDate,
                            endDate = trx ?: it.endDate,
                        )
                    }
                }

                is Resource.Error -> _uiState.update {
                    it.copy(
                        branch = it.branch.copy(loading = false),
                        error = result.message,
                        sessionExpired = result.isUnauthorized,
                    )
                }

                Resource.Loading -> Unit
            }
        }
    }

    fun onBranch(id: String) = _uiState.update { it.copy(branch = it.branch.pick(id)) }

    fun onStart(date: SimpleDate) = _uiState.update { it.copy(startDate = date) }

    fun onEnd(date: SimpleDate) = _uiState.update { it.copy(endDate = date) }

    fun reset() = _uiState.update {
        it.copy(
            startDate = defaultDate?.copy(month = 1, day = 1),
            endDate = defaultDate,
            report = null,
            error = null,
            appliedBranchName = null,
            appliedRange = null,
            hasRun = false,
        )
    }

    fun apply() {
        val state = _uiState.value
        val branch = state.branch.selected ?: return
        val start = state.startDate ?: return
        val end = state.endDate ?: return
        if (state.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            val result = cashFlowStatementRepository.fetch(
                branchId = branch.id,
                startDate = start.toApi(),
                endDate = end.toApi(),
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        report = result.data,
                        hasRun = true,
                        appliedBranchName = result.data.branchName.ifBlank { branch.name },
                        appliedRange = "${start.toDisplay()} — ${end.toDisplay()}",
                    )
                }

                // A failed read must not leave the previous period's statement
                // on screen under a new heading.
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.message,
                        hasRun = true,
                        report = null,
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
                CashFlowStatementViewModel(
                    reportRepository = ServiceLocator.provideReportRepository(app),
                    cashFlowStatementRepository = ServiceLocator.provideCashFlowStatementRepository(app),
                    ownBranchId = ServiceLocator.provideSessionManager(app).state.value.settings?.branchId,
                )
            }
        }
    }
}
