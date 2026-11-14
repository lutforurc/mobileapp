package com.example.cashbookbd.ui.reports

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.InvoiceWiseProfitRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.reports.model.SimpleDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives Invoice Wise Profit: the branch, an optional invoice-number search,
 * the web's "month so far" opening range, and the server's paged result.
 */
class InvoiceWiseProfitViewModel(
    private val reportRepository: ReportRepository,
    private val invoiceWiseProfitRepository: InvoiceWiseProfitRepository,
    private val ownBranchId: Long?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InvoiceWiseProfitUiState())
    val uiState: StateFlow<InvoiceWiseProfitUiState> = _uiState.asStateFlow()

    /** What the current page was fetched with, so a page turn re-asks the same. */
    private data class Applied(
        val branchId: Long,
        val branchName: String,
        val start: String,
        val end: String,
        val startDisplay: String,
        val endDisplay: String,
        val search: String,
    )

    private var applied: Applied? = null

    init {
        viewModelScope.launch {
            when (val result = reportRepository.loadBranchPick(ownBranchId)) {
                is Resource.Success -> {
                    val trx = result.data.trxDate ?: SimpleDate.today()
                    _uiState.update {
                        it.copy(
                            branch = result.data,
                            // The month so far — the same opening range the
                            // other profit reports use (web f0028a3b).
                            startDate = trx.copy(day = 1),
                            endDate = trx,
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

    fun onSearch(value: String) = _uiState.update { it.copy(search = value) }

    fun onStart(date: SimpleDate) = _uiState.update { it.copy(startDate = date) }

    fun onEnd(date: SimpleDate) = _uiState.update { it.copy(endDate = date) }

    /**
     * The web's Reset clears the search box and the sheet, and leaves the
     * branch and dates where they are (`handleResetFilters`).
     */
    fun reset() {
        applied = null
        _uiState.update {
            it.copy(
                search = "",
                report = null,
                error = null,
                appliedBranchName = null,
                appliedRange = null,
                appliedSearch = "",
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

        // The web refuses an end before the start; compare the zero-padded
        // API strings so the order is unambiguous.
        if (end.toApi() < start.toApi()) {
            _uiState.update { it.copy(error = "End Date cannot be earlier than Start Date.") }
            return
        }

        applied = Applied(
            branchId = branch.id,
            branchName = branch.name,
            start = start.toApi(),
            end = end.toApi(),
            startDisplay = start.toDisplay(),
            endDisplay = end.toDisplay(),
            search = state.search.trim(),
        )
        // A new run must not leave the previous filter's rows under the new
        // heading while the request is in flight.
        _uiState.update { it.copy(report = null, error = null) }
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
            val result = invoiceWiseProfitRepository.fetch(
                branchId = request.branchId,
                startDate = request.start,
                endDate = request.end,
                search = request.search,
                page = page,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        report = result.data,
                        appliedBranchName = request.branchName,
                        appliedRange = "${request.startDisplay} — ${request.endDisplay}",
                        appliedSearch = request.search,
                    )
                }

                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        // A page turn that fails keeps the rows already read; a
                        // fresh run that fails leaves no rows to mistake for it.
                        report = if (page == 1) null else it.report,
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
                InvoiceWiseProfitViewModel(
                    reportRepository = ServiceLocator.provideReportRepository(app),
                    invoiceWiseProfitRepository = ServiceLocator.provideInvoiceWiseProfitRepository(app),
                    ownBranchId = ServiceLocator.provideSessionManager(app).state.value.settings?.branchId,
                )
            }
        }
    }
}
