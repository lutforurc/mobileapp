package com.example.cashbookbd.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
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

    CompositionLocalProvider(LocalBrandPalette provides palette) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = Typography,
            content = content,
        )
    }
}
