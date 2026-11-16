package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val DarkColorScheme = darkColorScheme(
    primary = PurePrimary,
    onPrimary = OnPurePrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = SecondaryGray,
    onSecondary = OnSecondaryGray,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = TertiarySubtle,
    onTertiary = OnTertiarySubtle,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    error = ErrorMonochrome,
    onError = OnErrorMonochrome,
    errorContainer = ErrorContainerMono,
    onErrorContainer = OnErrorContainerMono,
    background = SurfaceBase,
    onBackground = OnSurfacePrimary,
    surface = SurfaceBase,
    onSurface = OnSurfacePrimary,
    surfaceVariant = SurfaceContainerHigh,
    onSurfaceVariant = OnSurfaceSecondary,
    surfaceDim = SurfaceContainerLowest,
    surfaceBright = SurfaceContainerHighest,
    surfaceContainerLowest = SurfaceContainerLowest,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
    outline = OutlineStrong,
    outlineVariant = OutlineSubtle
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
