package com.yangtianyu.frameworklab.vehicle

import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleDashboardDisplayMapperTest {
    @Test
    fun doorDisplayContainsEveryDoorAndLockState() {
        val snapshot = VehicleSnapshot.stoppedDefault().copy(
            isFrontLeftDoorOpen = true,
            isRearRightDoorOpen = true,
            areDoorsLocked = false,
        )

        assertEquals(
            VehicleDoorDisplay(true, false, false, true, false),
            VehicleDashboardDisplayMapper.doorDisplay(snapshot),
        )
    }

    @Test
    fun mapsEveryCommandResultIncludingUnknown() {
        assertEquals(CommandResultMessage.SUCCESS, VehicleDashboardDisplayMapper.commandResult(0))
        assertEquals(CommandResultMessage.INVALID_ARGUMENT, VehicleDashboardDisplayMapper.commandResult(1))
        assertEquals(CommandResultMessage.REJECTED_WHILE_DRIVING, VehicleDashboardDisplayMapper.commandResult(2))
        assertEquals(CommandResultMessage.SERVICE_UNAVAILABLE, VehicleDashboardDisplayMapper.commandResult(3))
        assertEquals(CommandResultMessage.DEBUG_ONLY, VehicleDashboardDisplayMapper.commandResult(4))
        assertEquals(CommandResultMessage.UNKNOWN, VehicleDashboardDisplayMapper.commandResult(99))
        assertEquals(null, VehicleDashboardDisplayMapper.commandResult(null))
    }
}
