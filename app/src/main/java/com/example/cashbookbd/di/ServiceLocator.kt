package com.example.cashbookbd.di

import android.content.Context
import com.example.cashbookbd.BuildConfig
import com.example.cashbookbd.data.local.DashboardCache
import com.example.cashbookbd.data.local.DeviceIdManager
import com.example.cashbookbd.data.local.InAppEventQueue
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
import com.example.cashbookbd.data.repository.HighlightRuleRepository
import com.example.cashbookbd.data.repository.HrmRepository
import com.example.cashbookbd.data.repository.InvoiceRepository
import com.example.cashbookbd.data.repository.LedgerRepository
import com.example.cashbookbd.data.repository.ProfitLossRepository
import com.example.cashbookbd.data.repository.RegistrationRepository
import com.example.cashbookbd.data.repository.ReportRepository
import com.example.cashbookbd.data.repository.SelectorRepository
import com.example.cashbookbd.data.repository.SessionRepository
import com.example.cashbookbd.data.repository.InAppMessageRepository
import com.example.cashbookbd.data.repository.NotificationRepository
import com.example.cashbookbd.data.repository.SettingsRepository
import com.example.cashbookbd.data.repository.TransactionRepository
import com.example.cashbookbd.data.repository.DeviceRepository
import com.example.cashbookbd.data.repository.SubscriptionRepository
import com.example.cashbookbd.data.repository.CashBankRepository
import com.example.cashbookbd.data.repository.TrialBalanceRepository
import com.example.cashbookbd.data.repository.CustomerRepository
import com.example.cashbookbd.data.repository.ProductRepository
import com.example.cashbookbd.data.repository.RoleRepository
import com.example.cashbookbd.data.repository.UserRepository
import com.example.cashbookbd.data.repository.VrSettingsRepository
import com.example.cashbookbd.data.repository.LabourInvoiceRepository
import com.example.cashbookbd.data.repository.RequisitionRepository
import com.example.cashbookbd.data.repository.InventoryMovementRepository
import com.example.cashbookbd.data.remote.RealEstateApiService
import com.example.cashbookbd.data.repository.RealEstateCrudRepository
import com.example.cashbookbd.data.repository.UnitSaleRepository
import com.example.cashbookbd.data.repository.UnitSalePaymentRepository
import com.example.cashbookbd.data.repository.RealEstateSalesRepository
import com.example.cashbookbd.data.repository.CoaRepository
import com.example.cashbookbd.data.repository.VoucherHistoryRepository
import com.example.cashbookbd.data.repository.SoftwareInfoRepository
import com.example.cashbookbd.inappmessage.InAppMessageManager
import com.example.cashbookbd.notifications.NotificationCenter
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
    private var cashBankRepository: CashBankRepository? = null

    @Volatile
    private var highlightRuleRepository: HighlightRuleRepository? = null

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
    private var labourInvoiceRepository: LabourInvoiceRepository? = null

    @Volatile
    private var requisitionRepository: RequisitionRepository? = null

    @Volatile
    private var inventoryMovementRepository: InventoryMovementRepository? = null

    @Volatile
    private var realEstateApiService: RealEstateApiService? = null

    @Volatile
    private var realEstateCrudRepository: RealEstateCrudRepository? = null

    @Volatile
    private var unitSaleRepository: UnitSaleRepository? = null

    @Volatile
    private var unitSalePaymentRepository: UnitSalePaymentRepository? = null

    @Volatile
    private var realEstateSalesRepository: RealEstateSalesRepository? = null

    @Volatile
    private var coaRepository: CoaRepository? = null

    @Volatile
    private var voucherHistoryRepository: VoucherHistoryRepository? = null

    @Volatile
    private var softwareInfoRepository: SoftwareInfoRepository? = null

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
    private var roleRepository: RoleRepository? = null
    @Volatile
    private var customerRepository: CustomerRepository? = null

    @Volatile
    private var productRepository: ProductRepository? = null

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

    @Volatile
    private var notificationRepository: NotificationRepository? = null

    @Volatile
    private var notificationCenter: NotificationCenter? = null
    private var inAppEventQueue: InAppEventQueue? = null
    private var inAppMessageRepository: InAppMessageRepository? = null
    private var inAppMessageManager: InAppMessageManager? = null

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

    fun provideCashBankRepository(context: Context): CashBankRepository =
        cashBankRepository ?: synchronized(this) {
            cashBankRepository ?: CashBankRepository(
                api = provideReportApiService(context),
            ).also { cashBankRepository = it }
        }

    fun provideHighlightRuleRepository(context: Context): HighlightRuleRepository =
        highlightRuleRepository ?: synchronized(this) {
            highlightRuleRepository ?: HighlightRuleRepository(
                api = provideApiService(context),
            ).also { highlightRuleRepository = it }
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

    fun provideLabourInvoiceRepository(context: Context): LabourInvoiceRepository =
        labourInvoiceRepository ?: synchronized(this) {
            labourInvoiceRepository ?: LabourInvoiceRepository(
                reportApi = provideReportApiService(context),
                transactionApi = provideTransactionApiService(context),
            ).also { labourInvoiceRepository = it }
        }

    private fun provideRealEstateApiService(context: Context): RealEstateApiService =
        realEstateApiService ?: synchronized(this) {
            realEstateApiService ?: provideRetrofit(context).create(RealEstateApiService::class.java)
                .also { realEstateApiService = it }
        }

    fun provideRealEstateCrudRepository(context: Context): RealEstateCrudRepository =
        realEstateCrudRepository ?: synchronized(this) {
            realEstateCrudRepository ?: RealEstateCrudRepository(
                api = provideRealEstateApiService(context),
            ).also { realEstateCrudRepository = it }
        }

    fun provideUnitSaleRepository(context: Context): UnitSaleRepository =
        unitSaleRepository ?: synchronized(this) {
            unitSaleRepository ?: UnitSaleRepository(
                reportApi = provideReportApiService(context),
                transactionApi = provideTransactionApiService(context),
            ).also { unitSaleRepository = it }
        }

    fun provideRealEstateSalesRepository(context: Context): RealEstateSalesRepository =
        realEstateSalesRepository ?: synchronized(this) {
            realEstateSalesRepository ?: RealEstateSalesRepository(
                reportApi = provideReportApiService(context),
                transactionApi = provideTransactionApiService(context),
            ).also { realEstateSalesRepository = it }
        }

    fun provideUnitSalePaymentRepository(context: Context): UnitSalePaymentRepository =
        unitSalePaymentRepository ?: synchronized(this) {
            unitSalePaymentRepository ?: UnitSalePaymentRepository(
                api = provideHrmApiService(context),
            ).also { unitSalePaymentRepository = it }
        }

    fun provideCoaRepository(context: Context): CoaRepository =
        coaRepository ?: synchronized(this) {
            coaRepository ?: CoaRepository(
                api = provideReportApiService(context),
            ).also { coaRepository = it }
        }

    fun provideVoucherHistoryRepository(context: Context): VoucherHistoryRepository =
        voucherHistoryRepository ?: synchronized(this) {
            voucherHistoryRepository ?: VoucherHistoryRepository(
                api = provideTransactionApiService(context),
            ).also { voucherHistoryRepository = it }
        }

    fun provideSoftwareInfoRepository(context: Context): SoftwareInfoRepository =
        softwareInfoRepository ?: synchronized(this) {
            softwareInfoRepository ?: SoftwareInfoRepository(
                api = provideReportApiService(context),
            ).also { softwareInfoRepository = it }
        }

    fun provideInventoryMovementRepository(context: Context): InventoryMovementRepository =
        inventoryMovementRepository ?: synchronized(this) {
            inventoryMovementRepository ?: InventoryMovementRepository(
                reportApi = provideReportApiService(context),
                transactionApi = provideTransactionApiService(context),
            ).also { inventoryMovementRepository = it }
        }

    fun provideRequisitionRepository(context: Context): RequisitionRepository =
        requisitionRepository ?: synchronized(this) {
            requisitionRepository ?: RequisitionRepository(
                api = provideTransactionApiService(context),
            ).also { requisitionRepository = it }
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

    fun provideRoleRepository(context: Context): RoleRepository =
        roleRepository ?: synchronized(this) {
            roleRepository ?: RoleRepository(
                api = provideReportApiService(context),
            ).also { roleRepository = it }
        }

    fun provideCustomerRepository(context: Context): CustomerRepository =
        customerRepository ?: synchronized(this) {
            customerRepository ?: CustomerRepository(
                api = provideReportApiService(context),
            ).also { customerRepository = it }
        }

    fun provideProductRepository(context: Context): ProductRepository =
        productRepository ?: synchronized(this) {
            productRepository ?: ProductRepository(
                api = provideReportApiService(context),
            ).also { productRepository = it }
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

    fun provideNotificationRepository(context: Context): NotificationRepository =
        notificationRepository ?: synchronized(this) {
            notificationRepository ?: NotificationRepository(
                api = provideApiService(context),
            ).also { notificationRepository = it }
        }

    /** Shared, app-wide holder of the notification-center items and badge count. */
    fun provideNotificationCenter(context: Context): NotificationCenter =
        notificationCenter ?: synchronized(this) {
            notificationCenter ?: NotificationCenter(
                repository = provideNotificationRepository(context),
            ).also { notificationCenter = it }
        }

    private fun provideInAppEventQueue(context: Context): InAppEventQueue =
        inAppEventQueue ?: synchronized(this) {
            inAppEventQueue ?: InAppEventQueue(context.applicationContext).also { inAppEventQueue = it }
        }

    private fun provideInAppMessageRepository(context: Context): InAppMessageRepository =
        inAppMessageRepository ?: synchronized(this) {
            inAppMessageRepository ?: InAppMessageRepository(
                api = provideApiService(context),
                deviceIdManager = provideDeviceIdManager(context),
                queue = provideInAppEventQueue(context),
                appVersion = BuildConfig.VERSION_NAME,
            ).also { inAppMessageRepository = it }
        }

    /** Shared, app-wide queue of admin-authored pop-up campaigns. */
    fun provideInAppMessageManager(context: Context): InAppMessageManager =
        inAppMessageManager ?: synchronized(this) {
            inAppMessageManager ?: InAppMessageManager(
                repository = provideInAppMessageRepository(context),
            ).also { inAppMessageManager = it }
        }
}
