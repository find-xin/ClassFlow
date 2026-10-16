package org.classflow.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext

/**
 * Aesthetic wallpaper background directly cloned from macOS WallpaperBackgroundView.swift.
 * Supported presets:
 * - "default": 默认设置
 * - "aurora": 极光
 * - "midnight": 星空
 * - "warm_cream": 暖阳
 * - "mint": 薄荷
 * - "sky": 天空
 * Supports custom wallpaper image loaded from gallery/storage via customWallpaperUri.
 */
fun isWallpaperDark(preset: String, isSystemDark: Boolean, usePureColor: Boolean = false): Boolean {
    if (usePureColor) return isSystemDark
    return when (preset) {
        "aurora", "midnight" -> true
        else -> isSystemDark
    }
}

@Composable
fun WallpaperBackground(
    isDark: Boolean = isSystemInDarkTheme(),
    wallpaperPreset: String = "default",
    customWallpaperUri: String? = null,
    usePureColorBackground: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    // Safely load custom image bitmap if URI provided
    val customBitmap = remember(customWallpaperUri) {
        if (!customWallpaperUri.isNullOrBlank()) {
            try {
                val uri = Uri.parse(customWallpaperUri)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (e: Throwable) {
                null
            }
        } else {
            null
        }
    }

    val gradientColors = if (usePureColorBackground) {
        listOf(
            if (isDark) Color(0xFF0E1117) else Color(0xFFF5F7FB),
            if (isDark) Color(0xFF0E1117) else Color(0xFFF5F7FB)
        )
    } else if (isDark) {
        when (wallpaperPreset) {
            "aurora" -> listOf(Color(0xFF0A111C), Color(0xFF14223A), Color(0xFF0D332F))
            "mint" -> listOf(Color(0xFF0A1412), Color(0xFF112620), Color(0xFF0E1D19))
            "warm_cream" -> listOf(Color(0xFF161311), Color(0xFF221D18), Color(0xFF181512))
            "midnight" -> listOf(Color(0xFF08090C), Color(0xFF0F1117), Color(0xFF060709))
            "sky" -> listOf(Color(0xFF09131E), Color(0xFF0F1D2E), Color(0xFF0B1622))
            else -> listOf(Color(0xFF0F1117), Color(0xFF161922), Color(0xFF0D0F15)) // default
        }
    } else {
        when (wallpaperPreset) {
            "aurora" -> listOf(Color(0xFF1A2238), Color(0xFF283C63), Color(0xFF1B998B))
            "mint" -> listOf(Color(0xFFC6E8DB), Color(0xFFDDF4EC), Color(0xFFC3E3D6))
            "warm_cream" -> listOf(Color(0xFFEFE0CE), Color(0xFFF9F2E7), Color(0xFFE8D5BF))
            "midnight" -> listOf(Color(0xFF151821), Color(0xFF1E2433), Color(0xFF11141B))
            "sky" -> listOf(Color(0xFFE0F2FE), Color(0xFFBAE6FD), Color(0xFF7DD3FC))
            else -> listOf(Color(0xFFF1F4F9), Color(0xFFF8FAFD), Color(0xFFEDF2F8)) // default macOS light canvas
        }
    }

    val backgroundBrush = Brush.linearGradient(
        colors = gradientColors,
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush = backgroundBrush)
    ) {
        if (customBitmap != null) {
            Image(
                bitmap = customBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Translucent scrim to protect readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (isDark) 0.35f else 0.12f))
            )
        } else {
            // macOS safety baseboard: only applied for vivid presets, not needed for pure color or default
            val safetyAlpha = if (usePureColorBackground || wallpaperPreset == "default") 0f else if (isDark) 0.25f else 0.06f
            if (safetyAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = safetyAlpha))
                )
            }
        }

        content()
    }
}
