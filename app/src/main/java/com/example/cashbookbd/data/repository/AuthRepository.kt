package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.local.DashboardCache
import com.example.cashbookbd.data.local.TokenManager
import com.example.cashbookbd.data.remote.ApiService
import com.example.cashbookbd.data.remote.dto.LoginData
import com.example.cashbookbd.data.remote.dto.LoginDeviceLimitBody
import com.example.cashbookbd.data.remote.dto.LoginRequest
import com.example.cashbookbd.data.remote.dto.ReleaseDeviceRequest
import com.example.cashbookbd.data.remote.dto.RevokeDeviceResponse
import com.example.cashbookbd.session.SessionManager
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/** One active device shown on the login "device limit reached" panel. */
data class LoginDevice(
    val id: Long,
    val name: String,
    val ip: String?,
    val lastUsed: String?,
)

/** Login was blocked because the plan's device limit is full. */
data class DeviceLimitBlock(
    val message: String,
    val deviceLimit: Int?,
    val devices: List<LoginDevice>,
)

/** The outcome of a login attempt: success, blocked on the device limit, or a plain failure. */
sealed interface LoginResult {
    data class Success(val data: LoginData) : LoginResult
    data class Blocked(val block: DeviceLimitBlock) : LoginResult
    data class Failure(val message: String) : LoginResult
}

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
    ): LoginResult =
        withContext(ioDispatcher) {
            try {
                val response = api.login(LoginRequest(login = identifier.trim(), password = password))

                if (!response.isSuccessful) {
                    // 403 with error.code 10010 means the plan's device limit is
                    // full; surface the active devices so the user can sign one out.
                    if (response.code() == HTTP_FORBIDDEN) {
                        parseDeviceLimit(response.errorBody()?.string())
                            ?.let { return@withContext LoginResult.Blocked(it) }
                    }
                    return@withContext LoginResult.Failure(
                        "Server error (${response.code()}). Please try again later."
                    )
                }

                val body = response.body()
                    ?: return@withContext LoginResult.Failure("Invalid response from server.")

                when {
                    // Backend signalled failure — surface error.message as required.
                    !body.success -> LoginResult.Failure(
                        body.error?.message ?: "Invalid username or password!"
                    )

                    // Success but no token means we can't authenticate future calls.
                    body.data?.token.isNullOrBlank() -> LoginResult.Failure(
                        "Invalid response from server."
                    )

                    else -> {
                        tokenManager.saveToken(body.data!!.token!!, rememberMe)
                        // Fresh session — drop any dashboard cached for a prior user.
                        dashboardCache.clear()
                        LoginResult.Success(body.data)
                    }
                }
            } catch (e: IOException) {
                // No connectivity, DNS failure, timeout, etc.
                LoginResult.Failure("No internet connection. Please check your network and try again.")
            } catch (e: HttpException) {
                LoginResult.Failure("Server error (${e.code()}). Please try again later.")
            } catch (e: Exception) {
                LoginResult.Failure("Something went wrong. Please try again.")
            }
        }

    /**
     * Frees a device slot from the login screen (the user has no token yet, so
     * credentials are re-verified server-side). On success the caller retries login.
     */
    suspend fun releaseDeviceAtLogin(
        identifier: String,
        password: String,
        tokenId: Long,
    ): Resource<String> = withContext(ioDispatcher) {
        try {
            val response = api.releaseDeviceAtLogin(
                ReleaseDeviceRequest(login = identifier.trim(), password = password, tokenId = tokenId)
            )
            val body = response.body()
                ?: response.errorBody()?.string()?.let {
                    runCatching { gson.fromJson(it, RevokeDeviceResponse::class.java) }.getOrNull()
                }
            if (response.isSuccessful && body?.success == true) {
                Resource.Success(body.message?.takeIf { it.isNotBlank() } ?: "Device signed out.")
            } else {
                Resource.Error(body?.error?.message ?: "Couldn't sign that device out. Please try again.")
            }
        } catch (e: IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /** Parses the 403 device-limit body into a [DeviceLimitBlock], or null if it isn't one. */
    private fun parseDeviceLimit(raw: String?): DeviceLimitBlock? {
        val body = raw?.takeIf { it.isNotBlank() }
            ?.let { runCatching { gson.fromJson(it, LoginDeviceLimitBody::class.java) }.getOrNull() }
            ?: return null
        if (body.error?.code != DEVICE_LIMIT_CODE) return null
        val devices = body.data?.devices.orEmpty().mapNotNull { dto ->
            val id = dto.id ?: return@mapNotNull null
            LoginDevice(
                id = id,
                name = dto.name?.takeIf { it.isNotBlank() } ?: "Unknown device",
                ip = dto.ip,
                lastUsed = dto.lastUsedAt,
            )
        }
        return DeviceLimitBlock(
            message = body.error.message ?: "Device limit reached for your plan.",
            deviceLimit = body.data?.deviceLimit,
            devices = devices,
        )
    }

    private val gson = Gson()

    private companion object {
        private const val HTTP_FORBIDDEN = 403

        /** Server error code for "plan device limit reached". */
        private const val DEVICE_LIMIT_CODE = 10010

        // Process-wide latch so the remember-me policy is applied only at cold
        // start, not on every Activity re-creation.
        @Volatile
        private var policyApplied = false
    }
}
