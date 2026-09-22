package com.example.cashbookbd.ui.products

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
import com.example.cashbookbd.data.repository.ProductRepository
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

/**
 * Product Group and Pack Size are the same shape end to end (id, name,
 * description, upsert-by-id, delete refused while a product still points at
 * it) — one form serves both, told apart only by these four strings.
 */
data class MasterFormConfig(
    val title: String,
    val storeEndpoint: String,
    /** The store endpoint's name field: `group_name` or `pack_size_name`. */
    val nameField: String,
    /** The unpaginated DDL — doubles as the edit form's prefill source. */
    val ddlEndpoint: String,
)

data class MasterFormUiState(
    val isLoading: Boolean = false,
    val loadError: String? = null,
    val name: String = "",
    val description: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    val canSave: Boolean get() = !isSaving && name.isNotBlank() && description.isNotBlank()
}

class MasterFormViewModel(
    private val id: String?,
    private val config: MasterFormConfig,
    private val repository: ProductRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MasterFormUiState(isLoading = id != null))
    val uiState: StateFlow<MasterFormUiState> = _uiState.asStateFlow()

    init {
        if (id != null) retry()
    }

    fun retry() {
        val entryId = id ?: return
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            when (val result = repository.fetchMasterEntry(config.ddlEndpoint, entryId)) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, name = result.data.first, description = result.data.second)
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

    fun onName(value: String) = _uiState.update { it.copy(name = value) }
    fun onDescription(value: String) = _uiState.update { it.copy(description = value) }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = repository.saveMasterEntry(
                storeEndpoint = config.storeEndpoint,
                nameField = config.nameField,
                id = id,
                name = state.name,
                description = state.description,
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
        fun provideFactory(context: Context, id: String?, config: MasterFormConfig) = viewModelFactory {
            initializer {
                MasterFormViewModel(
                    id = id,
                    config = config,
                    repository = ServiceLocator.provideProductRepository(context.applicationContext),
                )
            }
        }
    }
}

@Composable
fun MasterFormScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    config: MasterFormConfig,
    id: String?,
    modifier: Modifier = Modifier,
    viewModel: MasterFormViewModel = viewModel(
        factory = MasterFormViewModel.provideFactory(LocalContext.current, id, config)
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
    LaunchedEffect(state.savedMessage) {
        val message = state.savedMessage ?: return@LaunchedEffect
        navController.previousBackStackEntry?.savedStateHandle?.set(Routes.CREATED_MESSAGE, message)
        navController.popBackStack()
    }

    AuthenticatedShell(
        title = if (id == null) "Add ${config.title}" else "Edit ${config.title}",
        currentRoute = Routes.PRODUCTS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
                }

                state.loadError != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.loadError!!,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton(text = "Retry", onClick = viewModel::retry)
                    }
                }

                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AppTextField(
                        value = state.name,
                        onValueChange = viewModel::onName,
                        label = "Enter ${config.title.lowercase()} name",
                        caption = "${config.title} Name",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppTextField(
                        value = state.description,
                        onValueChange = viewModel::onDescription,
                        label = "Enter description",
                        caption = "Description",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!state.canSave && !state.isSaving) {
                        Text(
                            text = "Name and Description are required.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appColors.textOnScreenMuted,
                        )
                    }
                    PrimaryButton(
                        text = "Save ${config.title}",
                        onClick = viewModel::save,
                        enabled = state.canSave,
                        isLoading = state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}
