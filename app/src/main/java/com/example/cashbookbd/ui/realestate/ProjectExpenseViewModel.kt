package com.example.cashbookbd.ui.realestate

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.ProjectCostRepository
import com.example.cashbookbd.data.repository.ProjectExpenseLine
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.reports.model.SelectorOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectExpenseUiState(
    val projects: List<SelectorOption> = emptyList(),
    val accounts: List<SelectorOption> = emptyList(),
    val buildings: List<SelectorOption> = emptyList(),
    val isLoadingDdls: Boolean = false,
    val ddlError: String? = null,

    // The line form. Project and building deliberately survive an Add — most
    // lines of one voucher belong to the same place, and re-picking every time
    // is how people stop picking.
    val projectId: String = "",
    val projectName: String = "",
    /** Blank = "whole project" — a cost no single building carries. */
    val buildingId: String = "",
    val buildingName: String = "",
    val account: String = "",
    val accountName: String = "",
    val remarks: String = "",
    val amount: String = "",

    /** Voucher-level note. */
    val note: String = "",
    val rows: List<ProjectExpenseLine> = emptyList(),
    /** The table line open in the form, or null. */
    val editingRowKey: String? = null,

    val searchQuery: String = "",
    val isSearching: Boolean = false,
    /** Set while correcting a saved voucher — Save becomes Update. */
    val editingVrNo: String? = null,
    val editingMtmId: String? = null,
    /**
     * Where the loaded voucher's money came from (the credit line). 17 is
     * cash; anything else is a bank, and saving keeps it that way — the
     * banner says so instead of silently converting it.
     */
    val paidFrom: Int? = null,
    val paidFromName: String = "",

    val isSaving: Boolean = false,
    val actionMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    val total: Double get() = rows.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    val isRowEditing: Boolean get() = editingRowKey != null
    val paidFromBank: Boolean get() = editingVrNo != null && paidFrom != null &&
        paidFrom != ProjectCostRepository.CASH_COA4_ID
}

/**
 * Backs the Project Expense form: an ordinary cash payment voucher whose every
 * expense line also records the project (and optionally building) that money
 * belongs to. A voucher can be pulled up by number and rewritten in place.
 */
class ProjectExpenseViewModel(
    private val repository: ProjectCostRepository,
    /** A voucher number handed in by the untagged report's Tag button. */
    private val initialVrNo: String? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectExpenseUiState())
    val uiState: StateFlow<ProjectExpenseUiState> = _uiState.asStateFlow()

    private var lineCounter = 0
    private var autoLoaded = false

    init {
        loadDdls()
    }

    fun loadDdls() {
        _uiState.update { it.copy(isLoadingDdls = true, ddlError = null) }
        viewModelScope.launch {
            val projects = repository.projectsDdl()
            val accounts = repository.accountsDdl()
            val error = (projects as? Resource.Error)?.message ?: (accounts as? Resource.Error)?.message
            _uiState.update {
                it.copy(
                    isLoadingDdls = false,
                    ddlError = error,
                    projects = (projects as? Resource.Success)?.data ?: it.projects,
                    accounts = (accounts as? Resource.Success)?.data ?: it.accounts,
                    sessionExpired = it.sessionExpired ||
                        (projects as? Resource.Error)?.isUnauthorized == true ||
                        (accounts as? Resource.Error)?.isUnauthorized == true,
                )
            }
            // The untagged report's deep link: load its voucher once, and only
            // after the projects arrive — their names resolve off that list.
            if (!autoLoaded && !initialVrNo.isNullOrBlank() && _uiState.value.projects.isNotEmpty()) {
                autoLoaded = true
                _uiState.update { it.copy(searchQuery = initialVrNo) }
                search(initialVrNo)
            }
        }
    }

    fun onProjectSelected(option: SelectorOption) {
        _uiState.update {
            it.copy(projectId = option.id, projectName = option.label, buildingId = "", buildingName = "", buildings = emptyList())
        }
        loadBuildings(option.id.toIntOrNull() ?: return)
    }

    private fun loadBuildings(projectId: Int) {
        viewModelScope.launch {
            when (val result = repository.buildingsDdl(projectId)) {
                is Resource.Success -> _uiState.update { state ->
                    // Only if the project hasn't changed since the request left.
                    if (state.projectId.toIntOrNull() == projectId) state.copy(buildings = result.data) else state
                }
                is Resource.Error -> _uiState.update {
                    it.copy(sessionExpired = it.sessionExpired || result.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onBuildingSelected(option: SelectorOption) = _uiState.update {
        it.copy(buildingId = option.id, buildingName = if (option.id.isBlank()) "" else option.label)
    }

    fun onAccountSelected(option: SelectorOption) = _uiState.update {
        it.copy(account = option.id, accountName = option.label)
    }

    fun onRemarks(value: String) = _uiState.update { it.copy(remarks = value) }
    fun onAmount(value: String) = _uiState.update { it.copy(amount = value) }
    fun onNote(value: String) = _uiState.update { it.copy(note = value) }
    fun onSearchQuery(value: String) = _uiState.update { it.copy(searchQuery = value) }

    /** Adds the form as a new line, or writes it back over the line under edit. */
    fun saveLine() {
        val state = _uiState.value
        val message = when {
            state.projectId.isBlank() -> "Choose the project this money belongs to."
            state.account.isBlank() -> "Choose an account."
            (state.amount.trim().toDoubleOrNull() ?: 0.0) <= 0.0 -> "Enter an amount greater than zero."
            else -> null
        }
        if (message != null) {
            _uiState.update { it.copy(actionMessage = message) }
            return
        }
        val line = ProjectExpenseLine(
            key = state.editingRowKey ?: "new-${lineCounter++}",
            account = state.account.toIntOrNull() ?: return,
            accountName = state.accountName,
            remarks = state.remarks.trim(),
            amount = state.amount.trim(),
            projectId = state.projectId.toIntOrNull() ?: return,
            projectName = state.projectName,
            buildingId = state.buildingId.toIntOrNull(),
            buildingName = state.buildingName,
        )
        _uiState.update {
            it.copy(
                rows = if (it.editingRowKey == null) {
                    it.rows + line
                } else {
                    // In place, keeping the ordinal position — the on-screen
                    // voucher stays whole while a line is being corrected.
                    it.rows.map { row -> if (row.key == it.editingRowKey) line else row }
                },
                editingRowKey = null,
                // Project and building stay for the next line.
                account = "",
                accountName = "",
                remarks = "",
                amount = "",
            )
        }
    }

    /** Opens a table line in the form. The row stays put, tinted, until saved. */
    fun editRow(key: String) {
        val row = _uiState.value.rows.firstOrNull { it.key == key } ?: return
        _uiState.update {
            it.copy(
                editingRowKey = key,
                projectId = row.projectId.toString(),
                projectName = row.projectName,
                buildingId = row.buildingId?.toString().orEmpty(),
                buildingName = row.buildingName,
                account = row.account.toString(),
                accountName = row.accountName,
                remarks = row.remarks,
                amount = row.amount,
            )
        }
        loadBuildings(row.projectId)
    }

    fun cancelRowEdit() = _uiState.update {
        it.copy(editingRowKey = null, account = "", accountName = "", remarks = "", amount = "")
    }

    fun removeRow(key: String) {
        val wasEditing = _uiState.value.editingRowKey == key
        _uiState.update { it.copy(rows = it.rows.filter { row -> row.key != key }) }
        if (wasEditing) cancelRowEdit()
    }

    /** Saves the voucher — a create, or a rewrite of the one loaded by search. */
    fun save() {
        val state = _uiState.value
        if (state.isSaving) return
        if (state.isRowEditing) {
            _uiState.update { it.copy(actionMessage = "Finish the line you are editing first — Save Line, or Cancel.") }
            return
        }
        if (state.rows.isEmpty()) {
            _uiState.update { it.copy(actionMessage = "Add at least one line before saving.") }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = repository.expenseSave(
                note = state.note.trim(),
                paidFrom = state.paidFrom,
                rows = state.rows,
                mtmId = state.editingMtmId,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    // A fresh sheet, like the web after its toast.
                    it.copy(
                        isSaving = false,
                        actionMessage = result.data,
                        rows = emptyList(),
                        note = "",
                        editingVrNo = null,
                        editingMtmId = null,
                        paidFrom = null,
                        paidFromName = "",
                        editingRowKey = null,
                        account = "",
                        accountName = "",
                        remarks = "",
                        amount = "",
                        searchQuery = "",
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        actionMessage = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onSearch() {
        val vrNo = _uiState.value.searchQuery.trim()
        if (vrNo.isEmpty()) {
            _uiState.update { it.copy(actionMessage = "Type a voucher number to search.") }
            return
        }
        search(vrNo)
    }

    /** Pulls a saved voucher into the form for correction. */
    private fun search(vrNo: String) {
        if (_uiState.value.isSearching) return
        _uiState.update { it.copy(isSearching = true) }
        viewModelScope.launch {
            when (val result = repository.expenseEdit(vrNo)) {
                is Resource.Success -> {
                    val voucher = result.data
                    val projectNames = _uiState.value.projects
                        .mapNotNull { o -> o.id.toIntOrNull()?.let { it to o.label } }.toMap()
                    val buildingNames = buildingNames(
                        voucher.rows.filter { it.buildingId != null }.map { it.projectId },
                    )
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            rows = voucher.rows.map { row ->
                                row.copy(
                                    projectName = projectNames[row.projectId].orEmpty(),
                                    buildingName = row.buildingId
                                        ?.let { id -> buildingNames[row.projectId]?.get(id) }.orEmpty(),
                                )
                            },
                            note = voucher.note,
                            editingVrNo = voucher.vrNo,
                            editingMtmId = voucher.mtmId,
                            paidFrom = voucher.paidFrom,
                            paidFromName = voucher.paidFromName,
                            editingRowKey = null,
                            actionMessage = "Voucher ${voucher.vrNo} loaded",
                        )
                    }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSearching = false,
                        actionMessage = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    /** Building names per project, off the same ddl the picker uses. */
    private suspend fun buildingNames(projectIds: List<Int>): Map<Int, Map<Int, String>> {
        val result = mutableMapOf<Int, Map<Int, String>>()
        projectIds.distinct().forEach { projectId ->
            val ddl = repository.buildingsDdl(projectId)
            if (ddl is Resource.Success) {
                result[projectId] = ddl.data
                    .mapNotNull { o -> o.id.toIntOrNull()?.let { it to o.label } }
                    .toMap()
            }
        }
        return result
    }

    fun onActionMessageShown() = _uiState.update { it.copy(actionMessage = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context, initialVrNo: String? = null) = viewModelFactory {
            initializer {
                ProjectExpenseViewModel(
                    repository = ServiceLocator.provideProjectCostRepository(context.applicationContext),
                    initialVrNo = initialVrNo,
                )
            }
        }
    }
}
