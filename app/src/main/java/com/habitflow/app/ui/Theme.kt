package com.habitflow.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

val Success = Color(0xFF22C55E)
val Warn = Color(0xFFF59E0B)
val Danger = Color(0xFFEF4444)

class Palette(val name: String, val primary: Color)

val Palettes = listOf(
    Palette("Ocean", Color(0xFF3B82F6)),
    Palette("Emerald", Color(0xFF10B981)),
    Palette("Violet", Color(0xFF8B5CF6)),
    Palette("Sunset", Color(0xFFF97316)),
    Palette("Rose", Color(0xFFEC4899))
)

val HabitColors = listOf(
    0xFF3B82F6, 0xFF10B981, 0xFF8B5CF6, 0xFFF97316, 0xFFEC4899,
    0xFFEF4444, 0xFFEAB308, 0xFF06B6D4, 0xFF84CC16, 0xFF64748B
).map { it.toInt() }

@Composable
fun HabitTheme(themeMode: Int, palette: Int, content: @Composable () -> Unit) {
    val dark = when (themeMode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val p = Palettes.getOrElse(palette) { Palettes[0] }.primary
    val scheme = if (dark) darkColorScheme(
        primary = p, onPrimary = Color.White,
        background = Color(0xFF0B0B0D), onBackground = Color(0xFFF2F2F5),
        surface = Color(0xFF161618), onSurface = Color(0xFFF2F2F5),
        surfaceVariant = Color(0xFF26262A), onSurfaceVariant = Color(0xFFA1A1AA),
        surfaceContainerLowest = Color(0xFF0B0B0D), surfaceContainerLow = Color(0xFF121214),
        surfaceContainer = Color(0xFF161618), surfaceContainerHigh = Color(0xFF1F1F22),
        surfaceContainerHighest = Color(0xFF26262A),
        outline = Color(0xFF3A3A40), outlineVariant = Color(0xFF2A2A2F)
    ) else lightColorScheme(
        primary = p, onPrimary = Color.White,
        background = Color(0xFFF5F6FA), onBackground = Color(0xFF16161A),
        surface = Color.White, onSurface = Color(0xFF16161A),
        surfaceVariant = Color(0xFFECEEF3), onSurfaceVariant = Color(0xFF6B7280),
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8F9FC),
        surfaceContainer = Color(0xFFF1F3F8), surfaceContainerHigh = Color(0xFFECEEF3),
        surfaceContainerHighest = Color(0xFFE6E9F0),
        outline = Color(0xFFD1D5DB), outlineVariant = Color(0xFFE5E7EB)
    )
    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}

@Composable
fun isDarkNow(themeMode: Int): Boolean = when (themeMode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
