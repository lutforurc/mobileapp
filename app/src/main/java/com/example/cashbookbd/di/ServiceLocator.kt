package com.example.cashbookbd.di

import android.content.Context
import com.example.cashbookbd.data.local.DashboardCache
import com.example.cashbookbd.data.local.TokenManager
import com.example.cashbookbd.data.remote.ApiService
import com.example.cashbookbd.data.remote.LedgerApiService
import com.example.cashbookbd.data.remote.NetworkModule
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.data.repository.AuthRepository
import com.example.cashbookbd.data.repository.BalanceSheetRepository
import com.example.cashbookbd.data.repository.DashboardRepository
import com.example.cashbookbd.data.repository.DueListRepository
import com.example.cashbookbd.data.repository.GenericReportRepository
import com.example.cashbookbd.data.repository.LedgerRepository
import com.example.cashbookbd.data.repository.ProfitLossRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.data.repository.SessionRepository
import com.example.cashbookbd.data.repository.SettingsRepository
import com.example.cashbookbd.data.repository.TrialBalanceRepository
import com.example.cashbookbd.session.SessionManager
import com.example.cashbookbd.ui.theme.ThemeManager
import retrofit2.Retrofit

/**
 * Minimal manual dependency provider. Keeps construction in one place without
 * pulling in a full DI framework; swap for Hilt if the app grows.
 *
 * All heavy singletons ([TokenManager], [ApiService]) are created once and
 * shared. The [ApiService] is wired to read the auth token from [TokenManager]
 * on every request, so authenticated calls work immediately after login.
 */
object ServiceLocator {

    @Volatile
    private var tokenManager: TokenManager? = null

    @Volatile
    private var retrofit: Retrofit? = null

    @Volatile
    private var apiService: ApiService? = null

    @Volatile
    private var ledgerApiService: LedgerApiService? = null

    @Volatile
    private var reportApiService: ReportApiService? = null

    @Volatile
    private var genericReportRepository: GenericReportRepository? = null

    @Volatile
    private var trialBalanceRepository: TrialBalanceRepository? = null

    @Volatile
    private var profitLossRepository: ProfitLossRepository? = null

    @Volatile
    private var balanceSheetRepository: BalanceSheetRepository? = null

    @Volatile
    private var dueListRepository: DueListRepository? = null

    @Volatile
    private var ledgerRepository: LedgerRepository? = null

    @Volatile
    private var authRepository: AuthRepository? = null

    @Volatile
    private var dashboardRepository: DashboardRepository? = null

    @Volatile
    private var reportRepository: ReportRepository? = null

    @Volatile
    private var dashboardCache: DashboardCache? = null

    @Volatile
    private var sessionManager: SessionManager? = null

    @Volatile
    private var themeManager: ThemeManager? = null

    @Volatile
    private var settingsRepository: SettingsRepository? = null

    @Volatile
    private var sessionRepository: SessionRepository? = null

    fun provideTokenManager(context: Context): TokenManager =
        tokenManager ?: synchronized(this) {
            tokenManager ?: TokenManager(context.applicationContext).also { tokenManager = it }
        }

    /** Shared, app-wide holder of the current user's settings and permissions. */
    fun provideSessionManager(context: Context): SessionManager =
        sessionManager ?: synchronized(this) {
            sessionManager ?: SessionManager().also { sessionManager = it }
        }

    /** Shared, app-wide holder of the user's light/dark theme preference. */
    fun provideThemeManager(context: Context): ThemeManager =
        themeManager ?: synchronized(this) {
            themeManager ?: ThemeManager(context.applicationContext).also { themeManager = it }
        }

    private fun provideDashboardCache(context: Context): DashboardCache =
        dashboardCache ?: synchronized(this) {
            dashboardCache ?: DashboardCache(context.applicationContext).also { dashboardCache = it }
        }

    private fun provideRetrofit(context: Context): Retrofit =
        retrofit ?: synchronized(this) {
            retrofit ?: run {
                val tokens = provideTokenManager(context)
                NetworkModule.retrofit(tokenProvider = tokens::getToken)
            }.also { retrofit = it }
        }

    private fun provideApiService(context: Context): ApiService =
        apiService ?: synchronized(this) {
            apiService ?: provideRetrofit(context).create(ApiService::class.java)
                .also { apiService = it }
        }

    private fun provideLedgerApiService(context: Context): LedgerApiService =
        ledgerApiService ?: synchronized(this) {
            ledgerApiService ?: provideRetrofit(context).create(LedgerApiService::class.java)
                .also { ledgerApiService = it }
        }

    private fun provideReportApiService(context: Context): ReportApiService =
        reportApiService ?: synchronized(this) {
            reportApiService ?: provideRetrofit(context).create(ReportApiService::class.java)
                .also { reportApiService = it }
        }

    fun provideGenericReportRepository(context: Context): GenericReportRepository =
        genericReportRepository ?: synchronized(this) {
            genericReportRepository ?: GenericReportRepository(
                api = provideReportApiService(context),
            ).also { genericReportRepository = it }
        }

    fun provideTrialBalanceRepository(context: Context): TrialBalanceRepository =
        trialBalanceRepository ?: synchronized(this) {
            trialBalanceRepository ?: TrialBalanceRepository(
                api = provideReportApiService(context),
            ).also { trialBalanceRepository = it }
        }

    fun provideProfitLossRepository(context: Context): ProfitLossRepository =
        profitLossRepository ?: synchronized(this) {
            profitLossRepository ?: ProfitLossRepository(
                api = provideReportApiService(context),
            ).also { profitLossRepository = it }
        }

    fun provideBalanceSheetRepository(context: Context): BalanceSheetRepository =
        balanceSheetRepository ?: synchronized(this) {
            balanceSheetRepository ?: BalanceSheetRepository(
                api = provideReportApiService(context),
            ).also { balanceSheetRepository = it }
        }

    fun provideDueListRepository(context: Context): DueListRepository =
        dueListRepository ?: synchronized(this) {
            dueListRepository ?: DueListRepository(
                api = provideReportApiService(context),
            ).also { dueListRepository = it }
        }

    fun provideAuthRepository(context: Context): AuthRepository =
        authRepository ?: synchronized(this) {
            authRepository ?: AuthRepository(
                api = provideApiService(context),
                tokenManager = provideTokenManager(context),
                dashboardCache = provideDashboardCache(context),
                sessionManager = provideSessionManager(context),
            ).also { authRepository = it }
        }

    fun provideSettingsRepository(context: Context): SettingsRepository =
        settingsRepository ?: synchronized(this) {
            settingsRepository ?: SettingsRepository(
                api = provideApiService(context),
            ).also { settingsRepository = it }
        }

    fun provideSessionRepository(context: Context): SessionRepository =
        sessionRepository ?: synchronized(this) {
            sessionRepository ?: SessionRepository(
                settingsRepository = provideSettingsRepository(context),
                sessionManager = provideSessionManager(context),
            ).also { sessionRepository = it }
        }

    fun provideDashboardRepository(context: Context): DashboardRepository =
        dashboardRepository ?: synchronized(this) {
            dashboardRepository ?: DashboardRepository(
                api = provideApiService(context),
                cache = provideDashboardCache(context),
            ).also { dashboardRepository = it }
        }

    fun provideReportRepository(context: Context): ReportRepository =
        reportRepository ?: synchronized(this) {
            reportRepository ?: ReportRepository(
                api = provideApiService(context),
            ).also { reportRepository = it }
        }

    fun provideLedgerRepository(context: Context): LedgerRepository =
        ledgerRepository ?: synchronized(this) {
            ledgerRepository ?: LedgerRepository(
                api = provideLedgerApiService(context),
            ).also { ledgerRepository = it }
        }
}
