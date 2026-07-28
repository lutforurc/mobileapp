package com.example.cashbookbd.ui.realestate

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.realestate.ReCrudField
import com.example.cashbookbd.realestate.ReCrudFieldKind
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.FieldFrame
import com.example.cashbookbd.ui.components.FieldTextInput
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SearchableSelectDropdown
import com.example.cashbookbd.ui.reports.PickerField
import com.example.cashbookbd.ui.reports.model.SelectorOption
import java.util.Calendar
import java.util.Locale

/**
 * How many characters the real-estate DDL typeaheads need before searching —
 * the backend answers from 2, below the app-wide default of 3.
 */
private const val RE_MIN_SEARCH_CHARS = 2

/**
 * The config-driven add/edit form behind every Real Estate master-data list's
 * +Add and pencil — one screen renders whichever
 * [com.example.cashbookbd.realestate.ReCrudSpec] the route names, exactly as
 * the web's corresponding form does.
 *
 * On success the confirmation is handed to the list via
 * [Routes.CREATED_MESSAGE] and the form pops back — except a unit create,
 * which stays put with an in-place snackbar so several units of the same floor
 * can be entered back to back.
 */
@Composable
fun RealEstateCrudFormScreen(
    crudKey: String,
    crudId: String?,
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: RealEstateCrudFormViewModel = viewModel(
        factory = RealEstateCrudFormViewModel.provideFactory(context, crudKey, crudId),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    if (state.sessionExpired) {
        LaunchedEffect(Unit) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    // Saved: hand the confirmation to the list, which reloads and shows it.
    LaunchedEffect(state.savedMessage) {
        val message = state.savedMessage ?: return@LaunchedEffect
        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.set(Routes.CREATED_MESSAGE, message)
        navController.popBackStack()
    }

    // Stay-in-place create (units): the ViewModel already reset the form.
    LaunchedEffect(state.stayMessage) {
        val message = state.stayMessage ?: return@LaunchedEffect
        viewModel.onStayMessageShown()
        snackbarHostState.showSnackbar(message)
    }

    val title = state.spec?.title ?: "Form"
    AuthenticatedShell(
        title = if (state.isEdit) "Edit $title" else "Add $title",
        currentRoute = Routes.REAL_ESTATE,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
                }
                state.loadError != null -> Box(
                    Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(state.loadError.orEmpty(), color = MaterialTheme.colorScheme.onBackground)
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp,
                    ),
                ) {
                    item {
                        state.fields.forEach { fieldState ->
                            ReCrudFieldRow(
                                fieldState = fieldState,
                                state = state,
                                context = context,
                                onValueChanged = viewModel::onValueChanged,
                                searchOptions = viewModel::searchOptions,
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        PrimaryButton(
                            text = if (state.isEdit) "Update $title" else "Save $title",
                            onClick = viewModel::save,
                            enabled = state.canSave,
                            isLoading = state.isSaving,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        state.saveError?.let {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** Renders one field by kind, all through the shared components. */
@Composable
private fun ReCrudFieldRow(
    fieldState: ReCrudFieldState,
    state: RealEstateCrudFormUiState,
    context: Context,
    onValueChanged: (String, String, String) -> Unit,
    searchOptions: suspend (ReCrudField, String) -> Resource<List<SelectorOption>>,
) {
    val field = fieldState.field
    val label = if (field.required) "${field.label} *" else field.label

    when (field.kind) {
        ReCrudFieldKind.TEXT -> FieldFrame(label = label) {
            FieldTextInput(
                value = fieldState.value,
                onValueChange = { onValueChanged(field.key, it, "") },
                placeholder = field.placeholder,
            )
        }

        ReCrudFieldKind.NUMBER -> FieldFrame(label = label) {
            FieldTextInput(
                value = fieldState.value,
                onValueChange = { onValueChanged(field.key, it, "") },
                placeholder = field.placeholder.ifBlank { "0" },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }

        ReCrudFieldKind.DATE -> PickerField(
            label = label,
            value = fieldState.value.toDisplayDate(),
            placeholder = "dd/mm/yyyy",
            trailingIcon = Icons.Filled.DateRange,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val calendar = Calendar.getInstance()
                Regex("""^(\d{4})-(\d{2})-(\d{2})$""").find(fieldState.value)?.let { match ->
                    val (year, month, day) = match.destructured
                    calendar.set(year.toInt(), month.toInt() - 1, day.toInt())
                }
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        onValueChanged(
                            field.key,
                            String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth),
                            "",
                        )
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH),
                ).show()
            },
        )

        ReCrudFieldKind.STATIC_SELECT -> AppSelectDropdown(
            label = label,
            options = field.choices,
            selected = field.choices.firstOrNull { it.id == fieldState.value },
            onSelected = { onValueChanged(field.key, it.id, "") },
        )

        ReCrudFieldKind.BRANCH -> AppSelectDropdown(
            label = label,
            options = state.branches,
            selected = state.branches.firstOrNull { it.id == fieldState.value },
            onSelected = { onValueChanged(field.key, it.id, "") },
        )

        ReCrudFieldKind.ASYNC_PICKER -> SearchableSelectDropdown(
            selected = fieldState.value.takeIf { it.isNotBlank() }?.let {
                SelectorOption(it, fieldState.display.ifBlank { "#$it" })
            },
            onSelected = { onValueChanged(field.key, it.id, it.label) },
            search = { query -> searchOptions(field, query) },
            label = label,
            minSearchChars = RE_MIN_SEARCH_CHARS,
        )
    }
}

/** "2026-01-05" → "05/01/2026" for display; blank stays blank. */
private fun String.toDisplayDate(): String {
    val match = Regex("""^(\d{4})-(\d{2})-(\d{2})$""").find(this) ?: return this
    val (year, month, day) = match.destructured
    return "$day/$month/$year"
}
