package com.yangtianyu.frameworklab.vehicle

import org.junit.Assert.assertEquals
import org.junit.Test

class ForwardingVehicleListenerTest {
    @Test
    fun eventsAreSafeBeforeDelegateIsAttached() {
        val listener = ForwardingVehicleListener()

        listener.onConnectionChanged(VehicleConnectionStatus.CONNECTING)
        listener.onSnapshot(VehicleSnapshot.stoppedDefault())
        listener.onCommandResult(VehicleCommandResult.SUCCESS)
    }

    @Test
    fun eachClientEventIsForwardedOnce() {
        val listener = ForwardingVehicleListener()
        val snapshot = VehicleSnapshot.stoppedDefault(updatedAtElapsedRealtime = 123L)
        val recorder = RecordingListener()
        listener.delegate = recorder

        listener.onConnectionChanged(VehicleConnectionStatus.CONNECTED)
        listener.onSnapshot(snapshot)
        listener.onCommandResult(VehicleCommandResult.REJECTED_WHILE_DRIVING)

        assertEquals(listOf(VehicleConnectionStatus.CONNECTED), recorder.statuses)
        assertEquals(listOf(snapshot), recorder.snapshots)
        assertEquals(listOf(VehicleCommandResult.REJECTED_WHILE_DRIVING), recorder.results)
    }

    private class RecordingListener : VehicleServiceClient.Listener {
        val statuses = mutableListOf<VehicleConnectionStatus>()
        val snapshots = mutableListOf<VehicleSnapshot>()
        val results = mutableListOf<Int>()

        override fun onConnectionChanged(status: VehicleConnectionStatus) {
            statuses += status
        }

        override fun onSnapshot(snapshot: VehicleSnapshot) {
            snapshots += snapshot
        }

        override fun onCommandResult(result: Int) {
            results += result
        }
    }
}
