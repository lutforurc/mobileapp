package com.example.cashbookbd.ui.hrm

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.HrmRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.data.repository.SelectorRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.report.ReportSelectorSource
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.FieldFrame
import com.example.cashbookbd.ui.components.FieldTextInput
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SearchableSelectDropdown
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.hrm.model.AttendanceEntry
import com.example.cashbookbd.ui.hrm.model.HrmOption
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The attendance statuses the store endpoint accepts, in the web's order. */
private val ATTENDANCE_STATUSES = listOf(
    SelectorOption("present", "Present"),
    SelectorOption("absent", "Absent"),
    SelectorOption("half_day", "Half Day"),
    SelectorOption("leave", "Leave"),
    SelectorOption("holiday", "Holiday"),
    SelectorOption("weekly_holiday", "Weekly Holiday"),
    SelectorOption("late", "Late"),
    SelectorOption("early_out", "Early Out"),
    SelectorOption("pending", "Pending"),
)

/** The web's Attendance Type dropdown (employment types). */
private val ATTENDANCE_TYPES = listOf(
    SelectorOption("monthly", "Monthly Employee"),
    SelectorOption("daily", "Daily Labour"),
    SelectorOption("shifting", "Shift Based Employee"),
)

data class ManualAttendanceUiState(
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val isBranchesLoading: Boolean = false,

    val shifts: List<HrmOption> = emptyList(),
    val selectedShift: HrmOption? = null,

    val employee: SelectorOption? = null,
    val employmentType: SelectorOption = ATTENDANCE_TYPES.first(),
    val date: SimpleDate = SimpleDate.today(),
    val inTime: String = "",
    val outTime: String = "",
    val otHours: String = "",
    val status: SelectorOption = ATTENDANCE_STATUSES.first(),
    val remarks: String = "",

    val isSaving: Boolean = false,
    val showBulkConfirm: Boolean = false,
    val isBulkSaving: Boolean = false,
    val saveMessage: String? = null,
    val saveError: String? = null,

    // The web's Filter & Approval panel: its own branch + date range, independent
    // of the entry form's branch (web binds these to `filters.*`, not `form.*`).
    val listBranch: BranchOption? = null,
    val listFrom: SimpleDate = SimpleDate.today(),
    val listTo: SimpleDate = SimpleDate.today(),
    val entries: List<AttendanceEntry> = emptyList(),
    val isEntriesLoading: Boolean = false,
    val entriesError: String? = null,
    /** Entry id whose approve/reject/delete call is in flight. */
    val actingId: String? = null,
    val isBulkApproving: Boolean = false,
    val showClearConfirm: Boolean = false,
    val isBulkClearing: Boolean = false,

    val sessionExpired: Boolean = false,
) {
    val canSave: Boolean
        get() = selectedBranch != null && employee != null && !isSaving && !isBulkSaving

    val canBulk: Boolean
        get() = selectedBranch != null && !isSaving && !isBulkSaving

    // Anything not already approved is approvable in bulk — pending AND rejected,
    // matching the web's `approval_status !== 'approved'` filter (a rejected entry
    // flips back to approved).
    val approvableCount: Int get() = entries.count { !it.isApproved }

    val approvedIds: List<String> get() = entries.filter { it.isApproved }.map { it.id }

    /** OT hours ("1.5") → minutes, null when blank/invalid. */
    val overtimeMinutes: Int?
        get() = otHours.trim().toDoubleOrNull()?.takeIf { it > 0 }?.times(60)?.toInt()
}

class ManualAttendanceViewModel(
    private val hrmRepository: HrmRepository,
    private val reportRepository: ReportRepository,
    private val selectorRepository: SelectorRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManualAttendanceUiState())
    val uiState: StateFlow<ManualAttendanceUiState> = _uiState.asStateFlow()

    init {
        loadBranches()
        loadShifts()
    }

    private fun loadBranches() {
        _uiState.update { it.copy(isBranchesLoading = true) }
        viewModelScope.launch {
            when (val result = reportRepository.getBranches()) {
                is Resource.Success -> {
                    _uiState.update {
                        val today = result.data.transactionDate ?: it.date
                        val firstBranch = result.data.branches.firstOrNull()
                        it.copy(
                            isBranchesLoading = false,
                            branches = result.data.branches,
                            selectedBranch = it.selectedBranch ?: firstBranch,
                            listBranch = it.listBranch ?: firstBranch,
                            date = today,
                            listFrom = today,
                            listTo = today,
                        )
                    }
                    loadEntries()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isBranchesLoading = false,
                        entriesError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    private fun loadShifts() {
        viewModelScope.launch {
            val result = hrmRepository.getShiftOptions()
            if (result is Resource.Success) {
                _uiState.update { it.copy(shifts = result.data) }
            }
        }
    }

    /** The Filter & Approval panel's Load: existing entries for a branch + range. */
    fun loadEntries() {
        val state = _uiState.value
        // The list is driven by the filter panel's own branch (web: filters.branch_id),
        // falling back to the entry branch before the panel has initialised.
        val branch = state.listBranch ?: state.selectedBranch ?: return
        _uiState.update { it.copy(isEntriesLoading = true, entriesError = null) }
        viewModelScope.launch {
            applyEntriesResult(
                hrmRepository.getAttendanceEntries(
                    branchId = branch.id,
                    dateFrom = state.listFrom.toApi(),
                    dateTo = state.listTo.toApi(),
                )
            )
        }
    }

    /** Success/error handling shared by both loads. */
    private fun applyEntriesResult(result: Resource<List<AttendanceEntry>>) {
        when (result) {
            is Resource.Success -> _uiState.update {
                it.copy(isEntriesLoading = false, entries = result.data)
            }
            is Resource.Error -> _uiState.update {
                it.copy(
                    isEntriesLoading = false,
                    entriesError = result.message,
                    sessionExpired = it.sessionExpired || result.isUnauthorized,
                )
            }
            Resource.Loading -> Unit
        }
    }

    fun onBranchSelected(branch: BranchOption) {
        // Point the list's branch at the entry branch too, so the list keeps
        // tracking it as before; the filter panel can still override it.
        _uiState.update { it.copy(selectedBranch = branch, listBranch = branch, employee = null) }
        loadEntries()
    }

    /** The filter panel's own branch — changed here does not reload until Load. */
    fun onListBranchSelected(branch: BranchOption) =
        _uiState.update { it.copy(listBranch = branch) }

    /**
     * The entry date also refocuses the list on that day, like before — but it
     * leaves the filter panel's branch alone, so changing a date never discards
     * the branch the user picked there (the entry "Load" button is the explicit
     * way to pull the list back to the entry branch).
     */
    fun onDateSelected(date: SimpleDate) {
        _uiState.update { it.copy(date = date, listFrom = date, listTo = date) }
        loadEntries()
    }

    fun onListFrom(date: SimpleDate) {
        _uiState.update {
            val to = if (it.listTo.toApi() < date.toApi()) date else it.listTo
            it.copy(listFrom = date, listTo = to)
        }
    }

    fun onListTo(date: SimpleDate) = _uiState.update { it.copy(listTo = date) }

    fun onEmployeeSelected(option: SelectorOption) = _uiState.update { it.copy(employee = option) }
    fun onShiftSelected(option: HrmOption) = _uiState.update { it.copy(selectedShift = option) }
    fun onTypeSelected(option: SelectorOption) = _uiState.update { it.copy(employmentType = option) }
    fun onStatusSelected(option: SelectorOption) = _uiState.update { it.copy(status = option) }
    fun onInTime(value: String) = _uiState.update { it.copy(inTime = value) }
    fun onOutTime(value: String) = _uiState.update { it.copy(outTime = value) }
    fun onOtHours(value: String) = _uiState.update { it.copy(otHours = value) }
    fun onRemarks(value: String) = _uiState.update { it.copy(remarks = value) }

    /**
     * The table's edit (pencil) action: pull a row's values back into the entry
     * form so it can be adjusted and re-saved (Save Single sends update_existing).
     * Shift isn't restored — a row carries only the shift name, not its id, and
     * the field is optional anyway.
     */
    fun startEdit(entry: AttendanceEntry) = _uiState.update {
        it.copy(
            employee = SelectorOption(entry.employeeId, entry.employeeName),
            employmentType = ATTENDANCE_TYPES.firstOrNull { t -> t.id == entry.employmentType }
                ?: it.employmentType,
            date = SimpleDate.fromApi(entry.date) ?: it.date,
            inTime = entry.inTime,
            outTime = entry.outTime,
            otHours = if (entry.overtimeMinutes > 0) {
                String.format(java.util.Locale.US, "%.2f", entry.overtimeMinutes / 60.0)
            } else {
                ""
            },
            status = ATTENDANCE_STATUSES.firstOrNull { s -> s.id == entry.status } ?: it.status,
            remarks = entry.remarks,
            saveMessage = null,
            saveError = null,
        )
    }

    /**
     * The web's Reset: clear the entry form back to its defaults. Branch and date
     * are kept — on mobile the date drives the loaded list below, so resetting it
     * to today would silently jump that list; the web can afford to because its
     * list has its own separate date filter.
     */
    fun reset() = _uiState.update {
        it.copy(
            employee = null,
            selectedShift = null,
            employmentType = ATTENDANCE_TYPES.first(),
            inTime = "",
            outTime = "",
            otHours = "",
            status = ATTENDANCE_STATUSES.first(),
            remarks = "",
            saveMessage = null,
            saveError = null,
        )
    }

    /**
     * The web's form-panel Load: (re)fetch the list for the entry Date as a single
     * day, mirroring handleBulkLoad's date_from == date_to == attendance_date. The
     * Filter & Approval panel keeps its own range-based Load.
     */
    fun loadForEntryDate() {
        val state = _uiState.value
        val branch = state.selectedBranch ?: return
        // Point the filter panel at the entry's day/branch so the two stay in step,
        // then run the roster load (include_employee_list=1) for that single day —
        // the web's handleBulkLoad, distinct from the filter panel's range Load.
        _uiState.update {
            it.copy(
                listBranch = it.selectedBranch,
                listFrom = it.date,
                listTo = it.date,
                isEntriesLoading = true,
                entriesError = null,
            )
        }
        viewModelScope.launch {
            applyEntriesResult(
                hrmRepository.getAttendanceRoster(
                    branchId = branch.id,
                    date = state.date.toApi(),
                    shiftId = state.selectedShift?.id?.toLongOrNull(),
                    employmentType = state.employmentType.id,
                    inTime = state.inTime,
                    outTime = state.outTime,
                    status = state.status.id,
                    remarks = state.remarks,
                )
            )
        }
    }

    /** Employee typeahead, branch + type scoped like the web's dropdown. */
    suspend fun searchEmployees(query: String): Resource<List<SelectorOption>> =
        selectorRepository.fetch(
            source = ReportSelectorSource.EMPLOYEE,
            query = query,
            branchId = _uiState.value.selectedBranch?.id,
        )

    fun save() {
        val state = _uiState.value
        val branch = state.selectedBranch ?: return
        val employeeId = state.employee?.id?.toLongOrNull() ?: return
        if (!state.canSave) return

        _uiState.update { it.copy(isSaving = true, saveMessage = null, saveError = null) }
        viewModelScope.launch {
            val result = hrmRepository.saveAttendance(
                branchId = branch.id,
                employeeId = employeeId,
                shiftId = state.selectedShift?.id?.toLongOrNull(),
                date = state.date.toApi(),
                inTime = state.inTime,
                outTime = state.outTime,
                status = state.status.id,
                remarks = state.remarks,
                employmentType = state.employmentType.id,
                overtimeMinutes = state.overtimeMinutes,
            )
            when (result) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saveMessage = result.data,
                            employee = null,
                            inTime = "",
                            outTime = "",
                            otHours = "",
                            remarks = "",
                        )
                    }
                    loadEntries()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onBulkClick() = _uiState.update { it.copy(showBulkConfirm = true) }
    fun onBulkDismiss() = _uiState.update { it.copy(showBulkConfirm = false) }

    /** The web's Bulk Entry: every active employee of the branch, one call. */
    fun bulkSave() {
        val state = _uiState.value
        val branch = state.selectedBranch ?: return
        if (state.isBulkSaving) return

        _uiState.update {
            it.copy(showBulkConfirm = false, isBulkSaving = true, saveMessage = null, saveError = null)
        }
        viewModelScope.launch {
            val result = hrmRepository.bulkStoreAttendance(
                branchId = branch.id,
                shiftId = state.selectedShift?.id?.toLongOrNull(),
                employmentType = state.employmentType.id,
                date = state.date.toApi(),
                inTime = state.inTime,
                outTime = state.outTime,
                status = state.status.id,
                remarks = state.remarks,
                overtimeMinutes = state.overtimeMinutes,
            )
            when (result) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isBulkSaving = false, saveMessage = result.data) }
                    loadEntries()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isBulkSaving = false,
                        saveError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun approve(entryId: String, approve: Boolean) {
        if (_uiState.value.actingId != null) return
        _uiState.update { it.copy(actingId = entryId, saveError = null) }
        viewModelScope.launch {
            val result = hrmRepository.approveAttendance(entryId, approve, remarks = null)
            when (result) {
                is Resource.Success -> {
                    _uiState.update { it.copy(actingId = null, saveMessage = result.data) }
                    loadEntries()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        actingId = null,
                        saveError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun delete(entryId: String) {
        if (_uiState.value.actingId != null) return
        _uiState.update { it.copy(actingId = entryId, saveError = null) }
        viewModelScope.launch {
            val result = hrmRepository.deleteAttendance(entryId)
            when (result) {
                is Resource.Success -> {
                    _uiState.update { it.copy(actingId = null, saveMessage = result.data) }
                    loadEntries()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        actingId = null,
                        saveError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    /** The web's Bulk Approve: approves every not-yet-approved entry (pending or
     *  rejected) in the list. */
    fun bulkApprove() {
        val approvable = _uiState.value.entries.filter { !it.isApproved }
        if (approvable.isEmpty() || _uiState.value.isBulkApproving) return

        _uiState.update { it.copy(isBulkApproving = true, saveError = null, saveMessage = null) }
        viewModelScope.launch {
            var approved = 0
            var failed = 0
            approvable.forEach { entry ->
                when (hrmRepository.approveAttendance(entry.id, approve = true, remarks = null)) {
                    is Resource.Success -> approved++
                    else -> failed++
                }
            }
            _uiState.update {
                it.copy(
                    isBulkApproving = false,
                    saveMessage = "Approved $approved entr${if (approved == 1) "y" else "ies"}" +
                        if (failed > 0) ", $failed failed" else "",
                )
            }
            loadEntries()
        }
    }

    fun onClearClick() = _uiState.update { it.copy(showClearConfirm = true) }
    fun onClearDismiss() = _uiState.update { it.copy(showClearConfirm = false) }

    /** The web's Bulk Clear: removes the list's APPROVED entries. */
    fun bulkClear() {
        val approvedIds = _uiState.value.approvedIds
        if (approvedIds.isEmpty() || _uiState.value.isBulkClearing) return

        _uiState.update {
            it.copy(showClearConfirm = false, isBulkClearing = true, saveError = null, saveMessage = null)
        }
        viewModelScope.launch {
            when (val result = hrmRepository.bulkClearAttendance(approvedIds)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isBulkClearing = false, saveMessage = result.data) }
                    loadEntries()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isBulkClearing = false,
                        saveError = result.message,
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
                val appContext = context.applicationContext
                ManualAttendanceViewModel(
                    hrmRepository = ServiceLocator.provideHrmRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                    selectorRepository = ServiceLocator.provideSelectorRepository(appContext),
                )
            }
        }
    }
}

/**
 * Manual Attendance — the web page's three panels: the single/bulk entry form,
 * the Filter & Approval range with bulk actions, and the entry list with
 * approve/reject/delete per row.
 */
@Composable
fun ManualAttendanceScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: ManualAttendanceViewModel =
        viewModel(factory = ManualAttendanceViewModel.provideFactory(context))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.sessionExpired) {
        LaunchedEffect(Unit) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    if (state.showBulkConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::onBulkDismiss,
            title = { Text("Bulk attendance?") },
            text = {
                Text(
                    "This will save a \"${state.status.label}\" entry for EVERY active " +
                        "${state.employmentType.label.lowercase()} of this branch on " +
                        "${state.date.toDisplay()}. Existing pending entries update; " +
                        "approved ones are untouched.",
                )
            },
            confirmButton = { LinkButton(text = "Save All", onClick = viewModel::bulkSave) },
            dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::onBulkDismiss) },
        )
    }

    if (state.showClearConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::onClearDismiss,
            title = { Text("Clear approved entries?") },
            text = {
                Text(
                    "This removes the ${state.approvedIds.size} APPROVED entr" +
                        "${if (state.approvedIds.size == 1) "y" else "ies"} in this list " +
                        "so they can be re-entered.",
                )
            },
            confirmButton = { LinkButton(text = "Clear", onClick = viewModel::bulkClear) },
            dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::onClearDismiss) },
        )
    }

    AuthenticatedShell(
        title = "Manual Attendance",
        currentRoute = Routes.HRM,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        ) {
            item {
                SectionTitle("Attendance Entry")
                HrmBranchDropdown(
                    branches = state.branches,
                    selected = state.selectedBranch,
                    isLoading = state.isBranchesLoading,
                    onSelected = viewModel::onBranchSelected,
                )
                Spacer(Modifier.height(12.dp))
                SearchableSelectDropdown(
                    selected = state.employee,
                    onSelected = viewModel::onEmployeeSelected,
                    search = viewModel::searchEmployees,
                    label = "Select Employee",
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        AppSelectDropdown(
                            label = "Shift (optional)",
                            options = state.shifts.map { SelectorOption(it.id, it.name) },
                            selected = state.selectedShift?.let { SelectorOption(it.id, it.name) },
                            onSelected = { option ->
                                viewModel.onShiftSelected(HrmOption(option.id, option.label))
                            },
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        AppSelectDropdown(
                            label = "Attendance Type",
                            options = ATTENDANCE_TYPES,
                            selected = state.employmentType,
                            onSelected = viewModel::onTypeSelected,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HrmDateField(
                        label = "Date",
                        value = state.date,
                        context = context,
                        onSelected = viewModel::onDateSelected,
                        modifier = Modifier.weight(1f),
                    )
                    HrmTimeField(
                        label = "In Time",
                        value = state.inTime,
                        context = context,
                        onSelected = viewModel::onInTime,
                        modifier = Modifier.weight(1f),
                    )
                    HrmTimeField(
                        label = "Out Time",
                        value = state.outTime,
                        context = context,
                        onSelected = viewModel::onOutTime,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(2f)) {
                        AppSelectDropdown(
                            label = "Status",
                            options = ATTENDANCE_STATUSES,
                            selected = state.status,
                            onSelected = viewModel::onStatusSelected,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        FieldFrame(label = "OT Hr.") {
                            FieldTextInput(
                                value = state.otHours,
                                onValueChange = viewModel::onOtHours,
                                placeholder = "0.00",
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                FieldFrame(label = "Remarks") {
                    FieldTextInput(
                        value = state.remarks,
                        onValueChange = viewModel::onRemarks,
                        placeholder = "Remarks (optional)",
                    )
                }
                Spacer(Modifier.height(16.dp))
                // The web's four-button command row: Save Single, Bulk Entry,
                // Reset, Load — two per row so the labels stay readable on a phone.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(
                        text = "Save Single",
                        onClick = viewModel::save,
                        enabled = state.canSave,
                        isLoading = state.isSaving,
                        icon = Icons.Filled.Check,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = "Bulk Entry",
                        onClick = viewModel::onBulkClick,
                        enabled = state.canBulk,
                        icon = Icons.Filled.Person,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(
                        text = "Reset",
                        onClick = viewModel::reset,
                        enabled = !state.isSaving && !state.isBulkSaving,
                        icon = Icons.Filled.Refresh,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = "Load",
                        onClick = viewModel::loadForEntryDate,
                        enabled = state.selectedBranch != null && !state.isEntriesLoading,
                        icon = Icons.Filled.Search,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (state.isBulkSaving) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp), color = MaterialTheme.colorScheme.onBackground)
                        Spacer(Modifier.height(0.dp))
                        Text(
                            "  Saving bulk attendance…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        )
                    }
                }

                state.saveMessage?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
                state.saveError?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(24.dp))
                // The web's Filter & Approval panel: a badged header, then the
                // list's own branch + date range, then the three bulk actions.
                FilterApprovalHeader()
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    HrmDateField(
                        label = "From",
                        value = state.listFrom,
                        context = context,
                        onSelected = viewModel::onListFrom,
                        modifier = Modifier.weight(1f),
                    )
                    HrmDateField(
                        label = "To",
                        value = state.listTo,
                        context = context,
                        onSelected = viewModel::onListTo,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    HrmBranchDropdown(
                        branches = state.branches,
                        selected = state.listBranch,
                        isLoading = state.isBranchesLoading,
                        onSelected = viewModel::onListBranchSelected,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = "Load",
                        onClick = viewModel::loadEntries,
                        enabled = !state.isEntriesLoading,
                        icon = Icons.Filled.Search,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(
                        text = "Bulk Approve",
                        onClick = viewModel::bulkApprove,
                        enabled = state.approvableCount > 0 && !state.isBulkApproving,
                        isLoading = state.isBulkApproving,
                        icon = Icons.Filled.Check,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = "Bulk Clear",
                        onClick = viewModel::onClearClick,
                        enabled = state.approvedIds.isNotEmpty() && !state.isBulkClearing,
                        icon = Icons.Filled.Delete,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(20.dp))
                AttendanceListHeader(count = state.entries.size)
                Spacer(Modifier.height(8.dp))
            }

            when {
                state.isEntriesLoading -> item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground) }
                }
                state.entriesError != null -> item {
                    Text(
                        text = state.entriesError.orEmpty(),
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                state.entries.isEmpty() -> item {
                    Text(
                        text = "No attendance entries in this range.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                // The whole table is one item: header and rows share a single
                // horizontal scroll so their columns stay aligned, and the outer
                // LazyColumn keeps handling the vertical scroll.
                else -> item {
                    AttendanceTable(entries = state.entries, onEdit = viewModel::startEdit)
                }
            }
        }
    }
}

/** A small label over its value — the compact report-row cell used across HRM. */
@Composable
internal fun LabelValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

/** The web panel's badged header: a tinted search icon over title + subtitle. */
@Composable
private fun FilterApprovalHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    RoundedCornerShape(8.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Column {
            Text(
                text = "Filter & Approval",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Load entries and approve in bulk",
                style = MaterialTheme.typography.bodySmall,
                // onSurfaceVariant reads as a low-contrast maroon on this screen's
                // teal background (the header isn't inside a surface/card); the
                // dimmed onBackground the rest of the screen uses stays legible.
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
            )
        }
    }
}

/** The list header: a badged people icon, the title, and an "N entries" pill. */
@Composable
private fun AttendanceListHeader(count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = "Attendance List",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = "$count entries",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Fixed column widths, so the header and every body row line up under one scroll. */
private object AttCol {
    val sl = 44.dp
    val date = 76.dp
    val employee = 150.dp
    val type = 78.dp
    val shift = 64.dp
    val inT = 58.dp
    val outT = 58.dp
    val ot = 64.dp
    val status = 92.dp
    val approval = 88.dp
    val action = 60.dp
}

/**
 * The web's Attendance List table. All 11 columns are wider than a phone, so the
 * header and rows share one horizontal scroll (they align because they use the
 * same fixed widths); the outer LazyColumn keeps the vertical scroll.
 */
@Composable
private fun AttendanceTable(entries: List<AttendanceEntry>, onEdit: (AttendanceEntry) -> Unit) {
    val hScroll = rememberScrollState()
    val border = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, border, RoundedCornerShape(8.dp)),
    ) {
        Column(Modifier.horizontalScroll(hScroll)) {
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderCell("#", AttCol.sl)
                HeaderCell("DATE", AttCol.date)
                HeaderCell("EMPLOYEE", AttCol.employee)
                HeaderCell("TYPE", AttCol.type)
                HeaderCell("SHIFT", AttCol.shift)
                HeaderCell("IN", AttCol.inT)
                HeaderCell("OUT", AttCol.outT)
                HeaderCell("OT HR.", AttCol.ot)
                HeaderCell("STATUS", AttCol.status)
                HeaderCell("APPROVAL", AttCol.approval)
                HeaderCell("ACTION", AttCol.action)
            }
            entries.forEachIndexed { index, entry ->
                HorizontalDivider(color = border.copy(alpha = 0.6f))
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BodyCell("${index + 1}", AttCol.sl)
                    // dd/MM/yy — the row carries the yyyy-MM-dd wire date.
                    BodyCell(SimpleDate.fromApi(entry.date)?.toShortDisplay() ?: entry.date, AttCol.date)
                    BodyCell(
                        entry.employeeName +
                            entry.employeeSerial.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty(),
                        AttCol.employee,
                    )
                    BodyCell(entry.employmentType.ifBlank { "-" }, AttCol.type)
                    BodyCell(entry.shiftName.ifBlank { "-" }, AttCol.shift)
                    BodyCell(entry.inTime.ifBlank { "-" }, AttCol.inT)
                    BodyCell(entry.outTime.ifBlank { "-" }, AttCol.outT)
                    BodyCell(
                        if (entry.overtimeMinutes > 0) {
                            String.format(java.util.Locale.US, "%.2f", entry.overtimeMinutes / 60.0)
                        } else {
                            "0.00"
                        },
                        AttCol.ot,
                    )
                    CellBox(AttCol.status) { StatusPill(entry.status) }
                    CellBox(AttCol.approval) { ApprovalPill(entry.approvalStatus) }
                    CellBox(AttCol.action) {
                        IconButton(onClick = { onEdit(entry) }, modifier = Modifier.size(32.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Create,
                                    contentDescription = "Edit ${entry.employeeName}",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp) {
    Text(
        text = text,
        modifier = Modifier.width(width).padding(horizontal = 8.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

@Composable
private fun BodyCell(text: String, width: Dp) {
    Text(
        text = text,
        modifier = Modifier.width(width).padding(horizontal = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun CellBox(width: Dp, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.width(width).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** A colored status pill — green for Present, red for Absent, muted otherwise. */
@Composable
private fun StatusPill(status: String) {
    if (status.isBlank()) {
        Text("-", style = MaterialTheme.typography.labelSmall)
        return
    }
    val color = when (status.lowercase()) {
        "present", "late" -> Color(0xFF16A34A)
        "absent" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = status.replace('_', ' ').replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
        )
    }
}

/** A muted approval pill; "-" for roster rows that have no entry (approval) yet. */
@Composable
private fun ApprovalPill(approval: String) {
    if (approval.isBlank()) {
        Text("-", style = MaterialTheme.typography.labelSmall)
        return
    }
    val color = when (approval.lowercase()) {
        "approved" -> Color(0xFF16A34A)
        "rejected" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = approval.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
        )
    }
}
