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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.input.KeyboardType
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
import java.util.Locale
import kotlin.math.ceil

/** The web's bonus title templates. */
private val BONUS_TITLES = listOf(
    "Durga-puja Bonus",
    "Eid-ul-Fitr Bonus",
    "Eid-ul-Adha Bonus",
    "Pohela Boishakh Bonus",
    "Special Festival Bonus",
    "Other",
).map { SelectorOption(it, it) }

/** One employee row of the bonus sheet: basic salary and the derived bonus. */
data class BonusSheetRow(
    val id: Long,
    val name: String,
    val designationName: String,
    val basicSalary: Double,
    val bonusAmount: Double,
)

/** The web's rounding: ceil((basic × percent%) / 10) × 10. */
private fun bonusAmountOf(basic: Double, percent: Double): Double =
    ceil((basic * percent / 100.0) / 10.0) * 10.0

data class BonusGenerateUiState(
    val branches: List<BranchOption> = emptyList(),
    val selectedBranch: BranchOption? = null,
    val isBranchesLoading: Boolean = false,

    val bonusTitle: SelectorOption = BONUS_TITLES.first(),
    val monthYear: MonthYear = MonthYear.current(),
    val percentText: String = "50",

    val rows: List<BonusSheetRow> = emptyList(),
    val isLoading: Boolean = false,
    val loadError: String? = null,
    val searched: Boolean = false,

    val showConfirm: Boolean = false,
    val isGenerating: Boolean = false,
    val message: String? = null,
    val generateError: String? = null,

    val sessionExpired: Boolean = false,
) {
    val percent: Double get() = percentText.toDoubleOrNull() ?: 0.0

    val totalBonus: Double get() = rows.sumOf { it.bonusAmount }

    val canGenerate: Boolean
        get() = selectedBranch != null && rows.isNotEmpty() && percent > 0.0 &&
            !isGenerating && !isLoading
}

class BonusGenerateViewModel(
    private val hrmRepository: HrmRepository,
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BonusGenerateUiState())
    val uiState: StateFlow<BonusGenerateUiState> = _uiState.asStateFlow()

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

    fun onTitleSelected(option: SelectorOption) =
        _uiState.update { it.copy(bonusTitle = option, rows = emptyList(), searched = false) }

    fun onMonthSelected(monthYear: MonthYear) =
        _uiState.update { it.copy(monthYear = monthYear, rows = emptyList(), searched = false) }

    /** Re-derives every row's bonus when the percentage changes. */
    fun onPercentChanged(value: String) {
        _uiState.update { state ->
            val percent = value.toDoubleOrNull() ?: 0.0
            state.copy(
                percentText = value,
                rows = state.rows.map { it.copy(bonusAmount = bonusAmountOf(it.basicSalary, percent)) },
            )
        }
    }

    fun removeRow(rowId: Long) =
        _uiState.update { state -> state.copy(rows = state.rows.filterNot { it.id == rowId }) }

    private fun monthId(monthYear: MonthYear): String =
        String.format(Locale.US, "%02d-%04d", monthYear.month, monthYear.year)

    fun load() {
        val state = _uiState.value
        val branch = state.selectedBranch ?: return
        if (state.isLoading) return

        _uiState.update {
            it.copy(isLoading = true, loadError = null, message = null, generateError = null)
        }
        viewModelScope.launch {
            val result = hrmRepository.bonusView(
                branchId = branch.id,
                monthId = monthId(state.monthYear),
                bonusTitle = state.bonusTitle.id,
            )
            when (result) {
                is Resource.Success -> _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        searched = true,
                        rows = result.data.map { emp ->
                            BonusSheetRow(
                                id = emp.id,
                                name = emp.name,
                                designationName = emp.designationName,
                                basicSalary = emp.basicSalary,
                                bonusAmount = bonusAmountOf(emp.basicSalary, current.percent),
                            )
                        },
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
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
                state.rows.forEach { row ->
                    add(
                        JsonObject().apply {
                            addProperty("id", row.id)
                            addProperty("employee_id", row.id)
                            addProperty("name", row.name)
                            addProperty("designation_name", row.designationName)
                            addProperty("basic_salary", row.basicSalary)
                            addProperty("bonus_percent", state.percent)
                            addProperty("bonus_amount", row.bonusAmount)
                        },
                    )
                }
            }
            val result = hrmRepository.bonusGenerate(
                branchId = branch.id,
                monthId = monthId(state.monthYear),
                bonusTitle = state.bonusTitle.id,
                bonusPercent = state.percent,
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
                BonusGenerateViewModel(
                    hrmRepository = ServiceLocator.provideHrmRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                )
            }
        }
    }
}

/**
 * Bonus Generate: pick branch/title/month and a percentage of basic, load the
 * eligible employees, then generate the festival bonus sheet.
 */
@Composable
fun BonusGenerateScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: BonusGenerateViewModel =
        viewModel(factory = BonusGenerateViewModel.provideFactory(context))
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
            title = { Text("Generate bonus?") },
            text = {
                Text(
                    "This will create the \"${state.bonusTitle.label}\" sheet for " +
                        "${state.rows.size} employee(s), total " +
                        "${AmountFormat.format(state.totalBonus)}. This posts real vouchers.",
                )
            },
            confirmButton = { LinkButton(text = "Generate", onClick = viewModel::generate) },
            dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::onConfirmDismiss) },
        )
    }

    AuthenticatedShell(
        title = "Bonus Generate",
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
                AppSelectDropdown(
                    label = "Bonus Title",
                    options = BONUS_TITLES,
                    selected = state.bonusTitle,
                    onSelected = viewModel::onTitleSelected,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HrmMonthField(
                        label = "Bonus Month",
                        value = state.monthYear,
                        onSelected = viewModel::onMonthSelected,
                        modifier = Modifier.weight(1f),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        FieldFrame(label = "Bonus % of Basic") {
                            FieldTextInput(
                                value = state.percentText,
                                onValueChange = viewModel::onPercentChanged,
                                placeholder = "50",
                            )
                        }
                    }
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
                        text = "No eligible employees for this bonus.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            items(state.rows, key = { it.id }) { row ->
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
                            LinkButton(text = "Remove", onClick = { viewModel.removeRow(row.id) })
                        }
                        Spacer(Modifier.height(6.dp))
                        Row {
                            LabelValue("Basic", AmountFormat.formatOrDash(row.basicSalary), Modifier.weight(1f))
                            LabelValue("Bonus %", state.percentText.ifBlank { "-" }, Modifier.weight(1f))
                            LabelValue("Bonus", AmountFormat.formatOrDash(row.bonusAmount), Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (state.rows.isNotEmpty()) {
                item {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Row {
                        Text(
                            text = "Total Bonus",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = AmountFormat.format(state.totalBonus),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
