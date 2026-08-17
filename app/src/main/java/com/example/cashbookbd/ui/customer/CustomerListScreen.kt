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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
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
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.session.Permissions
import com.example.cashbookbd.ui.components.TutorialScreens
import com.example.cashbookbd.ui.components.TutorialVideoLink
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

    val sessionState by ServiceLocator
        .provideSessionManager(androidx.compose.ui.platform.LocalContext.current)
        .state.collectAsStateWithLifecycle()
    // Deleting an opening balance deletes a voucher, so it answers to the
    // voucher permission — the same one the API checks (not cs.delete).
    val canDeleteVoucher = Permissions.hasAny(sessionState.permissions, listOf("voucher.delete"))
    // The full Edit Customer form rides cs.edit, like the web's edit button.
    val canEditCustomer = Permissions.hasAny(sessionState.permissions, listOf("cs.edit"))
    // The branch's "Opening ongoing" flag: off, the web list drops the Opening
    // column — its input, voucher link and Delete — leaving only the ledger page.
    val openingEnabled = sessionState.settings?.openingOngoing == true

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
        actions = {
            // The web's demo-video link on the customer list header. The URL is
            // the operator's now, looked up by screen key — the link gates
            // itself on need_demo_tutorial and on a video being on file.
            TutorialVideoLink(screenKey = TutorialScreens.CUSTOMER_SUPPLIER)
        },
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
                        columns = customerColumns(
                            currentPage = state.currentPage,
                            mobileFormat = sessionState.settings?.mobileNumberFormat.orEmpty(),
                            openingEnabled = openingEnabled,
                            canDeleteVoucher = canDeleteVoucher,
                            canEditCustomer = canEditCustomer,
                            onEdit = viewModel::startEdit,
                            onEditCustomer = { row ->
                                navController.navigate("${Routes.CUSTOMER_EDIT}/${row.id}")
                            },
                            onOpenLedger = { row ->
                                if (row.coa4Id.isNotBlank()) {
                                    navController.navigate(Routes.ledgerFor(row.coa4Id, row.name))
                                }
                            },
                            onDeleteOpening = viewModel::askDeleteOpening,
                        ),
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
        EditDialog(state = state, row = row, openingEnabled = openingEnabled, viewModel = viewModel)
    }

    state.openingDeleteRow?.let { row ->
        DeleteOpeningDialog(state = state, row = row, viewModel = viewModel)
    }
}

@Composable
private fun customerColumns(
    currentPage: Int,
    /** The branch's display grouping for numbers ("" = as stored) — dc17c5a. */
    mobileFormat: String,
    openingEnabled: Boolean,
    canDeleteVoucher: Boolean,
    canEditCustomer: Boolean,
    onEdit: (CustomerRow) -> Unit,
    onEditCustomer: (CustomerRow) -> Unit,
    onOpenLedger: (CustomerRow) -> Unit,
    onDeleteOpening: (CustomerRow) -> Unit,
): List<ReportColumn<CustomerRow>> {
    val onScreen = MaterialTheme.colorScheme.onBackground
    val offset = (currentPage - 1) * CUSTOMERS_PER_PAGE
    return listOf<ReportColumn<CustomerRow>>(
        ReportColumn("#", ReportColWidth.Fixed(40.dp), TextAlign.Center) { _, index ->
            cellText((offset + index + 1).toString(), align = TextAlign.Center, color = onScreen)
        },
        ReportColumn("Name", ReportColWidth.Fixed(140.dp)) { row, _ ->
            cellText(row.name.ifBlank { "-" }, color = onScreen, maxLines = 2)
        },
    ) + listOfNotNull(
        // The whole Opening column rides the branch's "Opening ongoing" flag,
        // exactly like the web's isOpeningColumns: switched off, the figure,
        // its voucher link and the Delete all leave the list.
        if (!openingEnabled) null else ReportColumn<CustomerRow>(
            "Opening", ReportColWidth.Fixed(96.dp), TextAlign.End,
        ) { row, _ ->
            if (row.openingVrNo.isBlank()) {
                cellText(openingText(row), align = TextAlign.End, color = onScreen)
            } else {
                // The voucher this figure sits on. Without it the balance is a
                // number nobody can trace; tapping it opens the ledger already
                // pointed at this customer's account.
                ReportTableCell.Slot {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(
                            text = openingText(row),
                            style = MaterialTheme.typography.bodySmall,
                            color = onScreen,
                            maxLines = 1,
                        )
                        Text(
                            text = row.openingVrNo,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            modifier = Modifier.clickable { onOpenLedger(row) },
                        )
                    }
                }
            }
        },
    ) + listOf<ReportColumn<CustomerRow>>(
        ReportColumn("Address", ReportColWidth.Fixed(150.dp)) { row, _ ->
            cellText(row.address.ifBlank { "-" }, color = onScreen, maxLines = 2)
        },
        ReportColumn("Ledger Page", ReportColWidth.Fixed(110.dp)) { row, _ ->
            cellText(row.ledgerPage.ifBlank { "-" }, color = onScreen, maxLines = 2)
        },
        ReportColumn("Mobile", ReportColWidth.Fixed(120.dp)) { row, _ ->
            cellText(
                com.example.cashbookbd.core.MobileFormat.format(row.mobile, mobileFormat).ifBlank { "-" },
                color = onScreen,
            )
        },
        ReportColumn("Action", ReportColWidth.Fixed(120.dp), TextAlign.Center) { row, _ ->
            ReportTableCell.Slot {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The full Edit Customer form (every field, photo, panels) —
                    // the web's row edit, behind the same cs.edit.
                    if (canEditCustomer) {
                        IconButton(onClick = { onEditCustomer(row) }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = "Edit customer ${row.name}",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                    // The quick opening/ledger-page entry (the web's inline inputs).
                    IconButton(onClick = { onEdit(row) }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit opening/ledger of ${row.name}",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    // Only where there is a voucher to delete — a row that never
                    // had an opening balance has nothing to offer here — and only
                    // while the branch is still keying openings, like the web.
                    if (openingEnabled && row.openingVrNo.isNotBlank() && canDeleteVoucher) {
                        IconButton(
                            onClick = { onDeleteOpening(row) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete opening balance of ${row.name}",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        },
    )
}

/**
 * The web's confirm: it names the amount and the voucher, not just "are you
 * sure" — the clerk is about to remove a ledger entry, and this is the last
 * place they can check it is the right one.
 */
@Composable
private fun DeleteOpeningDialog(
    state: CustomerListUiState,
    row: CustomerRow,
    viewModel: CustomerListViewModel,
) {
    AlertDialog(
        onDismissRequest = viewModel::cancelDeleteOpening,
        title = { Text("Delete Opening Balance") },
        text = {
            Column {
                Text("Delete the opening balance of ${row.name.ifBlank { "this customer" }}?")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Amount ${row.opening}  •  Voucher ${row.openingVrNo}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = AppFontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "The voucher goes to the trash, not away for good. " +
                        "The customer is not deleted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            PrimaryButton(
                text = "Delete",
                onClick = viewModel::confirmDeleteOpening,
                enabled = !state.isDeletingOpening,
                isLoading = state.isDeletingOpening,
                compact = true,
            )
        },
        dismissButton = {
            LinkButton(
                text = "Cancel",
                onClick = viewModel::cancelDeleteOpening,
                enabled = !state.isDeletingOpening,
            )
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
    openingEnabled: Boolean,
    viewModel: CustomerListViewModel,
) {
    AlertDialog(
        onDismissRequest = viewModel::cancelEdit,
        title = { Text(row.name.ifBlank { "Edit customer" }) },
        text = {
            Column {
                // Shown only while the branch is keying openings ("Opening
                // ongoing") — off, the web list has no opening entry at all
                // and this dialog is only the ledger page's.
                if (openingEnabled) {
                    DialogLabel("Opening Balance")
                    AppTextField(
                        value = state.editOpening,
                        onValueChange = viewModel::onEditOpening,
                        label = "Enter opening balance",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Re-saving rewrites this voucher in place, so its number —
                    // and anything printed carrying it — stays put.
                    if (row.openingVrNo.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Voucher ${row.openingVrNo}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
                DialogLabel("Ledger Page")
                AppTextField(
                    value = state.editLedger,
                    onValueChange = viewModel::onEditLedger,
                    label = "Enter ledger page",
                    modifier = Modifier.fillMaxWidth(),
                )
                // Refusals show here, in the dialog: the snackbar sits behind
                // the scrim, where "approved voucher" would go by unseen.
                state.editError?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
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
            // Held while the save is in flight — dismissing then would let the
            // late result close or overwrite whatever dialog came next.
            LinkButton(text = "Cancel", onClick = viewModel::cancelEdit, enabled = !state.isSaving)
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
