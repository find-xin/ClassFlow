package org.classflow

import org.classflow.ui.components.AdaptiveLayout
import org.junit.Assert.*
import org.junit.Test

class AdaptiveLayoutTest {
    @Test fun tabletUsesAvailableWindowRatherThanDeviceIdentity() {
        assertTrue(AdaptiveLayout.forWindow(1280f).desktopToolbar)
        assertTrue(AdaptiveLayout.forWindow(840f).desktopToolbar)
        assertFalse(AdaptiveLayout.forWindow(839f).desktopToolbar)
        assertFalse(AdaptiveLayout.forWindow(600f).desktopToolbar)
        assertFalse(AdaptiveLayout.forWindow(393f).desktopToolbar)
    }

    @Test fun largeTextFallsBackBeforeToolbarOverflows() {
        assertFalse(AdaptiveLayout.forWindow(1000f, 1.5f).desktopToolbar)
        assertTrue(AdaptiveLayout.forWindow(1280f, 1.5f).desktopToolbar)
        assertFalse(AdaptiveLayout.forWindow(800f, 0.85f).desktopToolbar)
    }
}
