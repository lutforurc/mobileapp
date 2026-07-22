package com.example.cashbookbd.data.remote

import com.example.cashbookbd.data.remote.dto.BranchListResponse
import com.example.cashbookbd.data.remote.dto.BankBookResponse
import com.example.cashbookbd.data.remote.dto.CashBookResponse
import com.example.cashbookbd.data.remote.dto.DashboardResponse
import com.example.cashbookbd.data.remote.dto.DevicesResponse
import com.example.cashbookbd.data.remote.dto.MonthlyTopProductsResponse
import com.example.cashbookbd.data.remote.dto.LoginRequest
import com.example.cashbookbd.data.remote.dto.LoginResponse
import com.example.cashbookbd.data.remote.dto.NotificationDismissResponse
import com.example.cashbookbd.data.remote.dto.NotificationSummaryResponse
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
     * GET {BASE_URL}/reports/bankbook?branch_id=&start_date=&end_date=
     *
     * The bank-side twin of the cash book. Dates must be `yyyy-MM-dd` — note
     * the rows come back with `vr_date` as `dd/MM/yyyy`.
     */
    @GET("reports/bankbook")
    suspend fun getBankBook(
        @Query("branch_id") branchId: Long,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
    ): Response<BankBookResponse>

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

    /**
     * GET {BASE_URL}/notifications/summary — the notification center's items.
     *
     * No params: the backend defaults branch/date to the authenticated user's,
     * which is what the app wants. Returns derived business alerts (low stock,
     * due installments, …) and, once merged server-side, admin broadcasts too.
     */
    @GET("notifications/summary")
    suspend fun getNotificationSummary(): Response<NotificationSummaryResponse>

    /**
     * POST {BASE_URL}/notifications/dismiss — marks one notification read/dismissed
     * for the current user. Body: `notification_key` (required), `notification_id`.
     * branch_id is omitted so the backend uses the user's branch, matching summary.
     */
    @POST("notifications/dismiss")
    suspend fun dismissNotification(@Body body: Map<String, String>): Response<NotificationDismissResponse>

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
