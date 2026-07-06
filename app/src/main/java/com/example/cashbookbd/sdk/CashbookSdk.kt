package com.example.cashbookbd.sdk

import android.content.Context
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.local.TokenManager
import com.example.cashbookbd.data.remote.dto.LoginData
import com.example.cashbookbd.data.repository.AuthRepository
import com.example.cashbookbd.data.repository.DashboardRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.dashboard.model.Dashboard
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.CashBookReport

/**
 * Single entry point to the Cashbook backend.
 *
 * `CashbookSdk` hides the repository/Retrofit wiring behind one object: obtain
 * an instance with [CashbookSdk.from], then call [login], [dashboard],
 * [branches] and [cashbook]. Every call returns a [Resource] (never throws), and
 * the auth token is persisted encrypted by [TokenManager] and attached to
 * authenticated requests automatically — so once [login] succeeds the other
 * calls just work.
 *
 * The backend host is taken from `BuildConfig.BASE_URL` (see app/build.gradle.kts);
 * there is nothing to configure at call sites.
 *
 * Usage:
 * ```
 * val sdk = CashbookSdk.from(context)
 *
 * when (val res = sdk.login("017xxxxxxxx", "secret")) {
 *     is Resource.Success -> { /* logged in; token stored */ }
 *     is Resource.Error   -> showError(res.message)
 *     Resource.Loading    -> Unit
 * }
 *
 * val dash = sdk.dashboard()          // Resource<Dashboard>
 * val opts = sdk.branches()           // Resource<List<BranchOption>>
 * val book = sdk.cashbook(            // Resource<CashBookReport>
 *     branchId = 3,
 *     startDate = "2026-07-01",
 *     endDate = "2026-07-06",
 * )
 * ```
 *
 * All suspend functions are main-safe (the repositories switch to `Dispatchers.IO`
 * internally), so they may be called from a coroutine on any dispatcher.
 */
class CashbookSdk internal constructor(
    private val authRepository: AuthRepository,
    private val dashboardRepository: DashboardRepository,
    private val reportRepository: ReportRepository,
    private val tokenManager: TokenManager,
) {

    // ---- Session -----------------------------------------------------------

    /** True when a stored auth token exists (does not verify it with the server). */
    fun isLoggedIn(): Boolean = tokenManager.isLoggedIn()

    /** The current auth token, or null when logged out. */
    fun currentToken(): String? = tokenManager.getToken()

    /** Clears the stored token. Subsequent authenticated calls will 401. */
    fun logout() = tokenManager.clear()

    // ---- API ---------------------------------------------------------------

    /**
     * `POST /login`. On success the token is stored and returned inside [LoginData];
     * on failure the [Resource.Error.message] is a user-facing string.
     *
     * @param identifier email or phone — the backend resolves either.
     */
    suspend fun login(identifier: String, password: String): Resource<LoginData> =
        authRepository.login(identifier, password)

    /**
     * `GET /dashboard/data`. A 401 comes back as
     * [Resource.Error] with `isUnauthorized = true` so the caller can force re-login.
     */
    suspend fun dashboard(): Resource<Dashboard> =
        dashboardRepository.getDashboard()

    /** `GET /branch/ddl/protected-branch` — branch options for the report filter. */
    suspend fun branches(): Resource<List<BranchOption>> =
        when (val res = reportRepository.getBranches()) {
            is Resource.Success -> Resource.Success(res.data.branches)
            is Resource.Error -> res
            Resource.Loading -> Resource.Loading
        }

    /**
     * `GET /reports/cashbook`. Dates must be `yyyy-MM-dd`. An empty result set
     * comes back as [Resource.Success] with an empty report (not an error).
     */
    suspend fun cashbook(
        branchId: Long,
        startDate: String,
        endDate: String,
    ): Resource<CashBookReport> =
        reportRepository.getCashBook(branchId, startDate, endDate)

    companion object {
        /**
         * Builds an SDK backed by the app's shared singletons (see [ServiceLocator]),
         * so it reuses the same encrypted token store and Retrofit stack as the rest
         * of the app. Safe to call repeatedly; the heavy objects are created once.
         */
        fun from(context: Context): CashbookSdk {
            val appContext = context.applicationContext
            return CashbookSdk(
                authRepository = ServiceLocator.provideAuthRepository(appContext),
                dashboardRepository = ServiceLocator.provideDashboardRepository(appContext),
                reportRepository = ServiceLocator.provideReportRepository(appContext),
                tokenManager = ServiceLocator.provideTokenManager(appContext),
            )
        }
    }
}
