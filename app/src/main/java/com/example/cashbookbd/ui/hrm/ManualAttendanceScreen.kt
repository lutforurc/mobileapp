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

data class ManualAttendanceUiState(
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val isBranchesLoading: Boolean = false,

    val shifts: List<HrmOption> = emptyList(),
    val selectedShift: HrmOption? = null,

    val employee: SelectorOption? = null,
    val date: SimpleDate = SimpleDate.today(),
    val inTime: String = "",
    val outTime: String = "",
    val status: SelectorOption = ATTENDANCE_STATUSES.first(),
    val remarks: String = "",

    val isSaving: Boolean = false,
    val saveMessage: String? = null,
    val saveError: String? = null,

    val entries: List<AttendanceEntry> = emptyList(),
    val isEntriesLoading: Boolean = false,
    val entriesError: String? = null,
    /** Entry id whose approve/reject call is in flight. */
    val approvingId: String? = null,

    val sessionExpired: Boolean = false,
) {
    val canSave: Boolean
        get() = selectedBranch != null && employee != null && !isSaving
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
                        it.copy(
                            isBranchesLoading = false,
                            branches = result.data.branches,
                            selectedBranch = it.selectedBranch ?: result.data.branches.firstOrNull(),
                            date = result.data.transactionDate ?: it.date,
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
            when (val result = hrmRepository.getAttendanceEntries(branch.id, state.date.toApi())) {
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

    fun onDateSelected(date: SimpleDate) {
        _uiState.update { it.copy(date = date) }
        loadEntries()
    }

    fun onEmployeeSelected(option: SelectorOption) = _uiState.update { it.copy(employee = option) }
    fun onShiftSelected(option: HrmOption) = _uiState.update { it.copy(selectedShift = option) }
    fun onStatusSelected(option: SelectorOption) = _uiState.update { it.copy(status = option) }
    fun onInTime(value: String) = _uiState.update { it.copy(inTime = value) }
    fun onOutTime(value: String) = _uiState.update { it.copy(outTime = value) }
    fun onRemarks(value: String) = _uiState.update { it.copy(remarks = value) }

    /** Employee typeahead, branch-scoped like the web's dropdown. */
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
        if (state.isSaving) return

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

    fun approve(entryId: String, approve: Boolean) {
        if (_uiState.value.approvingId != null) return
        _uiState.update { it.copy(approvingId = entryId, saveError = null) }
        viewModelScope.launch {
            val result = hrmRepository.approveAttendance(entryId, approve, remarks = null)
            when (result) {
                is Resource.Success -> {
                    _uiState.update { it.copy(approvingId = null, saveMessage = result.data) }
                    loadEntries()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        approvingId = null,
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
 * Manual Attendance: the web page's single-entry form (employee, shift, date,
 * in/out, status) over the day's entry list with approve/reject actions.
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
                AppSelectDropdown(
                    label = "Shift (optional)",
                    options = state.shifts.map { SelectorOption(it.id, it.name) },
                    selected = state.selectedShift?.let { SelectorOption(it.id, it.name) },
                    onSelected = { option -> viewModel.onShiftSelected(HrmOption(option.id, option.label)) },
                )
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
                AppSelectDropdown(
                    label = "Status",
                    options = ATTENDANCE_STATUSES,
                    selected = state.status,
                    onSelected = viewModel::onStatusSelected,
                )
                Spacer(Modifier.height(12.dp))
                FieldFrame(label = "Remarks") {
                    FieldTextInput(
                        value = state.remarks,
                        onValueChange = viewModel::onRemarks,
                        placeholder = "Remarks (optional)",
                    )
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    text = "Save Attendance",
                    onClick = viewModel::save,
                    enabled = state.canSave,
                    isLoading = state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )

                state.saveMessage?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
                state.saveError?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Entries — ${state.date.toDisplay()}",
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
                        text = "No attendance entries for this date.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                else -> items(state.entries, key = { it.id }) { entry ->
                    AttendanceEntryRow(
                        entry = entry,
                        isApproving = state.approvingId == entry.id,
                        onApprove = { viewModel.approve(entry.id, approve = true) },
                        onReject = { viewModel.approve(entry.id, approve = false) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

/** One compact entry row: identity line, times line, status + approval actions. */
@Composable
private fun AttendanceEntryRow(
    entry: AttendanceEntry,
    isApproving: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.employeeName +
                        entry.employeeSerial.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
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
                LabelValue("Shift", entry.shiftName.ifBlank { "-" }, Modifier.weight(1f))
                LabelValue("Approval", entry.approvalStatus.ifBlank { "-" }, Modifier.weight(1f))
            }
            if (entry.isPendingApproval) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(
                        text = "Approve",
                        onClick = onApprove,
                        isLoading = isApproving,
                        compact = true,
                    )
                    SecondaryButton(
                        text = "Reject",
                        onClick = onReject,
                        enabled = !isApproving,
                        compact = true,
                    )
                }
            }
        }
    }
}

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
