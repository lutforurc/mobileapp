package com.example.cashbookbd.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

/**
 * The app's blue brand palette. Everything that reads as "the brand colour" —
 * filled buttons, the report/list table headers, primary icons — comes from
 * `colorScheme.primary`, so these values set that colour in one place.
 *
 * The two primaries are the app owner's chosen blues: a mid steel-blue on light
 * (white text sits on it), a lighter sky-blue on dark (which needs *dark* text,
 * per Material's dark convention — white would be unreadable on it). The rest is
 * a coordinated blue ramp with on-colours and containers for contrast in both
 * themes. Backgrounds stay a neutral near-white / near-black so the blue leads.
 */
object Brand {
    // Light — primary #458FB4
    val Primary = Color(0xFF458FB4)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = Color(0xFFCCE7F5)
    val OnPrimaryContainer = Color(0xFF08293A)
    val Secondary = Color(0xFF50606B)
    val OnSecondary = Color(0xFFFFFFFF)
    val SecondaryContainer = Color(0xFFD4E4EF)
    val OnSecondaryContainer = Color(0xFF0C1D27)
    val Tertiary = Color(0xFF3F6473)
    val OnTertiary = Color(0xFFFFFFFF)
    val TertiaryContainer = Color(0xFFC2E8FA)
    val OnTertiaryContainer = Color(0xFF001F2A)
    val Background = Color(0xFFF8FAFC)
    val OnBackground = Color(0xFF191C1E)
    val Surface = Color(0xFFF8FAFC)
    val OnSurface = Color(0xFF191C1E)
    val SurfaceVariant = Color(0xFFDCE3E9)
    val OnSurfaceVariant = Color(0xFF41484D)
    val Outline = Color(0xFF71787E)
    val OutlineVariant = Color(0xFFC1C7CD)

    // Dark — primary #7BB4D2. A neutral near-black charcoal (no colour cast) lets
    // the blue lead; surfaces step up in lightness (background < surface <
    // surfaceVariant) so cards and fields separate without borders. The primary
    // is light, so its on-colour is a dark navy — white would vanish on it.
    val DarkPrimary = Color(0xFF7BB4D2)
    val DarkOnPrimary = Color(0xFF08344A)
    val DarkPrimaryContainer = Color(0xFF264C60)
    val DarkOnPrimaryContainer = Color(0xFFCCE7F5)
    val DarkSecondary = Color(0xFFB6C9D6)
    val DarkOnSecondary = Color(0xFF20333F)
    val DarkSecondaryContainer = Color(0xFF374A56)
    val DarkOnSecondaryContainer = Color(0xFFD4E4EF)
    val DarkTertiary = Color(0xFFA6CDE0)
    val DarkOnTertiary = Color(0xFF0A3445)
    val DarkTertiaryContainer = Color(0xFF25495C)
    val DarkOnTertiaryContainer = Color(0xFFC2E8FA)
    val DarkBackground = Color(0xFF101416)
    val DarkOnBackground = Color(0xFFE1E3E5)
    val DarkSurface = Color(0xFF181C1F)
    val DarkOnSurface = Color(0xFFE1E3E5)
    val DarkSurfaceVariant = Color(0xFF2A2F33)
    val DarkOnSurfaceVariant = Color(0xFFC0C7CD)
    val DarkOutline = Color(0xFF8A9197)
    val DarkOutlineVariant = Color(0xFF3A4045)
}

/**
 * Brand accent colours used by the dashboard cards. These sit outside the M3
 * [androidx.compose.material3.ColorScheme], so they carry explicit light/dark
 * variants (darker/saturated on light surfaces, lighter on dark ones) to keep
 * adequate contrast in both themes.
 *
 * Provided via [LocalAppAccents] from `CashBookbdTheme`; read as
 * `MaterialTheme.accents`.
 */
@Immutable
data class AppAccents(
    val blue: Color,
    val green: Color,
    val red: Color,
    val purple: Color,
    val amber: Color,
    /** H/O panel title. */
    val rose: Color,
)

val LightAccents = AppAccents(
    blue = Color(0xFF2F80ED),
    green = Color(0xFF2F9E44),
    red = Color(0xFFE03131),
    purple = Color(0xFF7048E8),
    amber = Color(0xFFE8890C),
    rose = Color(0xFF8E244D),
)

val DarkAccents = AppAccents(
    blue = Color(0xFF6EA8FE),
    green = Color(0xFF51CF66),
    red = Color(0xFFFF6B6B),
    purple = Color(0xFF9775FA),
    amber = Color(0xFFFFC078),
    rose = Color(0xFFE896B3),
)

val LocalAppAccents = staticCompositionLocalOf { LightAccents }

/** Brand accents for the current theme. */
val MaterialTheme.accents: AppAccents
    @Composable
    @ReadOnlyComposable
    get() = LocalAppAccents.current