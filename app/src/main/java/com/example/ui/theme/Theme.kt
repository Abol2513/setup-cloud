package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val SetupCloudDarkColorScheme = darkColorScheme(
    primary = CloudflareOrange,
    onPrimary = ObsidianBg,
    primaryContainer = CloudflareOrangeContainer,
    onPrimaryContainer = CloudflareOrange,
    secondary = ElectricCyan,
    onSecondary = ObsidianBg,
    secondaryContainer = ElectricCyanContainer,
    onSecondaryContainer = ElectricCyan,
    tertiary = StatusSuccess,
    onTertiary = ObsidianBg,
    tertiaryContainer = StatusSuccessContainer,
    onTertiaryContainer = StatusSuccess,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = CodeSurface,
    surfaceContainerLow = SlateSurface,
    surfaceContainer = SlateElevated,
    surfaceContainerHigh = SlateHighlight,
    outline = SlateBorder,
    outlineVariant = ColorTokens.SubtleDivider,
    error = StatusError,
    onError = TextPrimary,
    errorContainer = StatusErrorContainer,
    onErrorContainer = StatusError
)

private object ColorTokens {
    val SubtleDivider = androidx.compose.ui.graphics.Color(0xFF1E293B)
}

val SetupCloudShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun SetupCloudTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SetupCloudDarkColorScheme,
        typography = SetupCloudTypography,
        shapes = SetupCloudShapes,
        content = content
    )
}
