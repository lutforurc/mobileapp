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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.core.jBool
import com.example.cashbookbd.core.jInt
import com.example.cashbookbd.core.jLong
import com.example.cashbookbd.core.jObjs
import com.example.cashbookbd.core.jStr
import com.example.cashbookbd.data.remote.WebApi
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.LogoutOnExpiry
import com.example.cashbookbd.ui.reports.ReportLoading
import com.example.cashbookbd.ui.reports.ReportNote
import com.example.cashbookbd.ui.reports.SnackOnMessage
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WebsiteTemplate(
    val id: Long,
    val key: String,
    val name: String,
    val description: String,
    val published: Boolean,
    val pages: Int,
    val publishedAt: String,
)

data class WebsiteTemplatesUiState(
    val isLoading: Boolean = false,
    val rows: List<WebsiteTemplate> = emptyList(),
    val newName: String = "",
    val creating: Boolean = false,
    val busyId: Long? = null,
    val askDelete: WebsiteTemplate? = null,
    val message: String? = null,
    val sessionExpired: Boolean = false,
)

/**
 * Admin → Website Templates (WebsiteTemplates.tsx): the platform's master
 * website templates. A draft is visible to nobody; publishing puts it in front
 * of every company. The server's `platform.admin` check is the lock. The
 * page-by-page template editor is a desktop design tool and stays on the web.
 */
class WebsiteTemplatesViewModel(private val api: WebApi) : ViewModel() {
    private val _uiState = MutableStateFlow(WebsiteTemplatesUiState())
    val uiState: StateFlow<WebsiteTemplatesUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            when (val r = api.get("admin/website-templates")) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        rows = r.data.jObjs("templates").map { o ->
                            WebsiteTemplate(
                                o.jLong("id") ?: 0L, o.jStr("key"), o.jStr("name"), o.jStr("description"),
                                o.jBool("is_published"), o.jInt("pages") ?: 0, o.jStr("published_at"),
                            )
                        },
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, rows = emptyList(), message = r.message.ifBlank { "Could not load website templates." }, sessionExpired = r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onName(v: String) = _uiState.update { it.copy(newName = v.take(150)) }

    fun create() {
        val name = _uiState.value.newName.trim()
        if (name.isEmpty()) {
            _uiState.update { it.copy(message = "Give the template a name.") }
            return
        }
        _uiState.update { it.copy(creating = true) }
        viewModelScope.launch {
            when (val r = api.post("admin/website-templates", JsonObject().apply { addProperty("name", name) })) {
                is Resource.Success -> {
                    _uiState.update { it.copy(creating = false, newName = "", message = "Template created. Open it on the web to start editing.") }
                    load()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(creating = false, message = r.message.ifBlank { "Could not create the template." }, sessionExpired = r.isUnauthorized)
                }
                Resource.Loading -> Unit
            }
        }
    }

    private fun run(row: WebsiteTemplate, ok: String, block: suspend () -> Resource<JsonObject>) {
        _uiState.update { it.copy(busyId = row.id) }
        viewModelScope.launch {
            val r = block()
            _uiState.update {
                it.copy(
                    busyId = null,
                    message = if (r is Resource.Success) ok else (r as? Resource.Error)?.message?.ifBlank { null } ?: "The request was refused.",
                    sessionExpired = (r as? Resource.Error)?.isUnauthorized == true,
                )
            }
            load()
        }
    }

    fun duplicate(row: WebsiteTemplate) = run(row, "Duplicated as a draft.") { api.post("admin/website-templates/${row.id}/duplicate") }
    fun publish(row: WebsiteTemplate) = run(row, "Published — companies can select it now.") { api.post("admin/website-templates/${row.id}/publish") }
    fun unpublish(row: WebsiteTemplate) = run(row, "Unpublished — companies can no longer select it.") { api.post("admin/website-templates/${row.id}/unpublish") }
    fun askDelete(row: WebsiteTemplate) = _uiState.update { it.copy(askDelete = row) }
    fun dismissDelete() = _uiState.update { it.copy(askDelete = null) }
    fun confirmDelete() {
        val row = _uiState.value.askDelete ?: return
        _uiState.update { it.copy(askDelete = null) }
        run(row, "Template deleted.") { api.delete("admin/website-templates/${row.id}") }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer { WebsiteTemplatesViewModel(ServiceLocator.provideWebApi(context.applicationContext)) }
        }
    }
}

@Composable
fun WebsiteTemplatesScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    viewModel: WebsiteTemplatesViewModel = viewModel(factory = WebsiteTemplatesViewModel.provideFactory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LogoutOnExpiry(state.sessionExpired, viewModel::onSessionExpiredHandled, onLogout)
    SnackOnMessage(state.message, snackbar, viewModel::onMessageShown)
    val muted = MaterialTheme.appColors.textMuted

    AuthenticatedShell(title = "Website Templates", currentRoute = Routes.ADMIN, navController = navController, onLogout = onLogout) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("Create a template", fontWeight = AppFontWeight.Bold, color = MaterialTheme.appColors.textOnScreen) }
                item {
                    Text(
                        "A template is the platform's own: companies copy it into their own website and may change their copy freely — " +
                            "the master and every other company are unaffected. Nothing is offered to companies until you publish it.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textOnScreenMuted,
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                        AppTextField(state.newName, viewModel::onName, label = "e.g. Restaurant & Cafe", caption = "Name", modifier = Modifier.weight(1f))
                        PrimaryButton(text = if (state.creating) "Creating…" else "Create blank", onClick = viewModel::create, isLoading = state.creating)
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Master templates", fontWeight = AppFontWeight.Bold, color = MaterialTheme.appColors.textOnScreen, modifier = Modifier.weight(1f))
                        SecondaryButton(text = "Refresh", onClick = viewModel::load, compact = true, isLoading = state.isLoading)
                    }
                }
                when {
                    state.isLoading && state.rows.isEmpty() -> item { ReportLoading() }
                    state.rows.isEmpty() -> item { ReportNote("No templates yet.") }
                    else -> itemsIndexed(state.rows, key = { _, r -> r.id }) { i, row ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${i + 1}. ${row.name}", fontWeight = AppFontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    Text(
                                        if (row.published) "Published" else "Draft",
                                        style = MaterialTheme.typography.labelSmall, fontWeight = AppFontWeight.Bold,
                                        color = if (row.published) MaterialTheme.appColors.success else MaterialTheme.appColors.warning,
                                    )
                                }
                                Text(row.key, style = MaterialTheme.typography.labelSmall, color = muted)
                                if (row.description.isNotBlank()) Text(row.description, style = MaterialTheme.typography.labelSmall)
                                Text(
                                    "Pages: ${row.pages} · Published: ${row.publishedAt.replace('T', ' ').take(16).ifBlank { "—" }}",
                                    style = MaterialTheme.typography.labelSmall, color = muted,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val busy = state.busyId == row.id
                                    LinkButton(text = "Duplicate", enabled = !busy, onClick = { viewModel.duplicate(row) })
                                    if (row.published) {
                                        LinkButton(text = "Unpublish", enabled = !busy, onClick = { viewModel.unpublish(row) }, color = MaterialTheme.appColors.warning)
                                    } else {
                                        LinkButton(text = "Publish", enabled = !busy, onClick = { viewModel.publish(row) }, color = MaterialTheme.appColors.success)
                                    }
                                    LinkButton(text = "Delete", enabled = !busy, onClick = { viewModel.askDelete(row) }, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
                item {
                    Text(
                        "Draft templates are invisible to companies. Publishing copies your draft across; unpublishing withdraws the template " +
                            "without deleting it or disturbing any company that already copied it. Editing a template's pages is done on the web.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.appColors.textOnScreenMuted,
                    )
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }

    state.askDelete?.let { row ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Delete template?") },
            text = { Text("Delete \"${row.name}\"? Companies that already used it keep their own copies.") },
            confirmButton = { LinkButton(text = "Delete", onClick = viewModel::confirmDelete, color = MaterialTheme.colorScheme.error) },
            dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::dismissDelete) },
        )
    }
}
