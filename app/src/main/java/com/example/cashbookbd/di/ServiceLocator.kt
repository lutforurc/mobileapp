package com.example.cashbookbd.di

import android.content.Context
import com.example.cashbookbd.data.local.DashboardCache
import com.example.cashbookbd.data.local.DeviceIdManager
import com.example.cashbookbd.data.local.TokenManager
import com.example.cashbookbd.data.remote.ApiService
import com.example.cashbookbd.data.remote.HrmApiService
import com.example.cashbookbd.data.remote.LedgerApiService
import com.example.cashbookbd.data.remote.NetworkModule
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.data.remote.TransactionApiService
import com.example.cashbookbd.data.repository.AdminRepository
import com.example.cashbookbd.data.repository.AppListRepository
import com.example.cashbookbd.data.repository.AuthRepository
import com.example.cashbookbd.data.repository.BalanceSheetRepository
import com.example.cashbookbd.data.repository.BranchRepository
import com.example.cashbookbd.data.repository.DashboardRepository
import com.example.cashbookbd.data.repository.DueListRepository
import com.example.cashbookbd.data.repository.GenericReportRepository
import com.example.cashbookbd.data.repository.HrmRepository
import com.example.cashbookbd.data.repository.InvoiceRepository
import com.example.cashbookbd.data.repository.LedgerRepository
import com.example.cashbookbd.data.repository.ProfitLossRepository
import com.example.cashbookbd.data.repository.RegistrationRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.data.repository.SelectorRepository
import com.example.cashbookbd.data.repository.SessionRepository
import com.example.cashbookbd.data.repository.SettingsRepository
import com.example.cashbookbd.data.repository.TransactionRepository
import com.example.cashbookbd.data.repository.DeviceRepository
import com.example.cashbookbd.data.repository.SubscriptionRepository
import com.example.cashbookbd.data.repository.TrialBalanceRepository
import com.example.cashbookbd.data.repository.UserRepository
import com.example.cashbookbd.data.repository.VrSettingsRepository
import com.example.cashbookbd.session.SessionManager
import com.example.cashbookbd.ui.theme.FullScreenManager
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
    private var selectorRepository: SelectorRepository? = null

    @Volatile
    private var transactionApiService: TransactionApiService? = null

    @Volatile
    private var transactionRepository: TransactionRepository? = null

    @Volatile
    private var hrmApiService: HrmApiService? = null

    @Volatile
    private var hrmRepository: HrmRepository? = null

    @Volatile
    private var invoiceRepository: InvoiceRepository? = null

    @Volatile
    private var vrSettingsRepository: VrSettingsRepository? = null

    @Volatile
    private var adminRepository: AdminRepository? = null

    @Volatile
    private var appListRepository: AppListRepository? = null

    @Volatile
    private var branchRepository: BranchRepository? = null

    @Volatile
    private var userRepository: UserRepository? = null

    @Volatile
    private var subscriptionRepository: SubscriptionRepository? = null

    private var deviceRepository: DeviceRepository? = null

    @Volatile
    private var authRepository: AuthRepository? = null

    @Volatile
    private var registrationRepository: RegistrationRepository? = null

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
    private var fullScreenManager: FullScreenManager? = null

    @Volatile
    private var settingsRepository: SettingsRepository? = null

    @Volatile
    private var sessionRepository: SessionRepository? = null

    @Volatile
    private var deviceIdManager: DeviceIdManager? = null

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

    /** Shared, app-wide holder of the user's full-screen preference. */
    fun provideFullScreenManager(context: Context): FullScreenManager =
        fullScreenManager ?: synchronized(this) {
            fullScreenManager ?: FullScreenManager(context.applicationContext)
                .also { fullScreenManager = it }
        }

    private fun provideDashboardCache(context: Context): DashboardCache =
        dashboardCache ?: synchronized(this) {
            dashboardCache ?: DashboardCache(context.applicationContext).also { dashboardCache = it }
        }

    /** Shared, app-wide identity for this install against the plan device limit. */
    fun provideDeviceIdManager(context: Context): DeviceIdManager =
        deviceIdManager ?: synchronized(this) {
            deviceIdManager ?: DeviceIdManager(context.applicationContext).also { deviceIdManager = it }
        }

    private fun provideRetrofit(context: Context): Retrofit =
        retrofit ?: synchronized(this) {
            retrofit ?: run {
                val tokens = provideTokenManager(context)
                val device = provideDeviceIdManager(context)
                NetworkModule.retrofit(
                    tokenProvider = tokens::getToken,
                    deviceIdProvider = device::getId,
                    deviceNameProvider = device::getName,
                )
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

    fun provideRegistrationRepository(context: Context): RegistrationRepository =
        registrationRepository ?: synchronized(this) {
            registrationRepository ?: RegistrationRepository(
                api = provideApiService(context),
                tokenManager = provideTokenManager(context),
                dashboardCache = provideDashboardCache(context),
            ).also { registrationRepository = it }
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

    fun provideSelectorRepository(context: Context): SelectorRepository =
        selectorRepository ?: synchronized(this) {
            selectorRepository ?: SelectorRepository(
                api = provideReportApiService(context),
            ).also { selectorRepository = it }
        }

    private fun provideTransactionApiService(context: Context): TransactionApiService =
        transactionApiService ?: synchronized(this) {
            transactionApiService ?: provideRetrofit(context).create(TransactionApiService::class.java)
                .also { transactionApiService = it }
        }

    private fun provideHrmApiService(context: Context): HrmApiService =
        hrmApiService ?: synchronized(this) {
            hrmApiService ?: provideRetrofit(context).create(HrmApiService::class.java)
                .also { hrmApiService = it }
        }

    fun provideHrmRepository(context: Context): HrmRepository =
        hrmRepository ?: synchronized(this) {
            hrmRepository ?: HrmRepository(
                api = provideHrmApiService(context),
            ).also { hrmRepository = it }
        }

    fun provideTransactionRepository(context: Context): TransactionRepository =
        transactionRepository ?: synchronized(this) {
            transactionRepository ?: TransactionRepository(
                api = provideTransactionApiService(context),
            ).also { transactionRepository = it }
        }

    fun provideInvoiceRepository(context: Context): InvoiceRepository =
        invoiceRepository ?: synchronized(this) {
            invoiceRepository ?: InvoiceRepository(
                reportApi = provideReportApiService(context),
                transactionApi = provideTransactionApiService(context),
            ).also { invoiceRepository = it }
        }

    fun provideVrSettingsRepository(context: Context): VrSettingsRepository =
        vrSettingsRepository ?: synchronized(this) {
            vrSettingsRepository ?: VrSettingsRepository(
                api = provideTransactionApiService(context),
            ).also { vrSettingsRepository = it }
        }

    fun provideAdminRepository(context: Context): AdminRepository =
        adminRepository ?: synchronized(this) {
            adminRepository ?: AdminRepository(
                api = provideTransactionApiService(context),
            ).also { adminRepository = it }
        }

    fun provideAppListRepository(context: Context): AppListRepository =
        appListRepository ?: synchronized(this) {
            appListRepository ?: AppListRepository(
                api = provideReportApiService(context),
            ).also { appListRepository = it }
        }

    fun provideBranchRepository(context: Context): BranchRepository =
        branchRepository ?: synchronized(this) {
            branchRepository ?: BranchRepository(
                api = provideReportApiService(context),
            ).also { branchRepository = it }
        }

    fun provideUserRepository(context: Context): UserRepository =
        userRepository ?: synchronized(this) {
            userRepository ?: UserRepository(
                api = provideReportApiService(context),
            ).also { userRepository = it }
        }

    fun provideSubscriptionRepository(context: Context): SubscriptionRepository =
        subscriptionRepository ?: synchronized(this) {
            subscriptionRepository ?: SubscriptionRepository(
                api = provideReportApiService(context),
            ).also { subscriptionRepository = it }
        }

    fun provideDeviceRepository(context: Context): DeviceRepository =
        deviceRepository ?: synchronized(this) {
            deviceRepository ?: DeviceRepository(
                api = provideApiService(context),
            ).also { deviceRepository = it }
        }
}
