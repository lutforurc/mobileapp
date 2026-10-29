package com.example.cashbookbd.ui.reports

import com.example.cashbookbd.ui.theme.muted
import com.example.cashbookbd.ui.theme.appColors
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.SectionMenuList
import com.example.cashbookbd.report.ReportConfig
import com.example.cashbookbd.report.ReportMenu

/**
 * The single "Reports" parent section. Lists every report the current user is
 * permitted to open (from [ReportMenu.visible]); native reports route to their
 * hand-built screens, the rest to the generic report flow.
 */
@Composable
fun ReportsHomeScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sessionManager = remember { ServiceLocator.provideSessionManager(context) }
    val sessionState by sessionManager.state.collectAsStateWithLifecycle()

    // The user's own inside-the-menu arrangement (the web's sidebar-sub).
    val menuPrefs = remember { ServiceLocator.provideMenuPreferencesRepository(context) }
    val subPrefs by menuPrefs.subState.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { menuPrefs.refreshSub() }

    val reports = com.example.cashbookbd.navigation.WebMenuIds.arrange(
        "reports", ReportMenu.visible(sessionState.permissions), subPrefs,
    ) { it.key }

    AuthenticatedShell(
        title = "Reports",
        currentRoute = Routes.REPORTS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        if (reports.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "You don't have access to any reports.",
                    color = MaterialTheme.appColors.textOnScreenMuted,
                    textAlign = TextAlign.Center,
                )
            }
            return@AuthenticatedShell
        }

        SectionMenuList(
            menuId = "reports",
            items = reports,
            keyOf = { it.key },
            arranged = com.example.cashbookbd.navigation.WebMenuIds.isArranged("reports", subPrefs),
        ) { report ->
            ReportRowItem(report = report, onClick = { navController.openReport(report) })
        }
    }
}

@Composable
private fun ReportRowItem(report: ReportConfig, onClick: () -> Unit) {
    com.example.cashbookbd.ui.components.MenuListRow(title = report.title, onClick = onClick)
}

/** Routes a tapped report to its native screen, or the generic report flow. */
private fun NavHostController.openReport(report: ReportConfig) {
    val route = when (report.key) {
        "ageing" -> com.example.cashbookbd.accounts.AccountsMenu.ROUTE_AGEING
        "auditTrail" -> com.example.cashbookbd.accounts.AccountsMenu.ROUTE_AUDIT_TRAIL
        "cashbook" -> Routes.CASHBOOK
        "ledger" -> Routes.LEDGER
        "bankbook" -> Routes.BANKBOOK
        "cashBankReceivedPayment" -> Routes.CASH_BANK
        "trialBalanceLevel3" -> Routes.TRIAL_BALANCE_L3
        "trialBalanceLevel4" -> Routes.TRIAL_BALANCE_L4
        "profitLoss" -> Routes.PROFIT_LOSS
        "balanceSheet" -> Routes.BALANCE_SHEET
        "dueList" -> Routes.DUE_LIST
        "dueInstallments" -> Routes.DUE_INSTALLMENTS
        "productStatement" -> Routes.productStatement(null)
        "productTrackingSummary" -> Routes.PRODUCT_TRACKING_SUMMARY
        "purchaseLedger" -> Routes.tradeLedger("purchase")
        "salesLedger" -> Routes.tradeLedger("sales")
        "purchaseReturnLedger" -> Routes.tradeLedger("purchase-return")
        "salesReturnLedger" -> Routes.tradeLedger("sales-return")
        "deliveryReport" -> com.example.cashbookbd.navigation.ParityRoutes.DELIVERY_REPORT
        "customerSales" -> com.example.cashbookbd.navigation.ParityRoutes.CUSTOMER_SALES
        "dailyAccountBook" -> com.example.cashbookbd.navigation.ParityRoutes.DAILY_ACCOUNT_BOOK
        "salesReferrer" -> com.example.cashbookbd.navigation.ParityRoutes.SALES_REFERRER
        "groupReport" -> Routes.GROUP_REPORT
        "connectedMember" -> Routes.CONNECTED_MEMBER
        "expenseReport" -> Routes.EXPENSE_REPORT
        "voucherRegister" -> Routes.VOUCHER_REGISTER
        "cashBankBook" -> Routes.CASH_BANK_BOOK
        else -> Routes.reportView(report.key)
    }
    navigate(route)
}
