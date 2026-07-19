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
 * The app's green brand palette. Everything that reads as "the brand colour" —
 * filled buttons, the report/list table headers, primary icons — comes from
 * `colorScheme.primary`, so these values set that colour in one place. A Material
 * green ramp: a deep green on light surfaces, a lighter one on dark, each with a
 * matching on-colour and container pair for contrast in both themes.
 */
object GreenBrand {
    // Light
    val Primary = Color(0xFF2E7D32)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = Color(0xFFB6F2B6)
    val OnPrimaryContainer = Color(0xFF002106)
    val Secondary = Color(0xFF52634F)
    val OnSecondary = Color(0xFFFFFFFF)
    val SecondaryContainer = Color(0xFFD5E8CF)
    val OnSecondaryContainer = Color(0xFF101F0F)
    val Tertiary = Color(0xFF38656A)
    val OnTertiary = Color(0xFFFFFFFF)
    val TertiaryContainer = Color(0xFFBCEBF0)
    val OnTertiaryContainer = Color(0xFF002023)
    val Background = Color(0xFFFCFDF7)
    val OnBackground = Color(0xFF1A1C19)
    val Surface = Color(0xFFFCFDF7)
    val OnSurface = Color(0xFF1A1C19)
    val SurfaceVariant = Color(0xFFDEE5D9)
    val OnSurfaceVariant = Color(0xFF424940)
    val Outline = Color(0xFF72796F)
    val OutlineVariant = Color(0xFFC2C8BC)

    // Dark
    val DarkPrimary = Color(0xFF9BD49B)
    val DarkOnPrimary = Color(0xFF003910)
    val DarkPrimaryContainer = Color(0xFF14531D)
    val DarkOnPrimaryContainer = Color(0xFFB6F2B6)
    val DarkSecondary = Color(0xFFB9CCB4)
    val DarkOnSecondary = Color(0xFF243424)
    val DarkSecondaryContainer = Color(0xFF3A4B37)
    val DarkOnSecondaryContainer = Color(0xFFD5E8CF)
    val DarkTertiary = Color(0xFFA0CFD4)
    val DarkOnTertiary = Color(0xFF00363B)
    val DarkTertiaryContainer = Color(0xFF1E4D52)
    val DarkOnTertiaryContainer = Color(0xFFBCEBF0)
    val DarkBackground = Color(0xFF1A1C19)
    val DarkOnBackground = Color(0xFFE2E3DD)
    val DarkSurface = Color(0xFF1A1C19)
    val DarkOnSurface = Color(0xFFE2E3DD)
    val DarkSurfaceVariant = Color(0xFF424940)
    val DarkOnSurfaceVariant = Color(0xFFC2C9BD)
    val DarkOutline = Color(0xFF8C9388)
    val DarkOutlineVariant = Color(0xFF424940)
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