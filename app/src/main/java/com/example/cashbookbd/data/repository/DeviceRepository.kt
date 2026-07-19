package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ApiService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/** One active session, as shown on the My Devices screen. */
data class UserDevice(
    val id: Long,
    val name: String,
    val ip: String?,
    val lastUsedAt: String?,
    val createdAt: String?,
    /** This phone. Never offered for sign-out — that would revoke our own token. */
    val isCurrent: Boolean,
)

/** The device list plus the plan's allowance, so the UI can show "2 of 3 used". */
data class UserDevices(
    val deviceLimit: Int?,
    val devices: List<UserDevice>,
)

/**
 * Reads and revokes the user's active devices (`GET`/`DELETE /devices`), the
 * per-plan device limit enforced at login. Maps every outcome to a [Resource]
 * and flags a 401 so callers can force re-login.
 */
class DeviceRepository(
    private val api: ApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
    }

    suspend fun getDevices(): Resource<UserDevices> = withContext(ioDispatcher) {
        try {
            val response = api.getDevices()

            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(
                    "Your session has expired. Please log in again.",
                    isUnauthorized = true,
                )
            }

            val body = response.body()
                ?: return@withContext Resource.Error("Invalid response from server.")

            if (!body.success) {
                return@withContext Resource.Error(
                    body.error?.message ?: body.message?.ifBlank { null } ?: "Couldn't load devices."
                )
            }

            val devices = body.data?.devices.orEmpty().mapNotNull { dto ->
                // Without an id the row cannot be revoked, so it is not shown.
                dto.id?.let {
                    UserDevice(
                        id = it,
                        name = dto.name?.takeIf(String::isNotBlank) ?: "Unknown device",
                        ip = dto.ip?.takeIf(String::isNotBlank),
                        lastUsedAt = dto.lastUsedAt?.takeIf(String::isNotBlank),
                        createdAt = dto.createdAt?.takeIf(String::isNotBlank),
                        isCurrent = dto.isCurrent,
                    )
                }
            }

            Resource.Success(
                UserDevices(deviceLimit = body.data?.deviceLimit, devices = devices)
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

    /**
     * Signs [deviceId] out. Safe to repeat: revoking an already-revoked token
     * returns 404, which is reported as a plain message rather than treated as an
     * ambiguous write, since the end state is the same either way.
     */
    suspend fun revokeDevice(deviceId: Long): Resource<String> = withContext(ioDispatcher) {
        try {
            val response = api.revokeDevice(deviceId)

            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(
                    "Your session has expired. Please log in again.",
                    isUnauthorized = true,
                )
            }

            // A failure here is a real HTTP 404, so the body arrives via
            // errorBody() and response.body() is null — don't read success off it.
            val body = response.body()
            if (body?.success == true) {
                Resource.Success(body.message?.ifBlank { null } ?: "Device signed out.")
            } else {
                Resource.Error(
                    if (response.code() == 404) "That device is no longer signed in."
                    else "Couldn't sign that device out. Please try again."
                )
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
