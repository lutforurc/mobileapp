package com.example.cashbookbd.ui.admin

import com.example.cashbookbd.ui.theme.muted
import com.example.cashbookbd.ui.theme.appColors
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.CompanyRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.PrimaryButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The web form caps the name at 255 characters (`max:255`). */
private const val NAME_MAX_LENGTH = 255

data class EditCompanyUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    /** The hashed `company_id` the update endpoint resolves on. */
    val hashedId: String = "",
    val name: String = "",
    val contactPerson: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val notes: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    val canSave: Boolean get() = !isLoading && !isSaving && loadError == null && name.isNotBlank()
}

class EditCompanyViewModel(
    private val companyId: String,
    private val repository: CompanyRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditCompanyUiState())
    val uiState: StateFlow<EditCompanyUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            when (val result = repository.loadForEdit(companyId)) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        hashedId = result.data.hashedId,
                        name = result.data.name,
                        contactPerson = result.data.contactPerson,
                        phone = result.data.phone,
                        email = result.data.email,
                        address = result.data.address,
                        notes = result.data.notes,
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
    fun onContactPerson(value: String) = _uiState.update { it.copy(contactPerson = value) }
    fun onPhone(value: String) = _uiState.update { it.copy(phone = value) }
    fun onEmail(value: String) = _uiState.update { it.copy(email = value) }
    fun onAddress(value: String) = _uiState.update { it.copy(address = value) }
    fun onNotes(value: String) = _uiState.update { it.copy(notes = value) }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = repository.update(
                companyId = state.hashedId.ifBlank { companyId },
                name = state.name,
                contactPerson = state.contactPerson,
                phone = state.phone,
                email = state.email,
                address = state.address,
                notes = state.notes,
            )
            when (result) {
                is Resource.Success -> _uiState.update { it.copy(isSaving = false, savedMessage = result.data) }
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
        fun provideFactory(context: Context, companyId: String) = viewModelFactory {
            initializer {
                EditCompanyViewModel(
                    companyId = companyId,
                    repository = ServiceLocator.provideCompanyRepository(context.applicationContext),
                )
            }
        }
    }
}

/**
 * Edits a company's contact details and letterhead notes — the web's
 * /company/company-edit/:id form. The logo upload stays web-only (it needs a
 * multipart image field); everything else on this page saves normally.
 */
@Composable
fun EditCompanyScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    companyId: String? = null,
    modifier: Modifier = Modifier,
) {
    if (companyId.isNullOrBlank()) {
        AuthenticatedShell(
            title = "Edit Company",
            currentRoute = Routes.ADMIN,
            navController = navController,
            onLogout = onLogout,
            modifier = modifier,
        ) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Company not found.", color = MaterialTheme.colorScheme.onBackground)
            }
        }
        return
    }

    val viewModel: EditCompanyViewModel = viewModel(
        key = companyId,
        factory = EditCompanyViewModel.provideFactory(LocalContext.current, companyId),
    )
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
        title = "Edit Company",
        currentRoute = Routes.ADMIN,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
                }

                state.loadError != null -> Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.loadError!!,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton(text = "Retry", onClick = viewModel::load)
                    }
                }

                else -> CompanyForm(state = state, viewModel = viewModel)
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun CompanyForm(state: EditCompanyUiState, viewModel: EditCompanyViewModel) {
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
            label = "Enter company name",
            caption = "Company Name *",
            modifier = Modifier.fillMaxWidth(),
        )
        AppTextField(
            value = state.contactPerson,
            onValueChange = viewModel::onContactPerson,
            label = "Enter contact person",
            caption = "Contact Person",
            modifier = Modifier.fillMaxWidth(),
        )
        AppTextField(
            value = state.phone,
            onValueChange = viewModel::onPhone,
            label = "Enter phone",
            caption = "Phone",
            keyboardType = KeyboardType.Phone,
            modifier = Modifier.fillMaxWidth(),
        )
        AppTextField(
            value = state.email,
            onValueChange = viewModel::onEmail,
            label = "Enter email",
            caption = "Email",
            keyboardType = KeyboardType.Email,
            modifier = Modifier.fillMaxWidth(),
        )
        AppTextField(
            value = state.address,
            onValueChange = viewModel::onAddress,
            label = "Enter address",
            caption = "Address",
            multiline = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AppTextField(
                value = state.notes,
                onValueChange = viewModel::onNotes,
                label = "Enter notes",
                caption = "Notes",
                multiline = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Printed under the company name on letterheads.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.textOnScreenMuted,
            )
        }
        Text(
            text = "The company logo can only be uploaded from the web for now. " +
                "Everything else on this page saves normally.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.appColors.textOnScreenMuted,
        )
        PrimaryButton(
            text = "Update Company",
            onClick = viewModel::save,
            enabled = state.canSave,
            isLoading = state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
