package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT
}

private val ChatDepDarkColorScheme = darkColorScheme(
    primary = ElectricBlueBright,
    onPrimary = CrispWhite,
    primaryContainer = Color(0xFF0E2A5E),
    onPrimaryContainer = IceCyan,
    secondary = SkyBlue,
    onSecondary = ObsidianBlack,
    secondaryContainer = Color(0xFF0C2A3E),
    onSecondaryContainer = IceCyan,
    tertiary = IceCyan,
    onTertiary = ObsidianBlack,
    background = ObsidianBlack,
    onBackground = CrispWhite,
    surface = DeepNavySurface,
    onSurface = CrispWhite,
    surfaceVariant = ElevatedNavy,
    onSurfaceVariant = MutedSlateLight,
    outline = CardBorderDark,
    outlineVariant = Color(0xFF17223B),
    error = ErrorRed,
    onError = CrispWhite
)

private val ChatDepLightColorScheme = lightColorScheme(
    primary = ElectricBlueLightMode,
    onPrimary = CrispWhite,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = Color(0xFF082254),
    secondary = SkyBlueLightMode,
    onSecondary = CrispWhite,
    secondaryContainer = Color(0xFFD9F2FF),
    onSecondaryContainer = Color(0xFF07324A),
    tertiary = ElectricBlue,
    onTertiary = CrispWhite,
    background = LightBackground,
    onBackground = InkBlack,
    surface = LightSurface,
    onSurface = InkBlack,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = MutedSlateDark,
    outline = LightBorder,
    outlineVariant = Color(0xFFE2EBF7),
    error = ErrorRed,
    onError = CrispWhite
)

private val ChatDepShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ChatDepTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val colorScheme = if (darkTheme) ChatDepDarkColorScheme else ChatDepLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ChatDepTypography,
        shapes = ChatDepShapes,
        content = content
    )
}
