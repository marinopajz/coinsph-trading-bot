package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CoinsEmerald,
    onPrimary = Color(0xFF002919),
    primaryContainer = CoinsEmeraldDark,
    onPrimaryContainer = Color(0xFFA7F3D0),

    secondary = ElectricCyan,
    onSecondary = Color(0xFF00363D),
    secondaryContainer = ElectricCyanDark,
    onSecondaryContainer = Color(0xFFCFFAFE),

    tertiary = WarningAmber,
    onTertiary = Color(0xFF452B00),

    background = CryptoBackground,
    onBackground = TextPrimary,

    surface = CryptoSurface,
    onSurface = TextPrimary,
    surfaceVariant = CryptoSurfaceVariant,
    onSurfaceVariant = TextSecondary,

    error = LossRed,
    onError = Color.White,

    outline = BorderSubtle,
    outlineVariant = BorderFocused
)

// Crypto trading terminal defaults to optimized dark theme
private val LightColorScheme = DarkColorScheme

@Composable
fun TradingBotTheme(
    darkTheme: Boolean = true, // Default to pro dark terminal theme
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
