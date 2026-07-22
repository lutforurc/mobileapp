package com.example.cashbookbd.data.remote

import com.example.cashbookbd.data.remote.dto.BranchListResponse
import com.example.cashbookbd.data.remote.dto.CashBookResponse
import com.example.cashbookbd.data.remote.dto.DashboardResponse
import com.example.cashbookbd.data.remote.dto.DevicesResponse
import com.example.cashbookbd.data.remote.dto.HighlightRuleWriteRequest
import com.example.cashbookbd.data.remote.dto.HighlightRuleWriteResponse
import com.example.cashbookbd.data.remote.dto.HighlightRulesResponse
import com.example.cashbookbd.data.remote.dto.MonthlyTopProductsResponse
import com.example.cashbookbd.data.remote.dto.LoginRequest
import com.example.cashbookbd.data.remote.dto.LoginResponse
import com.example.cashbookbd.data.remote.dto.ReceiveRequest
import com.example.cashbookbd.data.remote.dto.ReceiveResponse
import com.example.cashbookbd.data.remote.dto.RegisterOtpRequest
import com.example.cashbookbd.data.remote.dto.RequestOtpResponse
import com.example.cashbookbd.data.remote.dto.RevokeDeviceResponse
import com.example.cashbookbd.data.remote.dto.SettingsResponse
import com.example.cashbookbd.data.remote.dto.VerifyOtpRequest
import com.example.cashbookbd.data.remote.dto.VerifyOtpResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    /**
     * POST {BASE_URL}/login
     *
     * Returns [Response] so the repository can inspect the HTTP status code
     * (server errors, etc.) in addition to the parsed body.
     */
    @POST("login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    /**
     * POST {BASE_URL}/register/request-otp — public company sign-up, step 1.
     *
     * Parks the whole form server-side and texts a 6-digit code, returning an
     * `otp_session_id` to carry into [verifyRegistrationOtp]. Public (no token).
     * Rate-limited server-side (6/min); a 422 carries validation errors.
     */
    @POST("register/request-otp")
    suspend fun requestRegistrationOtp(@Body request: RegisterOtpRequest): Response<RequestOtpResponse>

    /**
     * POST {BASE_URL}/register/verify-otp — public company sign-up, step 2.
     *
     * Checks the code and, on success, creates the company + user and returns an
     * auth token (HTTP 201) — so the app saves it and enters like a fresh login.
     */
    @POST("register/verify-otp")
    suspend fun verifyRegistrationOtp(@Body request: VerifyOtpRequest): Response<VerifyOtpResponse>

    /**
     * GET {BASE_URL}/dashboard/data
     *
     * Requires `Authorization: Bearer <token>` (added by AuthInterceptor).
     * Returns [Response] so the repository can detect a 401 and force re-login.
     */
    @GET("dashboard/data")
    suspend fun getDashboard(): Response<DashboardResponse>

    /**
     * GET {BASE_URL}/dashboard/branch/monthly-purchase-sales
     *
     * Source of the Top Sales / Top Purchase lists on the non-construction
     * dashboards (the web's ComputerAccessories variant).
     */
    @GET("dashboard/branch/monthly-purchase-sales")
    suspend fun getMonthlyTopProducts(): Response<MonthlyTopProductsResponse>

    /**
     * GET {BASE_URL}/branch/ddl/protected-branch — branches for the report filter,
     * scoped to what the logged-in user may see.
     */
    @GET("branch/ddl/protected-branch")
    suspend fun getBranches(): Response<BranchListResponse>

    /**
     * POST {BASE_URL}/settings/get-settings — the current user's app settings,
     * including the permission list that drives client-side gating. Requires
     * `Authorization: Bearer <token>`; a 401 means the token expired.
     *
     * The backend expects a JSON body; an empty object (`{}`) is sufficient.
     */
    @POST("settings/get-settings")
    suspend fun getSettings(@Body body: Map<String, String>): Response<SettingsResponse>

    /**
     * GET {BASE_URL}/reports/cashbook?branch_id=&start_date=&end_date=
     *
     * Dates must be `yyyy-MM-dd`. Requires `Authorization: Bearer <token>`.
     */
    @GET("reports/cashbook")
    suspend fun getCashBook(
        @Query("branch_id") branchId: Long,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
    ): Response<CashBookResponse>

    /**
     * GET {BASE_URL}/highlight-rules/active — the company's active "phrase →
     * coloured border" rules, sorted priority DESC, id ASC. Company scoping is
     * done server-side from the auth token.
     */
    @GET("highlight-rules/active")
    suspend fun getActiveHighlightRules(): Response<HighlightRulesResponse>

    /** GET {BASE_URL}/admin/highlight-rules — every rule (any status), for the admin screen. */
    @GET("admin/highlight-rules")
    suspend fun getHighlightRules(): Response<HighlightRulesResponse>

    /** POST {BASE_URL}/admin/highlight-rules — create a rule (HTTP 201; a 422 carries the validation message). */
    @POST("admin/highlight-rules")
    suspend fun createHighlightRule(@Body body: HighlightRuleWriteRequest): Response<HighlightRuleWriteResponse>

    /** PUT {BASE_URL}/admin/highlight-rules/{id} — update one of the company's rules. */
    @PUT("admin/highlight-rules/{id}")
    suspend fun updateHighlightRule(
        @Path("id") id: Long,
        @Body body: HighlightRuleWriteRequest,
    ): Response<HighlightRuleWriteResponse>

    /** DELETE {BASE_URL}/admin/highlight-rules/{id} — remove one of the company's rules. */
    @DELETE("admin/highlight-rules/{id}")
    suspend fun deleteHighlightRule(@Path("id") id: Long): Response<HighlightRuleWriteResponse>

    /**
     * POST {BASE_URL}/accounts/payment/specific-item — confirms ("receives") one
     * head-office remittance, creating an accounting voucher server-side.
     *
     * ⚠️ NON-IDEMPOTENT with no server-side duplicate guard: posting the same
     * `mtm_id` twice debits cash twice, silently. NEVER auto-retry this call
     * (no OkHttp retry, no backoff, no WorkManager). Success is the body's
     * `success` flag — a failure returns HTTP 201.
     */
    @POST("accounts/payment/specific-item")
    suspend fun receiveSpecificItem(@Body request: ReceiveRequest): Response<ReceiveResponse>

    /** GET {BASE_URL}/devices — the user's active sessions and their plan's device limit. */
    @GET("devices")
    suspend fun getDevices(): Response<DevicesResponse>

    /**
     * DELETE {BASE_URL}/devices/{tokenId} — signs one device out, freeing a slot
     * against the plan's device limit. Returns 404 when the token is already gone.
     */
    @DELETE("devices/{tokenId}")
    suspend fun revokeDevice(@Path("tokenId") tokenId: Long): Response<RevokeDeviceResponse>
}
