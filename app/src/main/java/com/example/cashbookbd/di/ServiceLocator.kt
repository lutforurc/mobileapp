package com.example.cashbookbd.di

import android.content.Context
import com.example.cashbookbd.data.local.DashboardCache
import com.example.cashbookbd.data.local.TokenManager
import com.example.cashbookbd.data.remote.ApiService
import com.example.cashbookbd.data.remote.LedgerApiService
import com.example.cashbookbd.data.remote.NetworkModule
import com.example.cashbookbd.data.repository.AuthRepository
import com.example.cashbookbd.data.repository.DashboardRepository
import com.example.cashbookbd.data.repository.LedgerRepository
import com.example.cashbookbd.data.repository.ReportRepository
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
    private var ledgerRepository: LedgerRepository? = null

    @Volatile
    private var authRepository: AuthRepository? = null

    @Volatile
    private var dashboardRepository: DashboardRepository? = null

    @Volatile
    private var reportRepository: ReportRepository? = null

    @Volatile
    private var dashboardCache: DashboardCache? = null

    fun provideTokenManager(context: Context): TokenManager =
        tokenManager ?: synchronized(this) {
            tokenManager ?: TokenManager(context.applicationContext).also { tokenManager = it }
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

    fun provideAuthRepository(context: Context): AuthRepository =
        authRepository ?: synchronized(this) {
            authRepository ?: AuthRepository(
                api = provideApiService(context),
                tokenManager = provideTokenManager(context),
                dashboardCache = provideDashboardCache(context),
            ).also { authRepository = it }
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
