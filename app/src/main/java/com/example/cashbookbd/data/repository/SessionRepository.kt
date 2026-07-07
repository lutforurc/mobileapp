package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.session.SessionManager

/**
 * Coordinates loading the authenticated user's settings into the shared
 * [SessionManager]. Call [refresh] right after login and again during boot when
 * a stored token exists, so the app always has an up-to-date permission set
 * before gating menus and screens.
 */
class SessionRepository(
    private val settingsRepository: SettingsRepository,
    private val sessionManager: SessionManager,
) {

    /**
     * Fetches settings and populates the [SessionManager]. Returns
     * [Resource.Success] with `Unit` on success; on failure the manager is left
     * with no permissions and the caller receives the underlying [Resource.Error]
     * (whose `isUnauthorized` flag signals an expired token).
     */
    suspend fun refresh(): Resource<Unit> {
        sessionManager.setLoading(true)
        return when (val result = settingsRepository.getSettings()) {
            is Resource.Success -> {
                sessionManager.setSettings(result.data)
                Resource.Success(Unit)
            }
            is Resource.Error -> {
                sessionManager.setLoading(false)
                result
            }
            Resource.Loading -> {
                sessionManager.setLoading(false)
                Resource.Loading
            }
        }
    }
}