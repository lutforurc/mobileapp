package com.example.cashbookbd.ui.admin

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.common.ReloadOnResume
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import com.example.cashbookbd.ui.theme.muted
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// Shared bits
// ---------------------------------------------------------------------------

private val LAYOUTS = listOf(
    SelectorOption("MODAL", "Modal — centre pop-up"),
    SelectorOption("CARD", "Card — image beside text"),
    SelectorOption("BANNER_TOP", "Banner — top"),
    SelectorOption("BANNER_BOTTOM", "Banner — bottom"),
    SelectorOption("IMAGE_ONLY", "Image only"),
)

private val TRIGGERS = listOf(
    SelectorOption("APP_OPEN", "App open"),
    SelectorOption("LOGIN", "Login"),
)

private val AUDIENCES = listOf(
    SelectorOption("global", "Everyone"),
    SelectorOption("company", "Company"),
    SelectorOption("branch", "Branch"),
    SelectorOption("role", "Role"),
    SelectorOption("user", "User"),
)

private val PLATFORMS = listOf(
    SelectorOption("all", "All"),
    SelectorOption("web", "Web"),
    SelectorOption("android", "Android"),
    SelectorOption("ios", "iOS"),
)

private val STATUSES = listOf(
    SelectorOption("active", "Active"),
    SelectorOption("paused", "Paused"),
    SelectorOption("draft", "Draft"),
)

/** One campaign row of the admin list. */
data class CampaignRow(
    val id: Long,
    val title: String,
    val body: String,
    val layout: String,
    val trigger: String,
    val audienceType: String,
    val audienceId: Long?,
    val platform: String,
    val status: String,
    val startsAt: String,
    val endsAt: String,
    val impressions: Long,
    val reachedUsers: Long,
    val clicks: Long,
)

private fun JsonObject.text(key: String): String =
    get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

private fun JsonObject.long(key: String): Long? =
    text(key).toDoubleOrNull()?.toLong()

/** 401 → relogin; otherwise the JSON success flag is the verdict. */
private suspend fun adminCall(
    request: suspend () -> retrofit2.Response<com.google.gson.JsonElement>,
): Resource<JsonObject> = withContext(Dispatchers.IO) {
    try {
        val response = request()
        if (response.code() == 401) {
            return@withContext Resource.Error("Your session has expired. Please log in again.", isUnauthorized = true)
        }
        val body = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
            ?: response.errorBody()?.string()
                ?.let { runCatching { com.google.gson.JsonParser.parseString(it) }.getOrNull() }
                ?.takeIf { it.isJsonObject }?.asJsonObject
            ?: return@withContext Resource.Error("Server error (${response.code()}). Please try again later.")
        val success = body.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
        if (success == false) {
            return@withContext Resource.Error(body.text("message").ifBlank { "The request was refused." })
        }
        Resource.Success(body)
    } catch (e: java.io.IOException) {
        Resource.Error("No internet connection. Please check your network and try again.")
    } catch (e: Exception) {
        Resource.Error("Something went wrong. Please try again.")
    }
}

// ---------------------------------------------------------------------------
// List
// ---------------------------------------------------------------------------

data class CampaignListUiState(
    val isLoading: Boolean = false,
    val rows: List<CampaignRow> = emptyList(),
    val error: String? = null,
    val confirmDelete: CampaignRow? = null,
    val deletingId: Long? = null,
    val message: String? = null,
    val sessionExpired: Boolean = false,
)

class InAppMessagesAdminViewModel(private val api: ReportApiService) : ViewModel() {

    private val _uiState = MutableStateFlow(CampaignListUiState())
    val uiState: StateFlow<CampaignListUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = adminCall { api.get("admin/in-app-messages", emptyMap()) }) {
                is Resource.Success -> {
                    val rows = result.data.getAsJsonObject("data")?.getAsJsonObject("data")
                        ?.get("messages")?.takeIf { it.isJsonArray }?.asJsonArray
                        ?.mapNotNull { el ->
                            val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                            CampaignRow(
                                id = o.long("id") ?: return@mapNotNull null,
                                title = o.text("title"),
                                body = o.text("body"),
                                layout = o.text("layout"),
                                trigger = o.text("trigger_event"),
                                audienceType = o.text("audience_type"),
                                audienceId = o.long("company_id") ?: o.long("branch_id")
                                    ?: o.long("role_id") ?: o.long("user_id"),
                                platform = o.text("platform"),
                                status = o.text("status"),
                                startsAt = o.text("starts_at"),
                                endsAt = o.text("ends_at"),
                                impressions = o.long("impressions") ?: 0L,
                                reachedUsers = o.long("reached_users") ?: 0L,
                                clicks = o.long("clicks") ?: 0L,
                            )
                        }
                        .orEmpty()
                    _uiState.update { it.copy(isLoading = false, rows = rows) }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun requestDelete(row: CampaignRow) = _uiState.update { it.copy(confirmDelete = row) }
    fun cancelDelete() = _uiState.update { it.copy(confirmDelete = null) }

    /** Deleting a campaign also deletes its delivery events. */
    fun confirmDelete() {
        val row = _uiState.value.confirmDelete ?: return
        _uiState.update { it.copy(confirmDelete = null, deletingId = row.id) }
        viewModelScope.launch {
            val result = adminCall { api.delete("admin/in-app-messages/${row.id}") }
            _uiState.update {
                it.copy(
                    deletingId = null,
                    message = when (result) {
                        is Resource.Success -> result.data.text("message").ifBlank { "Deleted." }
                        is Resource.Error -> result.message
                        Resource.Loading -> null
                    },
                    sessionExpired = it.sessionExpired ||
                        (result as? Resource.Error)?.isUnauthorized == true,
                )
            }
            load()
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                InAppMessagesAdminViewModel(
                    api = ServiceLocator.provideReportApiService(context.applicationContext),
                )
            }
        }
    }
}

/** The pop-up campaigns the app itself shows — listed, edited and retired here. */
@Composable
fun InAppMessagesAdminScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InAppMessagesAdminViewModel = viewModel(
        factory = InAppMessagesAdminViewModel.provideFactory(LocalContext.current)
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    ReloadOnResume(viewModel::load)

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
        title = "In-App Messages",
        currentRoute = Routes.ADMIN,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    PrimaryButton(
                        text = "New Campaign",
                        onClick = { navController.navigate(Routes.IN_APP_MESSAGE_ADD) },
                        compact = true,
                    )
                }
                when {
                    state.isLoading && state.rows.isEmpty() -> item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    state.error != null -> item {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(state.error!!, color = MaterialTheme.colorScheme.onBackground)
                            LinkButton(text = "Retry", onClick = viewModel::load)
                        }
                    }
                    state.rows.isEmpty() -> item {
                        Text(
                            text = "No campaign yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appColors.textOnScreenMuted,
                        )
                    }
                    else -> items(state.rows, key = { it.id }) { row ->
                        CampaignCard(
                            row = row,
                            isDeleting = state.deletingId == row.id,
                            onEdit = { navController.navigate(Routes.inAppMessageEdit(row.id)) },
                            onDelete = { viewModel.requestDelete(row) },
                        )
                    }
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    state.confirmDelete?.let { row ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Delete \"${row.title}\"?") },
            text = { Text("The campaign and its delivery history are removed together.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CampaignCard(
    row: CampaignRow,
    isDeleting: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val onScreen = MaterialTheme.colorScheme.onBackground
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = AppFontWeight.SemiBold,
                    color = onScreen,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = row.status.replaceFirstChar { it.uppercaseChar() },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = AppFontWeight.SemiBold,
                    color = when (row.status) {
                        "active" -> MaterialTheme.appColors.success
                        else -> MaterialTheme.appColors.textOnScreenMuted
                    },
                )
            }
            if (row.body.isNotBlank()) {
                Text(
                    text = row.body,
                    style = MaterialTheme.typography.labelSmall,
                    color = onScreen.muted(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "${row.layout} / ${row.trigger} • " +
                    (if (row.audienceType == "global") "Everyone" else "${row.audienceType} #${row.audienceId ?: "?"}") +
                    " / ${row.platform}",
                style = MaterialTheme.typography.labelSmall,
                color = onScreen.muted(),
            )
            Text(
                text = "${row.startsAt.ifBlank { "Now" }} → ${row.endsAt.ifBlank { "No end" }} • " +
                    "${row.impressions} shown · ${row.reachedUsers} user(s) · ${row.clicks} click",
                style = MaterialTheme.typography.labelSmall,
                color = onScreen.muted(),
            )
            Row {
                LinkButton(text = "Edit", onClick = onEdit, enabled = !isDeleting)
                LinkButton(
                    text = if (isDeleting) "Deleting…" else "Delete",
                    onClick = onDelete,
                    enabled = !isDeleting,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Form (create + edit)
// ---------------------------------------------------------------------------

data class CampaignFormUiState(
    val messageId: Long? = null,
    val isLoadingMessage: Boolean = false,

    val title: String = "",
    val body: String = "",
    val imageUrl: String = "",
    val layout: SelectorOption = LAYOUTS.first(),
    val trigger: SelectorOption = TRIGGERS.first(),
    val primaryLabel: String = "",
    val primaryAction: String = "",
    val secondaryLabel: String = "",
    val secondaryAction: String = "",
    val audience: SelectorOption = AUDIENCES.first(),
    val audienceId: String = "",
    val platform: SelectorOption = PLATFORMS.first(),
    val status: SelectorOption = STATUSES.first(),
    /** yyyy-MM-ddTHH:mm as typed; blank = Now / No end. */
    val startsAt: String = "",
    val endsAt: String = "",
    val displayLimit: String = "",
    val minIntervalHours: String = "0",
    val priority: String = "0",
    val requireAck: Boolean = false,

    val isSaving: Boolean = false,
    val savedMessage: String? = null,
    val message: String? = null,
    val sessionExpired: Boolean = false,
)

class CampaignFormViewModel(
    private val api: ReportApiService,
    messageId: Long?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CampaignFormUiState(messageId = messageId))
    val uiState: StateFlow<CampaignFormUiState> = _uiState.asStateFlow()

    init {
        if (messageId != null) loadMessage(messageId)
    }

    private fun loadMessage(id: Long) {
        _uiState.update { it.copy(isLoadingMessage = true) }
        viewModelScope.launch {
            when (val result = adminCall { api.get("admin/in-app-messages/$id", emptyMap()) }) {
                is Resource.Success -> {
                    val m = result.data.getAsJsonObject("data")?.getAsJsonObject("data")
                        ?.get("message")?.takeIf { it.isJsonObject }?.asJsonObject
                    _uiState.update { state ->
                        if (m == null) state.copy(isLoadingMessage = false, message = "Campaign not found.")
                        else state.copy(
                            isLoadingMessage = false,
                            title = m.text("title"),
                            body = m.text("body"),
                            imageUrl = m.text("image_url"),
                            layout = LAYOUTS.firstOrNull { it.id == m.text("layout") } ?: LAYOUTS.first(),
                            trigger = TRIGGERS.firstOrNull { it.id == m.text("trigger_event") } ?: TRIGGERS.first(),
                            primaryLabel = m.text("primary_label"),
                            primaryAction = m.text("primary_action"),
                            secondaryLabel = m.text("secondary_label"),
                            secondaryAction = m.text("secondary_action"),
                            audience = AUDIENCES.firstOrNull { it.id == m.text("audience_type") } ?: AUDIENCES.first(),
                            audienceId = (m.long("company_id") ?: m.long("branch_id")
                                ?: m.long("role_id") ?: m.long("user_id"))?.toString().orEmpty(),
                            platform = PLATFORMS.firstOrNull { it.id == m.text("platform") } ?: PLATFORMS.first(),
                            status = STATUSES.firstOrNull { it.id == m.text("status") } ?: STATUSES.first(),
                            startsAt = m.text("starts_at"),
                            endsAt = m.text("ends_at"),
                            displayLimit = m.long("display_limit")?.toString().orEmpty(),
                            minIntervalHours = m.long("min_interval_hours")?.toString() ?: "0",
                            priority = m.long("priority")?.toString() ?: "0",
                            requireAck = m.text("require_ack")
                                .let { it == "true" || it.toDoubleOrNull() == 1.0 },
                        )
                    }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoadingMessage = false,
                        message = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onTitle(v: String) = _uiState.update { it.copy(title = v.take(200)) }
    fun onBody(v: String) = _uiState.update { it.copy(body = v) }
    fun onImageUrl(v: String) = _uiState.update { it.copy(imageUrl = v) }
    fun onLayout(o: SelectorOption) = _uiState.update { it.copy(layout = o) }
    fun onTrigger(o: SelectorOption) = _uiState.update { it.copy(trigger = o) }
    fun onPrimaryLabel(v: String) = _uiState.update { it.copy(primaryLabel = v.take(60)) }
    fun onPrimaryAction(v: String) = _uiState.update { it.copy(primaryAction = v) }
    fun onSecondaryLabel(v: String) = _uiState.update { it.copy(secondaryLabel = v.take(60)) }
    fun onSecondaryAction(v: String) = _uiState.update { it.copy(secondaryAction = v) }
    fun onAudience(o: SelectorOption) = _uiState.update { it.copy(audience = o, audienceId = "") }
    fun onAudienceId(v: String) = _uiState.update { it.copy(audienceId = v) }
    fun onPlatform(o: SelectorOption) = _uiState.update { it.copy(platform = o) }
    fun onStatus(o: SelectorOption) = _uiState.update { it.copy(status = o) }
    fun onStartsAt(v: String) = _uiState.update { it.copy(startsAt = v) }
    fun onEndsAt(v: String) = _uiState.update { it.copy(endsAt = v) }
    fun onDisplayLimit(v: String) = _uiState.update { it.copy(displayLimit = v) }
    fun onMinIntervalHours(v: String) = _uiState.update { it.copy(minIntervalHours = v) }
    fun onPriority(v: String) = _uiState.update { it.copy(priority = v) }
    fun onRequireAck(on: Boolean) = _uiState.update { it.copy(requireAck = on) }

    fun save() {
        val state = _uiState.value
        if (state.title.isBlank()) {
            _uiState.update { it.copy(message = "Title is required") }
            return
        }
        if (state.layout.id == "IMAGE_ONLY" && state.imageUrl.isBlank()) {
            _uiState.update { it.copy(message = "An image is required for the Image only layout") }
            return
        }
        if (state.audience.id != "global" && state.audienceId.toLongOrNull() == null) {
            _uiState.update { it.copy(message = "A ${state.audience.id} id is required for this audience.") }
            return
        }
        if (state.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            // Empty strings travel as null, per the web's payload shaping.
            val body = JsonObject().apply {
                addProperty("title", state.title.trim())
                addNullable("body", state.body)
                addNullable("image_url", state.imageUrl)
                addProperty("layout", state.layout.id)
                addProperty("trigger_event", state.trigger.id)
                addNullable("primary_label", state.primaryLabel)
                addNullable("primary_action", state.primaryAction)
                addNullable("secondary_label", state.secondaryLabel)
                addNullable("secondary_action", state.secondaryAction)
                addProperty("audience_type", state.audience.id)
                // Only the matching audience id travels; the rest are null.
                listOf("company_id", "branch_id", "role_id", "user_id").forEach { key ->
                    val matches = key.removeSuffix("_id") == state.audience.id
                    if (matches) {
                        state.audienceId.toLongOrNull()?.let { addProperty(key, it) }
                            ?: add(key, JsonNull.INSTANCE)
                    } else {
                        add(key, JsonNull.INSTANCE)
                    }
                }
                addProperty("platform", state.platform.id)
                addProperty("status", state.status.id)
                addNullable("starts_at", state.startsAt)
                addNullable("ends_at", state.endsAt)
                state.displayLimit.toLongOrNull()?.let { addProperty("display_limit", it) }
                    ?: add("display_limit", JsonNull.INSTANCE)
                addProperty("min_interval_hours", state.minIntervalHours.toLongOrNull() ?: 0L)
                addProperty("priority", state.priority.toLongOrNull() ?: 0L)
                addProperty("require_ack", if (state.requireAck) 1 else 0)
            }
            val id = state.messageId
            val result = adminCall {
                api.postObjectRaw(
                    if (id == null) "admin/in-app-messages" else "admin/in-app-messages/$id",
                    body,
                )
            }
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        savedMessage = result.data.text("message").ifBlank { "Campaign saved." },
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    private fun JsonObject.addNullable(key: String, value: String) {
        value.trim().takeIf { it.isNotEmpty() }?.let { addProperty(key, it) }
            ?: add(key, JsonNull.INSTANCE)
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context, messageId: Long?) = viewModelFactory {
            initializer {
                CampaignFormViewModel(
                    api = ServiceLocator.provideReportApiService(context.applicationContext),
                    messageId = messageId,
                )
            }
        }
    }
}

/**
 * Creates or edits one campaign. The image is given as a URL — uploading the
 * file itself stays on the web for now.
 */
@Composable
fun InAppMessageFormScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    messageId: Long? = null,
    viewModel: CampaignFormViewModel = viewModel(
        key = "campaign-${messageId ?: 0L}",
        factory = CampaignFormViewModel.provideFactory(LocalContext.current, messageId),
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
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onMessageShown()
    }
    LaunchedEffect(state.savedMessage) {
        val message = state.savedMessage ?: return@LaunchedEffect
        navController.previousBackStackEntry?.savedStateHandle?.set(Routes.CREATED_MESSAGE, message)
        navController.popBackStack()
    }

    AuthenticatedShell(
        title = if (state.messageId == null) "New Campaign" else "Edit Campaign",
        currentRoute = Routes.ADMIN,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (state.isLoadingMessage) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AppTextField(state.title, viewModel::onTitle, label = "Title", modifier = Modifier.fillMaxWidth())
                    AppTextField(state.body, viewModel::onBody, label = "Body", multiline = true, modifier = Modifier.fillMaxWidth())
                    AppTextField(
                        state.imageUrl, viewModel::onImageUrl,
                        label = "Image URL (uploading a file stays on the web)",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppSelectDropdown("Layout", LAYOUTS, state.layout, viewModel::onLayout, modifier = Modifier.fillMaxWidth())
                    AppSelectDropdown("Trigger", TRIGGERS, state.trigger, viewModel::onTrigger, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppTextField(state.primaryLabel, viewModel::onPrimaryLabel, label = "Primary button label", modifier = Modifier.weight(1f))
                        AppTextField(state.primaryAction, viewModel::onPrimaryAction, label = "Primary action", modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppTextField(state.secondaryLabel, viewModel::onSecondaryLabel, label = "Secondary button label", modifier = Modifier.weight(1f))
                        AppTextField(state.secondaryAction, viewModel::onSecondaryAction, label = "Secondary action", modifier = Modifier.weight(1f))
                    }
                    AppSelectDropdown("Audience", AUDIENCES, state.audience, viewModel::onAudience, modifier = Modifier.fillMaxWidth())
                    if (state.audience.id != "global") {
                        AppTextField(
                            state.audienceId, viewModel::onAudienceId,
                            label = "${state.audience.label} id",
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    AppSelectDropdown("Platform", PLATFORMS, state.platform, viewModel::onPlatform, modifier = Modifier.fillMaxWidth())
                    AppSelectDropdown("Status", STATUSES, state.status, viewModel::onStatus, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppTextField(
                            state.startsAt, viewModel::onStartsAt,
                            label = "Starts at (yyyy-MM-ddTHH:mm, blank = Now)",
                            modifier = Modifier.weight(1f),
                        )
                        AppTextField(
                            state.endsAt, viewModel::onEndsAt,
                            label = "Ends at (blank = No end)",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppTextField(
                            state.displayLimit, viewModel::onDisplayLimit,
                            label = "Show how many times (blank = unlimited)",
                            keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f),
                        )
                        AppTextField(
                            state.minIntervalHours, viewModel::onMinIntervalHours,
                            label = "Gap between showings (hours)",
                            keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f),
                        )
                    }
                    AppTextField(
                        state.priority, viewModel::onPriority,
                        label = "Priority (higher shows first)",
                        keyboardType = KeyboardType.Number, modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Must acknowledge (no close button)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = state.requireAck, onCheckedChange = viewModel::onRequireAck)
                    }
                    PrimaryButton(
                        text = if (state.messageId == null) "Save" else "Update",
                        onClick = viewModel::save,
                        isLoading = state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}
