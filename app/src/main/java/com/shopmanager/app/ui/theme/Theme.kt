package com.shopmanager.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

private val LightColors = lightColorScheme(
    primary = Indigo40,
    onPrimary = Color.White,
    primaryContainer = Indigo80,
    onPrimaryContainer = Indigo40,
    secondary = Violet40,
    onSecondary = Color.White,
    secondaryContainer = Violet80,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    error = DangerRed,
)

private val DarkColors = darkColorScheme(
    primary = Indigo80,
    onPrimary = Color(0xFF1A1650),
    primaryContainer = Indigo40,
    onPrimaryContainer = Color.White,
    secondary = Violet80,
    onSecondary = Color(0xFF2B1466),
    secondaryContainer = Violet40,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = Color(0xFFFF6B6B),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

enum class AppThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun rememberIsDarkTheme(themeMode: AppThemeMode): Boolean {
    val systemDark = isSystemInDarkTheme()
    return when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
}

@Composable
fun ShopManagerTheme(themeMode: AppThemeMode = AppThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val useDark = rememberIsDarkTheme(themeMode)
    val colors = if (useDark) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
}

