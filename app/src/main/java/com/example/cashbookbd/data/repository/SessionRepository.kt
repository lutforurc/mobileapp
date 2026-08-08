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

    /** When the last successful refresh landed (0 = never). */
    @Volatile
    private var lastRefreshAt: Long = 0L

    /**
     * Fetches settings and populates the [SessionManager]. Returns
     * [Resource.Success] with `Unit` on success; on failure the manager is left
     * with no permissions and the caller receives the underlying [Resource.Error]
     * (whose `isUnauthorized` flag signals an expired token).
     *
     * This is the *forced* fetch — login, boot, and the after-save paths (a
     * branch/user/profile edit just changed what settings say) call it. A
     * screen that merely wants fresh-enough settings rides [refreshIfStale].
     */
    suspend fun refresh(): Resource<Unit> {
        sessionManager.setLoading(true)
        return when (val result = settingsRepository.getSettings()) {
            is Resource.Success -> {
                sessionManager.setSettings(result.data)
                lastRefreshAt = System.currentTimeMillis()
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

    /**
     * As [refresh], but a no-op while the last successful refresh is younger
     * than [maxAgeMs] — the form-open callers use this, so opening ten
     * vouchers in a row costs one settings request, not ten. A skip still
     * answers Success: the settings on hand are the ones just vouched for.
     */
    suspend fun refreshIfStale(maxAgeMs: Long = DEFAULT_MAX_AGE_MS): Resource<Unit> {
        if (System.currentTimeMillis() - lastRefreshAt < maxAgeMs) return Resource.Success(Unit)
        return refresh()
    }

    companion object {
        /** Five minutes — fresh enough for a branch-type change to surface. */
        const val DEFAULT_MAX_AGE_MS = 5 * 60 * 1000L
    }
}