package com.example.cashbookbd.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Mirrors the Laravel login response.
 *
 * Success:
 * {
 *   "success": true,
 *   "message": "Successfully logged in!",
 *   "data": { "user": {...}, "token": "...", "company": 1, "branch": "Head Office" }
 * }
 *
 * Failure (note: HTTP status is still 200, so we must read `success`):
 * {
 *   "success": false,
 *   "error": { "code": 10001, "message": "Invalid username or password!" }
 * }
 */
data class LoginResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: LoginData? = null,
    @SerializedName("error") val error: ApiError? = null,
)

data class LoginData(
    @SerializedName("user") val user: UserDto? = null,
    @SerializedName("token") val token: String? = null,
    @SerializedName("company") val company: Long? = null,
    @SerializedName("branch") val branch: String? = null,
)

data class UserDto(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("branch_id") val branchId: Long? = null,
    @SerializedName("company_id") val companyId: Long? = null,
)

data class ApiError(
    @SerializedName("code") val code: Int? = null,
    @SerializedName("message") val message: String? = null,
)
