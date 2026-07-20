package com.example.cashbookbd.ui.hrm

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

    // The web's Filter & Approval panel (the list's own range).
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

    val pendingCount: Int get() = entries.count { it.isPendingApproval }

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
                        it.copy(
                            isBranchesLoading = false,
                            branches = result.data.branches,
                            selectedBranch = it.selectedBranch ?: result.data.branches.firstOrNull(),
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

    fun loadEntries() {
        val state = _uiState.value
        val branch = state.selectedBranch ?: return
        _uiState.update { it.copy(isEntriesLoading = true, entriesError = null) }
        viewModelScope.launch {
            val result = hrmRepository.getAttendanceEntries(
                branchId = branch.id,
                dateFrom = state.listFrom.toApi(),
                dateTo = state.listTo.toApi(),
            )
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
    }

    fun onBranchSelected(branch: BranchOption) {
        _uiState.update { it.copy(selectedBranch = branch, employee = null) }
        loadEntries()
    }

    /** The entry date also refocuses the list on that day, like before. */
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

    /** The web's Bulk Approve: approves every pending entry in the list. */
    fun bulkApprove() {
        val pending = _uiState.value.entries.filter { it.isPendingApproval }
        if (pending.isEmpty() || _uiState.value.isBulkApproving) return

        _uiState.update { it.copy(isBulkApproving = true, saveError = null, saveMessage = null) }
        viewModelScope.launch {
            var approved = 0
            var failed = 0
            pending.forEach { entry ->
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(
                        text = "Save Single",
                        onClick = viewModel::save,
                        enabled = state.canSave,
                        isLoading = state.isSaving,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = "Bulk Entry",
                        onClick = viewModel::onBulkClick,
                        enabled = state.canBulk,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (state.isBulkSaving) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp))
                        Spacer(Modifier.height(0.dp))
                        Text(
                            "  Saving bulk attendance…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                state.saveMessage?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
                state.saveError?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(20.dp))
                SectionTitle("Filter & Approval")
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
                    SecondaryButton(
                        text = "Load",
                        onClick = viewModel::loadEntries,
                        enabled = !state.isEntriesLoading,
                        compact = true,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(
                        text = "Bulk Approve (${state.pendingCount})",
                        onClick = viewModel::bulkApprove,
                        enabled = state.pendingCount > 0 && !state.isBulkApproving,
                        isLoading = state.isBulkApproving,
                        compact = true,
                    )
                    SecondaryButton(
                        text = "Bulk Clear (${state.approvedIds.size})",
                        onClick = viewModel::onClearClick,
                        enabled = state.approvedIds.isNotEmpty() && !state.isBulkClearing,
                        compact = true,
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Attendance List — ${state.entries.size} entries",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
            }

            when {
                state.isEntriesLoading -> item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) { CircularProgressIndicator() }
                }
                state.entriesError != null -> item {
                    Text(
                        text = state.entriesError.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                state.entries.isEmpty() -> item {
                    Text(
                        text = "No attendance entries in this range.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                else -> items(state.entries, key = { it.id }) { entry ->
                    AttendanceEntryRow(
                        entry = entry,
                        isActing = state.actingId == entry.id,
                        onApprove = { viewModel.approve(entry.id, approve = true) },
                        onReject = { viewModel.approve(entry.id, approve = false) },
                        onDelete = { viewModel.delete(entry.id) },
                    )
                    Spacer(Modifier.height(8.dp))
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

/** One compact entry row: identity, times, type/OT, and the action buttons. */
@Composable
private fun AttendanceEntryRow(
    entry: AttendanceEntry,
    isActing: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.employeeName +
                            entry.employeeSerial.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = entry.date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = entry.status.replace('_', ' '),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row {
                LabelValue("In", entry.inTime.ifBlank { "-" }, Modifier.weight(1f))
                LabelValue("Out", entry.outTime.ifBlank { "-" }, Modifier.weight(1f))
                LabelValue("Type", entry.employmentType.ifBlank { "-" }, Modifier.weight(1f))
                LabelValue(
                    "OT Hr.",
                    if (entry.overtimeMinutes > 0) {
                        String.format(java.util.Locale.US, "%.2f", entry.overtimeMinutes / 60.0)
                    } else {
                        "-"
                    },
                    Modifier.weight(1f),
                )
                LabelValue("Approval", entry.approvalStatus.ifBlank { "-" }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (entry.isPendingApproval) {
                    PrimaryButton(
                        text = "Approve",
                        onClick = onApprove,
                        isLoading = isActing,
                        compact = true,
                    )
                    SecondaryButton(
                        text = "Reject",
                        onClick = onReject,
                        enabled = !isActing,
                        compact = true,
                    )
                }
                if (!entry.isApproved) {
                    LinkButton(text = "Delete", onClick = onDelete, enabled = !isActing)
                }
            }
        }
    }
}
