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