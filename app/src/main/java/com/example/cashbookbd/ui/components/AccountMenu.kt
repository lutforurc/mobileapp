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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

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
    photoUrl: String? = null,
    isDark: Boolean,
    onThemeChange: (Boolean) -> Unit,
    isFullScreen: Boolean,
    onFullScreenChange: (Boolean) -> Unit,
    items: List<AccountMenuItem>,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            UserAvatar(
                photoUrl = photoUrl,
                contentDescription = "Account menu",
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(MenuWidth),
        ) {
            AccountHeader(
                userName = userName,
                transactionDate = transactionDate,
                photoUrl = photoUrl,
            )
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

            SwitchMenuItem(
                label = if (isDark) "Dark" else "Light",
                icon = Icons.Filled.Settings,
                checked = isDark,
                onCheckedChange = onThemeChange,
            )

            SwitchMenuItem(
                label = "Full Screen",
                icon = FullScreenIcon,
                checked = isFullScreen,
                onCheckedChange = onFullScreenChange,
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

/**
 * A menu row that toggles a setting. The single switch-row component for the
 * account menu — the theme and full-screen toggles both use it, so their
 * spacing, icon slot and tap behaviour stay identical and change in one place.
 *
 * The switch drives the same callback as the row, so tapping anywhere on the row
 * toggles the setting — as it does on the web.
 */
@Composable
private fun SwitchMenuItem(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        onClick = { onCheckedChange(!checked) },
    )
}

/**
 * Material's "fullscreen" glyph (four corner brackets), drawn here because the
 * project depends on the core icon set only, and adding
 * `material-icons-extended` for one icon would cost several MB.
 */
private val FullScreenIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "FullScreen",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            // Bottom-left bracket.
            moveTo(7f, 14f); lineTo(5f, 14f); lineTo(5f, 19f)
            lineTo(10f, 19f); lineTo(10f, 17f); lineTo(7f, 17f); close()
            // Top-left bracket.
            moveTo(5f, 10f); lineTo(7f, 10f); lineTo(7f, 7f)
            lineTo(10f, 7f); lineTo(10f, 5f); lineTo(5f, 5f); close()
            // Bottom-right bracket.
            moveTo(17f, 17f); lineTo(14f, 17f); lineTo(14f, 19f)
            lineTo(19f, 19f); lineTo(19f, 14f); lineTo(17f, 14f); close()
            // Top-right bracket.
            moveTo(14f, 5f); lineTo(14f, 7f); lineTo(17f, 7f)
            lineTo(17f, 10f); lineTo(19f, 10f); lineTo(19f, 5f); close()
        }
    }.build()
}

/** Name over transaction date, with the avatar — the web dropdown's header. */
@Composable
private fun AccountHeader(userName: String?, transactionDate: String?, photoUrl: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UserAvatar(
            photoUrl = photoUrl,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            background = MaterialTheme.colorScheme.primaryContainer,
        )
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
 * The circular avatar: the user's photo when they have one, otherwise the
 * [Icons.Filled.AccountCircle] fallback — the same substitution the web makes
 * with `me.profile_photo || UserOne`.
 *
 * The icon sits underneath rather than in Coil's `error`/`placeholder` slots so
 * it keeps its [tint]: it shows while the photo loads and stays put if the URL
 * 404s, which the stored-at-upload-time URLs can do after a host change.
 */
@Composable
private fun UserAvatar(
    photoUrl: String?,
    contentDescription: String?,
    tint: Color,
    background: Color? = null,
) {
    Box(
        modifier = Modifier
            .size(AvatarSize)
            .then(background?.let { Modifier.background(it, CircleShape) } ?: Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.AccountCircle,
            contentDescription = contentDescription,
            modifier = Modifier.size(AvatarSize),
            tint = tint,
        )
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(AvatarSize)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
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
