package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ApiService
import com.example.cashbookbd.session.Settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/**
 * Loads the current user's app settings (and, crucially, their permission list)
 * from `POST /settings/get-settings`. Maps every outcome to a [Resource] and
 * flags a 401 via [Resource.Error.isUnauthorized] so callers can force re-login.
 */
class SettingsRepository(
    private val api: ApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
    }

    suspend fun getSettings(): Resource<Settings> = withContext(ioDispatcher) {
        try {
            val response = api.getSettings(emptyMap())

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
            if (!body.success) {
                return@withContext Resource.Error(
                    body.error?.message ?: body.message?.ifBlank { null } ?: "Settings load failed."
                )
            }

            val payload = body.data?.payload
            val permissions = payload?.permissions
                .orEmpty()
                .mapNotNull { it.toPermission() }
            Resource.Success(
                Settings(
                    permissions = permissions,
                    businessTypeId = payload?.branch?.businessTypeId,
                    inventorySystemId = payload?.branch?.inventorySystemId,
                    branchId = payload?.branch?.id,
                    branchTypesId = payload?.branch?.branchTypesId,
                    userName = payload?.user?.name?.takeIf { it.isNotBlank() },
                    userEmail = payload?.user?.email?.takeIf { it.isNotBlank() },
                    userPhotoUrl = payload?.user?.profilePhoto?.takeIf { it.isNotBlank() },
                    transactionDate = payload?.trxDt?.takeIf { it.isNotBlank() },
                    decimalPlaces = payload?.branch?.decimalPlaces?.trim()?.toIntOrNull(),
                )
            )
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