package org.classflow.hyperos

import android.graphics.Typeface
import java.io.File

/**
 * Provides access to Xiaomi MiSans font families on HyperOS devices with graceful system fallback.
 */
object HyperOSFontProvider {
    private const val MISANS_NORMAL_PATH = "/system/fonts/MiSans-Normal.ttf"
    private const val MISANS_MEDIUM_PATH = "/system/fonts/MiSans-Medium.ttf"

    val isMiSansAvailable: Boolean by lazy {
        File(MISANS_NORMAL_PATH).exists()
    }

    fun getRegularTypeface(): Typeface {
        return if (isMiSansAvailable) {
            try {
                Typeface.createFromFile(MISANS_NORMAL_PATH)
            } catch (e: Throwable) {
                Typeface.DEFAULT
            }
        } else {
            Typeface.DEFAULT
        }
    }

    fun getMediumTypeface(): Typeface {
        return if (isMiSansAvailable && File(MISANS_MEDIUM_PATH).exists()) {
            try {
                Typeface.createFromFile(MISANS_MEDIUM_PATH)
            } catch (e: Throwable) {
                Typeface.DEFAULT_BOLD
            }
        } else {
            Typeface.DEFAULT_BOLD
        }
    }
}
