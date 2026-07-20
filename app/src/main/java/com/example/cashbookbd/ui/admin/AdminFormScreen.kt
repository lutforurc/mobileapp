package com.example.cashbookbd.ui.admin

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.DropdownAnchorField
import com.example.cashbookbd.ui.components.FieldButton
import com.example.cashbookbd.admin.AdminKind
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate

/**
 * An admin action form: close the day, approve a voucher date range, remove a
 * voucher's approval, or change a voucher's type. The UI adapts to the action.
 */
@Composable
fun AdminFormScreen(
    adminKey: String,
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdminFormViewModel = viewModel(
        factory = AdminFormViewModel.provideFactory(LocalContext.current, adminKey)
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    AuthenticatedShell(
        title = state.title,
        currentRoute = Routes.ADMIN,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        if (!state.isSupported) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "This screen isn't available in the mobile app yet.",
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
            when (state.kind) {
                AdminKind.DAY_CLOSE -> {
                    ReadOnlyField("Current Transaction Date", state.currentDate.toDisplay())
                    ReadOnlyField("Next Transaction Date", state.nextDate.toDisplay())
                }

                AdminKind.VOUCHER_APPROVAL -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DateField("Start Date", state.startDate, Modifier.weight(1f), viewModel::onStartDate, context)
                    DateField("End Date", state.endDate, Modifier.weight(1f), viewModel::onEndDate, context)
                }

                AdminKind.APPROVAL_REMOVE -> VoucherNoField(state.voucherNo, viewModel::onVoucherNoChange)

                AdminKind.CHANGE_VOUCHER_TYPE -> {
                    BranchDropdown(
                        branches = state.branches,
                        selected = state.selectedBranch,
                        isLoading = state.isBranchesLoading,
                        onSelected = viewModel::onBranchSelected,
                    )
                    state.branchesError?.let {
                        Text(it, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodySmall)
                    }
                    VoucherNoField(state.voucherNo, viewModel::onVoucherNoChange)
                    TypeDropdown(
                        options = state.voucherTypes,
                        selected = state.selectedType,
                        isLoading = state.isTypesLoading,
                        onSelected = viewModel::onTypeSelected,
                    )
                }

                null -> Unit
            }

            PrimaryButton(
                text = state.actionLabel,
                onClick = viewModel::submit,
                enabled = state.canSubmit,
                isLoading = state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )

            state.message?.let { message ->
                Text(
                    text = message,
                    color = if (state.isError) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ReadOnlyField(label: String, value: String) {
    AppTextField(
        value = value,
        onValueChange = {},
        label = "",
        caption = label,
        enabled = false,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun VoucherNoField(value: String, onChange: (String) -> Unit) {
    AppTextField(
        value = value,
        onValueChange = onChange,
        label = "Enter Voucher Number",
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BranchDropdown(
    branches: List<BranchOption>,
    selected: BranchOption?,
    isLoading: Boolean,
    onSelected: (BranchOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        DropdownAnchorField(
            label = "Select Branch",
            valueText = selected?.name,
            placeholder = if (isLoading) "Loading…" else "",
            onClick = { if (branches.isNotEmpty()) expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            branches.forEach { branch ->
                DropdownMenuItem(
                    text = { Text(branch.name) },
                    onClick = { onSelected(branch); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun TypeDropdown(
    options: List<SelectorOption>,
    selected: SelectorOption?,
    isLoading: Boolean,
    onSelected: (SelectorOption) -> Unit,
) {
    AppSelectDropdown(
        label = "Select Voucher Type",
        options = options,
        selected = selected,
        onSelected = onSelected,
        placeholder = if (isLoading) "Loading…" else "",
    )
}

@Composable
private fun DateField(
    label: String,
    value: SimpleDate,
    modifier: Modifier,
    onPicked: (SimpleDate) -> Unit,
    context: Context,
) {
    FieldButton(
        text = value.toDisplay(),
        onClick = { showDatePicker(context, value, onPicked) },
        icon = Icons.Filled.DateRange,
        modifier = modifier,
    )
}

private fun showDatePicker(context: Context, current: SimpleDate, onPicked: (SimpleDate) -> Unit) {
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth -> onPicked(SimpleDate(year = year, month = month + 1, day = dayOfMonth)) },
        current.year,
        current.month - 1,
        current.day,
    ).show()
}
