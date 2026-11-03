package org.classflow.widgets

import kotlin.math.pow

/** Consistent foreground for the actual stored RGB, independent of the device resource theme. */
object WidgetCardStyle {
    fun rgb(hex: String): Int {
        val value = hex.removePrefix("#")
        return value.takeLast(6).toLongOrNull(16)?.toInt()?.takeIf { value.length == 6 || value.length == 8 } ?: 0x4F46E5
    }

    fun useDarkText(rgb: Int): Boolean {
        fun linear(channel: Int): Double {
            val value = channel / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        val luminance = 0.2126 * linear((rgb shr 16) and 255) + 0.7152 * linear((rgb shr 8) and 255) + 0.0722 * linear(rgb and 255)
        return luminance > 0.179
    }
}
