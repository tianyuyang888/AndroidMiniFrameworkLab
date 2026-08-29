package com.yangtianyu.frameworklab.vehicle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugAccessPolicyTest {
    @Test
    fun allowsOnlyDebuggableBuild() {
        assertTrue(DebugAccessPolicy.isAllowed(isDebuggable = true))
        assertFalse(DebugAccessPolicy.isAllowed(isDebuggable = false))
    }
}
