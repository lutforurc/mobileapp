package com.example.cashbookbd.ui.components

import com.example.cashbookbd.ui.theme.AppFontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
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
    /**
     * A count worth interrupting for (new tasks handed to this user). Zero or
     * null shows nothing — a "0" badge is noise dressed as news.
     */
    val badgeCount: Int? = null,
    /**
     * Render this row after the theme / full-screen switches instead of with
     * the rest. The two security/destructive actions sit at the foot of the
     * menu, below those switches (owner's numbered order).
     */
    val afterToggles: Boolean = false,
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

            items.filterNot { it.afterToggles }.forEach { item ->
                AccountMenuEntry(item, onDismiss = { expanded = false })
            }

            SwitchMenuItem(
                label = if (isDark) "Dark" else "Light",
                icon = if (isDark) DarkModeIcon else LightModeIcon,
                checked = isDark,
                onCheckedChange = onThemeChange,
            )

            SwitchMenuItem(
                label = "Full Screen",
                icon = FullScreenIcon,
                checked = isFullScreen,
                onCheckedChange = onFullScreenChange,
            )

            items.filter { it.afterToggles }.forEach { item ->
                AccountMenuEntry(item, onDismiss = { expanded = false })
            }

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

/** One navigation row of the account menu; closes the menu before navigating. */
@Composable
private fun AccountMenuEntry(item: AccountMenuItem, onDismiss: () -> Unit) {
    DropdownMenuItem(
        text = {
            val count = item.badgeCount ?: 0
            if (count <= 0) {
                Text(item.label)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.label)
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        },
        leadingIcon = item.icon?.let { icon ->
            { Icon(icon, contentDescription = null) }
        },
        onClick = {
            // Close first: the click usually navigates away, and a menu left
            // open would linger over the new screen.
            onDismiss()
            item.onClick()
        },
    )
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
 * The account menu's "Full Screen" glyph: four rounded corner brackets, drawn
 * in the same stroked, round-capped style as the other custom menu icons so the
 * set reads as one family. Drawn here because the project ships the core icon
 * set only, and `material-icons-extended` for one glyph would cost several MB.
 */
private val FullScreenIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "FullScreen",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // Top-left bracket.
            moveTo(9f, 3.5f)
            horizontalLineTo(6f)
            quadTo(3.5f, 3.5f, 3.5f, 6f)
            verticalLineTo(9f)
            // Top-right bracket.
            moveTo(15f, 3.5f)
            horizontalLineTo(18f)
            quadTo(20.5f, 3.5f, 20.5f, 6f)
            verticalLineTo(9f)
            // Bottom-left bracket.
            moveTo(3.5f, 15f)
            verticalLineTo(18f)
            quadTo(3.5f, 20.5f, 6f, 20.5f)
            horizontalLineTo(9f)
            // Bottom-right bracket.
            moveTo(20.5f, 15f)
            verticalLineTo(18f)
            quadTo(20.5f, 20.5f, 18f, 20.5f)
            horizontalLineTo(15f)
        }
    }.build()
}

/**
 * The account menu's "My Tasks" glyph: a checklist card, two ticked rows and a
 * rounded page outline. Drawn here for the same reason as [FullScreenIcon] —
 * the project ships the core icon set only, and no stock glyph reads as a
 * checklist. Stroked, not filled, so it stays legible at menu size; it carries
 * no colour of its own, so [Icon] recolours it with the menu's ink like every
 * other row (which is also what keeps it visible in the dark theme).
 */
private val MyTasksIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "MyTasks",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // The page outline, with the same 3.5-corner radius on all four corners.
            moveTo(6.5f, 3f)
            horizontalLineTo(17.5f)
            quadTo(20.5f, 3f, 20.5f, 6.5f)
            verticalLineTo(17.5f)
            quadTo(20.5f, 20.5f, 17.5f, 20.5f)
            horizontalLineTo(6.5f)
            quadTo(3.5f, 20.5f, 3.5f, 17.5f)
            verticalLineTo(6.5f)
            quadTo(3.5f, 3f, 6.5f, 3f)
            close()
            // Top row: tick, then the line it ticks off.
            moveTo(7f, 9.2f)
            lineTo(8.7f, 10.9f)
            lineTo(11.5f, 7.8f)
            moveTo(14f, 9.6f)
            horizontalLineTo(17.4f)
            // Bottom row: tick, then the line it ticks off.
            moveTo(7f, 15.2f)
            lineTo(8.7f, 16.9f)
            lineTo(11.5f, 13.8f)
            moveTo(14f, 15.6f)
            horizontalLineTo(17.4f)
        }
    }.build()
}

/**
 * The account menu's "My Devices" glyph: a monitor with a phone beside it — the
 * pair that reads as "devices" rather than one specific piece of hardware.
 * Drawn here for the same reason as [MyTasksIcon]; stroked and colourless, so
 * [Icon] tints it with the menu's ink like every other row.
 */
private val MyDevicesIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "MyDevices",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // Monitor screen, its stand's neck and base.
            moveTo(3.4f, 4.5f)
            horizontalLineTo(12.6f)
            quadTo(14f, 4.5f, 14f, 5.9f)
            verticalLineTo(11.6f)
            quadTo(14f, 13f, 12.6f, 13f)
            horizontalLineTo(3.4f)
            quadTo(2f, 13f, 2f, 11.6f)
            verticalLineTo(5.9f)
            quadTo(2f, 4.5f, 3.4f, 4.5f)
            close()
            moveTo(8f, 13f)
            verticalLineTo(16.6f)
            moveTo(5.4f, 16.6f)
            horizontalLineTo(10.6f)
            // Phone body, and the home indicator on its chin.
            moveTo(17.4f, 8.8f)
            horizontalLineTo(20.2f)
            quadTo(22f, 8.8f, 22f, 10.6f)
            verticalLineTo(18.7f)
            quadTo(22f, 20.5f, 20.2f, 20.5f)
            horizontalLineTo(17.4f)
            quadTo(15.6f, 20.5f, 15.6f, 18.7f)
            verticalLineTo(10.6f)
            quadTo(15.6f, 8.8f, 17.4f, 8.8f)
            close()
            moveTo(17.6f, 18.2f)
            horizontalLineTo(20f)
        }
    }.build()
}

/**
 * The account menu's "Subscription" glyph: a Subscribe button with a finger
 * pressing it — the "tap to subscribe" idea rather than a star, which says
 * "favourite". Drawn here for the same reason as [MyTasksIcon]; stroked and
 * colourless, so [Icon] tints it with the menu's ink like every other row.
 */
internal val SubscriptionIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Subscription",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // The Subscribe button, a wide pill across the top.
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(5.1f, 4f)
            horizontalLineTo(18.9f)
            quadTo(21.5f, 4f, 21.5f, 6.6f)
            verticalLineTo(7.6f)
            quadTo(21.5f, 10.2f, 18.9f, 10.2f)
            horizontalLineTo(5.1f)
            quadTo(2.5f, 10.2f, 2.5f, 7.6f)
            verticalLineTo(6.6f)
            quadTo(2.5f, 4f, 5.1f, 4f)
            close()
        }
        // The pointing hand: index finger to the button, thumb out to the left —
        // the thumb is what stops the fist reading as a tag on a lanyard.
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(10.2f, 10.5f)
            verticalLineTo(14.3f)
            quadTo(7.8f, 14.7f, 7.8f, 16.7f)
            quadTo(7.8f, 18.4f, 9.3f, 19.2f)
            quadTo(9.5f, 20.6f, 11.1f, 20.6f)
            horizontalLineTo(15f)
            quadTo(17.4f, 20.6f, 17.4f, 18.3f)
            verticalLineTo(16.4f)
            quadTo(17.4f, 14.4f, 15.3f, 14.4f)
            horizontalLineTo(12.6f)
            verticalLineTo(10.5f)
            close()
        }
    }.build()
}

/**
 * The account menu's dark-mode glyph: a crescent moon. Drawn here because the
 * core icon set carries no moon (nor [LightModeIcon]'s sun); stroked and
 * colourless, so [Icon] tints it with the menu's ink.
 */
private val DarkModeIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "DarkMode",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(12f, 3f)
            arcToRelative(6f, 6f, 0f, false, false, 9f, 9f)
            arcToRelative(9f, 9f, 0f, true, true, -9f, -9f)
            close()
        }
    }.build()
}

/**
 * The account menu's light-mode glyph: a sun — the counterpart to
 * [DarkModeIcon], swapped in when the theme is light.
 */
private val LightModeIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "LightMode",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // The core disc.
            moveTo(8.2f, 12f)
            arcToRelative(3.8f, 3.8f, 0f, false, true, 7.6f, 0f)
            arcToRelative(3.8f, 3.8f, 0f, false, true, -7.6f, 0f)
            close()
            // The eight rays.
            moveTo(18.6f, 12f)
            horizontalLineTo(21f)
            moveTo(16.67f, 16.67f)
            lineTo(18.36f, 18.36f)
            moveTo(12f, 18.6f)
            verticalLineTo(21f)
            moveTo(7.33f, 16.67f)
            lineTo(5.64f, 18.36f)
            moveTo(5.4f, 12f)
            horizontalLineTo(3f)
            moveTo(7.33f, 7.33f)
            lineTo(5.64f, 5.64f)
            moveTo(12f, 5.4f)
            verticalLineTo(3f)
            moveTo(16.67f, 7.33f)
            lineTo(18.36f, 5.64f)
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
                fontWeight = AppFontWeight.Bold,
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
    onProfile: (() -> Unit)? = null,
    onMyTasks: (() -> Unit)? = null,
    /** Tasks newly handed to this user — the web sidebar's badge count. */
    myTasksNewCount: Int = 0,
    /** Move this login to another branch (web 53a2ba80). */
    onSwitchBranch: (() -> Unit)? = null,
    /** The user's own password. */
    onChangePassword: (() -> Unit)? = null,
    onAccountDeletion: (() -> Unit)? = null,
): List<AccountMenuItem> = buildList {
    add(AccountMenuItem("Dashboard", Icons.Filled.Home, onDashboard))
    onProfile?.let { add(AccountMenuItem("Profile", Icons.Filled.AccountCircle, it)) }
    // The personal todo board (web 1108dfe puts it in this same user menu).
    onMyTasks?.let {
        add(AccountMenuItem("My Tasks", MyTasksIcon, it, badgeCount = myTasksNewCount))
    }
    onSwitchBranch?.let { add(AccountMenuItem("Switch Branch", Icons.Filled.Share, it)) }
    // Devices and the plan sit with the everyday account actions; the two
    // destructive/security actions come last, in the owner's numbered order.
    add(AccountMenuItem("My Devices", MyDevicesIcon, onMyDevices))
    onSubscription?.let { add(AccountMenuItem("Subscription", SubscriptionIcon, it)) }
    onChangePassword?.let {
        add(AccountMenuItem("Change Password", Icons.Filled.Lock, it, afterToggles = true))
    }
    onAccountDeletion?.let {
        add(AccountMenuItem("Request Account Deletion", Icons.Filled.Delete, it, afterToggles = true))
    }
}

private val AvatarSize = 32.dp
private val MenuWidth = 240.dp
