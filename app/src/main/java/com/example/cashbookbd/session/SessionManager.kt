package com.example.cashbookbd.session

import com.example.cashbookbd.core.AmountFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory holder for the authenticated user's settings and permissions — the
 * Kotlin equivalent of the web app's `AuthContext`.
 *
 * A single instance is shared app-wide (see
 * [com.example.cashbookbd.di.ServiceLocator]). UI observes [state] to react to
 * permission changes; non-composable callers can read [permissions] and use the
 * [can]/[canAny]/[canAll] convenience checks.
 *
 * Permissions live only in memory: they are populated after login and, on a cold
 * start with a stored token, reloaded during boot via
 * [com.example.cashbookbd.data.repository.SessionRepository.refresh]. [clear]
 * wipes them on logout.
 */
class SessionManager {

    data class State(
        val settings: Settings? = null,
        val permissions: List<Permission> = emptyList(),
        /** True while settings are being (re)loaded from the backend. */
        val isLoading: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun setLoading(loading: Boolean) = _state.update { it.copy(isLoading = loading) }

    fun setSettings(settings: Settings) {
        // Keep the shared amount formatter in step with the current branch, so
        // every screen's figures use this branch's decimal places without each
        // one having to observe the session itself.
        AmountFormat.setDecimalPlaces(settings.decimalPlaces)
        _state.update {
            it.copy(settings = settings, permissions = settings.permissions, isLoading = false)
        }
    }

    /** Drop all session state (called on logout). */
    fun clear() {
        AmountFormat.setDecimalPlaces(null)
        _state.value = State()
    }

    /** Latest permissions snapshot for non-composable callers (e.g. repositories). */
    val permissions: List<Permission> get() = _state.value.permissions

    fun can(permission: String): Boolean = Permissions.has(permissions, permission)

    fun canAny(anyOf: Collection<String>): Boolean = Permissions.hasAny(permissions, anyOf)

    fun canAll(allOf: Collection<String>): Boolean = Permissions.hasAll(permissions, allOf)
}