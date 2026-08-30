package com.yangtianyu.frameworklab.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeVehicleDataSourceTest {
    @Test
    fun nextSnapshotIsValidAndUsesProvidedClock() {
        val next = FakeVehicleDataSource.next(VehicleSnapshot.stoppedDefault(), nowMs = 99L)

        assertTrue(VehicleSnapshotValidator.isValid(next))
        assertEquals(99L, next.updatedAtElapsedRealtime)
        assertEquals(VehicleGear.DRIVE, next.gear)
    }
}
