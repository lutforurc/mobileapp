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
    primary = GreenBrand.DarkPrimary,
    onPrimary = GreenBrand.DarkOnPrimary,
    primaryContainer = GreenBrand.DarkPrimaryContainer,
    onPrimaryContainer = GreenBrand.DarkOnPrimaryContainer,
    secondary = GreenBrand.DarkSecondary,
    onSecondary = GreenBrand.DarkOnSecondary,
    secondaryContainer = GreenBrand.DarkSecondaryContainer,
    onSecondaryContainer = GreenBrand.DarkOnSecondaryContainer,
    tertiary = GreenBrand.DarkTertiary,
    onTertiary = GreenBrand.DarkOnTertiary,
    tertiaryContainer = GreenBrand.DarkTertiaryContainer,
    onTertiaryContainer = GreenBrand.DarkOnTertiaryContainer,
    background = GreenBrand.DarkBackground,
    onBackground = GreenBrand.DarkOnBackground,
    surface = GreenBrand.DarkSurface,
    onSurface = GreenBrand.DarkOnSurface,
    surfaceVariant = GreenBrand.DarkSurfaceVariant,
    onSurfaceVariant = GreenBrand.DarkOnSurfaceVariant,
    outline = GreenBrand.DarkOutline,
    outlineVariant = GreenBrand.DarkOutlineVariant,
)

private val LightColorScheme = lightColorScheme(
    primary = GreenBrand.Primary,
    onPrimary = GreenBrand.OnPrimary,
    primaryContainer = GreenBrand.PrimaryContainer,
    onPrimaryContainer = GreenBrand.OnPrimaryContainer,
    secondary = GreenBrand.Secondary,
    onSecondary = GreenBrand.OnSecondary,
    secondaryContainer = GreenBrand.SecondaryContainer,
    onSecondaryContainer = GreenBrand.OnSecondaryContainer,
    tertiary = GreenBrand.Tertiary,
    onTertiary = GreenBrand.OnTertiary,
    tertiaryContainer = GreenBrand.TertiaryContainer,
    onTertiaryContainer = GreenBrand.OnTertiaryContainer,
    background = GreenBrand.Background,
    onBackground = GreenBrand.OnBackground,
    surface = GreenBrand.Surface,
    onSurface = GreenBrand.OnSurface,
    surfaceVariant = GreenBrand.SurfaceVariant,
    onSurfaceVariant = GreenBrand.OnSurfaceVariant,
    outline = GreenBrand.Outline,
    outlineVariant = GreenBrand.OutlineVariant,
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