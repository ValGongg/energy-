package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = PowerAmber,
    onPrimary = Color(0xFF1F1300),
    primaryContainer = PowerAmberContainerDark,
    onPrimaryContainer = Color(0xFFFDE68A),
    secondary = PowerOnGreen,
    onSecondary = Color(0xFF002116),
    secondaryContainer = PowerOnGreenSoft,
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = OutageRed,
    onTertiary = Color.White,
    error = OutageRed,
    onError = Color.White,
    errorContainer = OutageRedContainerDark,
    onErrorContainer = Color(0xFFFECACA),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceMuted,
    outline = DarkOutline,
)

private val LightColorScheme = lightColorScheme(
    primary = PowerAmberDark,
    onPrimary = Color.White,
    primaryContainer = PowerAmberContainerLight,
    onPrimaryContainer = Color(0xFF451A03),
    secondary = PowerOnGreen,
    onSecondary = Color.White,
    secondaryContainer = PowerOnGreenContainerLight,
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = OutageRed,
    onTertiary = Color.White,
    error = OutageRed,
    onError = Color.White,
    errorContainer = OutageRedContainerLight,
    onErrorContainer = Color(0xFF7F1D1D),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceMuted,
    outline = LightOutline,
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ZtoeTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val colorScheme = if (useDark) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
