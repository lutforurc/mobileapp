package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.local.DashboardCache
import com.example.cashbookbd.data.local.TokenManager
import com.example.cashbookbd.data.remote.ApiService
import com.example.cashbookbd.data.remote.dto.RegisterOtpRequest
import com.example.cashbookbd.data.remote.dto.VerifyOtpRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * Public company sign-up. Talks to the request-otp / verify-otp endpoints and
 * maps every outcome to a [Resource], the same way [AuthRepository] does, so the
 * ViewModel never sees Retrofit types.
 *
 * verify-otp is the final registration call: on success it returns an auth token,
 * which this saves via [TokenManager] exactly as login does — the caller then
 * refreshes the session and enters the app.
 */
class RegistrationRepository(
    private val api: ApiService,
    private val tokenManager: TokenManager,
    private val dashboardCache: DashboardCache,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /** Step 1: request an OTP. Returns the `otp_session_id` to carry into [verifyOtp]. */
    suspend fun requestOtp(request: RegisterOtpRequest): Resource<String> =
        withContext(ioDispatcher) {
            call {
                val response = api.requestRegistrationOtp(request)
                val body = response.body() ?: return@call Resource.Error(serverError(response))
                when {
                    !body.success -> Resource.Error(reason(response, body.error?.message, body.message))
                    body.data?.otpSessionId.isNullOrBlank() ->
                        Resource.Error("Couldn't start verification. Please try again.")
                    else -> Resource.Success(body.data!!.otpSessionId!!)
                }
            }
        }

    /**
     * Step 2: verify the OTP. On success the account is created and a token
     * returned; this saves it (registration is always "remembered") and clears
     * any stale dashboard cache, mirroring a fresh login.
     */
    suspend fun verifyOtp(otpSessionId: String, mobile: String, otp: String): Resource<Unit> =
        withContext(ioDispatcher) {
            call {
                val response = api.verifyRegistrationOtp(
                    VerifyOtpRequest(otpSessionId = otpSessionId, mobile = mobile, otp = otp.trim()),
                )
                val body = response.body() ?: return@call Resource.Error(serverError(response))
                when {
                    !body.success -> Resource.Error(reason(response, body.error?.message, body.message))
                    body.data?.token.isNullOrBlank() ->
                        Resource.Error("Registration succeeded but sign-in failed. Please log in.")
                    else -> {
                        tokenManager.saveToken(body.data!!.token!!, rememberMe = true)
                        dashboardCache.clear()
                        Resource.Success(Unit)
                    }
                }
            }
        }

    /**
     * The reason to show: a validation 422 carries it in the body's `error.message`
     * or top-level `message`; otherwise fall back to the HTTP code.
     */
    private fun reason(response: Response<*>, errorMessage: String?, message: String?): String =
        errorMessage?.takeIf { it.isNotBlank() }
            ?: message?.takeIf { it.isNotBlank() }
            ?: serverError(response)

    private fun serverError(response: Response<*>): String =
        "Server error (${response.code()}). Please try again later."

    /** Runs [block], turning connectivity and unexpected failures into [Resource.Error]. */
    private inline fun <T> call(block: () -> Resource<T>): Resource<T> = try {
        block()
    } catch (e: IOException) {
        Resource.Error("No internet connection. Please check your network and try again.")
    } catch (e: HttpException) {
        Resource.Error("Server error (${e.code()}). Please try again later.")
    } catch (e: Exception) {
        Resource.Error("Something went wrong. Please try again.")
    }
}
