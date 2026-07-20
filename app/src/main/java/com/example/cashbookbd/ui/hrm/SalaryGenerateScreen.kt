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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.HrmRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.FieldFrame
import com.example.cashbookbd.ui.components.FieldTextInput
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.hrm.model.AttendanceSummary
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.MonthYear
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Salary sheet kinds the web offers. */
private val SHEET_TYPES = listOf(
    SelectorOption("monthly", "Monthly Salary"),
    SelectorOption("overtime", "Overtime Salary"),
)

/**
 * One employee row of the sheet being built. Mirrors the web's `SalaryRow` —
 * monetary maths (proration, rounding, net) match `SalarySheetGenerate.tsx`.
 */
data class SalarySheetRow(
    val id: Long,
    val name: String,
    val designationName: String,
    val employmentType: String,
    val monthlyBasicSalary: Double,
    val monthlyOthersAllowance: Double,
    val loanBalance: Double,
    val othersDeduction: Double,
    val otRate: Double,
    val monthDays: Int,
    val workingDays: Double,
    val absentDays: Double,
    val unpaidLeaveDays: Double,
    val halfDays: Double,
    val lateCount: Double,
    val lateDeductionDays: Double,
    val earlyOutCount: Double,
    val earlyOutDeductionDays: Double,
    val attendanceDeductionAmount: Double,
    val overtimeMinutes: Double,
    val overtimeAmount: Double,
) {
    private val isDailyLabour: Boolean
        get() = employmentType.lowercase(Locale.US).contains("daily")

    private fun prorated(amount: Double): Double {
        val days = workingDays
        if (days <= 0.0 || monthDays <= 0) return 0.0
        if (days >= monthDays) return amount
        return ceil((amount / monthDays * days) / 10.0) * 10.0
    }

    fun proratedBasic(): Double = prorated(monthlyBasicSalary)

    fun proratedOthers(): Double = prorated(monthlyOthersAllowance)

    fun total(isOvertime: Boolean): Double =
        proratedBasic() +
            (if (isOvertime) 0.0 else proratedOthers()) +
            (if (isOvertime) overtimeAmount else 0.0)

    fun net(isOvertime: Boolean): Double {
        if (workingDays <= 0.0 || monthDays <= 0) return 0.0
        val attendanceDed = if (isDailyLabour) 0.0 else attendanceDeductionAmount
        return max(0.0, total(isOvertime) - loanBalance - attendanceDed)
    }

    /** The generate payload row, key-for-key what the web sends. */
    fun toPayload(sequence: Int, isOvertime: Boolean): JsonObject = JsonObject().apply {
        addProperty("id", id)
        addProperty("serial_no", sequence)
        addProperty("sequence", sequence)
        addProperty("sort_order", sequence)
        addProperty("name", name)
        addProperty("designation_name", designationName)
        addProperty("employment_type", employmentType)
        addProperty("monthly_basic_salary", monthlyBasicSalary)
        addProperty("monthly_others_allowance", monthlyOthersAllowance)
        addProperty("basic_salary", proratedBasic())
        addProperty("others_allowance", if (isOvertime) 0.0 else proratedOthers())
        addProperty("loan_balance", loanBalance)
        // The web saves the row's loan balance as its loan deduction.
        addProperty("loan_deduction", loanBalance)
        addProperty(
            "attendance_deduction_amount",
            if (isDailyLabour) 0.0 else attendanceDeductionAmount,
        )
        addProperty("attendance_absent_days", absentDays)
        addProperty("attendance_unpaid_leave_days", unpaidLeaveDays)
        addProperty("attendance_half_days", halfDays)
        addProperty("attendance_late_count", lateCount)
        addProperty("attendance_late_deduction_days", lateDeductionDays)
        addProperty("attendance_early_out_count", earlyOutCount)
        addProperty("attendance_early_out_deduction_days", earlyOutDeductionDays)
        addProperty("overtime_minutes", if (isOvertime) overtimeMinutes else 0.0)
        addProperty("ot_rate", if (isOvertime) otRate else 0.0)
        addProperty("overtime_amount", if (isOvertime) overtimeAmount else 0.0)
        addProperty("net_deduction", othersDeduction)
        addProperty("month_days", monthDays)
        addProperty("working_days", workingDays)
    }
}

data class SalaryGenerateUiState(
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val isBranchesLoading: Boolean = false,

    val sheetType: SelectorOption = SHEET_TYPES.first(),
    val monthYear: MonthYear = MonthYear.current(),

    val rows: List<SalarySheetRow> = emptyList(),
    val isLoading: Boolean = false,
    val loadError: String? = null,
    val searched: Boolean = false,

    val showConfirm: Boolean = false,
    val isGenerating: Boolean = false,
    val message: String? = null,
    val generateError: String? = null,

    val sessionExpired: Boolean = false,
) {
    val isOvertime: Boolean get() = sheetType.id == "overtime"

    val totalNet: Double get() = rows.sumOf { it.net(isOvertime) }

    val canGenerate: Boolean
        get() = selectedBranch != null && rows.isNotEmpty() && !isGenerating && !isLoading
}

class SalaryGenerateViewModel(
    private val hrmRepository: HrmRepository,
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SalaryGenerateUiState())
    val uiState: StateFlow<SalaryGenerateUiState> = _uiState.asStateFlow()

    init {
        loadBranches()
    }

    private fun loadBranches() {
        _uiState.update { it.copy(isBranchesLoading = true) }
        viewModelScope.launch {
            when (val result = reportRepository.getBranches()) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isBranchesLoading = false,
                        branches = result.data.branches,
                        selectedBranch = it.selectedBranch ?: result.data.branches.firstOrNull(),
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isBranchesLoading = false,
                        loadError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onBranchSelected(branch: BranchOption) =
        _uiState.update { it.copy(selectedBranch = branch, rows = emptyList(), searched = false) }

    fun onSheetTypeSelected(option: SelectorOption) =
        _uiState.update { it.copy(sheetType = option, rows = emptyList(), searched = false) }

    fun onMonthSelected(monthYear: MonthYear) =
        _uiState.update { it.copy(monthYear = monthYear, rows = emptyList(), searched = false) }

    fun onLoanChanged(rowId: Long, value: String) {
        val amount = value.replace(",", "").toDoubleOrNull() ?: 0.0
        _uiState.update { state ->
            state.copy(rows = state.rows.map { if (it.id == rowId) it.copy(loanBalance = amount) else it })
        }
    }

    fun removeRow(rowId: Long) =
        _uiState.update { state -> state.copy(rows = state.rows.filterNot { it.id == rowId }) }

    private fun monthId(monthYear: MonthYear): String =
        String.format(Locale.US, "%02d-%04d", monthYear.month, monthYear.year)

    private fun daysInMonth(monthYear: MonthYear): Int {
        val calendar = Calendar.getInstance()
        calendar.set(monthYear.year, monthYear.month - 1, 1)
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    /** The web's Search step: eligible employees + their attendance summary. */
    fun load() {
        val state = _uiState.value
        val branch = state.selectedBranch ?: return
        if (state.isLoading) return

        _uiState.update {
            it.copy(isLoading = true, loadError = null, message = null, generateError = null)
        }
        viewModelScope.launch {
            val employees = hrmRepository.salaryView(
                branchId = branch.id,
                monthId = monthId(state.monthYear),
                sheetType = state.sheetType.id,
            )
            if (employees is Resource.Error) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = employees.message,
                        sessionExpired = it.sessionExpired || employees.isUnauthorized,
                    )
                }
                return@launch
            }
            val list = (employees as Resource.Success).data

            // Attendance summary is best-effort, exactly like the web: without
            // it every row simply keeps the full month as its working days.
            val summaryResult = hrmRepository.monthlySummary(
                branchId = branch.id,
                month = state.monthYear.month,
                year = state.monthYear.year,
                monthlyOnly = !state.isOvertime,
            )
            val summaries: Map<Long, AttendanceSummary> =
                (summaryResult as? Resource.Success)?.data ?: emptyMap()

            val monthDays = daysInMonth(state.monthYear)
            val isOvertime = state.isOvertime
            val rows = list.map { emp ->
                val summary = summaries[emp.id]
                val payable = summary?.let { s -> s.payableDays ?: (monthDays - s.deductionDays) }
                SalarySheetRow(
                    id = emp.id,
                    name = emp.name,
                    designationName = emp.designationName,
                    employmentType = emp.employmentType,
                    monthlyBasicSalary = emp.basicSalary,
                    monthlyOthersAllowance = emp.othersAllowance,
                    loanBalance = emp.loanBalance,
                    othersDeduction = emp.othersDeduction,
                    otRate = emp.otRate,
                    monthDays = monthDays,
                    workingDays = payable?.let { min(monthDays.toDouble(), max(0.0, it)) }
                        ?: monthDays.toDouble(),
                    absentDays = summary?.absentDays ?: 0.0,
                    unpaidLeaveDays = summary?.unpaidLeaveDays ?: 0.0,
                    halfDays = summary?.halfDays ?: 0.0,
                    lateCount = summary?.lateCount ?: 0.0,
                    lateDeductionDays = summary?.lateDeductionDays ?: 0.0,
                    earlyOutCount = summary?.earlyOutCount ?: 0.0,
                    earlyOutDeductionDays = summary?.earlyOutDeductionDays ?: 0.0,
                    attendanceDeductionAmount = 0.0,
                    overtimeMinutes = summary?.overtimeMinutes ?: 0.0,
                    overtimeAmount = summary?.let { s ->
                        if (s.overtimeAmount > 0.0) s.overtimeAmount
                        else s.overtimeMinutes / 60.0 * emp.otRate
                    } ?: 0.0,
                )
            }.let { built ->
                if (isOvertime) {
                    built.filter { it.overtimeMinutes > 0.0 || it.overtimeAmount > 0.0 }
                } else {
                    built
                }
            }

            _uiState.update { it.copy(isLoading = false, rows = rows, searched = true) }
        }
    }

    fun onGenerateClick() = _uiState.update { it.copy(showConfirm = true) }
    fun onConfirmDismiss() = _uiState.update { it.copy(showConfirm = false) }

    fun generate() {
        val state = _uiState.value
        val branch = state.selectedBranch ?: return
        if (state.rows.isEmpty() || state.isGenerating) return

        _uiState.update {
            it.copy(isGenerating = true, showConfirm = false, message = null, generateError = null)
        }
        viewModelScope.launch {
            val employees = JsonArray().apply {
                state.rows.forEachIndexed { index, row ->
                    add(row.toPayload(sequence = index + 1, isOvertime = state.isOvertime))
                }
            }
            val result = hrmRepository.salaryGenerate(
                branchId = branch.id,
                monthId = monthId(state.monthYear),
                sheetType = state.sheetType.id,
                employees = employees,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isGenerating = false, message = result.data, rows = emptyList(), searched = false)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isGenerating = false,
                        generateError = result.message,
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
                SalaryGenerateViewModel(
                    hrmRepository = ServiceLocator.provideHrmRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                )
            }
        }
    }
}

/**
 * Salary Generate: pick branch/type/month, load the eligible employees with
 * their prorated amounts, adjust loan deductions, then generate the sheet.
 */
@Composable
fun SalaryGenerateScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: SalaryGenerateViewModel =
        viewModel(factory = SalaryGenerateViewModel.provideFactory(context))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.sessionExpired) {
        LaunchedEffect(Unit) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    if (state.showConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::onConfirmDismiss,
            title = { Text("Generate salary?") },
            text = {
                Text(
                    "This will create the ${state.monthYear.toDisplay()} " +
                        "${state.sheetType.label.lowercase(Locale.US)} sheet for " +
                        "${state.rows.size} employee(s), total " +
                        "${AmountFormat.format(state.totalNet)}. This posts real vouchers.",
                )
            },
            confirmButton = { LinkButton(text = "Generate", onClick = viewModel::generate) },
            dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::onConfirmDismiss) },
        )
    }

    AuthenticatedShell(
        title = "Salary Generate",
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
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        AppSelectDropdown(
                            label = "Sheet Type",
                            options = SHEET_TYPES,
                            selected = state.sheetType,
                            onSelected = viewModel::onSheetTypeSelected,
                        )
                    }
                    HrmMonthField(
                        label = "Salary Month",
                        value = state.monthYear,
                        onSelected = viewModel::onMonthSelected,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(
                        text = "Load Employees",
                        onClick = viewModel::load,
                        enabled = state.selectedBranch != null && !state.isLoading,
                        isLoading = state.isLoading,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = "Generate",
                        onClick = viewModel::onGenerateClick,
                        enabled = state.canGenerate,
                        modifier = Modifier.weight(1f),
                    )
                }

                state.message?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
                (state.loadError ?: state.generateError)?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
            }

            if (state.searched && state.rows.isEmpty() && !state.isLoading) {
                item {
                    Text(
                        text = "No eligible employees for this month.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            items(state.rows, key = { it.id }) { row ->
                SalaryRowCard(
                    row = row,
                    isOvertime = state.isOvertime,
                    onLoanChanged = { viewModel.onLoanChanged(row.id, it) },
                    onRemove = { viewModel.removeRow(row.id) },
                )
                Spacer(Modifier.height(8.dp))
            }

            if (state.rows.isNotEmpty()) {
                item {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Row {
                        Text(
                            text = "Total Net Salary",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = AmountFormat.format(state.totalNet),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SalaryRowCard(
    row: SalarySheetRow,
    isOvertime: Boolean,
    onLoanChanged: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (row.designationName.isNotBlank()) {
                        Text(
                            text = row.designationName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                LinkButton(text = "Remove", onClick = onRemove)
            }
            Spacer(Modifier.height(6.dp))
            Row {
                LabelValue("Days", "${row.workingDays.compact()}/${row.monthDays}", Modifier.weight(1f))
                LabelValue("Basic", AmountFormat.formatOrDash(row.proratedBasic()), Modifier.weight(1f))
                if (isOvertime) {
                    LabelValue("OT Hr.", (row.overtimeMinutes / 60.0).compact(), Modifier.weight(1f))
                    LabelValue("OT Amt", AmountFormat.formatOrDash(row.overtimeAmount), Modifier.weight(1f))
                } else {
                    LabelValue("Mobile Bill", AmountFormat.formatOrDash(row.proratedOthers()), Modifier.weight(1f))
                    LabelValue("Total", AmountFormat.formatOrDash(row.total(false)), Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.width(140.dp)) {
                    FieldFrame(label = "Loan Ded.") {
                        FieldTextInput(
                            value = if (row.loanBalance == 0.0) "" else row.loanBalance.compact(),
                            onValueChange = onLoanChanged,
                            placeholder = "0",
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Net Salary",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = AmountFormat.format(row.net(isOvertime)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/** "5.0" -> "5", "5.5" stays "5.5" — day/hour counts, not money. */
private fun Double.compact(): String =
    if (this == toLong().toDouble()) toLong().toString() else toString()
