package com.example.cashbookbd.ui.applist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.applist.AppLists
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.AppListRepository
import com.example.cashbookbd.data.repository.AppListRow
import com.example.cashbookbd.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Loads and holds a read-only list screen (resolved from [listKey]). */
class AppListViewModel(
    listKey: String,
    private val repository: AppListRepository,
) : ViewModel() {

    private val spec = AppLists.byKey(listKey)

    private val _uiState = MutableStateFlow(
        AppListUiState(
            title = spec?.title ?: "List",
            isSupported = spec != null,
            columns = spec?.columns.orEmpty(),
            isPaginated = spec?.paginated == true,
            perPage = spec?.perPage ?: 25,
            hasStatusToggle = spec?.statusToggle != null,
            addAction = spec?.addAction,
            editAction = spec?.editAction,
        )
    )
    val uiState: StateFlow<AppListUiState> = _uiState.asStateFlow()

    init {
        if (spec != null) load(1)
    }

    /**
     * Loads [page]. A [silent] load skips the spinner so the table stays on
     * screen — used to reconcile rows after a status toggle.
     */
    fun load(page: Int = _uiState.value.currentPage, silent: Boolean = false) {
        val currentSpec = spec ?: return
        if (!silent) _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.fetch(currentSpec, page, _uiState.value.perPage)) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        rows = result.data.rows,
                        currentPage = result.data.currentPage,
                        lastPage = result.data.lastPage,
                        total = result.data.total,
                        error = null,
                    )
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

    /** Changes the page size and reloads from page 1, as the web selector does. */
    fun onPerPageChange(perPage: Int) {
        if (perPage == _uiState.value.perPage) return
        _uiState.update { it.copy(perPage = perPage) }
        load(page = 1)
    }

    fun nextPage() {
        val s = _uiState.value
        if (s.canNext) load(s.currentPage + 1)
    }

    fun prevPage() {
        val s = _uiState.value
        if (s.canPrev) load(s.currentPage - 1)
    }

    /**
     * Flips [row]'s status. The switch moves immediately; on success the page is
     * silently reloaded so the rows settle on the server's own state, and on
     * failure the switch snaps back and the reason is surfaced.
     */
    fun onToggleStatus(row: AppListRow, on: Boolean) {
        val currentSpec = spec ?: return
        val id = row.id ?: return
        if (id in _uiState.value.togglingIds) return

        _uiState.update { state ->
            state.copy(
                rows = state.rows.map { if (it.id == id) it.copy(statusOn = on) else it },
                togglingIds = state.togglingIds + id,
            )
        }
        viewModelScope.launch {
            when (val result = repository.setStatus(currentSpec, id, on)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(togglingIds = it.togglingIds - id) }
                    load(_uiState.value.currentPage, silent = true)
                }
                is Resource.Error -> _uiState.update { state ->
                    state.copy(
                        rows = state.rows.map { if (it.id == id) it.copy(statusOn = !on) else it },
                        togglingIds = state.togglingIds - id,
                        actionMessage = result.message,
                        sessionExpired = state.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onActionMessageShown() = _uiState.update { it.copy(actionMessage = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context, listKey: String) = viewModelFactory {
            initializer {
                AppListViewModel(
                    listKey = listKey,
                    repository = ServiceLocator.provideAppListRepository(context.applicationContext),
                )
            }
        }
    }
}
