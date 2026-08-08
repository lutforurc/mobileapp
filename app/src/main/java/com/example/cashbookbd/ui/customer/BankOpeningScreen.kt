package com.example.cashbookbd.ui.customer

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.data.repository.BankOpeningRow
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.session.Permissions
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.FormFieldHeight
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.ReportTableCell
import com.example.cashbookbd.ui.reports.cellText
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import java.util.Locale

/**
 * Bank Opening — a port of the web's Account Opening Balance page. What cash,
 * the bank accounts and the mobile banking wallets held on the day the book
 * starts; each figure is posted as a journal voucher. Accounts are listed by
 * their level-3 group, edited through the same dialog pattern as the customer
 * list's opening entry.
 */
@Composable
fun BankOpeningScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BankOpeningViewModel = viewModel(
        factory = BankOpeningViewModel.provideFactory(androidx.compose.ui.platform.LocalContext.current)
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val sessionState by ServiceLocator
        .provideSessionManager(androidx.compose.ui.platform.LocalContext.current)
        .state.collectAsStateWithLifecycle()
    // Writing a figure raises a journal voucher, so the screen answers to its
    // own permission pair, not the CoA one — the same split the API makes.
    val canEdit = Permissions.hasAny(sessionState.permissions, listOf("bank.opening.edit"))
    // Deleting an opening deletes a voucher, so it answers to the voucher
    // permission — the same one the API checks on its delete.
    val canDeleteVoucher = Permissions.hasAny(sessionState.permissions, listOf("voucher.delete"))
    // The branch's "Opening ongoing" flag: off, the whole screen stands down.
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
        // With rows on screen the error passes by as a snackbar and is cleared,
        // so it cannot replay on a recomposition restart; with no rows it stays
        // in state to keep the full-screen error + Retry standing.
        if (state.rows.isNotEmpty()) {
            snackbarHostState.showSnackbar(message)
            viewModel.onErrorShown()
        }
    }

    AuthenticatedShell(
        title = "Bank Opening",
        currentRoute = Routes.CUSTOMERS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        if (!openingEnabled) {
            // The web replaces the whole page with this notice and makes no
            // call at all; the ViewModel skipped its load for the same reason.
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Card {
                    Text(
                        text = "Opening balances are switched off for this branch. " +
                            "Turn them on in the branch settings to record what cash " +
                            "and the bank accounts started with.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
            return@AuthenticatedShell
        }

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "What cash, the bank accounts and the mobile banking wallets held " +
                    "on the day this book starts. Each figure is posted as a journal voucher.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.textOnScreenMuted,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchQuery,
                    label = "Search account or group…",
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
                IconButton(onClick = viewModel::load) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
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
                        PrimaryButton(text = "Retry", onClick = viewModel::load)
                    }

                    state.rows.isEmpty() -> Box(
                        Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No accounts to open. Cash, bank and mobile banking " +
                                "accounts appear here once they are active in the " +
                                "Chart of Accounts.",
                            color = MaterialTheme.appColors.textOnScreenMuted,
                            textAlign = TextAlign.Center,
                        )
                    }

                    else -> Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        state.groups.forEach { group ->
                            GroupHeading(name = group.name, subtotal = group.subtotal)
                            ReportTable(
                                columns = bankOpeningColumns(
                                    canEdit = canEdit,
                                    canDeleteVoucher = canDeleteVoucher,
                                    onEdit = viewModel::startEdit,
                                    onOpenLedger = { row ->
                                        navController.navigate(Routes.ledgerFor(row.id.toString(), row.name))
                                    },
                                    onDelete = viewModel::askDelete,
                                ),
                                data = group.rows,
                                scrollable = false,
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                        // The web's grand-total line under every group.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Total opening",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = AppFontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = formatMoney(state.totalOpening),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = AppFontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
                SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    state.editing?.let { row ->
        EditOpeningDialog(state = state, row = row, viewModel = viewModel)
    }

    state.deleteRow?.let { row ->
        DeleteOpeningDialog(state = state, row = row, viewModel = viewModel)
    }
}

/** A group's uppercase heading with its subtotal at the other end, like the web. */
@Composable
private fun GroupHeading(name: String, subtotal: Double) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name.uppercase(Locale.US),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = AppFontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = formatMoney(subtotal),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun bankOpeningColumns(
    canEdit: Boolean,
    canDeleteVoucher: Boolean,
    onEdit: (BankOpeningRow) -> Unit,
    onOpenLedger: (BankOpeningRow) -> Unit,
    onDelete: (BankOpeningRow) -> Unit,
): List<ReportColumn<BankOpeningRow>> {
    val onScreen = MaterialTheme.colorScheme.onBackground
    return listOf(
        ReportColumn("#", ReportColWidth.Fixed(36.dp), TextAlign.Center) { _, index ->
            cellText((index + 1).toString(), align = TextAlign.Center, color = onScreen)
        },
        ReportColumn("Account", ReportColWidth.Weight(1f)) { row, _ ->
            if (row.isActive) {
                cellText(row.name.ifBlank { "-" }, color = onScreen, maxLines = 2)
            } else {
                // A closed account that still holds a live opening: its figure
                // can be removed but not changed, so the badge says why the
                // pencil is missing.
                ReportTableCell.Slot {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                        Text(
                            text = row.name.ifBlank { "-" },
                            style = MaterialTheme.typography.bodySmall,
                            color = onScreen,
                            maxLines = 2,
                        )
                        Text(
                            text = "Inactive",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.appColors.warning,
                        )
                    }
                }
            }
        },
        ReportColumn("Opening", ReportColWidth.Fixed(110.dp), TextAlign.End) { row, _ ->
            if (row.openingVrNo.isBlank()) {
                cellText(openingText(row), align = TextAlign.End, color = onScreen)
            } else {
                // The voucher this figure sits on. Tapping it opens the ledger
                // already pointed at this account.
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
        ReportColumn("Action", ReportColWidth.Fixed(88.dp), TextAlign.Center) { row, _ ->
            ReportTableCell.Slot {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // An inactive account's figure can be removed, not changed —
                    // the web disables its input the same way.
                    if (canEdit && row.isActive) {
                        IconButton(onClick = { onEdit(row) }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Edit opening of ${row.name}",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                    // Only where there is a voucher to delete, and only to who
                    // the API will actually allow: destroy() asks for
                    // bank.opening.edit before voucher.delete, so a button
                    // shown on the voucher permission alone (as the web still
                    // does) would only ever come back 403.
                    if (row.openingVrNo.isNotBlank() && canDeleteVoucher && canEdit) {
                        IconButton(onClick = { onDelete(row) }, modifier = Modifier.size(36.dp)) {
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

@Composable
private fun EditOpeningDialog(
    state: BankOpeningUiState,
    row: BankOpeningRow,
    viewModel: BankOpeningViewModel,
) {
    AlertDialog(
        onDismissRequest = viewModel::cancelEdit,
        title = { Text(row.name.ifBlank { "Opening balance" }) },
        text = {
            Column {
                DialogLabel("Opening Balance")
                AppTextField(
                    value = state.editAmount,
                    onValueChange = viewModel::onEditAmount,
                    label = "Enter opening balance",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                if (row.openingVrNo.isNotBlank()) {
                    // Re-saving rewrites this voucher in place, so its number —
                    // and anything printed carrying it — stays put.
                    Text(
                        text = "Voucher ${row.openingVrNo}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = "Money in the account is positive; an overdraft is negative. " +
                        "Saving an empty box sets the opening back to zero.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/**
 * The web's confirm: it names the account, the amount and the voucher — the
 * clerk is about to remove a ledger entry, and this is the last place they
 * can check it is the right one.
 */
@Composable
private fun DeleteOpeningDialog(
    state: BankOpeningUiState,
    row: BankOpeningRow,
    viewModel: BankOpeningViewModel,
) {
    AlertDialog(
        onDismissRequest = viewModel::cancelDelete,
        title = { Text("Delete Opening Balance") },
        text = {
            Column {
                Text("Delete the opening balance of ${row.name.ifBlank { "this account" }}?")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Amount ${formatMoney(row.openingBalance)}  •  Voucher ${row.openingVrNo}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = AppFontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "The voucher goes to the trash, not away for good. " +
                        "The account is not deleted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
        dismissButton = {
            LinkButton(text = "Cancel", onClick = viewModel::cancelDelete, enabled = !state.isDeleting)
        },
    )
}

/** A field caption inside the edit dialog — dark on the dialog surface. */
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

/** Opening shows as "-" while still zero (unset), or the amount once set. */
private fun openingText(row: BankOpeningRow): String = AmountFormat.formatOrDash(row.openingBalance)

/** Grouped and cut to the branch's decimal_places, like every other amount. */
private fun formatMoney(value: Double): String = AmountFormat.format(value)
