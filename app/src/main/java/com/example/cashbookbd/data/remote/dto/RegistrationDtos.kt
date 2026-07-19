package com.example.cashbookbd.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Registration request/response DTOs for the public `register/request-otp` and
 * `register/verify-otp` endpoints. Field names match the Laravel controller's
 * `validate()` keys exactly, so the JSON keys are the wire contract.
 *
 * The flow is two calls: request-otp parks the whole form server-side and texts
 * a 6-digit code, returning an `otp_session_id`; verify-otp checks the code and,
 * on success, creates the company/user and returns an auth token — so the app
 * logs straight in, exactly as the web does.
 */

/**
 * Body of `POST register/request-otp`. `password_confirmation` is required by
 * Laravel's `confirmed` rule even though it isn't listed in `validate()`.
 * [email] and [contactPerson] are optional server-side; sent as-is (may be blank).
 */
data class RegisterOtpRequest(
    @SerializedName("company_name") val companyName: String,
    @SerializedName("user_name") val userName: String,
    @SerializedName("contact_person") val contactPerson: String,
    @SerializedName("mobile") val mobile: String,
    @SerializedName("email") val email: String,
    @SerializedName("address") val address: String,
    @SerializedName("password") val password: String,
    @SerializedName("password_confirmation") val passwordConfirmation: String,
)

data class RequestOtpResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: RequestOtpData? = null,
    @SerializedName("error") val error: ApiError? = null,
)

data class RequestOtpData(
    @SerializedName("otp_session_id") val otpSessionId: String? = null,
    @SerializedName("expires_in_seconds") val expiresInSeconds: Int? = null,
    @SerializedName("resend_after_seconds") val resendAfterSeconds: Int? = null,
)

/** Body of `POST register/verify-otp`. */
data class VerifyOtpRequest(
    @SerializedName("otp_session_id") val otpSessionId: String,
    @SerializedName("mobile") val mobile: String,
    @SerializedName("otp") val otp: String,
)

data class VerifyOtpResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: VerifyOtpData? = null,
    @SerializedName("error") val error: ApiError? = null,
)

data class VerifyOtpData(
    @SerializedName("user") val user: UserDto? = null,
    @SerializedName("token") val token: String? = null,
    @SerializedName("company_id") val companyId: Long? = null,
    @SerializedName("branch_id") val branchId: Long? = null,
)
