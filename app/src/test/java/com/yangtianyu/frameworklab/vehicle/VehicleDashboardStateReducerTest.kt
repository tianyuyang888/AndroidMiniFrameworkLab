package com.yangtianyu.frameworklab.vehicle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleDashboardStateReducerTest {
    private val snapshot = VehicleSnapshot.stoppedDefault().copy(updatedAtElapsedRealtime = 1_000L)

    @Test
    fun connectedFreshParkedStateAllowsDoorUnlockAndSimulationEditing() {
        val state = VehicleDashboardStateReducer.reduce(
            status = VehicleConnectionStatus.CONNECTED,
            snapshot = snapshot,
            nowMs = 2_000L,
            commandResult = null,
        )

        assertFalse(state.isDataStale)
        assertTrue(state.canUnlockAllDoors)
        assertTrue(state.canEditSimulation)
    }

    @Test
    fun disconnectedOrOldSnapshotIsStale() {
        assertTrue(
            VehicleDashboardStateReducer.reduce(
                VehicleConnectionStatus.DISCONNECTED,
                snapshot,
                2_000L,
                null,
            ).isDataStale,
        )
        assertTrue(
            VehicleDashboardStateReducer.reduce(
                VehicleConnectionStatus.CONNECTED,
                snapshot,
                4_001L,
                null,
            ).isDataStale,
        )
    }

    @Test
    fun snapshotExactlyThreeSecondsOldIsStillFresh() {
        assertFalse(
            VehicleDashboardStateReducer.reduce(
                VehicleConnectionStatus.CONNECTED,
                snapshot,
                4_000L,
                null,
            ).isDataStale,
        )
    }

    @Test
    fun movingStateDisablesDoorUnlockAndSimulationEditing() {
        val moving = snapshot.copy(speedKph = 20, gear = VehicleGear.DRIVE)
        val state = VehicleDashboardStateReducer.reduce(
            VehicleConnectionStatus.CONNECTED,
            moving,
            2_000L,
            null,
        )

        assertFalse(state.canUnlockAllDoors)
        assertFalse(state.canEditSimulation)
    }
}
