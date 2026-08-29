package com.yangtianyu.frameworklab.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleStateStoreTest {
    @Test
    fun invalidUpdateKeepsLastValidSnapshot() {
        val initial = VehicleSnapshot.stoppedDefault()
        val store = VehicleStateStore(initial)

        assertFalse(store.updateIfValid(initial.copy(speedKph = 241)))
        assertEquals(initial, store.current())
    }

    @Test
    fun validUpdateBecomesCurrent() {
        val initial = VehicleSnapshot.stoppedDefault()
        val updated = initial.copy(temperatureCelsius = 23)
        val store = VehicleStateStore(initial)

        assertTrue(store.updateIfValid(updated))
        assertEquals(updated, store.current())
    }
}
