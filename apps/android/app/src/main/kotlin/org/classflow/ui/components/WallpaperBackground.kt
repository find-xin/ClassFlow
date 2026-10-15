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
 * Aesthetic wallpaper background matching macOS WallpaperBackgroundView:
 * - Light Mode: Clean fluid gradient with subtle pastel violet, sky-cyan and porcelain white.
 * - Dark Mode: Deep midnight obsidian (#0F1015 to #161922) providing rich contrast and soft card outlines.
 */
@Composable
fun WallpaperBackground(
    isDark: Boolean = isSystemInDarkTheme(),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val gradientBrush = if (isDark) {
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush = gradientBrush)
    ) {
        content()
    }
}
