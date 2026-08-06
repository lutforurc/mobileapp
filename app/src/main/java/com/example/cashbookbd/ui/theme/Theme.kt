package com.example.cashbookbd.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Maps one [BrandPalette] onto Material's ColorScheme.
 *
 * Every surface role is filled in from the palette — including the
 * `surfaceContainer*` family, which is what `Card`, `DropdownMenu` and
 * `AlertDialog` actually draw with. Left unset they fall back to M3's baseline
 * (a purple-tinted grey), which is how a card can end up ignoring the brand.
 *
 * The light and dark builders differ only in the few roles not set here
 * (scrims, inverse colours), so both themes are the same mapping applied to a
 * different [BrandPalette].
 */
private fun schemeOf(p: BrandPalette, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = p.primary,
        onPrimary = p.onPrimary,
        primaryContainer = p.primaryContainer,
        onPrimaryContainer = p.onPrimaryContainer,
        secondary = p.secondary,
        onSecondary = p.onSecondary,
        secondaryContainer = p.secondaryContainer,
        onSecondaryContainer = p.onSecondaryContainer,
        tertiary = p.tertiary,
        onTertiary = p.onTertiary,
        tertiaryContainer = p.tertiaryContainer,
        onTertiaryContainer = p.onTertiaryContainer,
        background = p.screen,
        onBackground = p.onScreen,
        surface = p.card,
        onSurface = p.onCard,
        surfaceVariant = p.cardMuted,
        onSurfaceVariant = p.onCardMuted,
        surfaceTint = p.primary,
        outline = p.outline,
        outlineVariant = p.outlineVariant,
        error = BrandSheet.Danger,
        onError = BrandSheet.White,
        // The surfaces Cards and menus actually paint with, stepping up in
        // lightness so nested surfaces (card > row > chip) read without borders.
        surfaceContainerLowest = p.card,
        surfaceContainerLow = p.card,
        surfaceContainer = p.card,
        surfaceContainerHigh = p.cardRow,
        surfaceContainerHighest = p.cardRaised,
        surfaceBright = p.cardRaised,
        surfaceDim = p.screen,
    )
}

private val LightScheme = schemeOf(LightPalette, dark = false)
private val DarkScheme = schemeOf(DarkPalette, dark = true)
private val DarkSchemeV2 = schemeOf(DarkPaletteV2, dark = true)

// Built once per theme so reading a token costs nothing at recomposition.
private val LightAppColors = appColorsOf(LightPalette)
private val DarkAppColors = appColorsOf(DarkPalette)
private val DarkAppColorsV2 = appColorsOf(DarkPaletteV2)

/**
 * Trial wrapper for [DarkPaletteV2]: re-themes just its [content] with the
 * reworked dark palette so one screen can preview it against the rest of the
 * app. In the light theme it is a pass-through — the trial is a dark-mode
 * question only. Delete this (and re-point [DarkPalette]) once a verdict
 * lands; screens keep reading MaterialTheme as always.
 */
@Composable
fun DarkV2Trial(content: @Composable () -> Unit) {
    if (LocalBrandPalette.current !== DarkPalette) {
        content()
        return
    }
    CompositionLocalProvider(
        LocalBrandPalette provides DarkPaletteV2,
        LocalAppColors provides DarkAppColorsV2,
    ) {
        MaterialTheme(
            colorScheme = DarkSchemeV2,
            typography = Typography,
            shapes = MaterialTheme.shapes,
            content = content,
        )
    }
}

/**
 * The app's theme. Android 12+ dynamic colour is deliberately not offered: the
 * brand colours are fixed, and letting the system derive them from the
 * wallpaper is what once turned the buttons and headers blue.
 */
@Composable
fun CashBookbdTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    val colors = if (darkTheme) DarkAppColors else LightAppColors

    CompositionLocalProvider(
        LocalBrandPalette provides palette,
        LocalAppColors provides colors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = Typography,
            // Every Material component (cards, menus, dialogs, date pickers,
            // sheets) draws from these slots — mapping them all to [AppShape]
            // is what gives the whole app the single shared corner radius.
            shapes = Shapes(
                extraSmall = AppShape,
                small = AppShape,
                medium = AppShape,
                large = AppShape,
                extraLarge = AppShape,
            ),
            content = content,
        )
    }
}
