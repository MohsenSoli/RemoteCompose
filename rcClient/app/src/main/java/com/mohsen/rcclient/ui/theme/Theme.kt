package com.mohsen.rcclient.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AccentBlueLight,
    background = BackgroundDark,
    surface = SurfaceDark,
    onBackground = TextDark,
    onSurface = TextDark,
    onSurfaceVariant = MutedDark,
)

private val LightColorScheme = lightColorScheme(
    primary = AccentBlue,
    background = BackgroundLight,
    surface = SurfaceLight,
    onBackground = TextLight,
    onSurface = TextLight,
    onSurfaceVariant = MutedLight,
)

@Composable
fun RcClientTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
