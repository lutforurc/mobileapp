package com.example.cashbookbd.ui.hrm

import com.example.cashbookbd.ui.theme.muted
import com.example.cashbookbd.ui.theme.appColors
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.example.cashbookbd.applist.AppLists
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.hrm.HrmItem
import com.example.cashbookbd.hrm.HrmMenu
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.SectionMenuList
import com.example.cashbookbd.report.ReportMenu

/**
 * The "HRM" parent section. Lists every HRM screen the user is permitted to open
 * (from [HrmMenu.visible]); an item routes to its list, report or form by key.
 */
@Composable
fun HrmHomeScreen(
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

    val items = com.example.cashbookbd.navigation.WebMenuIds.arrange(
        "hrm", HrmMenu.visible(sessionState.permissions), subPrefs,
    ) { it.key }

    AuthenticatedShell(
        title = "HRM",
        currentRoute = Routes.HRM,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "You don't have access to any HRM screens.",
                    color = MaterialTheme.appColors.textOnScreenMuted,
                    textAlign = TextAlign.Center,
                )
            }
            return@AuthenticatedShell
        }

        SectionMenuList(
            menuId = "hrm",
            items = items,
            keyOf = { it.key },
            arranged = com.example.cashbookbd.navigation.WebMenuIds.isArranged("hrm", subPrefs),
        ) { item ->
            HrmRowItem(
                item = item,
                onClick = {
                    // A key present in a shared registry opens that engine's
                    // screen; everything else is a hand-built HRM form.
                    val route = when {
                        // The web builds these on the client — the absent
                        // rows, the cards — so they have a screen of their own.
                        item.key == "hrmAttendanceReport" -> com.example.cashbookbd.navigation.ParityRoutes.ATTENDANCE_DAILY
                        item.key == "hrmAttendanceAlerts" -> com.example.cashbookbd.navigation.ParityRoutes.attendanceException(
                            com.example.cashbookbd.ui.hrm.AttendanceReportType.ABSENT,
                        )
                        AppLists.byKey(item.key) != null -> Routes.appListView(item.key)
                        ReportMenu.byKey(item.key) != null -> Routes.reportView(item.key)
                        else -> Routes.hrmView(item.key)
                    }
                    navController.navigate(route)
                },
            )
        }
    }
}

@Composable
private fun HrmRowItem(item: HrmItem, onClick: () -> Unit) {
    com.example.cashbookbd.ui.components.MenuListRow(title = item.title, onClick = onClick)
}
