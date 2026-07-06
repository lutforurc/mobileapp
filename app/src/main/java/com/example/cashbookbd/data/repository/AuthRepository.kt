package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.local.DashboardCache
import com.example.cashbookbd.data.local.TokenManager
import com.example.cashbookbd.data.remote.ApiService
import com.example.cashbookbd.data.remote.dto.LoginData
import com.example.cashbookbd.data.remote.dto.LoginRequest
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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    fun isLoggedIn(): Boolean = tokenManager.isLoggedIn()

    fun logout() {
        tokenManager.clear()
        // Don't let the next user (or the login screen) see the previous session's data.
        dashboardCache.clear()
    }

    suspend fun login(identifier: String, password: String): Resource<LoginData> =
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
                        tokenManager.saveToken(body.data!!.token!!)
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
}
