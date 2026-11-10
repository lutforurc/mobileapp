package com.example.cashbookbd.ui.reports

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.GeneralLedgerRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives General Ledger: the branch, an optional Accounts Head, the year-to-date
 * window the web opens on, and the server's page-by-Accounts-Head results.
 */
class GeneralLedgerViewModel(
    private val reportRepository: ReportRepository,
    private val generalLedgerRepository: GeneralLedgerRepository,
    private val ownBranchId: Long?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GeneralLedgerUiState())
    val uiState: StateFlow<GeneralLedgerUiState> = _uiState.asStateFlow()

    /** What the current page was asked for, so a page turn re-asks the same. */
    private data class Applied(
        val branchId: Long,
        val branchName: String,
        val start: String,
        val end: String,
        val startDisplay: String,
        val endDisplay: String,
        val coa4Id: Long?,
        val accountsHead: String,
    )

    private var applied: Applied? = null
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

    fun onAccount(option: SelectorOption?) = _uiState.update { it.copy(account = option) }

    fun onStart(date: SimpleDate) = _uiState.update { it.copy(startDate = date) }

    fun onEnd(date: SimpleDate) = _uiState.update { it.copy(endDate = date) }

    suspend fun searchAccounts(query: String) = generalLedgerRepository.searchAccounts(query)

    fun reset() {
        applied = null
        _uiState.update {
            it.copy(
                account = null,
                startDate = defaultDate?.copy(month = 1, day = 1),
                endDate = defaultDate,
                report = null,
                error = null,
                appliedBranchName = null,
                appliedRange = null,
                appliedAccountsHead = "All Accounts",
                resetKey = it.resetKey + 1,
            )
        }
    }

    fun apply() {
        val state = _uiState.value
        val branch = state.branch.selected ?: return
        val start = state.startDate ?: return
        val end = state.endDate ?: return
        if (state.isLoading) return

        applied = Applied(
            branchId = branch.id,
            branchName = branch.name,
            start = start.toApi(),
            end = end.toApi(),
            startDisplay = start.toDisplay(),
            endDisplay = end.toDisplay(),
            coa4Id = state.account?.id?.toLongOrNull(),
            accountsHead = state.account?.label ?: "All Accounts",
        )
        loadPage(1)
    }

    fun previousPage() {
        val current = _uiState.value.report?.pagination?.page ?: 1
        if (current > 1) loadPage(current - 1)
    }

    fun nextPage() {
        val pagination = _uiState.value.report?.pagination ?: return
        if (pagination.page < pagination.lastPage) loadPage(pagination.page + 1)
    }

    private fun loadPage(page: Int) {
        val request = applied ?: return
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            val result = generalLedgerRepository.fetch(
                branchId = request.branchId,
                startDate = request.start,
                endDate = request.end,
                coa4Id = request.coa4Id,
                page = page,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        report = result.data,
                        appliedBranchName = result.data.branchName.ifBlank { request.branchName },
                        appliedRange = "${request.startDisplay} — ${request.endDisplay}",
                        appliedAccountsHead = result.data.accountsHeadName
                            .ifBlank { request.accountsHead },
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
                GeneralLedgerViewModel(
                    reportRepository = ServiceLocator.provideReportRepository(app),
                    generalLedgerRepository = ServiceLocator.provideGeneralLedgerRepository(app),
                    ownBranchId = ServiceLocator.provideSessionManager(app).state.value.settings?.branchId,
                )
            }
        }
    }
}
