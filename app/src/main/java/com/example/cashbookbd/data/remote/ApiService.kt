package com.example.cashbookbd.data.remote

import com.example.cashbookbd.data.remote.dto.BranchListResponse
import com.example.cashbookbd.data.remote.dto.CashBookResponse
import com.example.cashbookbd.data.remote.dto.DashboardResponse
import com.example.cashbookbd.data.remote.dto.LoginRequest
import com.example.cashbookbd.data.remote.dto.LoginResponse
import com.example.cashbookbd.data.remote.dto.ReceiveRequest
import com.example.cashbookbd.data.remote.dto.ReceiveResponse
import com.example.cashbookbd.data.remote.dto.SettingsResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
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
     * GET {BASE_URL}/dashboard/data
     *
     * Requires `Authorization: Bearer <token>` (added by AuthInterceptor).
     * Returns [Response] so the repository can detect a 401 and force re-login.
     */
    @GET("dashboard/data")
    suspend fun getDashboard(): Response<DashboardResponse>

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
}
