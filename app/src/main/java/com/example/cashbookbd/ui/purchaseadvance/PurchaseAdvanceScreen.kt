package com.example.cashbookbd.ui.purchaseadvance

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.data.repository.PurchaseAdvanceLedgerRow
import com.example.cashbookbd.data.repository.PurchaseAdvanceStaff
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppDateField
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.KpiTile
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors

@Composable
fun PurchaseAdvanceScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PurchaseAdvanceViewModel = viewModel(
        factory = PurchaseAdvanceViewModel.provideFactory(LocalContext.current),
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
    LaunchedEffect(state.message) {
        val text = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.onMessageShown()
    }
    LaunchedEffect(state.error) {
        val text = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.onErrorShown()
    }

    AuthenticatedShell(
        title = "Advance for Purchase",
        currentRoute = Routes.TRANSACTIONS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val data = state.data
                if (data != null && !data.check.agrees) {
                    Text(
                        text = "The Employee Purchase Advance head stands at " +
                            "${moneyText(data.check.head)} in the ledger, but the employees' " +
                            "accounts add up to ${moneyText(data.check.rows)}. Something was " +
                            "posted to the head from another screen, with no employee named. " +
                            "Find it in the head's ledger and delete it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.danger,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.appColors.danger.copy(alpha = 0.10f),
                                RoundedCornerShape(6.dp),
                            )
                            .padding(10.dp),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpiTile(
                        "In employees' hands",
                        moneyText(data?.summary?.held ?: 0.0),
                        Modifier.weight(1f),
                    )
                    KpiTile(
                        "Owed to employees",
                        moneyText(data?.summary?.owed ?: 0.0),
                        Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpiTile(
                        "Entries are dated",
                        dayText(data?.summary?.date.orEmpty()),
                        Modifier.weight(1f),
                    )
                    if (state.isLoading) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text("Loading…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (state.canEnter) {
                    EntryCard(state, viewModel)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Show every employee",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = state.showAll, onCheckedChange = viewModel::onShowAll)
                    SecondaryButton(
                        text = "Refresh",
                        onClick = viewModel::load,
                        compact = true,
                        isLoading = state.isLoading,
                    )
                }

                if (state.listed.isEmpty()) {
                    Text(
                        text = if (state.isLoading) {
                            "Loading…"
                        } else {
                            "Nobody holds an advance, and nobody is owed for a bill."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textOnScreenMuted,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    state.listed.forEachIndexed { index, one ->
                        StaffRow(
                            index = index,
                            staff = one,
                            days = state.daysOpen(one),
                            onLedger = { viewModel.openLedger(one) },
                        )
                    }
                }

                Text(
                    text = "অগ্রিমের টাকায় কেনা পণ্য এখানে নয়, ক্রয়ের স্ক্রিনে এন্ট্রি হয় " +
                        "(Accounts Transaction, Payment-এ \"Advance\")। এটা বেতনের লোন নয়; " +
                        "বেতন থেকে কাটা হয় না।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textOnScreenMuted,
                )
                Spacer(Modifier.height(16.dp))
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    state.ledgerTarget?.let {
        LedgerDialog(state, viewModel)
    }
}

@Composable
private fun EntryCard(state: PurchaseAdvanceUiState, viewModel: PurchaseAdvanceViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.appColors.cardRow, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Segment(
                text = "Advance Payment",
                selected = !state.isSettle,
                onClick = { viewModel.onAction("give") },
                modifier = Modifier.weight(1f),
            )
            Segment(
                text = "Advance Settlement",
                selected = state.isSettle,
                onClick = { viewModel.onAction("settle") },
                modifier = Modifier.weight(1f),
            )
        }

        val employeeOptions = state.staff.map { one ->
            SelectorOption(
                id = one.id.toString(),
                label = if (one.balance != 0.0) {
                    "${one.name} — ${if (one.balance > 0) "holds" else "is owed"} " +
                        moneyText(kotlin.math.abs(one.balance))
                } else {
                    one.name
                },
            )
        }
        AppSelectDropdown(
            label = "Employee",
            options = employeeOptions,
            selected = state.employeeId?.let { id ->
                employeeOptions.firstOrNull { it.id == id.toString() }
            },
            onSelected = { viewModel.onEmployee(it.id.toLongOrNull()) },
            placeholder = "Select the employee",
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = if (state.isSettle) {
                state.settleSays
            } else {
                val held = state.chosen?.balance ?: 0.0
                if (held > 0) {
                    "${state.chosen?.name.orEmpty()} already holds ${moneyText(held)} that " +
                        "has not been accounted for."
                } else {
                    "Money handed over to buy goods with. It is not a loan and is never taken " +
                        "from salary."
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.appColors.textOnScreenMuted,
        )

        AppTextField(
            value = state.amount,
            onValueChange = viewModel::onAmount,
            label = "",
            caption = state.amountLabel,
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Segment(
                text = "Cash",
                selected = !state.bank,
                onClick = { viewModel.onBank(false) },
                modifier = Modifier.weight(1f),
            )
            Segment(
                text = "Bank",
                selected = state.bank,
                onClick = { viewModel.onBank(true) },
                modifier = Modifier.weight(1f),
            )
        }

        if (state.bank) {
            val bankOptions = state.data?.banks.orEmpty().map {
                SelectorOption(it.id.toString(), it.name)
            }
            AppSelectDropdown(
                label = "Bank Account",
                options = bankOptions,
                selected = state.bankAccount?.let { id ->
                    bankOptions.firstOrNull { it.id == id.toString() }
                },
                onSelected = { viewModel.onBankAccount(it.id.toLongOrNull()) },
                placeholder = "Select a bank account",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        AppTextField(
            value = state.note,
            onValueChange = viewModel::onNote,
            label = "Optional",
            caption = "Note",
            modifier = Modifier.fillMaxWidth(),
        )

        PrimaryButton(
            text = state.submitLabel,
            onClick = viewModel::submit,
            isLoading = state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Segment(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        PrimaryButton(text = text, onClick = onClick, compact = true, modifier = modifier)
    } else {
        SecondaryButton(text = text, onClick = onClick, compact = true, modifier = modifier)
    }
}

@Composable
private fun StaffRow(
    index: Int,
    staff: PurchaseAdvanceStaff,
    days: Long?,
    onLedger: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.appColors.cardRow, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${index + 1}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.appColors.textOnScreenMuted,
        )
        Spacer(Modifier.size(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = staff.name + if (staff.designation.isNotBlank()) " (${staff.designation})" else "",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = AppFontWeight.SemiBold,
            )
            Text(
                text = buildString {
                    append(
                        if (staff.balance == 0.0) "-"
                        else moneyText(kotlin.math.abs(staff.balance))
                    )
                    if (staff.balance < 0) append(" owed to him")
                    append(" · last advance ").append(dayText(staff.lastAdvance))
                    append(" · ").append(days?.toString() ?: "-").append(" days open")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (staff.balance < 0) MaterialTheme.appColors.danger
                else MaterialTheme.appColors.textOnScreenMuted,
            )
        }
        LinkButton(text = "Ledger", onClick = onLedger)
    }
}

@Composable
private fun LedgerDialog(state: PurchaseAdvanceUiState, viewModel: PurchaseAdvanceViewModel) {
    val one = state.ledgerTarget ?: return
    Dialog(
        onDismissRequest = viewModel::closeLedger,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = RoundedCornerShape(10.dp),
        ) {
            Column(Modifier.fillMaxSize().padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Ledger — ${one.name}" +
                            if (one.designation.isNotBlank()) " (${one.designation})" else "",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = AppFontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = viewModel::closeLedger) {
                        Icon(Icons.Filled.Close, contentDescription = "Close the ledger")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppDateField(
                        label = "From",
                        date = state.ledgerFrom,
                        onPicked = viewModel::onLedgerFrom,
                        modifier = Modifier.weight(1f),
                    )
                    AppDateField(
                        label = "To",
                        date = state.ledgerTo,
                        onPicked = viewModel::onLedgerTo,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        text = "Show",
                        onClick = viewModel::loadLedger,
                        compact = true,
                        isLoading = state.ledgerLoading,
                    )
                }

                state.ledgerError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.danger,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val ledger = state.ledger
                    if (ledger == null) {
                        Text(
                            text = if (state.ledgerLoading) "Loading…" else "Choose the dates and press Show.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appColors.textOnScreenMuted,
                        )
                    } else {
                        LedgerLine(
                            date = state.ledgerFrom?.toDisplay().orEmpty(),
                            vrNo = "",
                            particulars = "Opening Balance",
                            debit = "",
                            credit = "",
                            balance = balanceText(ledger.opening),
                            bold = true,
                        )
                        ledger.rows.forEach { row -> LedgerRowView(row, state, viewModel) }
                        Divider(Modifier.padding(vertical = 4.dp))
                        LedgerLine(
                            date = "",
                            vrNo = "",
                            particulars = "Total / Closing Balance",
                            debit = moneyText(ledger.debit),
                            credit = moneyText(ledger.credit),
                            balance = balanceText(ledger.balance),
                            bold = true,
                            danger = ledger.balance < 0,
                        )
                        Text(
                            text = "Debit: কর্মচারীর হাতে দেওয়া টাকা। Credit: তাঁর কেনা পণ্য বা " +
                                "ফেরত দেওয়া টাকা। Balance-এর পাশে Dr মানে তাঁর হাতে টাকা আছে, " +
                                "Cr মানে প্রতিষ্ঠান তাঁর কাছে দেনা।",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.appColors.textOnScreenMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerRowView(
    row: PurchaseAdvanceLedgerRow,
    state: PurchaseAdvanceUiState,
    viewModel: PurchaseAdvanceViewModel,
) {
    val expandable = row.items.isNotEmpty()
    val open = row.id in state.expandedBills
    Column {
        LedgerLine(
            date = dayText(row.vrDate),
            vrNo = row.vrNo,
            particulars = buildString {
                append(KIND_LABEL[row.kind] ?: row.kind)
                if (row.items.isNotEmpty()) {
                    append(" (").append(row.items.size)
                    append(if (row.items.size == 1) " item)" else " items)")
                }
                if (row.boughtFrom.isNotBlank()) append(" from ").append(row.boughtFrom)
                if (row.note.isNotBlank()) append(" — ").append(row.note)
            },
            debit = if (row.given != 0.0) moneyText(row.given) else "",
            credit = if (row.used != 0.0) moneyText(row.used) else "",
            balance = balanceText(row.balance),
            danger = row.balance < 0,
            expandable = expandable,
            open = open,
            onToggle = { viewModel.toggleBill(row.id) },
        )
        if (open) {
            row.items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 2.dp, bottom = 2.dp),
                ) {
                    Text(
                        text = item.name.ifBlank { "-" } +
                            if (item.kind == "asset") " (asset)" else "",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${item.quantity} ${item.unit}".trim(),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = moneyText(item.rate) + " · " + moneyText(item.amount),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun LedgerLine(
    date: String,
    vrNo: String,
    particulars: String,
    debit: String,
    credit: String,
    balance: String,
    bold: Boolean = false,
    danger: Boolean = false,
    expandable: Boolean = false,
    open: Boolean = false,
    onToggle: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (expandable) {
                IconButton(onClick = { onToggle?.invoke() }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        if (open) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                        contentDescription = if (open) "Collapse" else "Expand",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                text = listOf(date, vrNo).filter { it.isNotBlank() }.joinToString(" · ")
                    .ifBlank { " " },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.appColors.textOnScreenMuted,
            )
        }
        Text(
            text = particulars,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) AppFontWeight.SemiBold else AppFontWeight.Normal,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (debit.isNotBlank()) AmountPair("Dr", debit)
            if (credit.isNotBlank()) AmountPair("Cr", credit)
            Spacer(Modifier.weight(1f))
            Text(
                text = balance,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (bold) AppFontWeight.SemiBold else AppFontWeight.Normal,
                color = if (danger) MaterialTheme.appColors.danger
                else MaterialTheme.appColors.textOnScreen,
            )
        }
        Divider(Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun AmountPair(label: String, value: String) {
    Row {
        Text(
            text = "$label ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.appColors.textOnScreenMuted,
        )
        Text(text = value, style = MaterialTheme.typography.labelSmall)
    }
}
