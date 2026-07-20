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
import com.example.cashbookbd.ui.hrm.model.HrmOption
import com.example.cashbookbd.ui.hrm.model.LeaveApplication
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LeaveApplicationsUiState(
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val isBranchesLoading: Boolean = false,

    val leaveTypes: List<HrmOption> = emptyList(),
    val selectedLeaveType: HrmOption? = null,
    val employee: SelectorOption? = null,
    val fromDate: SimpleDate = SimpleDate.today(),
    val toDate: SimpleDate = SimpleDate.today(),
    val reason: String = "",

    val isSaving: Boolean = false,
    val saveMessage: String? = null,
    val saveError: String? = null,

    val applications: List<LeaveApplication> = emptyList(),
    val isListLoading: Boolean = false,
    val listError: String? = null,
    val approvingId: String? = null,

    val sessionExpired: Boolean = false,
) {
    val canSave: Boolean
        get() = selectedBranch != null && employee != null && selectedLeaveType != null && !isSaving
}

class LeaveApplicationsViewModel(
    private val hrmRepository: HrmRepository,
    private val reportRepository: ReportRepository,
    private val selectorRepository: SelectorRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaveApplicationsUiState())
    val uiState: StateFlow<LeaveApplicationsUiState> = _uiState.asStateFlow()

    init {
        loadBranches()
        loadLeaveTypes()
    }

    private fun loadBranches() {
        _uiState.update { it.copy(isBranchesLoading = true) }
        viewModelScope.launch {
            when (val result = reportRepository.getBranches()) {
                is Resource.Success -> {
                    val today = result.data.transactionDate ?: SimpleDate.today()
                    _uiState.update {
                        it.copy(
                            isBranchesLoading = false,
                            branches = result.data.branches,
                            selectedBranch = it.selectedBranch ?: result.data.branches.firstOrNull(),
                            fromDate = today,
                            toDate = today,
                        )
                    }
                    loadApplications()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isBranchesLoading = false,
                        listError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    private fun loadLeaveTypes() {
        viewModelScope.launch {
            val result = hrmRepository.getLeaveTypeOptions()
            if (result is Resource.Success) {
                _uiState.update { it.copy(leaveTypes = result.data) }
            }
        }
    }

    /** The list covers the whole month around the selected From date. */
    fun loadApplications() {
        val state = _uiState.value
        val branch = state.selectedBranch ?: return
        val monthStart = state.fromDate.copy(day = 1)
        val monthEnd = state.fromDate.copy(day = daysInMonth(state.fromDate))
        _uiState.update { it.copy(isListLoading = true, listError = null) }
        viewModelScope.launch {
            val result = hrmRepository.getLeaveApplications(
                branchId = branch.id,
                dateFrom = monthStart.toApi(),
                dateTo = monthEnd.toApi(),
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isListLoading = false, applications = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isListLoading = false,
                        listError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    private fun daysInMonth(date: SimpleDate): Int {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(date.year, date.month - 1, 1)
        return calendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    }

    fun onBranchSelected(branch: BranchOption) {
        _uiState.update { it.copy(selectedBranch = branch, employee = null) }
        loadApplications()
    }

    fun onFromDate(date: SimpleDate) {
        _uiState.update {
            // Keep To >= From (ISO strings compare correctly).
            val to = if (it.toDate.toApi() < date.toApi()) date else it.toDate
            it.copy(fromDate = date, toDate = to)
        }
        loadApplications()
    }

    fun onToDate(date: SimpleDate) = _uiState.update { it.copy(toDate = date) }
    fun onEmployeeSelected(option: SelectorOption) = _uiState.update { it.copy(employee = option) }
    fun onLeaveTypeSelected(option: HrmOption) = _uiState.update { it.copy(selectedLeaveType = option) }
    fun onReason(value: String) = _uiState.update { it.copy(reason = value) }

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
        val leaveType = state.selectedLeaveType ?: return
        if (state.isSaving) return

        _uiState.update { it.copy(isSaving = true, saveMessage = null, saveError = null) }
        viewModelScope.launch {
            val result = hrmRepository.saveLeaveApplication(
                branchId = branch.id,
                employeeId = employeeId,
                leaveTypeId = leaveType.id,
                fromDate = state.fromDate.toApi(),
                toDate = state.toDate.toApi(),
                reason = state.reason,
            )
            when (result) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(isSaving = false, saveMessage = result.data, employee = null, reason = "")
                    }
                    loadApplications()
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

    fun approve(applicationId: String, approve: Boolean) {
        if (_uiState.value.approvingId != null) return
        _uiState.update { it.copy(approvingId = applicationId, saveError = null) }
        viewModelScope.launch {
            val result = hrmRepository.approveLeave(applicationId, approve, remarks = null)
            when (result) {
                is Resource.Success -> {
                    _uiState.update { it.copy(approvingId = null, saveMessage = result.data) }
                    loadApplications()
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
                LeaveApplicationsViewModel(
                    hrmRepository = ServiceLocator.provideHrmRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                    selectorRepository = ServiceLocator.provideSelectorRepository(appContext),
                )
            }
        }
    }
}

/**
 * Leave Applications: the web page's apply form (employee, leave type, dates,
 * reason) over the month's application list with approve/reject actions.
 */
@Composable
fun LeaveApplicationsScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: LeaveApplicationsViewModel =
        viewModel(factory = LeaveApplicationsViewModel.provideFactory(context))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.sessionExpired) {
        LaunchedEffect(Unit) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    AuthenticatedShell(
        title = "Leave Applications",
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
                    label = "Leave Type",
                    options = state.leaveTypes.map { SelectorOption(it.id, it.name) },
                    selected = state.selectedLeaveType?.let { SelectorOption(it.id, it.name) },
                    onSelected = { option -> viewModel.onLeaveTypeSelected(HrmOption(option.id, option.label)) },
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HrmDateField(
                        label = "From",
                        value = state.fromDate,
                        context = context,
                        onSelected = viewModel::onFromDate,
                        modifier = Modifier.weight(1f),
                    )
                    HrmDateField(
                        label = "To",
                        value = state.toDate,
                        context = context,
                        onSelected = viewModel::onToDate,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                FieldFrame(label = "Reason") {
                    FieldTextInput(
                        value = state.reason,
                        onValueChange = viewModel::onReason,
                        placeholder = "Reason (optional)",
                    )
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    text = "Apply Leave",
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
                    text = "Applications — this month",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
            }

            when {
                state.isListLoading -> item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) { CircularProgressIndicator() }
                }
                state.listError != null -> item {
                    Text(
                        text = state.listError.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                state.applications.isEmpty() -> item {
                    Text(
                        text = "No leave applications in this period.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                else -> items(state.applications, key = { it.id }) { application ->
                    LeaveApplicationRow(
                        application = application,
                        isApproving = state.approvingId == application.id,
                        onApprove = { viewModel.approve(application.id, approve = true) },
                        onReject = { viewModel.approve(application.id, approve = false) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun LeaveApplicationRow(
    application: LeaveApplication,
    isApproving: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = application.employeeName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = application.approvalStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row {
                LabelValue("From", application.fromDate, Modifier.weight(1f))
                LabelValue("To", application.toDate, Modifier.weight(1f))
                LabelValue("Type", application.leaveTypeName.ifBlank { "-" }, Modifier.weight(1f))
                LabelValue("Days", application.requestedDays.ifBlank { "-" }, Modifier.weight(1f))
            }
            if (application.reason.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = application.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (application.isPendingApproval) {
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
