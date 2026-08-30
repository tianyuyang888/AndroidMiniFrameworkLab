package com.yangtianyu.frameworklab.vehicle

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RestartableExecutorOwnerTest {
    @Test
    fun stopShutsDownExecutorAndStartCreatesUsableReplacement() {
        val owner = RestartableExecutorOwner()
        val first = owner.acquire()

        owner.shutdownAfter { }

        assertTrue(first.awaitTermination(1, TimeUnit.SECONDS))
        val second = owner.acquire()
        assertNotSame(first, second)
        assertEquals(42, second.submit<Int> { 42 }.get(1, TimeUnit.SECONDS))

        owner.shutdownAfter { }
        assertTrue(second.awaitTermination(1, TimeUnit.SECONDS))
    }
}
