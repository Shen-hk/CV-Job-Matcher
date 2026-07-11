package com.example.tielink.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = CardLight,
    primaryContainer = PrimaryLight,
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Secondary,
    onSecondary = CardLight,
    secondaryContainer = Color(0xFFE6ECEF),
    onSecondaryContainer = Ink,
    tertiary = SignalCyan,
    onTertiary = CardLight,
    tertiaryContainer = Color(0xFFE8F0F2),
    onTertiaryContainer = Color(0xFF2C424B),
    error = MissRed,
    onError = CardLight,
    background = SurfaceLight,
    onBackground = TextPrimary,
    surface = CardLight,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceContainer,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = CardLight,
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = Color(0xFFE8EEF3),
    surfaceContainerHighest = Color(0xFFDDE6ED),
    outline = Color(0xFFC8D2DC),
    outlineVariant = BorderLight,
    inverseOnSurface = CardLight,
    inverseSurface = Ink,
    inversePrimary = PrimaryLight,
    surfaceTint = Primary,
    scrim = Ink,
    errorContainer = Color(0xFFF6E5E3),
    onErrorContainer = Color(0xFF623432)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(AppRadius.sm),
    small = RoundedCornerShape(AppRadius.sm),
    medium = RoundedCornerShape(AppRadius.md),
    large = RoundedCornerShape(AppRadius.lg),
    extraLarge = RoundedCornerShape(AppRadius.xl)
)

@Composable
fun TieLinkTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}

@Preview(showBackground = true)
@Composable
private fun TieLinkThemePreview() {
    TieLinkTheme {
        Text("Preview")
    }
}
