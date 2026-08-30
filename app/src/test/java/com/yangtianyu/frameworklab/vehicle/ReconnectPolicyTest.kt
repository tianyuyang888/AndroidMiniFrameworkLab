package com.yangtianyu.frameworklab.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReconnectPolicyTest {
    @Test
    fun returnsOneSecondForFirstThreeAttempts() {
        assertEquals(1_000L, ReconnectPolicy.delayMs(attempt = 1))
        assertEquals(1_000L, ReconnectPolicy.delayMs(attempt = 3))
    }

    @Test
    fun stopsAfterThreeAttempts() {
        assertNull(ReconnectPolicy.delayMs(attempt = 4))
    }
}
