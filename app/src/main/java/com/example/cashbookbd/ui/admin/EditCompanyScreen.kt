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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    /** Freshly picked logo bytes (JPEG, ≤2 MB); null leaves the stored one. */
    val lightLogo: ByteArray? = null,
    val darkLogo: ByteArray? = null,
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

    /** Encodes a picked logo to JPEG within the server's 2 MB rule. */
    fun onLogoPicked(context: Context, uri: android.net.Uri, dark: Boolean) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val bytes = encodeCompanyLogo(context.applicationContext, uri)
            _uiState.update {
                when {
                    bytes == null -> it.copy(error = "The logo could not be read, or won't fit under 2 MB.")
                    dark -> it.copy(darkLogo = bytes)
                    else -> it.copy(lightLogo = bytes)
                }
            }
        }
    }

    fun onLogoCleared(dark: Boolean) = _uiState.update {
        if (dark) it.copy(darkLogo = null) else it.copy(lightLogo = null)
    }

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
                lightLogo = state.lightLogo,
                darkLogo = state.darkLogo,
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
        // The web form's two logo uploads — light, and the optional dark-mode
        // variant that falls back to the light one everywhere it is missing.
        val context = LocalContext.current
        var pickingDark by androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(false)
        }
        val pickLogo = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.GetContent()
        ) { uri -> uri?.let { viewModel.onLogoPicked(context, it, dark = pickingDark) } }
        LogoPickRow(
            title = "Company Logo",
            picked = state.lightLogo != null,
            onPick = { pickingDark = false; pickLogo.launch("image/*") },
            onClear = { viewModel.onLogoCleared(dark = false) },
        )
        LogoPickRow(
            title = "Company Logo (Dark Mode)",
            picked = state.darkLogo != null,
            onPick = { pickingDark = true; pickLogo.launch("image/*") },
            onClear = { viewModel.onLogoCleared(dark = true) },
        )
        Text(
            text = "Optional — leave empty to keep the stored logos. The dark " +
                "one falls back to the light-mode logo everywhere it is missing.",
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

/** One logo row: its title, a picked marker, and Choose/Clear. */
@Composable
private fun LogoPickRow(
    title: String,
    picked: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = title + if (picked) "  ✓ selected" else "",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        com.example.cashbookbd.ui.components.SecondaryButton(
            text = "Choose",
            onClick = onPick,
            compact = true,
        )
        if (picked) {
            Spacer(Modifier.width(8.dp))
            com.example.cashbookbd.ui.components.SecondaryButton(
                text = "Clear",
                onClick = onClear,
                compact = true,
            )
        }
    }
}

/** Decodes and re-encodes a picked logo as JPEG within the server's 2 MB cap. */
private fun encodeCompanyLogo(context: Context, uri: android.net.Uri): ByteArray? {
    val maxBytes = 2 * 1024 * 1024
    val source = runCatching {
        context.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it)
        }
    }.getOrNull() ?: return null
    val scale = 1200f / maxOf(source.width, source.height)
    val bitmap = if (scale < 1f) {
        android.graphics.Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        source
    }
    var quality = 92
    var bytes: ByteArray
    do {
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
        bytes = out.toByteArray()
        quality -= 10
    } while (bytes.size > maxBytes && quality >= 40)
    return bytes.takeIf { it.size <= maxBytes }
}
