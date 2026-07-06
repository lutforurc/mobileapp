package com.example.cashbookbd.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch

/**
 * Shared chrome for every authenticated screen: a navigation drawer plus a top
 * bar with a hamburger button, screen [title] and optional [actions]. Each
 * screen supplies its own [currentRoute] (for drawer highlighting) and body via
 * [content].
 */
@Composable
fun AuthenticatedShell(
    title: String,
    currentRoute: String,
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    val drawerState = androidx.compose.material3.rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                currentRoute = currentRoute,
                onDestinationClick = { route ->
                    scope.launch { drawerState.close() }
                    if (route != currentRoute) {
                        navController.navigate(route) {
                            // Keep the dashboard as the single base of the stack.
                            popUpTo(Routes.HOME) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                },
                onLogout = {
                    scope.launch { drawerState.close() }
                    onLogout()
                },
            )
        },
    ) {
        Scaffold { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Filled.Menu, contentDescription = "Open menu")
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    actions()
                }

                Box(modifier = Modifier.weight(1f)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun AppDrawerContent(
    currentRoute: String,
    onDestinationClick: (String) -> Unit,
    onLogout: () -> Unit,
) {
    var reportsExpanded by remember { mutableStateOf(true) }

    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "CashBook",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 16.dp),
            )

            NavigationDrawerItem(
                label = { Text("Dashboard") },
                icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                selected = currentRoute == Routes.HOME,
                onClick = { onDestinationClick(Routes.HOME) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )

            // Reports (expandable parent) -> Cash Book (child).
            NavigationDrawerItem(
                label = { Text("Reports") },
                icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                badge = {
                    Icon(
                        imageVector = if (reportsExpanded) {
                            Icons.Filled.KeyboardArrowUp
                        } else {
                            Icons.Filled.KeyboardArrowDown
                        },
                        contentDescription = if (reportsExpanded) "Collapse" else "Expand",
                    )
                },
                selected = false,
                onClick = { reportsExpanded = !reportsExpanded },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )

            AnimatedVisibility(visible = reportsExpanded) {
                Column {
                    NavigationDrawerItem(
                        label = { Text("Cash Book") },
                        selected = currentRoute == Routes.CASHBOOK,
                        onClick = { onDestinationClick(Routes.CASHBOOK) },
                        modifier = Modifier.padding(
                            start = 28.dp, // extra indent to show it nests under Reports
                            top = 0.dp,
                            end = 12.dp,
                            bottom = 0.dp,
                        ),
                    )
                    NavigationDrawerItem(
                        label = { Text("Ledger") },
                        selected = currentRoute == Routes.LEDGER,
                        onClick = { onDestinationClick(Routes.LEDGER) },
                        modifier = Modifier.padding(
                            start = 28.dp,
                            top = 0.dp,
                            end = 12.dp,
                            bottom = 0.dp,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(12.dp))

            NavigationDrawerItem(
                label = { Text("Log out") },
                icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
                selected = false,
                onClick = onLogout,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
        }
    }
}
