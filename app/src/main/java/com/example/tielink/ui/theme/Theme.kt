package com.example.tielink.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.example.tielink.ui.theme.Primary
import com.example.tielink.ui.theme.PrimaryDark
import com.example.tielink.ui.theme.PrimaryLight
import com.example.tielink.ui.theme.Secondary
import com.example.tielink.ui.theme.SurfaceLight
import com.example.tielink.ui.theme.CardLight
import com.example.tielink.ui.theme.MatchGreen
import com.example.tielink.ui.theme.WarningOrange
import com.example.tielink.ui.theme.MissRed

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = CardLight,
    primaryContainer = PrimaryLight,
    onPrimaryContainer = PrimaryDark,
    secondary = Secondary,
    onSecondary = CardLight,
    tertiary = MatchGreen,
    onTertiary = CardLight,
    error = MissRed,
    onError = CardLight,
    background = SurfaceLight,
    onBackground = Color(0xFF162033),
    surface = CardLight,
    onSurface = Color(0xFF162033),
    surfaceVariant = Color(0xFFE8EEF7),
    onSurfaceVariant = Color(0xFF55657A),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    inverseOnSurface = CardLight,
    inverseSurface = Color(0xFF1E293B),
    inversePrimary = PrimaryLight,
    surfaceTint = Primary,
    scrim = Color(0xFF0F172A),
    tertiaryContainer = WarningOrange,
    onTertiaryContainer = Color(0xFF3B2F00),
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
