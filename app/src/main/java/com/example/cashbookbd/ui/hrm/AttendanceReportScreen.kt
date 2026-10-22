package com.example.cashbookbd.ui.hrm

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.core.jAsArr
import com.example.cashbookbd.core.jAsObj
import com.example.cashbookbd.core.jObjects
import com.example.cashbookbd.core.jStr
import com.example.cashbookbd.data.remote.WebApi
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.BranchField
import com.example.cashbookbd.ui.reports.BranchPick
import com.example.cashbookbd.ui.reports.DateRangeFields
import com.example.cashbookbd.ui.reports.LogoutOnExpiry
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportError
import com.example.cashbookbd.ui.reports.ReportLoading
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.reports.loadBranchPick
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Which attendance report: the daily one, or one of the three exception tabs
 * the web serves from one page (AttendanceExceptionReports.tsx).
 */
enum class AttendanceReportType(val title: String, val tab: String, val defaultStatus: String) {
    DAILY("Daily Attendance Report", "Daily", ""),
    ABSENT("Absent Report", "Absent", "absent"),
    LATE("Late Report", "Late", "late"),
    EARLY_OUT("Early Out Report", "Early Out", "early_out"),
}

data class AttendanceRow(
    val date: String,
    val employeeId: String,
    val serial: String,
    val name: String,
    val branch: String,
    val shift: String,
    val inTime: String,
    val outTime: String,
    val minutes: String,
    val status: String,
    val approval: String,
    /** A day with no entry, worked out here: absent, or leave if one was approved. */
    val generated: Boolean = false,
)

private val STATUS_OPTIONS = listOf(
    SelectorOption("", "All Status"), SelectorOption("present", "Present"), SelectorOption("absent", "Absent"),
    SelectorOption("half_day", "Half Day"), SelectorOption("leave", "Leave"), SelectorOption("holiday", "Holiday"),
    SelectorOption("weekly_holiday", "Weekly Holiday"), SelectorOption("late", "Late"),
    SelectorOption("early_out", "Early Out"), SelectorOption("pending", "Pending"),
)
private val APPROVAL_OPTIONS = listOf(
    SelectorOption("", "All Approval"), SelectorOption("pending", "Pending"),
    SelectorOption("approved", "Approved"), SelectorOption("rejected", "Rejected"),
)

data class AttendanceReportUiState(
    val type: AttendanceReportType,
    val branch: BranchPick = BranchPick(),
    val from: SimpleDate = SimpleDate.today(),
    val to: SimpleDate = SimpleDate.today(),
    val status: SelectorOption,
    val approval: SelectorOption = APPROVAL_OPTIONS.first(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val entries: List<AttendanceRow> = emptyList(),
    val employees: List<JsonObject> = emptyList(),
    val leaves: List<JsonObject> = emptyList(),
    val serverActive: Int = 0,
    val sessionExpired: Boolean = false,
)

private fun JsonObject.employeeKey(): String =
    jStr("employee_id").ifBlank { jStr("id") }.ifBlank { jStr("employee_serial") }.ifBlank { jStr("employee_name") }.ifBlank { jStr("name") }

private fun JsonObject.isActiveEmployee(): Boolean {
    val status = jStr("status").ifBlank { jStr("employee_status") }.ifBlank { jStr("status_name") }.lowercase()
    return status.isEmpty() || status == "1" || status == "active"
}

/** The rows wherever the payload put them (safeRows). */
private fun rowsOf(el: JsonElement?): List<JsonObject> {
    el.jAsArr()?.let { return it.jObjects() }
    val o = el.jAsObj() ?: return emptyList()
    o.get("data").jAsArr()?.let { return it.jObjects() }
    val d = o.get("data").jAsObj()
    d?.get("data").jAsArr()?.let { return it.jObjects() }
    d?.get("data").jAsObj()?.get("data").jAsArr()?.let { return it.jObjects() }
    return emptyList()
}

/**
 * AttendanceReport.tsx: the server's entries, plus a row for every active
 * employee on every day of the range who has none — "leave" where an
 * approved leave covers the day, "absent" otherwise — then filtered by status
 * and approval and sorted by date, then serial. The cards count those rows.
 */
class AttendanceReportViewModel(
    type: AttendanceReportType,
    private val api: WebApi,
    private val reports: ReportRepository,
    private val ownBranchId: Long?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AttendanceReportUiState(type = type, status = STATUS_OPTIONS.first { it.id == type.defaultStatus }),
    )
    val uiState: StateFlow<AttendanceReportUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            when (val r = reports.loadBranchPick(ownBranchId)) {
                is Resource.Success -> _uiState.update { it.copy(branch = r.data) }
                is Resource.Error -> _uiState.update { it.copy(branch = it.branch.copy(loading = false), sessionExpired = r.isUnauthorized) }
                Resource.Loading -> Unit
            }
            load()
        }
    }

    fun onBranch(id: String) = _uiState.update { it.copy(branch = it.branch.pick(id)) }
    fun onFrom(d: SimpleDate) = _uiState.update { it.copy(from = d) }
    fun onTo(d: SimpleDate) = _uiState.update { it.copy(to = d) }
    fun onStatus(o: SelectorOption) = _uiState.update { it.copy(status = o) }
    fun onApproval(o: SelectorOption) = _uiState.update { it.copy(approval = o) }

    fun load() {
        val s = _uiState.value
        val branchId = s.branch.selectedId?.toString().orEmpty()
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val report = async {
                api.get(
                    "hrms/attendance/entries/report",
                    mapOf(
                        "date_from" to s.from.toApi(), "date_to" to s.to.toApi(), "branch_id" to branchId,
                        "status" to s.status.id, "approval_status" to s.approval.id,
                    ),
                )
            }
            val employees = async { api.get("hrms/employees", mapOf("page" to "1", "per_page" to "1000", "branch_id" to branchId)) }
            val leaves = async {
                api.get(
                    "hrms/attendance/leave-applications",
                    mapOf("branch_id" to branchId, "date_from" to s.from.toApi(), "date_to" to s.to.toApi(), "approval_status" to "approved"),
                )
            }
            when (val r = report.await()) {
                is Resource.Success -> {
                    val payload = r.data.get("data").jAsObj()?.get("data").jAsObj() ?: r.data.get("data").jAsObj() ?: JsonObject()
                    val entries = rowsOf(payload.get("rows")).map { it.toRow() }
                    val active = payload.get("summary").jAsObj()?.jStr("active_employees")?.toDoubleOrNull()?.toInt() ?: 0
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            entries = entries,
                            serverActive = active,
                            employees = (employees.await() as? Resource.Success)?.data?.let { e -> rowsOf(e) }.orEmpty(),
                            leaves = (leaves.await() as? Resource.Success)?.data?.let { l -> rowsOf(l) }.orEmpty(),
                        )
                    }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, error = r.message.ifBlank { "Failed to fetch attendance report" }, sessionExpired = r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    private fun JsonObject.toRow() = AttendanceRow(
        date = jStr("attendance_date").take(10),
        employeeId = employeeKey(),
        serial = jStr("employee_serial"),
        name = jStr("employee_name"),
        branch = jStr("branch_name"),
        shift = jStr("shift_name"),
        inTime = jStr("in_time"),
        outTime = jStr("out_time"),
        minutes = jStr("work_minutes"),
        status = jStr("status"),
        approval = jStr("approval_status"),
    )

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context, type: AttendanceReportType) = viewModelFactory {
            initializer {
                val app = context.applicationContext
                AttendanceReportViewModel(
                    type, ServiceLocator.provideWebApi(app), ServiceLocator.provideReportRepository(app),
                    ServiceLocator.provideSessionManager(app).state.value.settings?.branchId,
                )
            }
        }
    }
}

/** Every day of the range, at most 62 — the web's own cap. */
private fun daysOf(from: SimpleDate, to: SimpleDate): List<String> {
    if (from.toApi() > to.toApi()) return emptyList()
    val out = mutableListOf<String>()
    var d = from
    while (d.toApi() <= to.toApi() && out.size < 62) {
        out += d.toApi()
        d = d.plusDays(1)
    }
    return out
}

private fun buildRows(s: AttendanceReportUiState): List<AttendanceRow> {
    val keys = s.entries.map { "${it.employeeId}-${it.date}" }.toSet()
    val branchId = s.branch.selectedId?.toString().orEmpty()
    val branchName = s.branch.selected?.name.orEmpty()
    val approvedLeaves = s.leaves.filter { it.jStr("approval_status") == "approved" }
    val generated = s.employees
        .filter { it.isActiveEmployee() }
        .filter { branchId.isEmpty() || it.jStr("branch_id") == branchId }
        .flatMap { e ->
            val id = e.employeeKey()
            daysOf(s.from, s.to).mapNotNull { day ->
                if (id.isEmpty() || "$id-$day" in keys) return@mapNotNull null
                val leave = approvedLeaves.firstOrNull { l ->
                    l.employeeKey() == id && l.jStr("from_date").take(10) <= day && l.jStr("to_date").take(10) >= day
                }
                AttendanceRow(
                    date = day,
                    employeeId = id,
                    serial = e.jStr("employee_serial").ifBlank { e.jStr("serial_no") }.ifBlank { e.jStr("id") },
                    name = e.jStr("employee_name").ifBlank { e.jStr("name") },
                    branch = e.jStr("branch_name").ifBlank { branchName.ifBlank { "-" } },
                    shift = e.jStr("shift_name").ifBlank { "-" },
                    inTime = "", outTime = "", minutes = "-",
                    status = if (leave != null) "leave" else "absent",
                    approval = if (leave != null) "approved" else "-",
                    generated = true,
                )
            }
        }
    return (s.entries + generated)
        .filter { s.status.id.isEmpty() || it.status == s.status.id }
        .filter { s.approval.id.isEmpty() || it.approval == s.approval.id }
        .sortedWith(
            compareBy<AttendanceRow> { it.date }
                .thenComparator { a, b ->
                    val x = a.serial.toDoubleOrNull()
                    val y = b.serial.toDoubleOrNull()
                    if (x != null && y != null) x.compareTo(y) else a.name.compareTo(b.name)
                },
        )
}

private fun timeOnly(v: String) = if (v.length >= 16) v.substring(11, 16) else "-"
private fun normalizeStatus(v: String) =
    v.replace('_', ' ').split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }.ifBlank { "-" }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttendanceReportScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    type: AttendanceReportType,
    viewModel: AttendanceReportViewModel = viewModel(
        key = "attendance-report-${type.name}",
        factory = AttendanceReportViewModel.provideFactory(LocalContext.current, type),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)
    val rows = buildRows(state)

    AuthenticatedShell(title = type.title, currentRoute = Routes.HRM, navController = navController, onLogout = onLogout) {
        Column(Modifier.fillMaxSize()) {
            // The exception reports share one page with a tab per kind.
            if (type != AttendanceReportType.DAILY) {
                val tabs = listOf(AttendanceReportType.ABSENT, AttendanceReportType.LATE, AttendanceReportType.EARLY_OUT)
                TabRow(selectedTabIndex = tabs.indexOf(type)) {
                    tabs.forEach { t ->
                        Tab(
                            selected = t == type,
                            onClick = {
                                if (t != type) navController.navigate(com.example.cashbookbd.navigation.ParityRoutes.attendanceException(t)) {
                                    popUpTo(com.example.cashbookbd.navigation.ParityRoutes.attendanceException(type)) { inclusive = true }
                                }
                            },
                            text = { Text(t.tab) },
                        )
                    }
                }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { DateRangeFields(state.from, state.to, viewModel::onFrom, viewModel::onTo, "From", "To") }
                item { BranchField(state.branch, viewModel::onBranch, label = "Branch") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppSelectDropdown("Status", STATUS_OPTIONS, state.status, viewModel::onStatus, Modifier.weight(1f))
                        AppSelectDropdown("Approval", APPROVAL_OPTIONS, state.approval, viewModel::onApproval, Modifier.weight(1f))
                    }
                }
                item { PrimaryButton(text = "Load", onClick = viewModel::load, isLoading = state.isLoading, modifier = Modifier.fillMaxWidth()) }
                item {
                    val count = { st: String -> rows.count { it.status == st } }
                    val active = state.employees.count { it.isActiveEmployee() }.takeIf { it > 0 } ?: state.serverActive
                    val cards = buildList {
                        add("Active Employee" to active.toString())
                        add("Attendance Entry" to rows.count { !it.generated }.toString())
                        add("Absent/Missing" to count("absent").toString())
                        add("Present" to count("present").toString())
                        add("Half Day" to count("half_day").toString())
                        add("Leave" to count("leave").toString())
                        add("Late" to count("late").toString())
                        if (type == AttendanceReportType.LATE) add("Late Deduction Days" to (count("late") / 3).toString())
                        add("Early Out" to count("early_out").toString())
                        if (type == AttendanceReportType.EARLY_OUT) add("Early Out Deduction Days" to (count("early_out") / 3).toString())
                        add("Pending Approval" to rows.count { it.approval == "pending" }.toString())
                        add("Approved" to rows.count { it.approval == "approved" }.toString())
                        add("Rejected" to rows.count { it.approval == "rejected" }.toString())
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        cards.forEach { (label, value) ->
                            Card(Modifier.width(104.dp)) {
                                Column(Modifier.padding(8.dp)) {
                                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textMuted, maxLines = 2)
                                    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = AppFontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                when {
                    state.isLoading && state.entries.isEmpty() -> item { ReportLoading() }
                    state.error != null -> item { ReportError(state.error!!, viewModel::load) }
                    else -> item {
                        ReportTable(
                            columns = listOf(
                                ReportColumn<AttendanceRow>("SL. NO.", ReportColWidth.Fixed(56.dp), TextAlign.Center) { _, i -> cellText((i + 1).toString()) },
                                ReportColumn<AttendanceRow>("DATE", ReportColWidth.Fixed(92.dp)) { r, _ ->
                                    cellText(SimpleDate.fromApi(r.date)?.toDisplay() ?: r.date)
                                },
                                ReportColumn<AttendanceRow>("ID", ReportColWidth.Fixed(64.dp)) { r, _ -> cellText(r.serial) },
                                ReportColumn<AttendanceRow>("EMPLOYEE", ReportColWidth.Fixed(150.dp)) { r, _ -> cellText(r.name, maxLines = 2) },
                                ReportColumn<AttendanceRow>("BRANCH", ReportColWidth.Fixed(120.dp)) { r, _ -> cellText(r.branch.ifBlank { "-" }) },
                                ReportColumn<AttendanceRow>("SHIFT", ReportColWidth.Fixed(96.dp)) { r, _ -> cellText(r.shift.ifBlank { "-" }) },
                                ReportColumn<AttendanceRow>("IN", ReportColWidth.Fixed(64.dp)) { r, _ -> cellText(timeOnly(r.inTime)) },
                                ReportColumn<AttendanceRow>("OUT", ReportColWidth.Fixed(64.dp)) { r, _ -> cellText(timeOnly(r.outTime)) },
                                ReportColumn<AttendanceRow>("MINUTES", ReportColWidth.Fixed(76.dp), TextAlign.End) { r, _ -> cellText(r.minutes.ifBlank { "-" }) },
                                ReportColumn<AttendanceRow>("STATUS", ReportColWidth.Fixed(100.dp)) { r, _ -> cellText(normalizeStatus(r.status)) },
                                ReportColumn<AttendanceRow>("APPROVAL", ReportColWidth.Fixed(96.dp)) { r, _ -> cellText(normalizeStatus(r.approval)) },
                            ),
                            data = rows,
                            scrollable = false,
                        )
                    }
                }
            }
        }
    }
}
