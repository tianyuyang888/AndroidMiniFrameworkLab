package com.yangtianyu.frameworklab.vehicle

import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleConnectionInitializerTest {
    @Test
    fun initializationLinksDeathThenRegistersCallbackWithoutSnapshotQuery() {
        val operations = mutableListOf<String>()
        val initializer = VehicleConnectionInitializer(
            linkToDeath = { operations += "link" },
            registerCallback = { operations += "register" },
        )

        initializer.initialize()

        assertEquals(listOf("link", "register"), operations)
    }

    @Test(expected = IllegalStateException::class)
    fun registrationFailureIsPropagatedToConnectionBoundary() {
        val initializer = VehicleConnectionInitializer(
            linkToDeath = {},
            registerCallback = { throw IllegalStateException("register failed") },
        )

        initializer.initialize()
    }
}
