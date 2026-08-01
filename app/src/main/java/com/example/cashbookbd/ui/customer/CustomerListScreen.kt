package com.example.cashbookbd.ui.customer

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.data.repository.CustomerRow
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AddButton
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.FormFieldHeight
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.cellText

/**
 * The Customers list — a port of the web's List Customers. Search + paginated
 * table, an Add Customer button, and a per-row edit that sets the customer's
 * opening balance (needed at the start of business) and ledger page.
 */
@Composable
fun CustomerListScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CustomerListViewModel = viewModel(
        factory = CustomerListViewModel.provideFactory(androidx.compose.ui.platform.LocalContext.current)
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
    LaunchedEffect(state.actionMessage) {
        val message = state.actionMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onActionMessageShown()
    }
    LaunchedEffect(state.error) {
        val message = state.error ?: return@LaunchedEffect
        if (state.rows.isNotEmpty()) snackbarHostState.showSnackbar(message)
    }

    // Coming back from Add Customer: reload so the new row shows.
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
        title = "Customers",
        currentRoute = Routes.CUSTOMERS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchQuery,
                    label = "Search name, mobile, address…",
                    modifier = Modifier.weight(1f),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                // Matched to the field beside it by the same constant the field
                // uses, so restyling FieldFrame keeps the two level.
                PrimaryButton(
                    text = "Search",
                    onClick = viewModel::onSearch,
                    compact = true,
                    modifier = Modifier.height(FormFieldHeight),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AddButton(
                    text = "Add Customer",
                    onClick = { navController.navigate(Routes.CUSTOMER_ADD) },
                    compact = true,
                )
            }
            Spacer(Modifier.height(12.dp))

            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.isLoading && state.rows.isEmpty() -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    state.error != null && state.rows.isEmpty() -> Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = state.error!!,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton(text = "Retry", onClick = { viewModel.load(page = 1) })
                    }

                    else -> ReportTable(
                        columns = customerColumns(state.currentPage, viewModel::startEdit),
                        data = state.rows,
                        noDataMessage = "No customers found.",
                    )
                }
                SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
            }

            if (state.showPagination) {
                PaginationBar(state = state, onPrev = viewModel::prevPage, onNext = viewModel::nextPage)
            }
        }
    }

    state.editing?.let { row ->
        EditDialog(state = state, row = row, viewModel = viewModel)
    }
}

@Composable
private fun customerColumns(
    currentPage: Int,
    onEdit: (CustomerRow) -> Unit,
): List<ReportColumn<CustomerRow>> {
    val onScreen = MaterialTheme.colorScheme.onBackground
    val offset = (currentPage - 1) * CUSTOMERS_PER_PAGE
    return listOf(
        ReportColumn("#", ReportColWidth.Fixed(40.dp), TextAlign.Center) { _, index ->
            cellText((offset + index + 1).toString(), align = TextAlign.Center, color = onScreen)
        },
        ReportColumn("Name", ReportColWidth.Fixed(140.dp)) { row, _ ->
            cellText(row.name.ifBlank { "-" }, color = onScreen, maxLines = 2)
        },
        ReportColumn("Opening", ReportColWidth.Fixed(90.dp), TextAlign.End) { row, _ ->
            cellText(openingText(row), align = TextAlign.End, color = onScreen)
        },
        ReportColumn("Address", ReportColWidth.Fixed(150.dp)) { row, _ ->
            cellText(row.address.ifBlank { "-" }, color = onScreen, maxLines = 2)
        },
        ReportColumn("Ledger Page", ReportColWidth.Fixed(110.dp)) { row, _ ->
            cellText(row.ledgerPage.ifBlank { "-" }, color = onScreen, maxLines = 2)
        },
        ReportColumn("Mobile", ReportColWidth.Fixed(120.dp)) { row, _ ->
            cellText(row.mobile.ifBlank { "-" }, color = onScreen)
        },
        ReportColumn("Action", ReportColWidth.Fixed(56.dp), TextAlign.Center) { row, _ ->
            ReportTableCell.Slot {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    IconButton(onClick = { onEdit(row) }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit ${row.name}",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
        },
    )
}

/** Opening shows as "-" while still zero (unset), or the amount once set. */
private fun openingText(row: CustomerRow): String =
    if ((row.opening.toDoubleOrNull() ?: 0.0) == 0.0) "-" else row.opening

@Composable
private fun EditDialog(
    state: CustomerListUiState,
    row: CustomerRow,
    viewModel: CustomerListViewModel,
) {
    AlertDialog(
        onDismissRequest = viewModel::cancelEdit,
        title = { Text(row.name.ifBlank { "Edit customer" }) },
        text = {
            Column {
                if (state.openingEditable) {
                    DialogLabel("Opening Balance")
                    AppTextField(
                        value = state.editOpening,
                        onValueChange = viewModel::onEditOpening,
                        label = "Enter opening balance",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = "Opening balance: ${row.opening} — already set, can't be changed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(12.dp))
                DialogLabel("Ledger Page")
                AppTextField(
                    value = state.editLedger,
                    onValueChange = viewModel::onEditLedger,
                    label = "Enter ledger page",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            PrimaryButton(
                text = "Save",
                onClick = viewModel::saveEdit,
                enabled = !state.isSaving,
                isLoading = state.isSaving,
                compact = true,
            )
        },
        dismissButton = {
            LinkButton(text = "Cancel", onClick = viewModel::cancelEdit)
        },
    )
}

/** A field caption inside the edit dialog — dark on the dialog surface (the shared
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

@Composable
private fun PaginationBar(
    state: CustomerListUiState,
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
