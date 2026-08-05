package com.example.cashbookbd.ui.account

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.data.repository.SessionRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.session.SessionManager
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

private const val MAX_PHOTO_BYTES = 2 * 1024 * 1024 // the server's 2 MB rule

data class ProfileUiState(
    val isUploading: Boolean = false,
    val message: String? = null,
    val sessionExpired: Boolean = false,
)

class ProfileViewModel(
    private val api: ReportApiService,
    private val sessionRepository: SessionRepository,
    val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    /** Uploads the picked image, then refreshes the session so it shows app-wide. */
    fun uploadPhoto(context: Context, uri: Uri) {
        if (_uiState.value.isUploading) return
        _uiState.update { it.copy(isUploading = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val resolver = context.contentResolver
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@withContext Resource.Error("Could not read the picked image.")
                    if (bytes.size > MAX_PHOTO_BYTES) {
                        return@withContext Resource.Error("Max 2MB allowed.")
                    }
                    val mime = resolver.getType(uri) ?: "image/jpeg"
                    if (!mime.startsWith("image/")) {
                        return@withContext Resource.Error("Please select an image file.")
                    }
                    val part = MultipartBody.Part.createFormData(
                        "image",
                        "profile." + (mime.substringAfter('/').ifBlank { "jpg" }),
                        bytes.toRequestBody(mime.toMediaType()),
                    )
                    val response = api.uploadImage("user/profile-photo", part)
                    if (response.code() == 401) {
                        return@withContext Resource.Error(
                            "Your session has expired. Please log in again.", isUnauthorized = true,
                        )
                    }
                    val body = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
                    val success = body?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
                    if (success != true) {
                        return@withContext Resource.Error(
                            body?.get("message")?.takeUnless { it.isJsonNull }?.asString
                                ?.ifBlank { null } ?: "The photo could not be saved.",
                        )
                    }
                    Resource.Success("Photo updated.")
                } catch (e: java.io.IOException) {
                    Resource.Error("No internet connection. Please check your network and try again.")
                } catch (e: Exception) {
                    Resource.Error("Something went wrong. Please try again.")
                }
            }
            when (result) {
                is Resource.Success -> {
                    // The photo URL lives in get-settings; a refresh shows it
                    // everywhere at once (the account menu included).
                    sessionRepository.refresh()
                    _uiState.update { it.copy(isUploading = false, message = result.data) }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isUploading = false,
                        message = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                ProfileViewModel(
                    api = ServiceLocator.provideReportApiService(appContext),
                    sessionRepository = ServiceLocator.provideSessionRepository(appContext),
                    sessionManager = ServiceLocator.provideSessionManager(appContext),
                )
            }
        }
    }
}

/** The signed-in user's own page: name, contact, and the profile photo. */
@Composable
fun ProfileScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = viewModel(
        factory = ProfileViewModel.provideFactory(LocalContext.current)
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sessionState by viewModel.sessionManager.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadPhoto(context, it) }
    }

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onMessageShown()
    }

    AuthenticatedShell(
        title = "Profile",
        currentRoute = Routes.HOME,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = sessionState.settings?.userPhotoUrl,
                        contentDescription = "Profile photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape),
                    )
                    if (state.isUploading) CircularProgressIndicator()
                }
                LinkButton(
                    text = if (state.isUploading) "Uploading…" else "Change Photo",
                    onClick = { pickImage.launch("image/*") },
                    enabled = !state.isUploading,
                )
                Text(
                    text = "JPG/PNG/WebP, max 2 MB.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.appColors.textOnScreenMuted,
                )
                Text(
                    text = sessionState.settings?.userName ?: "User Name",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = AppFontWeight.SemiBold,
                )
                sessionState.settings?.userEmail?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textOnScreenMuted,
                    )
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}
