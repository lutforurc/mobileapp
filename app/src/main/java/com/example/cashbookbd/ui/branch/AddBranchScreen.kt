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
                            )

                            is BranchField.Choice -> {
                                val options = state.options(field.source)
                                ChoiceField(
                                    label = field.label,
                                    options = options,
                                    selected = state.option(field.key, options),
                                    isLoading = state.isLoadingOptions,
                                    onSelected = { viewModel.onValue(field.key, it.id) },
                                )
                            }

                            is BranchField.Toggle -> ToggleField(
                                label = field.label,
                                checked = state.flag(field.key),
                                onChange = { viewModel.onToggle(field.key, it) },
                            )
                        }
                    }

                    // Clearing an opening belongs beside "Opening ongoing", and
                    // this is the step that carries it. Shown only to those
                    // holding branch.opening.clear, and only when editing.
                    if (state.currentStep == FEATURE_STEP && state.showClearOpening) {
                        if (state.isClearingOpening) {
                            Text(
                                text = "Clearing opening balances...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            // Filled red, not a link: it wipes figures kept
                            // nowhere else, and read as ordinary text beside
                            // Back and Update it was the quietest thing on a
                            // step where it is the only one that destroys.
                            PrimaryButton(
                                text = "Clear Opening",
                                onClick = viewModel::onClearOpeningRequested,
                                compact = true,
                                containerColor = MaterialTheme.accents.red,
                            )
                        }
                        state.clearedOpeningMessage?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.appColors.textOnScreenMuted,
                            )
                        }
                    }

                    // The letterhead image is the web's one remaining field here;
                    // it needs a picker and a multipart upload, so for now the
                    // form says so rather than dropping the choice silently.
                    if (state.currentStep == PRINT_STEP && state.text("pad_heading_print") == CUSTOM_PAD) {
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
}

private const val PRINT_STEP = 1
private const val CUSTOM_PAD = "3"

/** Feature Controls -- the last step, and where "Opening ongoing" sits. */
private val FEATURE_STEP = BranchForm.steps.lastIndex

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

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text,
    multiline: Boolean = false,
) {
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
}

/** A label with a switch, for the form's many on/off settings. */
@Composable
private fun ToggleField(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceField(
    label: String,
    options: List<SelectorOption>,
    selected: SelectorOption?,
    isLoading: Boolean,
    onSelected: (SelectorOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
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
}

private val StepMarkerSize = 24.dp
