package com.example.cashbookbd.ui.applist

import com.example.cashbookbd.ui.theme.muted
import com.example.cashbookbd.ui.theme.appColors
import com.example.cashbookbd.ui.theme.AppFontWeight
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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.admin.AdminMenu
import com.example.cashbookbd.customer.CustomerMenu
import com.example.cashbookbd.products.ProductsMenu
import com.example.cashbookbd.realestate.RealEstateMenu
import com.example.cashbookbd.requisition.RequisitionMenu
import com.example.cashbookbd.subscription.SubscriptionMenu
import com.example.cashbookbd.data.repository.AppListRow
import com.example.cashbookbd.ui.components.AddButton
import com.example.cashbookbd.ui.components.AppTextField
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
        ProductsMenu.byKey(listKey) != null -> Routes.PRODUCTS
        SubscriptionMenu.byKey(listKey) != null -> Routes.SUBSCRIPTION
        AdminMenu.byKey(listKey) != null -> Routes.ADMIN
        RealEstateMenu.byKey(listKey) != null -> Routes.REAL_ESTATE
        RequisitionMenu.byKey(listKey) != null -> Routes.REQUISITIONS
        else -> Routes.VR_SETTINGS
    }

    // Coming back to this list (from an add/edit screen or elsewhere): always
    // refresh — the saved-message reload below can lose the pop-transition race.
    com.example.cashbookbd.ui.common.ReloadOnResume(onReload = viewModel::reloadCurrent)

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

    // The web's tutorial icon beside the title — spec-declared URL, shown only
    // while the branch's need_demo_tutorial setting is on.
    val tutorialUrl = com.example.cashbookbd.applist.AppLists.byKey(listKey)?.tutorialUrl
    val showTutorial = tutorialUrl != null &&
        com.example.cashbookbd.di.ServiceLocator
            .provideSessionManager(androidx.compose.ui.platform.LocalContext.current)
            .state.collectAsStateWithLifecycle().value.settings?.needDemoTutorial == true

    AuthenticatedShell(
        title = state.title,
        currentRoute = parentRoute,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
        actions = {
            if (showTutorial) {
                com.example.cashbookbd.ui.components.TutorialVideoButton(url = tutorialUrl!!)
            }
        },
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
                    onEdit = { row ->
                        val edit = state.editAction ?: return@ListBody
                        val id = row.editId ?: return@ListBody
                        navController.navigate("${edit.route}/$id")
                    },
                    onDelete = viewModel::requestDelete,
                    onOpeningEdit = viewModel::startOpeningEdit,
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

    if (state.openingEdit != null) {
        OpeningStockDialog(state = state, viewModel = viewModel)
    }

    if (state.pendingDelete != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Delete this record?") },
            text = {
                Text(
                    // The first non-blank cell names the row being deleted.
                    state.pendingDelete?.cells?.firstOrNull { it.isNotBlank() && it != "-" }
                        ?.let { "\"$it\" will be deleted. This cannot be undone." }
                        ?: "The record will be deleted. This cannot be undone.",
                )
            },
            confirmButton = {
                PrimaryButton(
                    text = "Delete",
                    onClick = viewModel::confirmDelete,
                    enabled = !state.isDeleting,
                    isLoading = state.isDeleting,
                    compact = true,
                )
            },
            dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::cancelDelete) },
        )
    }
}

/**
 * The Product List's opening stock entry — the web's inline IMEI/Qty/Rate
 * columns as a dialog. Serial lines drive the qty; Save books the opening
 * purchase voucher via `product/update-qty-rate`.
 */
@Composable
private fun OpeningStockDialog(state: AppListUiState, viewModel: AppListViewModel) {
    val opening = state.openingEdit?.opening ?: return
    val hasSerials = state.openingSerial.isNotBlank()
    AlertDialog(
        onDismissRequest = viewModel::cancelOpeningEdit,
        title = { Text(opening.name.ifBlank { "Opening stock" }) },
        text = {
            Column {
                DialogLabel("IMEI / Serial (one per line)")
                AppTextField(
                    value = state.openingSerial,
                    onValueChange = viewModel::onOpeningSerial,
                    label = "IMEI Number",
                    multiline = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                DialogLabel("Qty")
                AppTextField(
                    value = state.openingQty,
                    onValueChange = viewModel::onOpeningQty,
                    label = "Qty",
                    // With serials the qty is their count, like the web's recount.
                    enabled = !hasSerials,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                DialogLabel("Rate")
                AppTextField(
                    value = state.openingRate,
                    onValueChange = viewModel::onOpeningRate,
                    label = "Rate",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            PrimaryButton(
                text = "Save",
                onClick = viewModel::saveOpeningStock,
                enabled = !state.openingSaving,
                isLoading = state.openingSaving,
                compact = true,
            )
        },
        dismissButton = {
            LinkButton(text = "Cancel", onClick = viewModel::cancelOpeningEdit)
        },
    )
}

/** A field caption inside the dialog — dark on the dialog surface (the shared
 *  field caption uses the on-teal ink, which is faint here). */
@Composable
private fun DialogLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = AppFontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 4.dp, bottom = 3.dp),
    )
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
        modifier = Modifier
            .fillMaxWidth()
            // A hair of space below, so the buttons do not touch the table header.
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 2.dp),
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
                    compact = true,
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
            AddButton(text = add.label, onClick = { onAdd(add.route) }, compact = true)
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
            color = MaterialTheme.appColors.textOnScreenMuted,
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
    onEdit: (AppListRow) -> Unit,
    onDelete: (AppListRow) -> Unit,
    onOpeningEdit: (AppListRow) -> Unit,
) {
    when {
        state.isLoading -> Center { CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground) }

        state.error != null -> Center {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.error!!, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                PrimaryButton(text = "Retry", onClick = onRetry)
            }
        }

        state.rows.isEmpty() -> Center {
            Text(
                text = "No records found.",
                color = MaterialTheme.appColors.textOnScreenMuted,
                textAlign = TextAlign.Center,
            )
        }

        else -> {
            val columns = remember(
                state.columns, state.hasStatusToggle, state.editAction, state.deleteAction,
                state.togglingIds, state.openingEnabled,
            ) {
                buildColumns(state, onToggleStatus, onEdit, onDelete, onOpeningEdit)
            }
            ReportTable(columns = columns, data = state.rows)
        }
    }
}

private val COL_SL = 48.dp
private val COL_ACTION = 88.dp
private val COL_ACTION_WITH_EDIT = 132.dp

private fun buildColumns(
    state: AppListUiState,
    onToggleStatus: (AppListRow, Boolean) -> Unit,
    onEdit: (AppListRow) -> Unit,
    onDelete: (AppListRow) -> Unit,
    onOpeningEdit: (AppListRow) -> Unit,
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
    val hasEdit = state.editAction != null
    val hasDelete = state.deleteAction != null
    if (state.hasStatusToggle || hasEdit || hasDelete || state.openingEnabled) {
        // Two icon buttons (or a button plus the toggle) need the wide column.
        val actionCount = listOf(hasEdit, hasDelete, state.hasStatusToggle, state.openingEnabled)
            .count { it }
        val width = if (actionCount > 1) COL_ACTION_WITH_EDIT else COL_ACTION
        add(
            ReportColumn("Action", ReportColWidth.Fixed(width), TextAlign.Center) { row, _ ->
                ReportTableCell.Slot {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (hasEdit) {
                            IconButton(
                                onClick = { onEdit(row) },
                                // Without an id there is nothing to open.
                                enabled = row.editId != null,
                                modifier = Modifier.size(EditButtonSize),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = "Edit",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        if (state.openingEnabled) {
                            // Opening stock entry (Product List while the branch
                            // is "Opening ongoing") — opens the IMEI/Qty/Rate dialog.
                            IconButton(
                                onClick = { onOpeningEdit(row) },
                                enabled = row.opening != null,
                                modifier = Modifier.size(EditButtonSize),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = "Set opening stock",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        if (hasDelete) {
                            IconButton(
                                onClick = { onDelete(row) },
                                enabled = row.deleteId != null,
                                modifier = Modifier.size(EditButtonSize),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        if (state.hasStatusToggle) {
                            Switch(
                                checked = row.statusOn,
                                onCheckedChange = { onToggleStatus(row, it) },
                                // No id means nothing to send; a change already in
                                // flight must land before another is accepted.
                                enabled = row.id != null && row.id !in state.togglingIds,
                            )
                        }
                    }
                }
            },
        )
    }
}

private val EditButtonSize = 36.dp

@Composable
private fun Center(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}
