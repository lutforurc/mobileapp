package com.example.cashbookbd.ui.applist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.admin.AdminMenu
import com.example.cashbookbd.customer.CustomerMenu
import com.example.cashbookbd.subscription.SubscriptionMenu
import com.example.cashbookbd.data.repository.AppListRow
import com.example.cashbookbd.ui.components.AddButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.cellText

/**
 * A list screen (Recycle Bin, Log Changes, Branch/User/Order lists, …). Fetches
 * its [AppListViewModel] rows and renders them through the shared table. Lists
 * whose spec declares a status toggle also get a trailing Action column.
 */
@Composable
fun AppListScreen(
    listKey: String,
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppListViewModel = viewModel(
        factory = AppListViewModel.provideFactory(LocalContext.current, listKey)
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

    // A failed status change reports why, then clears.
    LaunchedEffect(state.actionMessage) {
        val message = state.actionMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onActionMessageShown()
    }

    // Keep the right drawer section highlighted.
    val parentRoute = when {
        CustomerMenu.byKey(listKey) != null -> Routes.CUSTOMERS
        SubscriptionMenu.byKey(listKey) != null -> Routes.SUBSCRIPTION
        AdminMenu.byKey(listKey) != null -> Routes.ADMIN
        else -> Routes.VR_SETTINGS
    }

    // Coming back from a successful create: reload so the new row shows.
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
        title = state.title,
        currentRoute = parentRoute,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // No title heading here — the app bar already shows the menu name.
            if (state.isPaginated || state.addAction != null) {
                ListToolbar(
                    state = state,
                    onPerPageChange = viewModel::onPerPageChange,
                    onAdd = { navController.navigate(it) },
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                ListBody(
                    state = state,
                    onRetry = { viewModel.load() },
                    onToggleStatus = viewModel::onToggleStatus,
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
}

/**
 * The web list's toolbar: rows-per-page on the left, the create button (when the
 * list has a create screen) on the right.
 */
@Composable
private fun ListToolbar(
    state: AppListUiState,
    onPerPageChange: (Int) -> Unit,
    onAdd: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (state.isPaginated) {
            Box {
                SecondaryButton(
                    text = state.perPage.toString(),
                    onClick = { expanded = true },
                    enabled = !state.isLoading,
                    trailingIcon = Icons.Filled.ArrowDropDown,
                    trailingIconDescription = "Rows per page",
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    PER_PAGE_OPTIONS.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.toString()) },
                            onClick = {
                                expanded = false
                                onPerPageChange(option)
                            },
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.width(1.dp))
        }

        state.addAction?.let { add ->
            AddButton(text = add.label, onClick = { onAdd(add.route) })
        }
    }
}

@Composable
private fun PaginationBar(state: AppListUiState, onPrev: () -> Unit, onNext: () -> Unit) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        LinkButton(
            text = "Prev",
            onClick = onPrev,
            enabled = state.canPrev,
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            iconDescription = "Previous",
        )
        Text(
            text = "Page ${state.currentPage} of ${state.lastPage}  •  ${state.total} total",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinkButton(
            text = "Next",
            onClick = onNext,
            enabled = state.canNext,
            trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            iconDescription = "Next",
        )
    }
}

@Composable
private fun ListBody(
    state: AppListUiState,
    onRetry: () -> Unit,
    onToggleStatus: (AppListRow, Boolean) -> Unit,
) {
    when {
        state.isLoading -> Center { CircularProgressIndicator() }

        state.error != null -> Center {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                PrimaryButton(text = "Retry", onClick = onRetry)
            }
        }

        state.rows.isEmpty() -> Center {
            Text(
                text = "No records found.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        else -> {
            val columns = remember(state.columns, state.hasStatusToggle, state.togglingIds) {
                buildColumns(state, onToggleStatus)
            }
            ReportTable(columns = columns, data = state.rows)
        }
    }
}

private val COL_SL = 48.dp
private val COL_ACTION = 88.dp

private fun buildColumns(
    state: AppListUiState,
    onToggleStatus: (AppListRow, Boolean) -> Unit,
): List<ReportColumn<AppListRow>> = buildList {
    add(
        ReportColumn("#", ReportColWidth.Fixed(COL_SL), TextAlign.Center) { _, index ->
            cellText((index + 1).toString(), align = TextAlign.Center)
        },
    )
    state.columns.forEachIndexed { ci, col ->
        val align = if (col.numeric) TextAlign.End else TextAlign.Start
        add(
            ReportColumn(
                header = col.label,
                width = ReportColWidth.Fixed(if (col.numeric) 112.dp else 168.dp),
                align = align,
            ) { row, _ -> cellText(row.cells.getOrNull(ci).orEmpty(), align = align, maxLines = 2) },
        )
    }
    if (state.hasStatusToggle) {
        add(
            ReportColumn("Action", ReportColWidth.Fixed(COL_ACTION), TextAlign.Center) { row, _ ->
                ReportTableCell.Slot {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Switch(
                            checked = row.statusOn,
                            onCheckedChange = { onToggleStatus(row, it) },
                            // No id means nothing to send; a change already in
                            // flight must land before another is accepted.
                            enabled = row.id != null && row.id !in state.togglingIds,
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}
