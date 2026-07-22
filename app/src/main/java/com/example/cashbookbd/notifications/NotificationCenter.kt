package com.example.cashbookbd.notifications

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.NotificationRepository
import com.example.cashbookbd.session.Permission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * App-wide holder for the notification center — the Kotlin twin of the web's
 * DropdownNotification state, shared so the bell shows the same items on every
 * screen without refetching on each navigation (see
 * [com.example.cashbookbd.di.ServiceLocator]).
 */
class NotificationCenter(
    private val repository: NotificationRepository,
) {

    data class State(
        val items: List<AppNotification> = emptyList(),
        val isLoading: Boolean = false,
        /** True once the first load has completed (success or error). */
        val loadedOnce: Boolean = false,
    ) {
        /** Badge number — sum of counts, treating a count-less item as one. */
        val totalCount: Int get() = items.sumOf { if (it.count > 0) it.count else 1 }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Fetches the latest notifications; a no-op while one is already in flight. */
    fun refresh(permissions: List<Permission>) {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true) }
        scope.launch {
            when (val result = repository.getNotifications(permissions)) {
                is Resource.Success ->
                    _state.value = State(items = result.data, isLoading = false, loadedOnce = true)
                is Resource.Error ->
                    // Keep whatever we last showed; just stop the spinner.
                    _state.update { it.copy(isLoading = false, loadedOnce = true) }
                Resource.Loading -> Unit
            }
        }
    }

    /** Loads once on first use, so the badge appears without an explicit open. */
    fun ensureLoaded(permissions: List<Permission>) {
        if (!_state.value.loadedOnce && !_state.value.isLoading) refresh(permissions)
    }

    /** Marks one notification read: hides it now, tells the server in the background. */
    fun dismiss(item: AppNotification) {
        _state.update { state -> state.copy(items = state.items.filterNot { it.key == item.key }) }
        scope.launch { repository.dismiss(item.key, item.id) }
    }

    /** Drop all state on logout. */
    fun clear() {
        _state.value = State()
    }
}
