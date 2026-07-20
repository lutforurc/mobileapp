package com.example.cashbookbd.ui.hrm

import android.app.DatePickerDialog
import android.content.Context
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
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
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.FieldFrame
import com.example.cashbookbd.ui.components.FieldTextInput
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.hrm.model.EmployeeSettings
import com.example.cashbookbd.ui.hrm.model.HrmOption
import com.example.cashbookbd.ui.reports.PickerField
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

// Static choice lists, mirroring the web form's DataConstant values.
private val STATUS_OPTIONS = listOf(
    SelectorOption("1", "Active"),
    SelectorOption("0", "Inactive"),
    SelectorOption("2", "Pending"),
)
private val EMPLOYMENT_TYPES = listOf(
    SelectorOption("monthly", "Monthly Employee"),
    SelectorOption("daily", "Daily Labour"),
    SelectorOption("shifting", "Shift Based Employee"),
)
private val PAYABLE_OPTIONS = listOf(
    SelectorOption("1", "Payable"),
    SelectorOption("0", "Not Payable"),
)
private val YES_NO_OPTIONS = listOf(
    SelectorOption("0", "No"),
    SelectorOption("1", "Yes"),
)

data class EmployeeFormUiState(
    /** Null while adding; the raw list-row id while editing. */
    val employeeId: String? = null,
    val isLoading: Boolean = false,
    val loadError: String? = null,

    val settings: EmployeeSettings? = null,
    val shifts: List<HrmOption> = emptyList(),
    val policies: List<HrmOption> = emptyList(),

    // Personal
    val name: String = "",
    val fatherName: String = "",
    val mobile: String = "",
    val nid: String = "",
    val dateOfBirth: String = "",
    val selectedSex: SelectorOption? = null,
    val qualification: String = "",
    val presentAddress: String = "",
    val permanentAddress: String = "",

    // Job
    val selectedBranch: SelectorOption? = null,
    val selectedDesignation: SelectorOption? = null,
    val employeeSerial: String = "",
    val joiningDate: String = "",
    val selectedStatus: SelectorOption = STATUS_OPTIONS.first(),
    val selectedEmploymentType: SelectorOption = EMPLOYMENT_TYPES.first(),

    // Salary
    val basicSalary: String = "",
    val houseRent: String = "",
    val medical: String = "",
    val othersAllowance: String = "",
    val loanDeduction: String = "",
    val othersDeduction: String = "",
    val selectedPayable: SelectorOption = PAYABLE_OPTIONS.first(),

    // Attendance / overtime
    val selectedPolicy: SelectorOption? = null,
    val selectedShift: SelectorOption? = null,
    val selectedOvertimeEligible: SelectorOption = YES_NO_OPTIONS.first(),
    val dailyWage: String = "",
    val otRate: String = "",
    val standardWorkMinutes: String = "480",

    val isSaving: Boolean = false,
    val saveError: String? = null,
    /** Set on success; the screen posts it back to the list and pops. */
    val savedMessage: String? = null,

    val sessionExpired: Boolean = false,
) {
    val isEdit: Boolean get() = employeeId != null

    val canSave: Boolean
        get() = name.isNotBlank() && selectedBranch != null && selectedSex != null && !isSaving
}

class EmployeeFormViewModel(
    private val employeeId: String?,
    private val hrmRepository: HrmRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        EmployeeFormUiState(employeeId = employeeId, isLoading = true),
    )
    val uiState: StateFlow<EmployeeFormUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            // Lookups first, so an edit can resolve its selections against them.
            val settings = hrmRepository.getEmployeeSettings()
            if (settings is Resource.Error) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = settings.message,
                        sessionExpired = it.sessionExpired || settings.isUnauthorized,
                    )
                }
                return@launch
            }
            val bundle = (settings as Resource.Success).data
            val shifts = (hrmRepository.getShiftOptions() as? Resource.Success)?.data.orEmpty()
            val policies = (hrmRepository.getPolicyOptions() as? Resource.Success)?.data.orEmpty()
            _uiState.update {
                it.copy(settings = bundle, shifts = shifts, policies = policies)
            }

            if (employeeId == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            when (val employee = hrmRepository.getEmployee(employeeId)) {
                is Resource.Success -> {
                    val detail = employee.data
                    fun pick(options: List<HrmOption>, id: String): SelectorOption? =
                        options.firstOrNull { it.id == id }?.let { SelectorOption(it.id, it.name) }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            name = detail.name,
                            fatherName = detail.fatherName,
                            mobile = detail.mobile,
                            nid = detail.nid,
                            dateOfBirth = toDisplayDate(detail.dateOfBirth),
                            selectedSex = pick(bundle.sexes, detail.sexId),
                            qualification = detail.qualification,
                            presentAddress = detail.presentAddress,
                            permanentAddress = detail.permanentAddress,
                            // The employee's branch may not be in this user's own
                            // branch list — keep it selectable anyway so an update
                            // can't silently move (or blank) the employee's project.
                            selectedBranch = pick(bundle.branches, detail.projectId)
                                ?: detail.projectId.takeIf { id -> id.isNotBlank() }?.let { id ->
                                    SelectorOption(id, detail.branchName.ifBlank { "Branch #$id" })
                                },
                            selectedDesignation = pick(bundle.designations, detail.designationId),
                            employeeSerial = detail.employeeSerial,
                            joiningDate = toDisplayDate(detail.joiningDate),
                            selectedStatus = STATUS_OPTIONS.firstOrNull { s -> s.id == detail.status }
                                ?: STATUS_OPTIONS.first(),
                            selectedEmploymentType = EMPLOYMENT_TYPES
                                .firstOrNull { t -> t.id == detail.employmentType }
                                ?: EMPLOYMENT_TYPES.first(),
                            basicSalary = detail.basicSalary,
                            houseRent = detail.houseRent,
                            medical = detail.medicalAllowance,
                            othersAllowance = detail.othersAllowance,
                            loanDeduction = detail.loanDeduction,
                            othersDeduction = detail.othersDeduction,
                            selectedPayable = PAYABLE_OPTIONS
                                .firstOrNull { p -> p.id == detail.salaryPayable }
                                ?: PAYABLE_OPTIONS.first(),
                            selectedPolicy = pick(policies, detail.attendancePolicyId),
                            selectedShift = pick(shifts, detail.defaultShiftId),
                            selectedOvertimeEligible = YES_NO_OPTIONS
                                .firstOrNull { o -> o.id == detail.overtimeEligible }
                                ?: YES_NO_OPTIONS.first(),
                            dailyWage = detail.dailyWage,
                            otRate = detail.otRate,
                            standardWorkMinutes = detail.standardWorkMinutes.ifBlank { "480" },
                        )
                    }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = employee.message,
                        sessionExpired = it.sessionExpired || employee.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    /** "2001-02-13" (DB) or "13/02/2001" (already display) → "13/02/2001". */
    private fun toDisplayDate(raw: String): String {
        val trimmed = raw.trim()
        Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(trimmed)?.let { match ->
            val (year, month, day) = match.destructured
            return "$day/$month/$year"
        }
        return if (Regex("""^\d{2}/\d{2}/\d{4}$""").matches(trimmed)) trimmed else ""
    }

    // Plain field setters.
    fun set(transform: (EmployeeFormUiState) -> EmployeeFormUiState) = _uiState.update(transform)

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        val branch = state.selectedBranch ?: return
        val sex = state.selectedSex ?: return

        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            fun String.orZero(): String = trim().ifBlank { "0" }
            val payload = JsonObject().apply {
                addProperty("name", state.name.trim())
                addProperty("father_name", state.fatherName.trim())
                addProperty("nid", state.nid.trim())
                addProperty("designation", state.selectedDesignation?.id ?: "")
                addProperty("qualification", state.qualification.trim())
                addProperty("date_of_birth", state.dateOfBirth)
                addProperty("joning_dt", state.joiningDate)
                addProperty("present_address", state.presentAddress.trim())
                addProperty("permanent_address", state.permanentAddress.trim())
                addProperty("mobile", state.mobile.trim())
                addProperty("sex", sex.id)
                addProperty("project_id", branch.id.toLongOrNull() ?: 0L)
                addProperty("status", state.selectedStatus.id.toIntOrNull() ?: 1)
                addProperty("basic_salary", state.basicSalary.orZero())
                addProperty("house_rent", state.houseRent.orZero())
                // The store endpoint reads medical_allowance, the update endpoint
                // reads medical — send both so neither call drops the value.
                addProperty("medical_allowance", state.medical.orZero())
                addProperty("medical", state.medical.orZero())
                addProperty("others_allowance", state.othersAllowance.orZero())
                addProperty("loan_deduction", state.loanDeduction.orZero())
                addProperty("others_deduction", state.othersDeduction.orZero())
                addProperty("salary_payable", state.selectedPayable.id)
                addProperty("employment_type", state.selectedEmploymentType.id)
                val policyId = state.selectedPolicy?.id
                if (policyId.isNullOrBlank()) add("attendance_policy_id", JsonNull.INSTANCE)
                else addProperty("attendance_policy_id", policyId)
                val shiftId = state.selectedShift?.id
                if (shiftId.isNullOrBlank()) {
                    add("default_shift_id", JsonNull.INSTANCE)
                    add("attendance_shift_id", JsonNull.INSTANCE)
                } else {
                    addProperty("default_shift_id", shiftId)
                    addProperty("attendance_shift_id", shiftId)
                }
                addProperty("overtime_eligible", state.selectedOvertimeEligible.id.toIntOrNull() ?: 0)
                addProperty("daily_wage", state.dailyWage.orZero())
                addProperty("ot_rate", state.otRate.orZero())
                addProperty("standard_work_minutes", state.standardWorkMinutes.trim().ifBlank { "480" })
                addProperty("employee_serial", state.employeeSerial.orZero())
            }
            when (val result = hrmRepository.saveEmployee(payload, state.employeeId)) {
                is Resource.Success -> _uiState.update {
                    it.copy(isSaving = false, savedMessage = result.data)
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

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context, employeeId: String?) = viewModelFactory {
            initializer {
                EmployeeFormViewModel(
                    employeeId = employeeId,
                    hrmRepository = ServiceLocator.provideHrmRepository(context.applicationContext),
                )
            }
        }
    }
}

/**
 * Add/Edit Employee — the web form's field set in four sections. On save it
 * posts the full key set (the update endpoint rewrites every column, so
 * everything the form knows must go back).
 */
@Composable
fun EmployeeFormScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    employeeId: String? = null,
) {
    val context = LocalContext.current
    val viewModel: EmployeeFormViewModel =
        viewModel(factory = EmployeeFormViewModel.provideFactory(context, employeeId))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.sessionExpired) {
        LaunchedEffect(Unit) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    // Success: hand the message to the list below and leave.
    LaunchedEffect(state.savedMessage) {
        val message = state.savedMessage ?: return@LaunchedEffect
        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.set(Routes.CREATED_MESSAGE, message)
        navController.popBackStack()
    }

    AuthenticatedShell(
        title = if (state.isEdit) "Edit Employee" else "Add Employee",
        currentRoute = Routes.HRM,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@AuthenticatedShell
        }
        state.loadError?.let { error ->
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            return@AuthenticatedShell
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        ) {
            item {
                SectionHeader("Personal")
                TextRow("Employee Name", state.name, placeholder = "Full name") { value ->
                    viewModel.set { it.copy(name = value) }
                }
                TextRow("Father's Name", state.fatherName) { value ->
                    viewModel.set { it.copy(fatherName = value) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        TextRow("Mobile", state.mobile, placeholder = "01XXXXXXXXX") { value ->
                            viewModel.set { it.copy(mobile = value) }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        TextRow("NID", state.nid) { value ->
                            viewModel.set { it.copy(nid = value) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OptionalDateField(
                        label = "Date of Birth",
                        value = state.dateOfBirth,
                        context = context,
                        onSelected = { value -> viewModel.set { it.copy(dateOfBirth = value) } },
                        modifier = Modifier.weight(1f),
                    )
                    Column(Modifier.weight(1f)) {
                        DropdownRow(
                            label = "Gender",
                            options = state.settings?.sexes.orEmpty(),
                            selected = state.selectedSex,
                        ) { option -> viewModel.set { it.copy(selectedSex = option) } }
                    }
                }
                TextRow("Qualification", state.qualification) { value ->
                    viewModel.set { it.copy(qualification = value) }
                }
                TextRow("Present Address", state.presentAddress) { value ->
                    viewModel.set { it.copy(presentAddress = value) }
                }
                TextRow("Permanent Address", state.permanentAddress) { value ->
                    viewModel.set { it.copy(permanentAddress = value) }
                }

                SectionHeader("Job")
                DropdownRow(
                    label = "Project / Branch",
                    options = state.settings?.branches.orEmpty(),
                    selected = state.selectedBranch,
                ) { option -> viewModel.set { it.copy(selectedBranch = option) } }
                Spacer(Modifier.height(12.dp))
                DropdownRow(
                    label = "Designation",
                    options = state.settings?.designations.orEmpty(),
                    selected = state.selectedDesignation,
                ) { option -> viewModel.set { it.copy(selectedDesignation = option) } }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        TextRow("Salary Sl No", state.employeeSerial, spaced = false) { value ->
                            viewModel.set { it.copy(employeeSerial = value) }
                        }
                    }
                    OptionalDateField(
                        label = "Joining Date",
                        value = state.joiningDate,
                        context = context,
                        onSelected = { value -> viewModel.set { it.copy(joiningDate = value) } },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        StaticDropdownRow("Status", STATUS_OPTIONS, state.selectedStatus) { option ->
                            viewModel.set { it.copy(selectedStatus = option) }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        StaticDropdownRow(
                            "Employment Type", EMPLOYMENT_TYPES, state.selectedEmploymentType,
                        ) { option -> viewModel.set { it.copy(selectedEmploymentType = option) } }
                    }
                }

                SectionHeader("Salary")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        TextRow("Basic Salary", state.basicSalary, spaced = false) { value ->
                            viewModel.set { it.copy(basicSalary = value) }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        TextRow("House Rent", state.houseRent, spaced = false) { value ->
                            viewModel.set { it.copy(houseRent = value) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        TextRow("Medical Allowance", state.medical, spaced = false) { value ->
                            viewModel.set { it.copy(medical = value) }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        TextRow("Others Allowance", state.othersAllowance, spaced = false) { value ->
                            viewModel.set { it.copy(othersAllowance = value) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        TextRow("Loan Deduction", state.loanDeduction, spaced = false) { value ->
                            viewModel.set { it.copy(loanDeduction = value) }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        TextRow("Others Deduction", state.othersDeduction, spaced = false) { value ->
                            viewModel.set { it.copy(othersDeduction = value) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                StaticDropdownRow("Salary Payable", PAYABLE_OPTIONS, state.selectedPayable) { option ->
                    viewModel.set { it.copy(selectedPayable = option) }
                }

                SectionHeader("Attendance & Overtime")
                DropdownRow(
                    label = "Attendance Policy (optional)",
                    options = state.policies,
                    selected = state.selectedPolicy,
                ) { option -> viewModel.set { it.copy(selectedPolicy = option) } }
                Spacer(Modifier.height(12.dp))
                DropdownRow(
                    label = "Default Shift (optional)",
                    options = state.shifts,
                    selected = state.selectedShift,
                ) { option -> viewModel.set { it.copy(selectedShift = option) } }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        StaticDropdownRow(
                            "Overtime Eligible", YES_NO_OPTIONS, state.selectedOvertimeEligible,
                        ) { option -> viewModel.set { it.copy(selectedOvertimeEligible = option) } }
                    }
                    Column(Modifier.weight(1f)) {
                        TextRow("OT Rate", state.otRate, spaced = false) { value ->
                            viewModel.set { it.copy(otRate = value) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        TextRow("Daily Wage", state.dailyWage, spaced = false) { value ->
                            viewModel.set { it.copy(dailyWage = value) }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        TextRow("Std. Work Minutes", state.standardWorkMinutes, spaced = false) { value ->
                            viewModel.set { it.copy(standardWorkMinutes = value) }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                PrimaryButton(
                    text = if (state.isEdit) "Update Employee" else "Save Employee",
                    onClick = viewModel::save,
                    enabled = state.canSave,
                    isLoading = state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.saveError?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 18.dp, bottom = 10.dp),
    )
}

/** A labelled text field with the standard 12dp gap below (unless [spaced] off). */
@Composable
private fun TextRow(
    label: String,
    value: String,
    placeholder: String = "",
    spaced: Boolean = true,
    onValueChange: (String) -> Unit,
) {
    FieldFrame(label = label) {
        FieldTextInput(value = value, onValueChange = onValueChange, placeholder = placeholder)
    }
    if (spaced) Spacer(Modifier.height(12.dp))
}

/** An [HrmOption]-backed dropdown in the shared select component. */
@Composable
private fun DropdownRow(
    label: String,
    options: List<HrmOption>,
    selected: SelectorOption?,
    onSelected: (SelectorOption) -> Unit,
) {
    AppSelectDropdown(
        label = label,
        options = options.map { SelectorOption(it.id, it.name) },
        selected = selected,
        onSelected = onSelected,
    )
}

@Composable
private fun StaticDropdownRow(
    label: String,
    options: List<SelectorOption>,
    selected: SelectorOption?,
    onSelected: (SelectorOption) -> Unit,
) {
    AppSelectDropdown(
        label = label,
        options = options,
        selected = selected,
        onSelected = onSelected,
    )
}

/** A DD/MM/YYYY date field that may stay blank until first picked. */
@Composable
private fun OptionalDateField(
    label: String,
    value: String,
    context: Context,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PickerField(
        label = label,
        value = value,
        placeholder = "dd/mm/yyyy",
        trailingIcon = Icons.Filled.DateRange,
        modifier = modifier,
        onClick = {
            val calendar = Calendar.getInstance()
            Regex("""^(\d{2})/(\d{2})/(\d{4})$""").find(value)?.let { match ->
                val (day, month, year) = match.destructured
                calendar.set(year.toInt(), month.toInt() - 1, day.toInt())
            }
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    onSelected(
                        String.format(Locale.US, "%02d/%02d/%04d", dayOfMonth, month + 1, year),
                    )
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH),
            ).show()
        },
    )
}
