package com.example.cashbookbd.ui.user

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.data.repository.UserRow
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.session.Permissions
import com.example.cashbookbd.ui.components.AddButton
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.BrandPill
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.cellText

/** Permissions that let a user open the Add User form, matching the web. */
private val CREATE_USER_PERMISSIONS = listOf("all.user.create", "user.create", "user.store", "all.user.add")

/**
 * The User List — a port of the web's UserList page. Search + paginated table of
 * the company's users, an Add User button, and per-row actions: an edit pencil,
 * and (for the super admin only, exactly as the web gates it on user id 1) a
 * temporary-password key.
 */
@Composable
fun UserListScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserListViewModel = viewModel(
        factory = UserListViewModel.provideFactory(LocalContext.current)
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sessionManager = remember { ServiceLocator.provideSessionManager(context) }
    val sessionState by sessionManager.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val canAddUser = Permissions.hasAny(sessionState.permissions, CREATE_USER_PERMISSIONS)
    // The web shows the temporary-password key only for the global super admin.
    val isSuperAdmin = sessionState.settings?.userId == 1L

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    LaunchedEffect(state.actionMessage) {
        val message = state.actionMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onActionMessageShown()
    }

    // Coming back from a successful create/edit: reload so the change shows.
    val savedHandle = navController.currentBackStackEntry?.savedStateHandle
    val savedMessage by savedHandle
        ?.getStateFlow<String?>(Routes.CREATED_MESSAGE, null)
        ?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }

    LaunchedEffect(savedMessage) {
        val message = savedMessage ?: return@LaunchedEffect
        savedHandle?.set(Routes.CREATED_MESSAGE, null)
        viewModel.load(page = 1)
        snackbarHostState.showSnackbar(message)
    }

    AuthenticatedShell(
        title = "User List",
        currentRoute = Routes.ADMIN,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            SearchToolbar(
                query = state.searchQuery,
                onQuery = viewModel::onSearchQuery,
                onSearch = viewModel::onSearch,
                canAddUser = canAddUser,
                onAdd = { navController.navigate(Routes.USER_ADD) },
            )
            Spacer(Modifier.height(12.dp))

            Box(modifier = Modifier.weight(1f)) {
                UserListBody(
                    state = state,
                    isSuperAdmin = isSuperAdmin,
                    onEdit = { row -> navController.navigate("${Routes.USER_EDIT}/${row.userId}") },
                    onTempPassword = { row -> viewModel.generateTemporaryPassword(row.userId) },
                    onRetry = { viewModel.load(page = 1) },
                )
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

    state.tempPassword?.let { generated ->
        TemporaryPasswordDialog(
            email = generated.email,
            password = generated.password,
            onDismiss = viewModel::dismissTemporaryPassword,
        )
    }
}

@Composable
private fun SearchToolbar(
    query: String,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    canAddUser: Boolean,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppTextField(
            value = query,
            onValueChange = onQuery,
            label = "Search name, email, phone…",
            modifier = Modifier.weight(1f),
            trailingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        PrimaryButton(text = "Search", onClick = onSearch, compact = true)
    }
    if (canAddUser) {
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            AddButton(text = "Add User", onClick = onAdd, compact = true)
        }
    }
}

@Composable
private fun UserListBody(
    state: UserListUiState,
    isSuperAdmin: Boolean,
    onEdit: (UserRow) -> Unit,
    onTempPassword: (UserRow) -> Unit,
    onRetry: () -> Unit,
) {
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
            columns = userColumns(state, isSuperAdmin, onEdit, onTempPassword),
            data = state.rows,
            noDataMessage = "No users found.",
        )
    }
}

/** The table columns: # | User Name | Working Branch | Email | Role | Action. */
@Composable
private fun userColumns(
    state: UserListUiState,
    isSuperAdmin: Boolean,
    onEdit: (UserRow) -> Unit,
    onTempPassword: (UserRow) -> Unit,
): List<ReportColumn<UserRow>> {
    val onScreen = MaterialTheme.colorScheme.onBackground
    // Serial continues across pages, like the web's offset-based numbering.
    val offset = (state.currentPage - 1) * USERS_PER_PAGE
    return listOf(
        ReportColumn("#", ReportColWidth.Fixed(40.dp), TextAlign.Center) { _, index ->
            cellText((offset + index + 1).toString(), align = TextAlign.Center, color = onScreen)
        },
        ReportColumn("User Name", ReportColWidth.Fixed(140.dp)) { row, _ ->
            cellText(row.name.ifBlank { "-" }, color = onScreen, maxLines = 2)
        },
        ReportColumn("Working Branch", ReportColWidth.Fixed(150.dp)) { row, _ ->
            cellText(row.branch.ifBlank { "-" }, color = onScreen, maxLines = 2)
        },
        ReportColumn("Email", ReportColWidth.Fixed(190.dp)) { row, _ ->
            cellText(row.email.ifBlank { "-" }, color = onScreen, maxLines = 2)
        },
        ReportColumn("Role", ReportColWidth.Fixed(160.dp)) { row, _ ->
            if (row.role.isBlank()) {
                cellText("-", color = onScreen)
            } else {
                ReportTableCell.Slot {
                    Box(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        BrandPill(text = row.role, compact = true)
                    }
                }
            }
        },
        ReportColumn("Action", ReportColWidth.Fixed(if (isSuperAdmin) 104.dp else 64.dp), TextAlign.Center) { row, _ ->
            ReportTableCell.Slot {
                UserActionCell(
                    row = row,
                    isSuperAdmin = isSuperAdmin,
                    isGenerating = state.tempPasswordForId == row.userId,
                    onEdit = onEdit,
                    onTempPassword = onTempPassword,
                )
            }
        },
    )
}

@Composable
private fun UserActionCell(
    row: UserRow,
    isSuperAdmin: Boolean,
    isGenerating: Boolean,
    onEdit: (UserRow) -> Unit,
    onTempPassword: (UserRow) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onEdit(row) }, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "Edit ${row.name}",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        if (isSuperAdmin) {
            if (isGenerating) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = { onTempPassword(row) }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Generate temporary password for ${row.name}",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}

@Composable
private fun PaginationBar(
    state: UserListUiState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f))
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

@Composable
private fun TemporaryPasswordDialog(
    email: String,
    password: String,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Temporary password") },
        text = {
            Column {
                if (email.isNotBlank()) {
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    text = password,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Share this with the user — it replaces their password on next login.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            LinkButton(text = "Copy", onClick = { clipboard.setText(AnnotatedString(password)) })
        },
        dismissButton = {
            LinkButton(text = "Close", onClick = onDismiss)
        },
    )
}
