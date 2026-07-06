package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.local.DashboardCache
import com.example.cashbookbd.data.remote.ApiService
import com.example.cashbookbd.ui.dashboard.model.Dashboard
import com.example.cashbookbd.ui.dashboard.model.toDashboard
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/**
 * Fetches the dashboard payload and maps every outcome to a [Resource] so the
 * ViewModel never touches Retrofit types. A 401 is flagged via
 * [Resource.Error.isUnauthorized] so the UI can force re-login.
 */
class DashboardRepository(
    private val api: ApiService,
    private val cache: DashboardCache,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
    }

    /**
     * The last cached dashboard, or null if nothing has been stored yet. Lets the
     * UI show content instantly on open while [getDashboard] refreshes it.
     */
    suspend fun getCachedDashboard(): Dashboard? = withContext(ioDispatcher) {
        cache.load()?.toDashboard()
    }

    suspend fun getDashboard(): Resource<Dashboard> = withContext(ioDispatcher) {
        try {
            val response = api.getDashboard()

            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(
                    "Your session has expired. Please log in again.",
                    isUnauthorized = true,
                )
            }

            if (!response.isSuccessful) {
                return@withContext Resource.Error(
                    "Server error (${response.code()}). Please try again later."
                )
            }

            val body = response.body()
                ?: return@withContext Resource.Error("Invalid response from server.")

            when {
                !body.success -> Resource.Error(body.message?.ifBlank { null } ?: "Couldn't load dashboard.")

                body.data?.payload == null -> Resource.Error("No dashboard data available.")

                else -> {
                    // Store the full payload so the next open can render offline.
                    cache.save(body.data.payload)
                    Resource.Success(body.data.payload.toDashboard())
                }
            }
        } catch (e: IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: HttpException) {
            if (e.code() == HTTP_UNAUTHORIZED) {
                Resource.Error("Your session has expired. Please log in again.", isUnauthorized = true)
            } else {
                Resource.Error("Server error (${e.code()}). Please try again later.")
            }
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }
}
