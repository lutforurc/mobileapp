package com.example.cashbookbd.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Brand.DarkPrimary,
    onPrimary = Brand.DarkOnPrimary,
    primaryContainer = Brand.DarkPrimaryContainer,
    onPrimaryContainer = Brand.DarkOnPrimaryContainer,
    secondary = Brand.DarkSecondary,
    onSecondary = Brand.DarkOnSecondary,
    secondaryContainer = Brand.DarkSecondaryContainer,
    onSecondaryContainer = Brand.DarkOnSecondaryContainer,
    tertiary = Brand.DarkTertiary,
    onTertiary = Brand.DarkOnTertiary,
    tertiaryContainer = Brand.DarkTertiaryContainer,
    onTertiaryContainer = Brand.DarkOnTertiaryContainer,
    background = Brand.DarkBackground,
    onBackground = Brand.DarkOnBackground,
    surface = Brand.DarkSurface,
    onSurface = Brand.DarkOnSurface,
    surfaceVariant = Brand.DarkSurfaceVariant,
    onSurfaceVariant = Brand.DarkOnSurfaceVariant,
    outline = Brand.DarkOutline,
    outlineVariant = Brand.DarkOutlineVariant,
)

private val LightColorScheme = lightColorScheme(
    primary = Brand.Primary,
    onPrimary = Brand.OnPrimary,
    primaryContainer = Brand.PrimaryContainer,
    onPrimaryContainer = Brand.OnPrimaryContainer,
    secondary = Brand.Secondary,
    onSecondary = Brand.OnSecondary,
    secondaryContainer = Brand.SecondaryContainer,
    onSecondaryContainer = Brand.OnSecondaryContainer,
    tertiary = Brand.Tertiary,
    onTertiary = Brand.OnTertiary,
    tertiaryContainer = Brand.TertiaryContainer,
    onTertiaryContainer = Brand.OnTertiaryContainer,
    background = Brand.Background,
    onBackground = Brand.OnBackground,
    surface = Brand.Surface,
    onSurface = Brand.OnSurface,
    surfaceVariant = Brand.SurfaceVariant,
    onSurfaceVariant = Brand.OnSurfaceVariant,
    outline = Brand.Outline,
    outlineVariant = Brand.OutlineVariant,
)

@Composable
fun CashBookbdTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off by default: the brand colour is a fixed green, so the app must not let
    // Android 12+ derive primary from the wallpaper (which is what made the
    // buttons and headers come out blue). Kept as a parameter only so a preview
    // can opt back in if ever needed.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(
        LocalAppAccents provides if (darkTheme) DarkAccents else LightAccents,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}