package com.yangtianyu.frameworklab.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleSimulationPresetBuilderTest {
    private val current = VehicleSnapshot.stoppedDefault(100L)

    @Test
    fun buildsParkedPreset() {
        val result = VehicleSimulationPresetBuilder.build(VehicleSimulationPreset.PARKED, current, 200L)
        assertEquals(0, result.speedKph)
        assertEquals(VehicleGear.PARK, result.gear)
        assertFalse(result.isRearRightDoorOpen)
        assertEquals(200L, result.updatedAtElapsedRealtime)
    }

    @Test
    fun buildsDrivingPreset() {
        val result = VehicleSimulationPresetBuilder.build(VehicleSimulationPreset.DRIVING, current, 200L)
        assertEquals(40, result.speedKph)
        assertEquals(VehicleGear.DRIVE, result.gear)
    }

    @Test
    fun buildsRearRightDoorOpenPresetWhileParked() {
        val result = VehicleSimulationPresetBuilder.build(
            VehicleSimulationPreset.REAR_RIGHT_DOOR_OPEN,
            current,
            200L,
        )
        assertEquals(0, result.speedKph)
        assertEquals(VehicleGear.PARK, result.gear)
        assertTrue(result.isRearRightDoorOpen)
    }
}
