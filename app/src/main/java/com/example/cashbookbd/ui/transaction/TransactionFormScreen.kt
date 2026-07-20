package com.example.cashbookbd.ui.transaction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
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
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.DropdownAnchorField
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.TxnSelection
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.transaction.TxnField
import com.example.cashbookbd.transaction.TxnPicker
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.example.cashbookbd.ui.components.SearchableLedgerDropdown
import com.example.cashbookbd.ui.components.SearchableSelectDropdown
import com.example.cashbookbd.ui.reports.model.SelectorOption

/**
 * A single transaction entry form (Cash/Bank Received & Payment, Journal, Employee
 * Loan). Renders the form's account pickers, a remarks and an amount field, and
 * submits to the server (which derives the date, cash/bank contra leg and voucher
 * number). Shows the returned voucher message on success.
 */
@Composable
fun TransactionFormScreen(
    txnKey: String,
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransactionFormViewModel = viewModel(
        factory = TransactionFormViewModel.provideFactory(LocalContext.current, txnKey)
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    AuthenticatedShell(
        title = state.title,
        currentRoute = Routes.TRANSACTIONS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        if (!state.isSupported) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "This form isn't available in the mobile app yet.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                )
            }
            return@AuthenticatedShell
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            state.fields.forEach { field ->
                AccountField(
                    field = field,
                    state = state,
                    onSelected = viewModel::onFieldSelected,
                    searchLedgers = viewModel::searchLedgers,
                    searchEmployees = viewModel::searchEmployees,
                )
            }

            AppTextField(
                value = state.remarks,
                onValueChange = viewModel::onRemarksChange,
                label = state.remarksLabel,
                modifier = Modifier.fillMaxWidth(),
            )

            AppTextField(
                value = state.amount,
                onValueChange = viewModel::onAmountChange,
                label = state.amountLabel,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(2.dp))

            PrimaryButton(
                text = "Save",
                onClick = viewModel::submit,
                enabled = state.canSubmit,
                isLoading = state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )

            state.message?.let { message ->
                Text(
                    text = message,
                    color = if (state.isError) {
                        MaterialTheme.colorScheme.onBackground
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AccountField(
    field: TxnField,
    state: TransactionFormUiState,
    onSelected: (String, TxnSelection) -> Unit,
    searchLedgers: suspend (String) -> Resource<List<LedgerDropdownItem>>,
    searchEmployees: suspend (String) -> Resource<List<SelectorOption>>,
) {
    val selection = state.selections[field.key]
    when (field.picker) {
        TxnPicker.LEDGER -> SearchableLedgerDropdown(
            selectedLedger = selection?.let { LedgerDropdownItem(it.id.toIntOrNull() ?: 0, it.name, null) },
            onLedgerSelected = { onSelected(field.key, TxnSelection(it.id.toString(), it.name)) },
            searchLedgers = searchLedgers,
            label = field.label,
        )

        TxnPicker.EMPLOYEE -> SearchableSelectDropdown(
            selected = selection?.let { SelectorOption(it.id, it.name) },
            onSelected = { onSelected(field.key, TxnSelection(it.id, it.label)) },
            search = searchEmployees,
            label = field.label,
            emptyText = "No employee found",
        )

        TxnPicker.BANK -> BankDropdown(
            label = field.label,
            options = state.bankAccounts,
            selected = selection,
            isLoading = state.isBankLoading,
            error = state.bankError,
            onSelected = { onSelected(field.key, TxnSelection(it.id, it.label)) },
        )
    }
}

/** Load-once bank-account dropdown. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BankDropdown(
    label: String,
    options: List<SelectorOption>,
    selected: TxnSelection?,
    isLoading: Boolean,
    error: String?,
    onSelected: (SelectorOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            DropdownAnchorField(
                label = label,
                valueText = selected?.name,
                placeholder = if (isLoading) "Loading…" else "",
                onClick = { if (options.isNotEmpty()) expanded = true },
                trailingIcon = if (isLoading) {
                    { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
                } else {
                    null
                },
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            onSelected(option)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp, top = 3.dp),
            )
        }
    }
}
