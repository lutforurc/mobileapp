package com.example.cashbookbd.ui.reports

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.ParityRoutes
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.navigation.WebMenuIds
import com.example.cashbookbd.ui.components.SectionMenuList
import com.example.cashbookbd.report.ReportConfig
import com.example.cashbookbd.report.ReportMenu
import com.example.cashbookbd.session.Permissions
import com.example.cashbookbd.ui.components.MenuListRow
import com.example.cashbookbd.ui.theme.appColors

/**
 * The web sidebar's own top-level **Financial Statement** menu (it is no longer
 * a group inside Reports). The entries are listed in the web's drawn order; one
 * this app has no screen for is simply absent rather than a dead row.
 */
/** The report keys that belong to the Financial Statement menu, not Reports. */
val FINANCIAL_STATEMENT_KEYS: List<String> = listOf(
    "productProfitLoss",
    "productProfitSummary",
    "invoiceWiseProfit",
    "profitLossDateWise",
    "monthlyStatement",
    "trialBalanceLevel4",
    "trialBalanceLevel3",
    "trialBalance",
    "expenseReport",
    "profitLoss",
    "balanceSheet",
    "businessSummary",
    "cashFlowStatement",
    "generalLedger",
)

/** Any of these shows the section — the web's own list. */
val FINANCIAL_PARENT_PERMISSIONS: List<String> = listOf(
    "productwise.profit",
    "productwise.profit.summary",
    "invoice.wise.profit",
    "profit.loss.date.wise",
    "monthly.statement",
    "trial.balance.l4",
    "trial.balance.l3",
    "trial.balance",
    "expense.report",
    "profit.loss",
    "balancesheet.view",
    "business.summary",
    "cash.flow.statement",
    "general.ledger",
)

@Composable
fun FinancialStatementHomeScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sessionManager = remember { com.example.cashbookbd.di.ServiceLocator.provideSessionManager(context) }
    val sessionState by sessionManager.state.collectAsStateWithLifecycle()

    val menuPrefs = remember {
        com.example.cashbookbd.di.ServiceLocator.provideMenuPreferencesRepository(context)
    }
    val subPrefs by menuPrefs.subState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { menuPrefs.refreshSub() }

    val statements = FINANCIAL_STATEMENT_KEYS
        .mapNotNull { ReportMenu.byKey(it) }
        .filter { Permissions.hasAny(sessionState.permissions, it.anyOf) }
        .let { WebMenuIds.arrange("financial_statement", it, subPrefs) { config -> config.key } }

    AuthenticatedShell(
        title = "Financial Statement",
        currentRoute = ParityRoutes.FINANCIAL_STATEMENT,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        if (statements.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "You don't have access to any financial statement.",
                    color = MaterialTheme.appColors.textOnScreenMuted,
                    textAlign = TextAlign.Center,
                )
            }
            return@AuthenticatedShell
        }

        SectionMenuList(
            menuId = "financial_statement",
            items = statements,
            keyOf = { it.key },
            arranged = WebMenuIds.isArranged("financial_statement", subPrefs),
        ) { report ->
            MenuListRow(title = report.title, onClick = { navController.openStatement(report) })
        }
    }
}

/** Routes a statement to its native screen, or the generic report flow. */
private fun NavHostController.openStatement(report: ReportConfig) {
    val route = when (report.key) {
        "trialBalanceLevel3" -> Routes.TRIAL_BALANCE_L3
        "trialBalanceLevel4" -> Routes.TRIAL_BALANCE_L4
        "cashFlowStatement" -> ParityRoutes.CASH_FLOW_STATEMENT
        "generalLedger" -> ParityRoutes.GENERAL_LEDGER
        "invoiceWiseProfit" -> ParityRoutes.INVOICE_WISE_PROFIT
        "profitLoss" -> Routes.PROFIT_LOSS
        "balanceSheet" -> Routes.BALANCE_SHEET
        "expenseReport" -> Routes.EXPENSE_REPORT
        else -> Routes.reportView(report.key)
    }
    navigate(route)
}
