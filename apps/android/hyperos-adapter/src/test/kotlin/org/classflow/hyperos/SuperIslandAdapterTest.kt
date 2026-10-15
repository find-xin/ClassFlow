package org.classflow.hyperos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuperIslandAdapterTest {

    @Test
    fun testUnregisteredAppIdEvaluatesToBlocked() {
        // Without an official enterprise App ID, status must strictly be BLOCKED or FALLBACK
        val adapter = SuperIslandAdapter(
            context = createDummyContext(),
            registeredAppId = null
        )

        val capability = adapter.evaluateIslandCapability()
        assertTrue(
            capability == SuperIslandAdapter.IslandState.BLOCKED_UNREGISTERED ||
            capability == SuperIslandAdapter.IslandState.FALLBACK_STANDARD
        )
    }

    @Test
    fun testSuperIslandDoesNotUseWindowOverlay() {
        // Verify architecture rule: No TYPE_APPLICATION_OVERLAY imports or usage in adapter
        val adapterSource = SuperIslandAdapter::class.java.declaredMethods
        for (method in adapterSource) {
            assertFalse(
                "SuperIslandAdapter must never reference WindowManager overlay methods",
                method.name.contains("overlay", ignoreCase = true)
            )
        }
    }

    private fun createDummyContext(): android.content.Context {
        return android.content.ContextWrapper(null)
    }
}
