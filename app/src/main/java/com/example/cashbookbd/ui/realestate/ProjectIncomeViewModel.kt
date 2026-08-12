package com.example.cashbookbd.ui.realestate

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.ProjectCostRepository
import com.example.cashbookbd.data.repository.ProjectIncomeLine
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.reports.model.SelectorOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectIncomeUiState(
    val projects: List<SelectorOption> = emptyList(),
    val buildings: List<SelectorOption> = emptyList(),
    val isLoadingDdls: Boolean = false,
    val ddlError: String? = null,

    // The line form. Project and building survive an Add, as on the payment
    // form — most lines of one voucher belong to the same place.
    val projectId: String = "",
    val projectName: String = "",
    /** Blank = "whole project" — earnings no single building brought in. */
    val buildingId: String = "",
    val buildingName: String = "",
    val account: String = "",
    val accountName: String = "",
    /** Whether the chosen account is an income head — only those take a project. */
    val isIncomeAccount: Boolean = false,
    val remarks: String = "",
    val amount: String = "",

    /** Voucher-level note. */
    val note: String = "",
    val rows: List<ProjectIncomeLine> = emptyList(),
    /** The table line open in the form, or null. */
    val editingRowKey: String? = null,

    val searchQuery: String = "",
    val isSearching: Boolean = false,
    /** Set while correcting a saved voucher — Save becomes Update. */
    val editingVrNo: String? = null,
    val editingMtmId: String? = null,
    /**
     * Where the loaded voucher's money landed (the debit line). 17 is cash;
     * anything else is a bank, and saving keeps it that way — the banner says
     * so instead of silently turning a banked receipt into a cash one.
     */
    val receivedIn: Int? = null,
    val receivedInName: String = "",

    val isSaving: Boolean = false,
    val actionMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    val total: Double get() = rows.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    val isRowEditing: Boolean get() = editingRowKey != null
    val receivedInBank: Boolean get() = editingVrNo != null && receivedIn != null &&
        receivedIn != ProjectCostRepository.CASH_COA4_ID
}

/**
 * Backs the Project Income form — Project Expense pointed the other way. Every
 * line is an ordinary receipt line; an income line may additionally record the
 * project (and optionally the building) the money came from, which is what the
 * project income report reads. A voucher can be pulled up by number and
 * rewritten in place.
 *
 * A flat sold is deliberately not entered here. The Unit Sales screen tags its
 * own income line with the project and building of the unit, so a sale reaches
 * the report on its own; entering it again here would count the money twice.
 */
class ProjectIncomeViewModel(
    private val repository: ProjectCostRepository,
    /** A voucher number handed in by the untagged report's Tag button. */
    private val initialVrNo: String? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectIncomeUiState())
    val uiState: StateFlow<ProjectIncomeUiState> = _uiState.asStateFlow()

    private var lineCounter = 0
    private var autoLoaded = false

    /** Whether each account picked so far is an income head, by its id. */
    private var incomeHeads: Map<String, Boolean> = emptyMap()

    init {
        loadDdls()
    }

    fun loadDdls() {
        _uiState.update { it.copy(isLoadingDdls = true, ddlError = null) }
        viewModelScope.launch {
            val projects = repository.projectsDdl()
            _uiState.update {
                it.copy(
                    isLoadingDdls = false,
                    ddlError = (projects as? Resource.Error)?.message,
                    projects = (projects as? Resource.Success)?.data ?: it.projects,
                    sessionExpired = it.sessionExpired ||
                        (projects as? Resource.Error)?.isUnauthorized == true,
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

    /** A blank id is the picker's "branch income" option — no project at all. */
    fun onProjectSelected(option: SelectorOption) {
        _uiState.update {
            it.copy(
                projectId = option.id,
                projectName = if (option.id.isBlank()) "" else option.label,
                buildingId = "",
                buildingName = "",
                buildings = emptyList(),
            )
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

    /**
     * Account type-ahead. The `is_income` flag each option carries is kept
     * aside — [SelectorOption] has nowhere to hold it — and read back when one
     * of them is picked.
     */
    suspend fun searchAccounts(query: String): Resource<List<SelectorOption>> =
        when (val result = repository.searchIncomeAccounts(query)) {
            is Resource.Success -> {
                incomeHeads = incomeHeads + result.data.associate { it.id.toString() to it.isIncome }
                Resource.Success(
                    result.data.map {
                        SelectorOption(
                            id = it.id.toString(),
                            label = it.name,
                            sublabel = it.group.ifBlank { null },
                        )
                    }
                )
            }
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }

    /**
     * Picking a non-income account drops whatever project was on the form:
     * only an income line may carry one, and leaving a stale project sitting
     * in a disabled box is how a line gets saved against the wrong place.
     */
    fun onAccountSelected(option: SelectorOption) {
        val isIncome = incomeHeads[option.id] == true
        _uiState.update {
            it.copy(
                account = option.id,
                accountName = option.label,
                isIncomeAccount = isIncome,
                projectId = if (isIncome) it.projectId else "",
                projectName = if (isIncome) it.projectName else "",
                buildingId = if (isIncome) it.buildingId else "",
                buildingName = if (isIncome) it.buildingName else "",
                buildings = if (isIncome) it.buildings else emptyList(),
            )
        }
    }

    fun onRemarks(value: String) = _uiState.update { it.copy(remarks = value) }
    fun onAmount(value: String) = _uiState.update { it.copy(amount = value) }
    fun onNote(value: String) = _uiState.update { it.copy(note = value) }
    fun onSearchQuery(value: String) = _uiState.update { it.copy(searchQuery = value) }

    /** Adds the form as a new line, or writes it back over the line under edit. */
    fun saveLine() {
        val state = _uiState.value
        val message = when {
            state.account.isBlank() -> "Choose an account."
            (state.amount.trim().toDoubleOrNull() ?: 0.0) <= 0.0 -> "Enter an amount greater than zero."
            else -> null
        }
        if (message != null) {
            _uiState.update { it.copy(actionMessage = message) }
            return
        }
        // The project is optional, and belongs to income lines alone: without
        // one an income line is plain branch income, and any other account is
        // never project-tracked at all.
        val tagged = state.isIncomeAccount
        val line = ProjectIncomeLine(
            key = state.editingRowKey ?: "new-${lineCounter++}",
            account = state.account.toIntOrNull() ?: return,
            accountName = state.accountName,
            remarks = state.remarks.trim(),
            amount = state.amount.trim(),
            projectId = if (tagged) state.projectId.toIntOrNull() else null,
            projectName = if (tagged) state.projectName else "",
            buildingId = if (tagged) state.buildingId.toIntOrNull() else null,
            buildingName = if (tagged) state.buildingName else "",
            isIncome = tagged,
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
                isIncomeAccount = false,
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
                projectId = row.projectId?.toString().orEmpty(),
                projectName = row.projectName,
                buildingId = row.buildingId?.toString().orEmpty(),
                buildingName = row.buildingName,
                account = row.account.toString(),
                accountName = row.accountName,
                isIncomeAccount = row.isIncome,
                remarks = row.remarks,
                amount = row.amount,
            )
        }
        row.projectId?.let { loadBuildings(it) }
    }

    fun cancelRowEdit() = _uiState.update {
        it.copy(
            editingRowKey = null,
            account = "",
            accountName = "",
            isIncomeAccount = false,
            remarks = "",
            amount = "",
        )
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
            val result = repository.incomeSave(
                note = state.note.trim(),
                receivedIn = state.receivedIn,
                rows = state.rows,
                mtmId = state.editingMtmId,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    // A fresh sheet, like the web after its toast.
                    ProjectIncomeUiState(
                        projects = it.projects,
                        actionMessage = result.data,
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
            when (val result = repository.incomeEdit(vrNo)) {
                is Resource.Success -> {
                    val voucher = result.data
                    val projectNames = _uiState.value.projects
                        .mapNotNull { o -> o.id.toIntOrNull()?.let { it to o.label } }.toMap()
                    val buildingNames = buildingNames(
                        voucher.rows.mapNotNull { row -> row.projectId.takeIf { row.buildingId != null } },
                    )
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            rows = voucher.rows.map { row ->
                                row.copy(
                                    projectName = row.projectId?.let { id -> projectNames[id] }.orEmpty(),
                                    buildingName = row.buildingId
                                        ?.let { id -> row.projectId?.let { p -> buildingNames[p]?.get(id) } }
                                        .orEmpty(),
                                )
                            },
                            note = voucher.note,
                            editingVrNo = voucher.vrNo,
                            editingMtmId = voucher.mtmId,
                            receivedIn = voucher.receivedIn,
                            receivedInName = voucher.receivedInName,
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
                ProjectIncomeViewModel(
                    repository = ServiceLocator.provideProjectCostRepository(context.applicationContext),
                    initialVrNo = initialVrNo,
                )
            }
        }
    }
}
