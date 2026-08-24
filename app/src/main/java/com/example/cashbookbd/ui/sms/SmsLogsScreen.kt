package com.example.cashbookbd.ui.sms

import com.example.cashbookbd.ui.theme.appColors
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
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
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.data.repository.SmsLogRow
import com.example.cashbookbd.data.repository.SmsRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.FormFieldHeight
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SelectorOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Fixed page size, matching the web SMS Logs' default of 10 per page. */
const val SMS_LOGS_PER_PAGE = 10

data class SmsLogsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val rows: List<SmsLogRow> = emptyList(),

    val branches: List<BranchOption> = emptyList(),
    /** null = All Branches (the filter field is omitted from the request). */
    val selectedBranchId: Long? = null,
    /** The text in the mobile search box (applied on Search, like the web). */
    val mobileQuery: String = "",

    val currentPage: Int = 1,
    val lastPage: Int = 1,
    val total: Int = 0,

    val sessionExpired: Boolean = false,
) {
    val canPrev: Boolean get() = currentPage > 1
    val canNext: Boolean get() = currentPage < lastPage
    val showPagination: Boolean get() = lastPage > 1
}

/** Backs the SMS Logs: the branch/mobile filters and pagination. */
class SmsLogsViewModel(
    private val repository: SmsRepository,
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SmsLogsUiState())
    val uiState: StateFlow<SmsLogsUiState> = _uiState.asStateFlow()

    init {
        loadBranches()
        load(page = 1)
    }

    private fun loadBranches() {
        viewModelScope.launch {
            when (val result = reportRepository.getBranches()) {
                is Resource.Success -> _uiState.update { it.copy(branches = result.data.branches) }
                is Resource.Error -> _uiState.update {
                    // The dropdown just stays "All Branches"; only a 401 matters.
                    it.copy(sessionExpired = it.sessionExpired || result.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    // One in-flight page fetch at a time — a stale response must not land
    // after a newer page/filter has been requested.
    private var loadJob: kotlinx.coroutines.Job? = null

    fun load(page: Int) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val state = _uiState.value
            val result = repository.loadLogs(
                page = page,
                branchId = state.selectedBranchId,
                mobile = state.mobileQuery,
                perPage = SMS_LOGS_PER_PAGE,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        rows = result.data.rows,
                        currentPage = result.data.currentPage,
                        lastPage = result.data.lastPage,
                        total = result.data.total,
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    /** Selecting a branch applies immediately, from the first page. */
    fun onBranchSelected(branchId: Long?) {
        _uiState.update { it.copy(selectedBranchId = branchId) }
        load(page = 1)
    }

    fun onMobileQuery(value: String) = _uiState.update { it.copy(mobileQuery = value) }

    /** Runs the current mobile search from the first page (the web's Search). */
    fun onSearch() = load(page = 1)

    fun nextPage() {
        if (_uiState.value.canNext) load(_uiState.value.currentPage + 1)
    }

    fun prevPage() {
        if (_uiState.value.canPrev) load(_uiState.value.currentPage - 1)
    }

    fun onErrorShown() = _uiState.update { it.copy(error = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                SmsLogsViewModel(
                    repository = ServiceLocator.provideSmsRepository(appContext),
                    reportRepository = ServiceLocator.provideReportRepository(appContext),
                )
            }
        }
    }
}

/**
 * SMS Logs — a port of the web's /sms/send "SMS Logs" page: an optional branch
 * filter, a mobile-number search, and the paginated table of sent messages.
 */
@Composable
fun SmsLogsScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SmsLogsViewModel = viewModel(
        factory = SmsLogsViewModel.provideFactory(LocalContext.current)
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    // A refresh failure while rows are already on screen has no empty state to
    // fall back on, so surface it as a snackbar instead of silently keeping the
    // stale page (the empty-list case still shows the full error + Retry).
    LaunchedEffect(state.error) {
        val message = state.error ?: return@LaunchedEffect
        if (state.rows.isNotEmpty()) {
            snackbarHostState.showSnackbar(message)
            viewModel.onErrorShown()
        }
    }

    AuthenticatedShell(
        title = "SMS Logs",
        currentRoute = Routes.ADMIN,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            SmsLogsFilters(state = state, viewModel = viewModel)
            Spacer(Modifier.height(12.dp))

            Box(modifier = Modifier.weight(1f)) {
                SmsLogsBody(state = state, onRetry = { viewModel.load(page = 1) })
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            if (state.showPagination) {
                PaginationBar(state = state, onPrev = viewModel::prevPage, onNext = viewModel::nextPage)
            }
        }
    }
}

@Composable
private fun SmsLogsFilters(state: SmsLogsUiState, viewModel: SmsLogsViewModel) {
    // "All Branches" first, like the web's optional branch filter.
    val branchOptions = remember(state.branches) {
        listOf(SelectorOption("", "All Branches")) +
            state.branches.map { SelectorOption(it.id.toString(), it.name) }
    }
    AppSelectDropdown(
        label = "Branch",
        options = branchOptions,
        selected = branchOptions.firstOrNull { it.id == (state.selectedBranchId?.toString() ?: "") },
        onSelected = { viewModel.onBranchSelected(it.id.toLongOrNull()) },
    )
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppTextField(
            value = state.mobileQuery,
            onValueChange = viewModel::onMobileQuery,
            label = "Search mobile number…",
            keyboardType = KeyboardType.Phone,
            modifier = Modifier.weight(1f),
            trailingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        // Levelled with the field beside it by the constant the field itself
        // uses, so restyling FieldFrame keeps the two in step.
        PrimaryButton(
            text = "Search",
            onClick = viewModel::onSearch,
            compact = true,
            modifier = Modifier.height(FormFieldHeight),
        )
    }
}

@Composable
private fun SmsLogsBody(state: SmsLogsUiState, onRetry: () -> Unit) {
    when {
        state.isLoading && state.rows.isEmpty() -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        state.error != null && state.rows.isEmpty() -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = state.error,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(text = "Retry", onClick = onRetry)
        }

        else -> ReportTable(
            columns = smsLogColumns(state),
            data = state.rows,
            noDataMessage = "No SMS sent logs found.",
        )
    }
}

/** # | Mobile | Name | Message | Provider | Status | Attempts | Sent At | Created. */
@Composable
private fun smsLogColumns(state: SmsLogsUiState): List<ReportColumn<SmsLogRow>> {
    val onScreen = MaterialTheme.colorScheme.onBackground
    // Serial continues across pages, like the web's offset-based numbering.
    val offset = (state.currentPage - 1) * SMS_LOGS_PER_PAGE
    return listOf(
        ReportColumn("#", ReportColWidth.Fixed(40.dp), TextAlign.Center) { _, index ->
            cellText((offset + index + 1).toString(), align = TextAlign.Center, color = onScreen)
        },
        ReportColumn("Mobile", ReportColWidth.Fixed(120.dp)) { row, _ ->
            cellText(row.mobile.ifBlank { "-" }, color = onScreen)
        },
        // Who the message went to (web f4828a4b) — a log of numbers reads as
        // a log of numbers. Narrow and wrapping, like the message beside it.
        ReportColumn("Name", ReportColWidth.Fixed(120.dp)) { row, _ ->
            cellText(row.recipientName.ifBlank { "-" }, color = onScreen, maxLines = 2)
        },
        ReportColumn("Message", ReportColWidth.Fixed(220.dp)) { row, _ ->
            cellText(row.message.ifBlank { "-" }, color = onScreen, maxLines = 3)
        },
        ReportColumn("Provider", ReportColWidth.Fixed(100.dp)) { row, _ ->
            cellText(row.provider.ifBlank { "-" }, color = onScreen)
        },
        ReportColumn("Status", ReportColWidth.Fixed(90.dp)) { row, _ ->
            cellText(row.status.ifBlank { "-" }, color = onScreen)
        },
        ReportColumn("Attempts", ReportColWidth.Fixed(80.dp), TextAlign.Center) { row, _ ->
            // Numeric 0 shows as a dash, like every other report table.
            val attempts = row.attempts.takeIf { it.isNotBlank() && it != "0" } ?: "-"
            cellText(attempts, align = TextAlign.Center, color = onScreen)
        },
        ReportColumn("Sent At", ReportColWidth.Fixed(130.dp)) { row, _ ->
            cellText(row.sentAt.ifBlank { "-" }, color = onScreen, maxLines = 2)
        },
        ReportColumn("Created", ReportColWidth.Fixed(130.dp)) { row, _ ->
            cellText(row.createdAt.ifBlank { "-" }, color = onScreen, maxLines = 2)
        },
    )
}

@Composable
private fun PaginationBar(
    state: SmsLogsUiState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    HorizontalDivider(color = MaterialTheme.appColors.divider)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        LinkButton(
            text = "Prev",
            onClick = onPrev,
            enabled = state.canPrev,
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Page ${state.currentPage} of ${state.lastPage} • ${state.total} total",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        LinkButton(
            text = "Next",
            onClick = onNext,
            enabled = state.canNext,
            trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
