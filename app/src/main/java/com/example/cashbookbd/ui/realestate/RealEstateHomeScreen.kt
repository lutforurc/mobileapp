package com.example.cashbookbd.ui.realestate

import com.example.cashbookbd.ui.theme.muted
import com.example.cashbookbd.ui.theme.appColors
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.cashbookbd.applist.AppLists
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.realestate.RealEstateItem
import com.example.cashbookbd.realestate.RealEstateMenu

/**
 * The "Real Estate" parent section (business type 9 branches). Lists every
 * screen the user is permitted to open (from [RealEstateMenu.visible]); master
 * lists ride the shared list engine, the sales flow has native screens.
 */
@Composable
fun RealEstateHomeScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sessionManager = remember { ServiceLocator.provideSessionManager(context) }
    val sessionState by sessionManager.state.collectAsStateWithLifecycle()

    val items = RealEstateMenu.visible(sessionState.permissions)

    AuthenticatedShell(
        title = "Real Estate",
        currentRoute = Routes.REAL_ESTATE,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "You don't have access to any real estate screens.",
                    color = MaterialTheme.appColors.textOnScreenMuted,
                    textAlign = TextAlign.Center,
                )
            }
            return@AuthenticatedShell
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items) { item ->
                RealEstateRowItem(
                    item = item,
                    onClick = {
                        val route = when {
                            item.key == RealEstateMenu.UNIT_SALES_KEY -> Routes.UNIT_SALE
                            item.key == RealEstateMenu.SOLD_UNITS_KEY -> Routes.SOLD_UNITS
                            item.key == RealEstateMenu.INSTALLMENT_CREATE_KEY -> Routes.RE_INSTALLMENT_CREATE
                            item.key == RealEstateMenu.FLAT_LAYOUT_KEY -> Routes.FLAT_LAYOUT
                            item.key == RealEstateMenu.PROJECT_EXPENSE_KEY -> Routes.PROJECT_EXPENSE
                            item.key == RealEstateMenu.PROJECT_PURCHASE_KEY -> Routes.PROJECT_PURCHASE
                            item.key == RealEstateMenu.PROJECT_LABOUR_KEY -> Routes.PROJECT_LABOUR
                            item.key == RealEstateMenu.PROJECT_COST_REPORT_KEY -> Routes.PROJECT_COST_REPORT
                            AppLists.byKey(item.key) != null -> Routes.appListView(item.key)
                            else -> return@RealEstateRowItem
                        }
                        navController.navigate(route)
                    },
                )
            }
        }
    }
}

@Composable
private fun RealEstateRowItem(item: RealEstateItem, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = item.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
