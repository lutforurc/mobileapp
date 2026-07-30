package com.example.cashbookbd.ui.admin

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.AdminExtrasRepository
import com.example.cashbookbd.data.repository.AdminNotificationForm
import com.example.cashbookbd.data.repository.AdminNotificationRow
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.data.repository.RoleRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.FieldButton
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.accents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Platform notifications management, a port of the web's /admin/notifications:
 * the publish form on top, the notification list beneath it (the Highlight
 * Rules layout). The server allows the PLATFORM company only — its 403
 * "You are not allowed…" is rendered as a clear message.
 */

private val TONE_OPTIONS = listOf(
    SelectorOption("info", "Info"),
    SelectorOption("warning", "Warning"),
    SelectorOption("danger", "Danger"),
    SelectorOption("success", "Success"),
)

private val AUDIENCE_OPTIONS = listOf(
    SelectorOption("global", "Global"),
    SelectorOption("branch", "Branch"),
    SelectorOption("role", "Role"),
)

private const val MAX_TITLE = 200

data class AdminNotificationsUiState(
    // List
    val isLoading: Boolean = false,
    val loadError: String? = null,
    val rows: List<AdminNotificationRow> = emptyList(),

    // Form
    val title: String = "",
    val bodyText: String = "",
    val tone: String = "info",
    val audienceType: String = "global",
    val selectedBranch: SelectorOption? = null,
    val selectedRole: SelectorOption? = null,
    /** Optional expiry; null renders as "Never". */
    val expiresAt: SimpleDate? = null,

    // Audience dropdown sources (also resolve list rows' branch/role names).
    val branches: List<SelectorOption> = emptyList(),
    val roles: List<SelectorOption> = emptyList(),
    val isBranchesLoading: Boolean = false,
    val isRolesLoading: Boolean = false,

    val isSaving: Boolean = false,
    /** Row awaiting its server delete (spinner replaces the bin icon). */
    val deletingId: Long? = null,
    /** Row the confirmation dialog is asking about; null = no dialog. */
    val confirmDelete: AdminNotificationRow? = null,
    /** One-shot snackbar text (publish/delete outcome). */
    val message: String? = null,
    val sessionExpired: Boolean = false,
) {
    val canSave: Boolean
        get() = title.isNotBlank() && bodyText.isNotBlank() && !isSaving &&
            (audienceType != "branch" || selectedBranch != null) &&
            (audienceType != "role" || selectedRole != null)
}

class AdminNotificationsViewModel(
    private val repository: AdminExtrasRepository,
    private val reportRepository: ReportRepository,
    private val roleRepository: RoleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminNotificationsUiState())
    val uiState: StateFlow<AdminNotificationsUiState> = _uiState.asStateFlow()

    init {
        load()
        loadBranches()
        loadRoles()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            when (val result = repository.fetchNotifications()) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, rows = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = result.message,
                        sessionExpired = result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    /** Branch ddl — also resolves branch names on the list rows. */
    private fun loadBranches() {
        _uiState.update { it.copy(isBranchesLoading = true) }
        viewModelScope.launch {
            when (val result = reportRepository.getBranches()) {
                is Resource.Success -> _uiState.update { state ->
                    state.copy(
                        isBranchesLoading = false,
                        branches = result.data.branches.map { SelectorOption(it.id.toString(), it.name) },
                    )
                }
                is Resource.Error -> _uiState.update {
                    // Quiet failure: the picker stays empty; audience labels
                    // fall back to the raw type.
                    it.copy(isBranchesLoading = false, sessionExpired = it.sessionExpired || result.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    /** Role ddl (reuses [RoleRepository.loadRoles]) — also resolves role names on rows. */
    private fun loadRoles() {
        _uiState.update { it.copy(isRolesLoading = true) }
        viewModelScope.launch {
            when (val result = roleRepository.loadRoles()) {
                is Resource.Success -> _uiState.update { state ->
                    state.copy(
                        isRolesLoading = false,
                        roles = result.data.map { SelectorOption(it.id.toString(), it.name) },
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isRolesLoading = false, sessionExpired = it.sessionExpired || result.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onTitle(value: String) = _uiState.update { it.copy(title = value.take(MAX_TITLE)) }

    fun onBodyText(value: String) = _uiState.update { it.copy(bodyText = value) }

    fun onTone(option: SelectorOption) = _uiState.update { it.copy(tone = option.id) }

    fun onAudience(option: SelectorOption) = _uiState.update {
        it.copy(audienceType = option.id, selectedBranch = null, selectedRole = null)
    }

    fun onBranch(option: SelectorOption) = _uiState.update { it.copy(selectedBranch = option) }

    fun onRole(option: SelectorOption) = _uiState.update { it.copy(selectedRole = option) }

    fun onExpiresAt(value: SimpleDate?) = _uiState.update { it.copy(expiresAt = value) }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        val form = AdminNotificationForm(
            title = state.title.trim(),
            message = state.bodyText.trim(),
            tone = state.tone,
            audienceType = state.audienceType,
            branchId = if (state.audienceType == "branch") state.selectedBranch?.id?.toLongOrNull() else null,
            roleId = if (state.audienceType == "role") state.selectedRole?.id?.toLongOrNull() else null,
            expiresAt = state.expiresAt?.toApi(),
        )
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = repository.createNotification(form)) {
                is Resource.Success -> {
                    _uiState.update { clearForm(it).copy(isSaving = false, message = result.data) }
                    load()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = result.message,
                        sessionExpired = result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun requestDelete(row: AdminNotificationRow) = _uiState.update { it.copy(confirmDelete = row) }

    fun dismissDelete() = _uiState.update { it.copy(confirmDelete = null) }

    fun confirmDelete() {
        val row = _uiState.value.confirmDelete ?: return
        _uiState.update { it.copy(confirmDelete = null, deletingId = row.id) }
        viewModelScope.launch {
            when (val result = repository.deleteNotification(row.id)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(deletingId = null, message = result.data) }
                    load()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        deletingId = null,
                        message = result.message,
                        sessionExpired = result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    private fun clearForm(state: AdminNotificationsUiState) = state.copy(
        title = "",
        bodyText = "",
        tone = "info",
        audienceType = "global",
        selectedBranch = null,
        selectedRole = null,
        expiresAt = null,
    )

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return viewModelFactory {
                initializer {
                    AdminNotificationsViewModel(
                        ServiceLocator.provideAdminExtrasRepository(appContext),
                        ServiceLocator.provideReportRepository(appContext),
                        ServiceLocator.provideRoleRepository(appContext),
                    )
                }
            }
        }
    }
}

@Composable
fun AdminNotificationsScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdminNotificationsViewModel = viewModel(
        factory = AdminNotificationsViewModel.provideFactory(LocalContext.current)
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.sessionExpired) {
        if (uiState.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    AuthenticatedShell(
        title = "Admin Notifications",
        currentRoute = Routes.ADMIN,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                NotificationForm(state = uiState, viewModel = viewModel)
                Spacer(Modifier.height(24.dp))
                NotificationList(state = uiState, viewModel = viewModel)
                Spacer(Modifier.height(24.dp))
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    uiState.confirmDelete?.let { row ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Delete notification?") },
            text = { Text("\"${row.title}\" will be removed for every user it targets.") },
            confirmButton = { LinkButton(text = "Delete", onClick = viewModel::confirmDelete) },
            dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::dismissDelete) },
        )
    }
}

@Composable
private fun NotificationForm(state: AdminNotificationsUiState, viewModel: AdminNotificationsViewModel) {
    // The form sits directly on the screen backdrop (not on a card), so every
    // text uses the background's on-colour.
    val onScreen = MaterialTheme.colorScheme.onBackground
    val onScreenMuted = onScreen.copy(alpha = 0.75f)
    val context = LocalContext.current

    Text(
        text = "Publish Notification",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = onScreen,
    )
    Spacer(Modifier.height(12.dp))

    AppTextField(
        value = state.title,
        onValueChange = viewModel::onTitle,
        label = "Notification title",
        caption = "Title",
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))

    AppTextField(
        value = state.bodyText,
        onValueChange = viewModel::onBodyText,
        label = "What should the users read?",
        caption = "Message",
        multiline = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))

    AppSelectDropdown(
        label = "Tone",
        options = TONE_OPTIONS,
        selected = TONE_OPTIONS.firstOrNull { it.id == state.tone } ?: TONE_OPTIONS.first(),
        onSelected = viewModel::onTone,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))

    AppSelectDropdown(
        label = "Audience",
        options = AUDIENCE_OPTIONS,
        selected = AUDIENCE_OPTIONS.firstOrNull { it.id == state.audienceType } ?: AUDIENCE_OPTIONS.first(),
        onSelected = viewModel::onAudience,
        modifier = Modifier.fillMaxWidth(),
    )

    if (state.audienceType == "branch") {
        Spacer(Modifier.height(12.dp))
        AppSelectDropdown(
            label = "Branch",
            options = state.branches,
            selected = state.selectedBranch,
            onSelected = viewModel::onBranch,
            placeholder = if (state.isBranchesLoading) "Loading…" else "Select Branch",
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (state.audienceType == "role") {
        Spacer(Modifier.height(12.dp))
        AppSelectDropdown(
            label = "Role",
            options = state.roles,
            selected = state.selectedRole,
            onSelected = viewModel::onRole,
            placeholder = if (state.isRolesLoading) "Loading…" else "Select Role",
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(12.dp))

    Text(
        text = "Expires At (optional)",
        style = MaterialTheme.typography.labelSmall,
        color = onScreenMuted,
    )
    Spacer(Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FieldButton(
            text = state.expiresAt?.toDisplay() ?: "Never",
            onClick = {
                showExpiryPicker(context, state.expiresAt ?: SimpleDate.today(), viewModel::onExpiresAt)
            },
            icon = Icons.Filled.DateRange,
            modifier = Modifier.weight(1f),
        )
        if (state.expiresAt != null) {
            LinkButton(
                text = "Never",
                onClick = { viewModel.onExpiresAt(null) },
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
    Spacer(Modifier.height(16.dp))

    PrimaryButton(
        text = "Publish Notification",
        onClick = viewModel::save,
        enabled = state.canSave,
        isLoading = state.isSaving,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NotificationList(state: AdminNotificationsUiState, viewModel: AdminNotificationsViewModel) {
    val onScreen = MaterialTheme.colorScheme.onBackground
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Notifications",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = onScreen,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = viewModel::load, enabled = !state.isLoading) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Refresh list",
                tint = onScreen.copy(alpha = 0.8f),
            )
        }
    }
    Spacer(Modifier.height(8.dp))

    when {
        state.isLoading && state.rows.isEmpty() -> Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        // The PLATFORM-only 403 ("You are not allowed…") lands here as a clear message.
        state.loadError != null -> Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = state.loadError,
                color = onScreen,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(text = "Retry", onClick = viewModel::load)
        }

        else -> ReportTable(
            columns = notificationColumns(state, viewModel),
            data = state.rows,
            noDataMessage = "No notifications yet.",
            scrollable = false,
        )
    }
}

/** # | Notification (title + message) | Tone | Audience | Reads | Created | Expires | Action. */
@Composable
private fun notificationColumns(
    state: AdminNotificationsUiState,
    viewModel: AdminNotificationsViewModel,
): List<ReportColumn<AdminNotificationRow>> {
    val onScreen = MaterialTheme.colorScheme.onBackground
    return remember(state.deletingId, state.branches, state.roles, onScreen) {
        listOf(
            ReportColumn("#", ReportColWidth.Fixed(36.dp), TextAlign.Center) { _, index ->
                cellText((index + 1).toString(), align = TextAlign.Center, color = onScreen)
            },
            ReportColumn("Notification", ReportColWidth.Fixed(200.dp)) { r, _ ->
                ReportTableCell.Slot { NotificationCell(r, onScreen) }
            },
            ReportColumn("Tone", ReportColWidth.Fixed(96.dp)) { r, _ ->
                ReportTableCell.Slot { ToneBadge(r.tone) }
            },
            ReportColumn("Audience", ReportColWidth.Fixed(120.dp)) { r, _ ->
                cellText(audienceLabel(r, state.branches, state.roles), maxLines = 2, color = onScreen)
            },
            ReportColumn("Reads", ReportColWidth.Fixed(56.dp), TextAlign.End) { r, _ ->
                cellText(r.readCount.toString(), align = TextAlign.End, color = onScreen)
            },
            ReportColumn("Created", ReportColWidth.Fixed(92.dp)) { r, _ ->
                cellText(displayDate(r.createdAt) ?: "-", color = onScreen)
            },
            ReportColumn("Expires", ReportColWidth.Fixed(92.dp)) { r, _ ->
                cellText(displayDate(r.expiresAt) ?: "Never", color = onScreen)
            },
            ReportColumn("Action", ReportColWidth.Fixed(60.dp), TextAlign.Center) { r, _ ->
                ReportTableCell.Slot { DeleteCell(r, state.deletingId == r.id, viewModel) }
            },
        )
    }
}

@Composable
private fun NotificationCell(row: AdminNotificationRow, textColor: androidx.compose.ui.graphics.Color) {
    Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
        Text(
            text = row.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (row.message.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = row.message,
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.75f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The tone as a small outlined pill in the tone's accent colour. */
@Composable
private fun ToneBadge(tone: String) {
    val color = when (tone.lowercase(Locale.US)) {
        "warning" -> MaterialTheme.accents.amber
        "danger" -> MaterialTheme.accents.red
        "success" -> MaterialTheme.accents.green
        else -> MaterialTheme.accents.blue
    }
    Box(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
        Box(
            modifier = Modifier
                .border(1.dp, color, RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Text(
                text = tone.replaceFirstChar { it.titlecase(Locale.US) },
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DeleteCell(
    row: AdminNotificationRow,
    isDeleting: Boolean,
    viewModel: AdminNotificationsViewModel,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isDeleting) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            IconButton(onClick = { viewModel.requestDelete(row) }) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete ${row.title}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** The branch/role name a row targets, when resolvable; else the audience type. */
private fun audienceLabel(
    row: AdminNotificationRow,
    branches: List<SelectorOption>,
    roles: List<SelectorOption>,
): String = when (row.audienceType.lowercase(Locale.US)) {
    "branch" -> branches.firstOrNull { it.id == row.branchId?.toString() }?.label ?: "Branch"
    "role" -> roles.firstOrNull { it.id == row.roleId?.toString() }?.label ?: "Role"
    else -> row.audienceType.replaceFirstChar { it.titlecase(Locale.US) }
}

/** dd/MM/yyyy from an API date/datetime string; null when blank/unparseable. */
private fun displayDate(raw: String?): String? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null
    return SimpleDate.fromApi(text.take(10))?.toDisplay() ?: text
}

private fun showExpiryPicker(context: Context, current: SimpleDate, onPicked: (SimpleDate) -> Unit) {
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth -> onPicked(SimpleDate(year = year, month = month + 1, day = dayOfMonth)) },
        current.year,
        current.month - 1,
        current.day,
    ).show()
}
