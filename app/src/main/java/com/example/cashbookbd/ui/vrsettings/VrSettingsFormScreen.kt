package com.example.cashbookbd.ui.vrsettings

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
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.DropdownAnchorField
import com.example.cashbookbd.ui.components.FieldButton
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SimpleDate

private val VOUCHER_TYPES = listOf("1" to "Received", "2" to "Payment", "3" to "Journal")

/**
 * A VR-settings action form: delete a voucher (with a force-confirm step), delete
 * a voucher's installments, or shift a range of vouchers to a new date.
 */
@Composable
fun VrSettingsFormScreen(
    settingKey: String,
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VrSettingsFormViewModel = viewModel(
        factory = VrSettingsFormViewModel.provideFactory(LocalContext.current, settingKey)
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
        currentRoute = Routes.VR_SETTINGS,
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
            if (state.isDateChange) {
                DateChangeFields(state = state, viewModel = viewModel)
            } else {
                AppTextField(
                    value = state.voucherNo,
                    onValueChange = viewModel::onVoucherNoChange,
                    label = "Enter Voucher Number",
                    modifier = Modifier.fillMaxWidth(),
                )
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

            // Already-deleted → offer a force delete.
            if (state.requiresConfirmation) {
                SecondaryButton(
                    text = "Force Delete",
                    onClick = viewModel::forceDelete,
                    enabled = !state.isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DateChangeFields(state: VrSettingsFormUiState, viewModel: VrSettingsFormViewModel) {
    val context = LocalContext.current

    BranchDropdown(
        branches = state.branches,
        selected = state.selectedBranch,
        isLoading = state.isBranchesLoading,
        onSelected = viewModel::onBranchSelected,
    )
    state.branchesError?.let {
        Text(it, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodySmall)
    }

    VoucherTypeDropdown(selected = state.voucherType, onSelected = viewModel::onVoucherTypeChange)

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DateField(
            label = "Present Date",
            value = state.presentDate,
            modifier = Modifier.weight(1f),
            onPicked = viewModel::onPresentDate,
            context = context,
        )
        DateField(
            label = "Change Date",
            value = state.changeDate,
            modifier = Modifier.weight(1f),
            onPicked = viewModel::onChangeDate,
            context = context,
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppTextField(
            value = state.startVoucher,
            onValueChange = viewModel::onStartVoucher,
            label = "Start Voucher",
            modifier = Modifier.weight(1f),
        )
        AppTextField(
            value = state.endVoucher,
            onValueChange = viewModel::onEndVoucher,
            label = "End Voucher",
            modifier = Modifier.weight(1f),
        )
    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoucherTypeDropdown(selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = VOUCHER_TYPES.firstOrNull { it.first == selected }?.second ?: "Received"
    Box(modifier = Modifier.fillMaxWidth()) {
        DropdownAnchorField(
            label = "Voucher Type",
            valueText = label,
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            VOUCHER_TYPES.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { onSelected(value); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
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
