package com.yangtianyu.frameworklab.vehicle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleSnapshotValidatorTest {
    private val valid = VehicleSnapshot.stoppedDefault()

    @Test fun acceptsBoundaryValues() {
        assertTrue(VehicleSnapshotValidator.isValid(valid.copy(speedKph = 0, batteryPercent = 0)))
        assertTrue(VehicleSnapshotValidator.isValid(valid.copy(speedKph = 240, batteryPercent = 100)))
    }

    @Test fun rejectsOutOfRangeValues() {
        assertFalse(VehicleSnapshotValidator.isValid(valid.copy(speedKph = -1)))
        assertFalse(VehicleSnapshotValidator.isValid(valid.copy(rangeKm = 2001)))
        assertFalse(VehicleSnapshotValidator.isValid(valid.copy(temperatureCelsius = 31)))
        assertFalse(VehicleSnapshotValidator.isValid(valid.copy(fanSpeed = 8)))
    }
}
