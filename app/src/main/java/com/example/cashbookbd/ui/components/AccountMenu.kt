package com.example.cashbookbd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * One entry in the account menu. Kept as data so the menu's look is defined in a
 * single place — add an item here and it picks up the shared row styling, rather
 * than each caller hand-rolling a [DropdownMenuItem].
 */
data class AccountMenuItem(
    val label: String,
    val icon: ImageVector? = null,
    val onClick: () -> Unit,
)

/**
 * The avatar button and its dropdown, mirroring the web's `DropdownUser`: the
 * signed-in user's name and transaction date on top, then navigation entries, a
 * dark-mode switch, and Log Out at the bottom.
 *
 * This is the single account-menu component for the whole app — [AuthenticatedShell]
 * renders it on every authenticated screen, so a change here (an item, a colour,
 * the avatar) applies everywhere at once.
 */
@Composable
fun AccountMenu(
    userName: String?,
    transactionDate: String?,
    isDark: Boolean,
    onThemeChange: (Boolean) -> Unit,
    items: List<AccountMenuItem>,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = "Account menu",
                modifier = Modifier.size(AvatarSize),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(MenuWidth),
        ) {
            AccountHeader(userName = userName, transactionDate = transactionDate)
            HorizontalDivider()

            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label) },
                    leadingIcon = item.icon?.let { icon ->
                        { Icon(icon, contentDescription = null) }
                    },
                    onClick = {
                        // Close first: the click usually navigates away, and a menu
                        // left open would linger over the new screen.
                        expanded = false
                        item.onClick()
                    },
                )
            }

            // The switch drives the same callback as the row, so tapping anywhere
            // on the row toggles the theme — as it does on the web.
            DropdownMenuItem(
                text = { Text(if (isDark) "Dark" else "Light") },
                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                trailingIcon = {
                    Switch(checked = isDark, onCheckedChange = onThemeChange)
                },
                onClick = { onThemeChange(!isDark) },
            )

            HorizontalDivider()

            DropdownMenuItem(
                text = { Text("Log Out") },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onLogout()
                },
            )
        }
    }
}

/** Name over transaction date, with the avatar — the web dropdown's header. */
@Composable
private fun AccountHeader(userName: String?, transactionDate: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(AvatarSize)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Column {
            Text(
                text = userName?.takeIf { it.isNotBlank() } ?: "Signed in",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            // Hidden rather than shown blank — the backend omits it for branches
            // that have no open transaction date.
            transactionDate?.takeIf { it.isNotBlank() }?.let { date ->
                Text(
                    text = "Trx. Dt. $date",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Builds the standard entry list. Callers pass only the navigation actions, so
 * the menu's contents and their order stay defined in one place.
 */
fun accountMenuItems(
    onDashboard: () -> Unit,
    onMyDevices: () -> Unit,
    onSubscription: (() -> Unit)?,
): List<AccountMenuItem> = buildList {
    add(AccountMenuItem("Dashboard", Icons.Filled.Home, onDashboard))
    add(AccountMenuItem("My Devices", Icons.Filled.Phone, onMyDevices))
    onSubscription?.let { add(AccountMenuItem("Subscription", Icons.Filled.Star, it)) }
}

private val AvatarSize = 32.dp
private val MenuWidth = 240.dp
