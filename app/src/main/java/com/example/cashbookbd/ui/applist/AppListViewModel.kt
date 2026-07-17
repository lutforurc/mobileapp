package com.example.cashbookbd.ui.applist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.applist.AppLists
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.AppListRepository
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
        )
    )
    val uiState: StateFlow<AppListUiState> = _uiState.asStateFlow()

    init {
        if (spec != null) load(1)
    }

    fun load(page: Int = _uiState.value.currentPage) {
        val currentSpec = spec ?: return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.fetch(currentSpec, page)) {
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

    fun nextPage() {
        val s = _uiState.value
        if (s.canNext) load(s.currentPage + 1)
    }

    fun prevPage() {
        val s = _uiState.value
        if (s.canPrev) load(s.currentPage - 1)
    }

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
