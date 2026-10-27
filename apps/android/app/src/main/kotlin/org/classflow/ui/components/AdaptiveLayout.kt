package org.classflow.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity

/** Window-based policy: a tablet in a narrow split-screen keeps the compact controls. */
data class AdaptiveLayout(val desktopToolbar: Boolean = false) {
    val axisWidth: Int get() = if (desktopToolbar) 64 else 44
    val gridPadding: Int get() = if (desktopToolbar) 20 else 8

    companion object {
        fun forWindow(widthDp: Float, fontScale: Float = 1f): AdaptiveLayout =
            AdaptiveLayout(widthDp >= 840f * fontScale.coerceAtLeast(1f))
    }
}

val LocalAdaptiveLayout = staticCompositionLocalOf { AdaptiveLayout() }

@Composable
fun AdaptiveLayoutProvider(content: @Composable () -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalAdaptiveLayout provides AdaptiveLayout.forWindow(maxWidth.value, fontScale)
        ) { content() }
    }
}
