package org.classflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Wallpaper background supporting WakeUp aesthetic styles:
 * - "anime_blue": Dreamy anime-inspired deep violet & indigo twilight palette (matching WakeUp screenshot).
 * - "default": Clean flowing pastel or midnight gradient.
 * - Adds a soft ambient scrim so white text, location tags, and cards are crystal clear.
 */
@Composable
fun WallpaperBackground(
    isDark: Boolean = isSystemInDarkTheme(),
    wallpaperPreset: String = "anime_blue",
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val backgroundBrush = when (wallpaperPreset) {
        "anime_blue" -> {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF2C2250), // Twilight violet
                    Color(0xFF22254C), // Midnight purple
                    Color(0xFF1B2042), // Deep indigo
                    Color(0xFF12142B)  // Obsidian blue
                )
            )
        }
        "forest_twilight" -> {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF172D24),
                    Color(0xFF12241D),
                    Color(0xFF0E1C16),
                    Color(0xFF0A1310)
                )
            )
        }
        else -> {
            if (isDark) {
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF111217),
                        Color(0xFF151821),
                        Color(0xFF13141B),
                        Color(0xFF0E0F14)
                    )
                )
            } else {
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF5F7FB),
                        Color(0xFFF0F4FA),
                        Color(0xFFF3F1F8),
                        Color(0xFFF8F9FC)
                    )
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush = backgroundBrush)
    ) {
        content()
    }
}
