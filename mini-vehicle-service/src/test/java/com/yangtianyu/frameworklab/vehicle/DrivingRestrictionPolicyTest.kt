package com.yangtianyu.frameworklab.vehicle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingRestrictionPolicyTest {
    private val parked = VehicleSnapshot.stoppedDefault()

    @Test
    fun parkedVehicleAllowsDoorUnlockAndSimulationEditing() {
        assertTrue(DrivingRestrictionPolicy.canUnlockAllDoors(parked))
        assertTrue(DrivingRestrictionPolicy.canEditSimulation(parked))
    }

    @Test
    fun movingOrDriveGearRejectsDangerousCommands() {
        assertFalse(DrivingRestrictionPolicy.canUnlockAllDoors(parked.copy(speedKph = 1)))
        assertFalse(DrivingRestrictionPolicy.canUnlockAllDoors(parked.copy(gear = VehicleGear.DRIVE)))
        assertFalse(DrivingRestrictionPolicy.canEditSimulation(parked.copy(gear = VehicleGear.REVERSE)))
    }
}
