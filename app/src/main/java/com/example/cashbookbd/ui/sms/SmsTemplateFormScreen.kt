package com.example.cashbookbd.ui.sms

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.SmsRepository
import com.example.cashbookbd.data.repository.SmsTemplateDraft
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.session.SessionManager
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.model.SelectorOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The web's template name limit; input is capped rather than rejected. */
private const val NAME_MAX_LENGTH = 120

/** `{{ placeholder }}` tokens in the message body, whitespace-tolerant. */
private val PLACEHOLDER_REGEX = Regex("""\{\{\s*(\w+)\s*\}\}""")

/** Active/Inactive, matching the web's status select (default Active). */
private val STATUS_OPTIONS = listOf(
    SelectorOption("1", "Active"),
    SelectorOption("0", "Inactive"),
)

data class SmsTemplateFormUiState(
    /** The id being edited; null = create. */
    val templateId: String? = null,
    /** True while the edit prefill is being fetched. */
    val isLoading: Boolean = false,
    val loadError: String? = null,

    val name: String = "",
    val code: String = "",
    val body: String = "",
    val active: Boolean = true,
    /** The loaded record's branch (edit), round-tripped on update. */
    val branchId: Long? = null,

    val isSaving: Boolean = false,
    /** One-shot snackbar text — a save rejection (e.g. a duplicate key 422). */
    val error: String? = null,
    val savedMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    val isEdit: Boolean get() = templateId != null

    val canSave: Boolean
        get() = !isSaving && name.isNotBlank() && code.isNotBlank() && body.isNotBlank()

    /** The `{{placeholders}}` the body currently uses, in order, deduplicated. */
    val placeholders: List<String>
        get() = PLACEHOLDER_REGEX.findAll(body).map { it.groupValues[1] }.distinct().toList()
}

/** Backs the template form: the edit prefill and the store/update submit. */
class SmsTemplateFormViewModel(
    private val templateId: String?,
    private val repository: SmsRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SmsTemplateFormUiState(templateId = templateId))
    val uiState: StateFlow<SmsTemplateFormUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** Fetches the edit prefill; a create form has nothing to load. */
    fun load() {
        val id = templateId ?: return
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            when (val result = repository.getTemplate(id)) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        name = result.data.name,
                        code = result.data.code,
                        body = result.data.body,
                        active = result.data.active,
                        branchId = result.data.branchId,
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onName(value: String) = _uiState.update { it.copy(name = value.take(NAME_MAX_LENGTH)) }

    fun onCode(value: String) = _uiState.update { it.copy(code = value) }

    fun onBody(value: String) = _uiState.update { it.copy(body = value) }

    fun onStatus(active: Boolean) = _uiState.update { it.copy(active = active) }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val draft = SmsTemplateDraft(
                templateId = templateId,
                // Edit keeps the record's own branch; create defaults to the
                // user's working branch (null lets the server fall back too).
                branchId = state.branchId ?: sessionManager.state.value.settings?.branchId,
                code = state.code,
                name = state.name,
                body = state.body,
                active = state.active,
            )
            when (val result = repository.saveTemplate(draft)) {
                is Resource.Success -> _uiState.update {
                    it.copy(isSaving = false, savedMessage = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onErrorShown() = _uiState.update { it.copy(error = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context, templateId: String?) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                SmsTemplateFormViewModel(
                    templateId = templateId,
                    repository = ServiceLocator.provideSmsRepository(appContext),
                    sessionManager = ServiceLocator.provideSessionManager(appContext),
                )
            }
        }
    }
}

/**
 * The SMS template create/edit form — a port of the web's /sms/templates
 * create and edit pages: name, key, message body (with a live line of the
 * `{{placeholders}}` it uses) and an Active/Inactive status.
 */
@Composable
fun SmsTemplateFormScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    templateId: String? = null,
    modifier: Modifier = Modifier,
    viewModel: SmsTemplateFormViewModel = viewModel(
        factory = SmsTemplateFormViewModel.provideFactory(LocalContext.current, templateId)
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
        navController.previousBackStackEntry?.savedStateHandle?.set(Routes.CREATED_MESSAGE, message)
        navController.popBackStack()
    }

    AuthenticatedShell(
        title = if (state.isEdit) "Edit SMS Template" else "Add SMS Template",
        currentRoute = Routes.ADMIN,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.loadError != null -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = state.loadError.orEmpty(),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(text = "Retry", onClick = viewModel::load)
                }

                else -> TemplateForm(state = state, viewModel = viewModel)
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun TemplateForm(state: SmsTemplateFormUiState, viewModel: SmsTemplateFormViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppTextField(
            value = state.name,
            onValueChange = viewModel::onName,
            label = "Enter template name",
            caption = "Template Name *",
            modifier = Modifier.fillMaxWidth(),
        )
        Column {
            AppTextField(
                value = state.code,
                onValueChange = viewModel::onCode,
                label = "invoice_due_reminder",
                caption = "Template Key *",
                modifier = Modifier.fillMaxWidth(),
            )
            HelperText("Lowercase letters, digits and underscore only.")
        }
        Column {
            AppTextField(
                value = state.body,
                onValueChange = viewModel::onBody,
                label = "Write the SMS message…",
                caption = "Message Body *",
                multiline = true,
                modifier = Modifier.fillMaxWidth(),
            )
            HelperText(
                "Use {{party_name}}, {{amount}}, {{date}}, {{voucher_no}}, " +
                    "{{invoice_no}} placeholders."
            )
        }
        if (state.placeholders.isNotEmpty()) {
            DetectedPlaceholders(placeholders = state.placeholders)
        }
        AppSelectDropdown(
            label = "Status",
            options = STATUS_OPTIONS,
            selected = STATUS_OPTIONS.first { it.id == if (state.active) "1" else "0" },
            onSelected = { viewModel.onStatus(it.id == "1") },
        )
        if (!state.canSave && !state.isSaving) {
            Text(
                text = "Template Name, Template Key and Message Body are required.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            )
        }
        PrimaryButton(
            text = if (state.isEdit) "Update Template" else "Save Template",
            onClick = viewModel::save,
            enabled = state.canSave,
            isLoading = state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A muted hint line under a field, on the screen background. */
@Composable
private fun HelperText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

/** The live chips line of the `{{placeholders}}` the body currently uses. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetectedPlaceholders(placeholders: List<String>) {
    Column {
        Text(
            text = "Detected placeholders",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            placeholders.forEach { name ->
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "{{$name}}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}
