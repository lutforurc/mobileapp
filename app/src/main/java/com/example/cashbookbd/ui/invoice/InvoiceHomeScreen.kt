package com.example.cashbookbd.ui.invoice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.invoice.InvoiceItem
import com.example.cashbookbd.invoice.InvoiceMenu
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes

/**
 * The "Invoice" parent section. Lists every invoice form the current user is
 * permitted to open (from [InvoiceMenu.visible]); each routes to its entry form.
 */
@Composable
fun InvoiceHomeScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sessionManager = remember { ServiceLocator.provideSessionManager(context) }
    val sessionState by sessionManager.state.collectAsStateWithLifecycle()

    val items = InvoiceMenu.visible(sessionState.permissions)

    AuthenticatedShell(
        title = "Invoice",
        currentRoute = Routes.INVOICES,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "You don't have access to any invoices.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                InvoiceRowItem(
                    item = item,
                    onClick = { navController.navigate(Routes.invoiceView(item.key)) },
                )
            }
        }
    }
}

@Composable
private fun InvoiceRowItem(item: InvoiceItem, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.title, style = MaterialTheme.typography.bodyLarge)
                if (!item.supported) {
                    Text(
                        text = "Coming soon",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
