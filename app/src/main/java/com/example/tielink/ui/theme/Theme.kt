package com.example.tielink.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = CardLight,
    primaryContainer = PrimaryLight,
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Secondary,
    onSecondary = CardLight,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Ink,
    tertiary = PrimaryDark,
    onTertiary = CardLight,
    tertiaryContainer = PrimarySoft,
    onTertiaryContainer = PrimaryDark,
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
    surfaceContainerHigh = Color(0xFFEFF4FB),
    surfaceContainerHighest = Color(0xFFE2E8F0),
    outline = Color(0xFFCBD5E1),
    outlineVariant = BorderLight,
    inverseOnSurface = CardLight,
    inverseSurface = Ink,
    inversePrimary = PrimaryLight,
    surfaceTint = Primary,
    scrim = Ink,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
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
