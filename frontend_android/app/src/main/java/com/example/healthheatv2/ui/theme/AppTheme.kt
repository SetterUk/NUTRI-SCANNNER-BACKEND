package com.example.healthheatv2.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

// ────────────────────────────────────────────────
//  Color Tokens
// ────────────────────────────────────────────────
data class AppColors(
    val background: Color,
    val surface: Color,
    val card: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textHint: Color,
    val accentGreen: Color,
    val accentRed: Color,
    val accentAmber: Color,
    val accentBlue: Color,
    val isDark: Boolean
) {
    // Derived convenience colors
    val accentGreenSubtle get() = accentGreen.copy(alpha = 0.12f)
    val accentRedSubtle get() = accentRed.copy(alpha = 0.12f)
    val accentAmberSubtle get() = accentAmber.copy(alpha = 0.12f)
    val accentBlueSubtle get() = accentBlue.copy(alpha = 0.12f)
    val divider get() = textSecondary.copy(alpha = 0.1f)
}

// ────────────────────────────────────────────────
//  Dark Theme  (Pure Obsidian Black)
// ────────────────────────────────────────────────
val DarkAppColors = AppColors(
    background  = Color(0xFF000000), // Pure Black (Native OLED look)
    surface     = Color(0xFF1C1C1E), // Deep Gray (iOS style)
    card        = Color(0xFF1C1C1E),
    border      = Color(0x20FFFFFF),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF9A9A9A),
    textHint    = Color(0xFF5A5A5A),
    accentGreen = Color(0xFF2ECC71),
    accentRed   = Color(0xFFE74C3C),
    accentAmber = Color(0xFFF39C12),
    accentBlue  = Color(0xFF3498DB),
    isDark      = true
)

// ────────────────────────────────────────────────
//  Light Theme  (Warm White / Cream)
// ────────────────────────────────────────────────
val LightAppColors = AppColors(
    background  = Color(0xFFF8F7F4),
    surface     = Color(0xFFFFFFFF),
    card        = Color(0xFFF2F0EC),
    border      = Color(0xFFE5E5E5),
    textPrimary = Color(0xFF1A1A1A),
    textSecondary = Color(0xFF6B6B6B),
    textHint    = Color(0xFFB0B0B0),
    accentGreen = Color(0xFF27AE60),
    accentRed   = Color(0xFFC0392B),
    accentAmber = Color(0xFFE67E22),
    accentBlue  = Color(0xFF2980B9),
    isDark      = false
)

// ────────────────────────────────────────────────
//  CompositionLocal
// ────────────────────────────────────────────────
val LocalAppColors = compositionLocalOf { DarkAppColors }

@Composable
fun AppTheme(
    isDark: Boolean,
    content: @Composable () -> Unit
) {
    val colors = if (isDark) DarkAppColors else LightAppColors
    CompositionLocalProvider(
        LocalAppColors provides colors,
        content = content
    )
}
