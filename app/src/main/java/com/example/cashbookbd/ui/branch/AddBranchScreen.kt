package com.example.cashbookbd.ui.branch

import com.example.cashbookbd.ui.components.AppStepBar
import com.example.cashbookbd.ui.theme.muted
import com.example.cashbookbd.ui.theme.accents
import com.example.cashbookbd.ui.theme.appColors
import com.example.cashbookbd.ui.theme.AppFontWeight
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.DropdownAnchorField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.model.SelectorOption

/**
 * Creates or edits a branch, depending on whether [branchId] is given.
 *
 * Laid out as the web's four-step wizard — Basic Info, Print Setup, Invoice
 * Setup, Feature Controls — with the fields for each step taken from
 * [BranchForm]. Required fields are spread across steps, so Save stays enabled
 * only once all of them are filled, wherever they live.
 *
 * Editing loads the branch's other settings and posts them back untouched (see
 * [com.example.cashbookbd.data.repository.BranchRepository.update]).
 */
@Composable
fun AddBranchScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    branchId: String? = null,
    viewModel: AddBranchViewModel = viewModel(
        key = branchId ?: "add",
        factory = AddBranchViewModel.provideFactory(LocalContext.current, branchId),
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

    LaunchedEffect(state.error) {
        val message = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onErrorShown()
    }

    // Saved: hand the confirmation to the list, which reloads and shows it.
    LaunchedEffect(state.savedMessage) {
        val message = state.savedMessage ?: return@LaunchedEffect
        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.set(Routes.CREATED_MESSAGE, message)
        navController.popBackStack()
    }

    AuthenticatedShell(
        title = state.screenTitle,
        currentRoute = Routes.ADMIN,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                AppStepBar(
                    steps = state.steps.map { it.title },
                    currentStep = state.currentStep,
                    onStepClick = viewModel::goToStep,
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = state.step.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textOnScreenMuted,
                    )

                    if (state.optionsError != null) {
                        Text(
                            text = state.optionsError!!,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        LinkButton(
                            text = "Retry",
                            onClick = viewModel::load,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }

                    state.step.fields.forEach { field ->
                        when (field) {
                            is BranchField.Text -> Field(
                                label = field.label,
                                value = state.text(field.key),
                                onChange = { viewModel.onValue(field.key, it) },
                                keyboard = field.keyboard,
                                multiline = field.multiline,
                                description = field.description,
                            )

                            is BranchField.Choice -> {
                                val options = state.options(field.source)
                                ChoiceField(
                                    label = field.label,
                                    options = options,
                                    selected = state.option(field.key, options),
                                    isLoading = state.isLoadingOptions,
                                    onSelected = { viewModel.onValue(field.key, it.id) },
                                    description = field.description,
                                )
                            }

                            is BranchField.Toggle -> ToggleField(
                                label = field.label,
                                checked = state.flag(field.key),
                                onChange = { viewModel.onToggle(field.key, it) },
                                description = field.description,
                            )
                        }
                    }

                    // The clears belong beside "Opening ongoing", and this is
                    // the step that carries it. Each shows only to those
                    // holding its permission, only when editing, and only while
                    // the branch is still marked "Opening ongoing".
                    if (state.step.title == FEATURE_STEP_TITLE &&
                        (state.showClearOpening || state.showClearTransactions)
                    ) {
                        if (state.clearInFlight) {
                            Text(
                                text = if (state.isClearingOpening) {
                                    "Clearing opening balances..."
                                } else {
                                    "Clearing transactions..."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            // Filled red, not links: they wipe or withdraw
                            // figures kept nowhere else, and read as ordinary
                            // text beside Back and Update they were the
                            // quietest things on the step.
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (state.showClearOpening) {
                                    PrimaryButton(
                                        text = "Clear Opening",
                                        onClick = viewModel::onClearOpeningRequested,
                                        compact = true,
                                        containerColor = MaterialTheme.accents.red,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (state.showClearTransactions) {
                                    PrimaryButton(
                                        text = "Clear Transactions",
                                        onClick = viewModel::onClearTransactionsRequested,
                                        compact = true,
                                        containerColor = MaterialTheme.accents.red,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                        state.clearedOpeningMessage?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.appColors.textOnScreenMuted,
                            )
                        }
                        state.clearedTransactionsMessage?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.appColors.textOnScreenMuted,
                            )
                        }
                        // A refusal the server voiced as a notice — "call the
                        // administrator", not a fault — stays inline in the
                        // info tone rather than passing by red in the snackbar.
                        state.clearNotice?.let { message ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.appColors.info,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.appColors.info,
                                )
                            }
                        }
                    }

                    // The letterhead image is the web's one remaining field here;
                    // it needs a picker and a multipart upload, so for now the
                    // form says so rather than dropping the choice silently.
                    if (state.step.title == PRINT_STEP_TITLE && state.text("pad_heading_print") == CUSTOM_PAD) {
                        Text(
                            text = "The letterhead image can only be uploaded from the web for now. " +
                                "Everything else on this page saves normally.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appColors.textOnScreenMuted,
                        )
                    }
                }

                StepActions(
                    state = state,
                    onPrevious = viewModel::previousStep,
                    onNext = viewModel::nextStep,
                    onSave = viewModel::save,
                )
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    // Named plainly, because the figures cannot be recovered here -- they have
    // to be keyed in again. What is left alone is said too, so nobody expects
    // the accounts to move.
    if (state.confirmClearOpening) {
        AlertDialog(
            onDismissRequest = viewModel::onClearOpeningDismissed,
            title = { Text("Clear Opening Balances") },
            text = {
                Text(
                    "Set every opening balance in this branch back to zero? " +
                        "Products and customers/suppliers both. The figures are not " +
                        "kept anywhere else, so they will have to be entered again.\n\n" +
                        "The journal and stock entries already posted are left as they are."
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::clearOpening) {
                    Text("Clear Opening", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onClearOpeningDismissed) { Text("Cancel") }
            },
        )
    }

    // Says plainly what survives: the vouchers are not erased, they stop
    // counting. That is the difference between this and a deletion, and it is
    // the thing somebody approving it needs to know.
    if (state.confirmClearTransactions) {
        AlertDialog(
            onDismissRequest = viewModel::onClearTransactionsDismissed,
            title = { Text("Clear Transactions") },
            text = {
                Text(
                    "Withdraw every voucher in this branch from the books?\n\n" +
                        "Every voucher this branch holds is marked inactive at once, " +
                        "so it stops showing in reports, ledgers and balances.\n\n" +
                        "Nothing is deleted — the entries stay on record, but there " +
                        "is no button that puts them all back."
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::clearTransactions) {
                    Text("Clear Transactions", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onClearTransactionsDismissed) { Text("Cancel") }
            },
        )
    }
}

private const val CUSTOM_PAD = "3"

// Steps are asked for by name, not by the number they happen to sit at — the
// list varies per user (the SaaS step), and a numbered check silently renames
// every panel after an inserted step.
private const val PRINT_STEP_TITLE = "Print Setup"
private const val FEATURE_STEP_TITLE = "Feature Controls"

/**
 * Back / Next, with Save replacing Next on the last step.
 *
 * Save is disabled until every required field is filled, and names the first
 * one missing — they are spread across steps, so "why is Save greyed out?"
 * would otherwise mean hunting through four pages.
 */
@Composable
private fun StepActions(
    state: AddBranchUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (state.isLastStep && !state.canSave && !state.isSaving) {
            state.missingLabel?.let { missing ->
                Text(
                    text = "$missing is required.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(
                text = "Back",
                onClick = onPrevious,
                enabled = !state.isFirstStep && !state.isSaving,
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                modifier = Modifier.weight(1f),
            )
            if (state.isLastStep) {
                PrimaryButton(
                    text = state.saveLabel,
                    onClick = onSave,
                    enabled = state.canSave,
                    isLoading = state.isSaving,
                    modifier = Modifier.weight(1f),
                )
            } else {
                PrimaryButton(
                    text = "Next",
                    onClick = onNext,
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** What a setting does, said under the field itself. */
@Composable
private fun FieldDescription(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.appColors.textOnScreenMuted,
    )
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text,
    multiline: Boolean = false,
    description: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AppTextField(
            value = value,
            onValueChange = onChange,
            // The name goes on the border, as the dropdowns on this form already do
            // — as an in-box hint it vanished the moment the field was filled, and
            // a form of half-labelled, half-anonymous boxes is what read as a mess.
            caption = label,
            label = "",
            keyboardType = keyboard,
            multiline = multiline,
            modifier = Modifier.fillMaxWidth(),
        )
        description?.let { FieldDescription(it) }
    }
}

/** A label with a switch, for the form's many on/off settings. */
@Composable
private fun ToggleField(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    description: String? = null,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onChange(!checked) }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = checked, onCheckedChange = onChange)
        }
        description?.let { FieldDescription(it) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceField(
    label: String,
    options: List<SelectorOption>,
    selected: SelectorOption?,
    isLoading: Boolean,
    onSelected: (SelectorOption) -> Unit,
    description: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            DropdownAnchorField(
                label = label,
                valueText = selected?.label,
                placeholder = if (isLoading) "Loading…" else "",
                onClick = { if (options.isNotEmpty()) expanded = true },
                trailingIcon = if (isLoading && options.isEmpty()) {
                    { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) }
                } else {
                    null
                },
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = { onSelected(option); expanded = false },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
        description?.let { FieldDescription(it) }
    }
}

private val StepMarkerSize = 24.dp
