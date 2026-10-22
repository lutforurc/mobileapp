package com.example.cashbookbd.ui.admin

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.core.jInt
import com.example.cashbookbd.core.jLong
import com.example.cashbookbd.core.jObj
import com.example.cashbookbd.core.jObjs
import com.example.cashbookbd.core.jPayload
import com.example.cashbookbd.core.jStr
import com.example.cashbookbd.data.remote.WebApi
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.ParityRoutes
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.session.Permissions
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.DateRangeFields
import com.example.cashbookbd.ui.reports.LogoutOnExpiry
import com.example.cashbookbd.ui.reports.ReportLoading
import com.example.cashbookbd.ui.reports.ReportNote
import com.example.cashbookbd.ui.reports.SnackOnMessage
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val ALL_BRANCHES = SelectorOption("", "All Branches")
private val PER_PAGE = listOf(10, 25, 50, 100, 200).map { SelectorOption(it.toString(), it.toString()) }

private fun chartDate(v: String) = SimpleDate.fromApi(v.take(10))?.toDisplay() ?: v.ifBlank { "-" }
private fun timeOnly(v: String) = Regex("(\\d{1,2}:\\d{2})").find(v)?.value ?: "-"
private fun statusText(v: String) =
    v.ifBlank { "-" }.replace('_', ' ').split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

private suspend fun ReportRepository.branchOptions(): List<SelectorOption> =
    (getBranches() as? Resource.Success)?.data?.branches.orEmpty().map { SelectorOption(it.id.toString(), it.name) }

// ---- Approval Center -------------------------------------------------------------

enum class ApprovalTab { ATTENDANCE, VOUCHER }

data class ApprovalItem(
    val id: Long,
    // Attendance
    val date: String = "",
    val employee: String = "",
    val serial: String = "",
    val inTime: String = "",
    val outTime: String = "",
    val status: String = "",
    // Voucher
    val vrNo: String = "",
    val type: String = "",
    val createdBy: String = "",
    val approvedBy: String = "",
    val approvedOn: String = "",
    val branch: String = "",
)

data class ApprovalCenterUiState(
    val canRemoveApproval: Boolean = false,
    val tab: ApprovalTab = ApprovalTab.ATTENDANCE,
    val branches: List<SelectorOption> = listOf(ALL_BRANCHES),
    val branch: SelectorOption = ALL_BRANCHES,
    val from: SimpleDate? = null,
    val to: SimpleDate? = null,
    val search: String = "",
    val perPage: SelectorOption = PER_PAGE[1],
    val voucherStatus: String = "pending",
    val attendancePage: Int = 1,
    val voucherPage: Int = 1,
    val attendanceLastPage: Int = 1,
    val voucherLastPage: Int = 1,
    val countTotal: Int = 0,
    val countAttendance: Int = 0,
    val countVoucher: Int = 0,
    val attendance: List<ApprovalItem> = emptyList(),
    val vouchers: List<ApprovalItem> = emptyList(),
    val selectedAttendance: Set<Long> = emptySet(),
    val selectedVouchers: Set<Long> = emptySet(),
    val isLoading: Boolean = false,
    val isActing: Boolean = false,
    /** The action being confirmed: which list, and approve/reject/clear/remove. */
    val confirm: Pair<ApprovalTab, String>? = null,
    val remarks: String = "",
    val message: String? = null,
    val sessionExpired: Boolean = false,
) {
    val rows: List<ApprovalItem> get() = if (tab == ApprovalTab.ATTENDANCE) attendance else vouchers
    val selection: Set<Long> get() = if (tab == ApprovalTab.ATTENDANCE) selectedAttendance else selectedVouchers
}

/**
 * The Approval Center (ApprovalCenter.tsx): pending attendance entries and
 * vouchers awaiting sign-off, approved in bulk through
 * `approval-center/action` — the one door every approval goes through.
 */
class ApprovalCenterViewModel(
    private val api: WebApi,
    private val reports: ReportRepository,
    canRemoveApproval: Boolean,
    initialTab: ApprovalTab,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ApprovalCenterUiState(canRemoveApproval = canRemoveApproval, tab = initialTab))
    val uiState: StateFlow<ApprovalCenterUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val list = reports.branchOptions()
            _uiState.update { it.copy(branches = listOf(ALL_BRANCHES) + list) }
        }
        load()
    }

    fun onTab(t: ApprovalTab) = _uiState.update { it.copy(tab = t) }
    fun onBranch(o: SelectorOption) = _uiState.update { it.copy(branch = o, attendancePage = 1, voucherPage = 1) }
    fun onFrom(d: SimpleDate) = _uiState.update { it.copy(from = d) }
    fun onTo(d: SimpleDate) = _uiState.update { it.copy(to = d) }
    fun onSearch(v: String) = _uiState.update { it.copy(search = v) }
    fun onPerPage(o: SelectorOption) = _uiState.update { it.copy(perPage = o, attendancePage = 1, voucherPage = 1) }
    fun onRemarks(v: String) = _uiState.update { it.copy(remarks = v.take(255)) }

    fun loadFirstPage() {
        _uiState.update { it.copy(attendancePage = 1, voucherPage = 1) }
        load()
    }

    fun changeVoucherStatus(status: String) {
        if (_uiState.value.voucherStatus == status) return
        _uiState.update { it.copy(voucherStatus = status, voucherPage = 1) }
        load()
    }

    fun changePage(page: Int) {
        val s = _uiState.value
        val last = if (s.tab == ApprovalTab.ATTENDANCE) s.attendanceLastPage else s.voucherLastPage
        val bounded = page.coerceIn(1, last)
        _uiState.update { if (it.tab == ApprovalTab.ATTENDANCE) it.copy(attendancePage = bounded) else it.copy(voucherPage = bounded) }
        load()
    }

    fun toggle(id: Long) = _uiState.update {
        if (it.tab == ApprovalTab.ATTENDANCE) {
            it.copy(selectedAttendance = if (id in it.selectedAttendance) it.selectedAttendance - id else it.selectedAttendance + id)
        } else {
            it.copy(selectedVouchers = if (id in it.selectedVouchers) it.selectedVouchers - id else it.selectedVouchers + id)
        }
    }

    fun toggleAll() = _uiState.update {
        val ids = it.rows.map { r -> r.id }.toSet()
        val all = it.selection.size == ids.size
        if (it.tab == ApprovalTab.ATTENDANCE) it.copy(selectedAttendance = if (all) emptySet() else ids)
        else it.copy(selectedVouchers = if (all) emptySet() else ids)
    }

    fun load() {
        val s = _uiState.value
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val params = mapOf(
                "date_from" to (s.from?.toApi() ?: ""),
                "date_to" to (s.to?.toApi() ?: ""),
                "branch_id" to s.branch.id,
                "search" to s.search,
                "per_page" to s.perPage.id,
                "attendance_page" to s.attendancePage.toString(),
                "voucher_page" to s.voucherPage.toString(),
                "voucher_status" to s.voucherStatus,
            )
            when (val r = api.get("approval-center/summary", params)) {
                is Resource.Success -> {
                    val d = r.data.jPayload()
                    val counts = d.jObj("counts")
                    val meta = d.jObj("meta")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            countTotal = counts?.jInt("total") ?: 0,
                            countAttendance = counts?.jInt("attendance") ?: 0,
                            countVoucher = counts?.jInt("voucher") ?: 0,
                            attendanceLastPage = meta?.jInt("attendance_last_page") ?: 1,
                            voucherLastPage = meta?.jInt("voucher_last_page") ?: 1,
                            attendancePage = meta?.jInt("attendance_page") ?: it.attendancePage,
                            voucherPage = meta?.jInt("voucher_page") ?: it.voucherPage,
                            attendance = d.jObjs("attendance").map { o ->
                                ApprovalItem(
                                    id = o.jLong("id") ?: 0L, date = o.jStr("attendance_date"), employee = o.jStr("employee_name"),
                                    serial = o.jStr("employee_serial"), branch = o.jStr("branch_name"), inTime = o.jStr("in_time"),
                                    outTime = o.jStr("out_time"), status = o.jStr("status"),
                                )
                            },
                            vouchers = d.jObjs("vouchers").map { o ->
                                ApprovalItem(
                                    id = o.jLong("id") ?: 0L, vrNo = o.jStr("vr_no"), date = o.jStr("vr_date"),
                                    branch = o.jStr("branch_name"), type = o.jStr("transaction_type"),
                                    createdBy = o.jStr("created_by_name"), approvedBy = o.jStr("approved_by_name"),
                                    approvedOn = o.jStr("approved_date"),
                                )
                            },
                            selectedAttendance = emptySet(),
                            selectedVouchers = emptySet(),
                        )
                    }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, message = r.message.ifBlank { "Approval center data load failed" }, sessionExpired = r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun ask(tab: ApprovalTab, action: String) = _uiState.update { it.copy(confirm = tab to action, remarks = "") }
    fun dismiss() = _uiState.update { it.copy(confirm = null, remarks = "") }

    fun run() {
        val s = _uiState.value
        val (tab, action) = s.confirm ?: return
        val ids = if (tab == ApprovalTab.ATTENDANCE) s.selectedAttendance else s.selectedVouchers
        if (ids.isEmpty()) {
            _uiState.update { it.copy(confirm = null, message = "Please select at least one item") }
            return
        }
        _uiState.update { it.copy(isActing = true) }
        viewModelScope.launch {
            val body = JsonObject().apply {
                addProperty("type", if (tab == ApprovalTab.ATTENDANCE) "attendance" else "voucher")
                addProperty("action", action)
                add("ids", JsonArray().apply { ids.forEach { add(it) } })
                addProperty("remarks", s.remarks.trim().ifBlank { "${statusText(action)} from Approval Center" })
            }
            when (val r = api.post("approval-center/action", body)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(isActing = false, confirm = null, remarks = "", message = r.data.jStr("message").ifBlank { "Approval action completed" })
                    }
                    load()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isActing = false, message = r.message.ifBlank { "Approval action failed" }, sessionExpired = r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context, initialTab: ApprovalTab) = viewModelFactory {
            initializer {
                val app = context.applicationContext
                ApprovalCenterViewModel(
                    api = ServiceLocator.provideWebApi(app),
                    reports = ServiceLocator.provideReportRepository(app),
                    canRemoveApproval = Permissions.has(
                        ServiceLocator.provideSessionManager(app).state.value.settings?.permissions, "remove.approval",
                    ),
                    initialTab = initialTab,
                )
            }
        }
    }
}

@Composable
fun ApprovalCenterScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    initialTab: ApprovalTab = ApprovalTab.ATTENDANCE,
    viewModel: ApprovalCenterViewModel = viewModel(factory = ApprovalCenterViewModel.provideFactory(LocalContext.current, initialTab)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)
    SnackOnMessage(state.message, snackbar, viewModel::onMessageShown)
    val muted = MaterialTheme.appColors.textMuted
    val voucherApproved = state.tab == ApprovalTab.VOUCHER && state.voucherStatus == "approved"
    val page = if (state.tab == ApprovalTab.ATTENDANCE) state.attendancePage else state.voucherPage
    val lastPage = if (state.tab == ApprovalTab.ATTENDANCE) state.attendanceLastPage else state.voucherLastPage
    val total = if (state.tab == ApprovalTab.ATTENDANCE) state.countAttendance else state.countVoucher

    AuthenticatedShell(title = "Approval Center", currentRoute = Routes.ADMIN, navController = navController, onLogout = onLogout) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { DateRangeFields(state.from, state.to, viewModel::onFrom, viewModel::onTo, "From", "To") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppSelectDropdown("Branch/Project", state.branches, state.branch, viewModel::onBranch, Modifier.weight(1f))
                        AppSelectDropdown("Per Page", PER_PAGE, state.perPage, viewModel::onPerPage, Modifier.width(110.dp))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                        AppTextField(state.search, viewModel::onSearch, label = "Employee, voucher, branch", caption = "Search", modifier = Modifier.weight(1f))
                        PrimaryButton(text = "Load", onClick = viewModel::loadFirstPage, isLoading = state.isLoading)
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "Total Pending" to state.countTotal, "Attendance" to state.countAttendance,
                            "Vouchers" to state.countVoucher, "Selected" to state.selection.size,
                        ).forEach { (label, value) ->
                            Card(Modifier.weight(1f)) {
                                Column(Modifier.padding(8.dp)) {
                                    Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1)
                                    Text(value.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = AppFontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                item {
                    TabRow(selectedTabIndex = state.tab.ordinal) {
                        Tab(selected = state.tab == ApprovalTab.ATTENDANCE, onClick = { viewModel.onTab(ApprovalTab.ATTENDANCE) },
                            text = { Text("Attendance (${state.countAttendance})") })
                        Tab(selected = state.tab == ApprovalTab.VOUCHER, onClick = { viewModel.onTab(ApprovalTab.VOUCHER) },
                            text = { Text("Vouchers (${state.countVoucher})") })
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        LinkButton(text = "Audit History", onClick = { navController.navigate(ParityRoutes.APPROVAL_AUDIT) })
                        if (state.tab == ApprovalTab.VOUCHER) {
                            SecondaryButton(text = "Pending", onClick = { viewModel.changeVoucherStatus("pending") }, enabled = state.voucherStatus != "pending", compact = true)
                            SecondaryButton(text = "Approved", onClick = { viewModel.changeVoucherStatus("approved") }, enabled = state.voucherStatus != "approved", compact = true)
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PrimaryButton(
                            text = "Approve", compact = true,
                            enabled = state.selection.isNotEmpty() && !voucherApproved,
                            onClick = { viewModel.ask(state.tab, "approve") },
                        )
                        if (voucherApproved && state.canRemoveApproval) {
                            SecondaryButton(text = "Remove Approval", compact = true, enabled = state.selectedVouchers.isNotEmpty(),
                                onClick = { viewModel.ask(ApprovalTab.VOUCHER, "remove") })
                        }
                        SecondaryButton(text = "Reject", compact = true,
                            enabled = state.tab == ApprovalTab.ATTENDANCE && state.selection.isNotEmpty(),
                            onClick = { viewModel.ask(ApprovalTab.ATTENDANCE, "reject") })
                        SecondaryButton(text = "Clear", compact = true,
                            enabled = state.tab == ApprovalTab.ATTENDANCE && state.selection.isNotEmpty(),
                            onClick = { viewModel.ask(ApprovalTab.ATTENDANCE, "clear") })
                    }
                }
                if (state.rows.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = state.selection.size == state.rows.size, onCheckedChange = { viewModel.toggleAll() })
                            Text("Select all on this page", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.appColors.textOnScreen)
                        }
                    }
                }
                when {
                    state.isLoading && state.rows.isEmpty() -> item { ReportLoading() }
                    state.rows.isEmpty() -> item { ReportNote(if (voucherApproved) "No approved vouchers found" else "No pending approvals found") }
                    else -> items(state.rows, key = { "${state.tab}-${it.id}" }) { row ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Top) {
                                Checkbox(checked = row.id in state.selection, onCheckedChange = { viewModel.toggle(row.id) })
                                Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    if (state.tab == ApprovalTab.ATTENDANCE) {
                                        Text(row.employee.ifBlank { "-" }, fontWeight = AppFontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                        Text("${row.serial} · ${chartDate(row.date)} · ${row.branch.ifBlank { "-" }}", style = MaterialTheme.typography.labelSmall, color = muted)
                                        Text("In ${timeOnly(row.inTime)} · Out ${timeOnly(row.outTime)} · ${statusText(row.status)}", style = MaterialTheme.typography.labelSmall)
                                    } else {
                                        Text(row.vrNo, fontWeight = AppFontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                        Text("${chartDate(row.date)} · ${row.branch.ifBlank { "-" }} · ${row.type.ifBlank { "-" }}", style = MaterialTheme.typography.labelSmall, color = muted)
                                        Text("Created By: ${row.createdBy.ifBlank { "-" }}", style = MaterialTheme.typography.labelSmall)
                                        if (state.voucherStatus == "approved") {
                                            Text(
                                                "Approved By: ${row.approvedBy.ifBlank { "-" }} · Approved On: ${if (row.approvedOn.isBlank()) "-" else chartDate(row.approvedOn)}",
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        LinkButton(text = "Previous", enabled = page > 1, onClick = { viewModel.changePage(page - 1) })
                        Text("Page $page of $lastPage | $total item(s)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.appColors.textOnScreen)
                        LinkButton(text = "Next", enabled = page < lastPage, onClick = { viewModel.changePage(page + 1) })
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }

    state.confirm?.let { (tab, action) ->
        val count = if (tab == ApprovalTab.ATTENDANCE) state.selectedAttendance.size else state.selectedVouchers.size
        AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text("Confirm Approval Action") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Are you sure you want to $action selected ${if (tab == ApprovalTab.ATTENDANCE) "attendance" else "voucher"} items $count item(s) ?")
                    if (tab == ApprovalTab.ATTENDANCE) {
                        AppTextField(
                            state.remarks, viewModel::onRemarks,
                            label = "Reason / note for this $action",
                            caption = "Remarks ${if (action == "approve") "(optional)" else "(recommended)"}",
                            multiline = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = { LinkButton(text = "Confirm", onClick = viewModel::run, enabled = !state.isActing, color = MaterialTheme.colorScheme.error) },
            dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::dismiss) },
        )
    }
}

// ---- Approval Audit ----------------------------------------------------------------

private val AUDIT_ACTIONS = listOf(
    SelectorOption("", "All Actions"), SelectorOption("approved", "Approved"),
    SelectorOption("rejected", "Rejected"), SelectorOption("cancelled", "Cleared / Cancelled"),
)

data class AuditRow(
    val id: Long,
    val actionAt: String,
    val attendanceDate: String,
    val employee: String,
    val serial: String,
    val branch: String,
    val action: String,
    val actionBy: String,
    val remarks: String,
)

data class ApprovalAuditUiState(
    val branches: List<SelectorOption> = listOf(ALL_BRANCHES),
    val branch: SelectorOption = ALL_BRANCHES,
    val from: SimpleDate? = null,
    val to: SimpleDate? = null,
    val action: SelectorOption = AUDIT_ACTIONS.first(),
    val search: String = "",
    val perPage: SelectorOption = PER_PAGE[1],
    val page: Int = 1,
    val lastPage: Int = 1,
    val total: Int = 0,
    val rows: List<AuditRow> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null,
    val sessionExpired: Boolean = false,
)

/** Every attendance approval, rejection and clearing, with who did it (ApprovalAudit.tsx). */
class ApprovalAuditViewModel(private val api: WebApi, private val reports: ReportRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ApprovalAuditUiState())
    val uiState: StateFlow<ApprovalAuditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val list = reports.branchOptions()
            _uiState.update { it.copy(branches = listOf(ALL_BRANCHES) + list) }
        }
        load()
    }

    fun onBranch(o: SelectorOption) = _uiState.update { it.copy(branch = o, page = 1) }
    fun onFrom(d: SimpleDate) = _uiState.update { it.copy(from = d, page = 1) }
    fun onTo(d: SimpleDate) = _uiState.update { it.copy(to = d, page = 1) }
    fun onAction(o: SelectorOption) = _uiState.update { it.copy(action = o, page = 1) }
    fun onSearch(v: String) = _uiState.update { it.copy(search = v, page = 1) }
    fun onPerPage(o: SelectorOption) = _uiState.update { it.copy(perPage = o, page = 1) }
    fun loadFirstPage() { _uiState.update { it.copy(page = 1) }; load() }
    fun changePage(p: Int) { _uiState.update { it.copy(page = p.coerceIn(1, it.lastPage)) }; load() }

    fun load() {
        val s = _uiState.value
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val params = mapOf(
                "date_from" to (s.from?.toApi() ?: ""), "date_to" to (s.to?.toApi() ?: ""),
                "branch_id" to s.branch.id, "action" to s.action.id, "search" to s.search,
                "per_page" to s.perPage.id, "page" to s.page.toString(),
            )
            when (val r = api.get("approval-center/audit", params)) {
                is Resource.Success -> {
                    val d = r.data.jPayload()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            page = d.jObj("meta")?.jInt("page") ?: it.page,
                            lastPage = d.jObj("meta")?.jInt("last_page") ?: 1,
                            total = d.jObj("counts")?.jInt("total") ?: 0,
                            rows = d.jObjs("rows").map { o ->
                                AuditRow(
                                    id = o.jLong("id") ?: 0L, actionAt = o.jStr("action_at"), attendanceDate = o.jStr("attendance_date"),
                                    employee = o.jStr("employee_name"), serial = o.jStr("employee_serial"), branch = o.jStr("branch_name"),
                                    action = o.jStr("action"), actionBy = o.jStr("action_by_name"), remarks = o.jStr("remarks"),
                                )
                            },
                        )
                    }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, message = r.message.ifBlank { "Approval audit load failed" }, sessionExpired = r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val app = context.applicationContext
                ApprovalAuditViewModel(ServiceLocator.provideWebApi(app), ServiceLocator.provideReportRepository(app))
            }
        }
    }
}

@Composable
fun ApprovalAuditScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    viewModel: ApprovalAuditViewModel = viewModel(factory = ApprovalAuditViewModel.provideFactory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)
    SnackOnMessage(state.message, snackbar, viewModel::onMessageShown)
    val muted = MaterialTheme.appColors.textMuted

    AuthenticatedShell(title = "Approval Audit", currentRoute = Routes.ADMIN, navController = navController, onLogout = onLogout) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { DateRangeFields(state.from, state.to, viewModel::onFrom, viewModel::onTo, "From", "To") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppSelectDropdown("Branch/Project", state.branches, state.branch, viewModel::onBranch, Modifier.weight(1f))
                        AppSelectDropdown("Action", AUDIT_ACTIONS, state.action, viewModel::onAction, Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                        AppTextField(state.search, viewModel::onSearch, label = "Employee, user, remarks", caption = "Search", modifier = Modifier.weight(1f))
                        AppSelectDropdown("Per Page", PER_PAGE, state.perPage, viewModel::onPerPage, Modifier.width(110.dp))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PrimaryButton(text = "Load", onClick = viewModel::loadFirstPage, isLoading = state.isLoading)
                        LinkButton(text = "Back to Approval Center", onClick = { navController.popBackStack() })
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Attendance Approval Audit", fontWeight = AppFontWeight.Bold, color = MaterialTheme.appColors.textOnScreen)
                        Text("${state.total} record(s)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.appColors.textOnScreenMuted)
                    }
                }
                when {
                    state.isLoading && state.rows.isEmpty() -> item { ReportLoading() }
                    state.rows.isEmpty() -> item { ReportNote("No audit records found") }
                    else -> items(state.rows, key = { it.id }) { row ->
                        val tone = when (row.action) {
                            "approved" -> MaterialTheme.appColors.success
                            "rejected" -> MaterialTheme.appColors.danger
                            "cancelled" -> MaterialTheme.appColors.warning
                            else -> muted
                        }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(row.employee.ifBlank { "-" }, fontWeight = AppFontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    Text(row.action.ifBlank { "-" }.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, fontWeight = AppFontWeight.SemiBold, color = tone)
                                }
                                Text(
                                    "${row.serial} · ${row.branch.ifBlank { "-" }} · Attendance ${if (row.attendanceDate.isBlank()) "-" else chartDate(row.attendanceDate)}",
                                    style = MaterialTheme.typography.labelSmall, color = muted,
                                )
                                Text(
                                    "At ${row.actionAt.replace('T', ' ').take(16).ifBlank { "-" }} by ${row.actionBy.ifBlank { "-" }}",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Text("Remarks: ${row.remarks.ifBlank { "-" }}", style = MaterialTheme.typography.labelSmall, color = muted)
                            }
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        LinkButton(text = "Previous", enabled = state.page > 1, onClick = { viewModel.changePage(state.page - 1) })
                        Text("Page ${state.page} of ${state.lastPage} | ${state.total} record(s)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.appColors.textOnScreen)
                        LinkButton(text = "Next", enabled = state.page < state.lastPage, onClick = { viewModel.changePage(state.page + 1) })
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}
