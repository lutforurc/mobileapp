package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.local.DashboardCache
import com.example.cashbookbd.data.local.TokenManager
import com.example.cashbookbd.data.remote.ApiService
import com.example.cashbookbd.data.remote.dto.LoginData
import com.example.cashbookbd.data.remote.dto.LoginRequest
import com.example.cashbookbd.session.SessionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/**
 * Single source of truth for authentication. Talks to [ApiService], persists
 * the token via [TokenManager], and maps every outcome (including network and
 * server failures) to a [Resource] so the ViewModel never touches Retrofit types.
 */
class AuthRepository(
    private val api: ApiService,
    private val tokenManager: TokenManager,
    private val dashboardCache: DashboardCache,
    private val sessionManager: SessionManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    fun isLoggedIn(): Boolean = tokenManager.isLoggedIn()

    /**
     * Enforces the "Remember me" choice exactly once per process: if the last
     * login opted out, the stored token is dropped so a cold start returns to
     * the login screen. Guarded so it runs only at process start and never logs
     * out a user who signed in during this session (e.g. after a config change).
     */
    fun enforceRememberMePolicy() {
        if (policyApplied) return
        policyApplied = true
        tokenManager.clearTokenIfNotRemembered()
    }

    fun logout() {
        tokenManager.clear()
        // Don't let the next user (or the login screen) see the previous session's data.
        dashboardCache.clear()
        // Drop the previous user's permissions so nothing leaks across sessions.
        sessionManager.clear()
    }

    suspend fun login(
        identifier: String,
        password: String,
        rememberMe: Boolean,
    ): Resource<LoginData> =
        withContext(ioDispatcher) {
            try {
                val response = api.login(LoginRequest(login = identifier.trim(), password = password))

                if (!response.isSuccessful) {
                    return@withContext Resource.Error(
                        "Server error (${response.code()}). Please try again later."
                    )
                }

                val body = response.body()
                    ?: return@withContext Resource.Error("Invalid response from server.")

                when {
                    // Backend signalled failure — surface error.message as required.
                    !body.success -> Resource.Error(
                        body.error?.message ?: "Invalid username or password!"
                    )

                    // Success but no token means we can't authenticate future calls.
                    body.data?.token.isNullOrBlank() -> Resource.Error(
                        "Invalid response from server."
                    )

                    else -> {
                        tokenManager.saveToken(body.data!!.token!!, rememberMe)
                        // Fresh session — drop any dashboard cached for a prior user.
                        dashboardCache.clear()
                        Resource.Success(body.data)
                    }
                }
            } catch (e: IOException) {
                // No connectivity, DNS failure, timeout, etc.
                Resource.Error("No internet connection. Please check your network and try again.")
            } catch (e: HttpException) {
                Resource.Error("Server error (${e.code()}). Please try again later.")
            } catch (e: Exception) {
                Resource.Error("Something went wrong. Please try again.")
            }
        }

    private companion object {
        // Process-wide latch so the remember-me policy is applied only at cold
        // start, not on every Activity re-creation.
        @Volatile
        private var policyApplied = false
    }
}
