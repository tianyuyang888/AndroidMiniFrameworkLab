package com.yangtianyu.frameworklab.vehicle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleClientGenerationTrackerTest {
    @Test
    fun oldGenerationSnapshotIsRejectedAfterNewConnectionStarts() {
        val tracker = VehicleClientGenerationTracker()
        tracker.start()
        val oldGeneration = tracker.nextConnection()
        val currentGeneration = tracker.nextConnection()

        assertFalse(tracker.isCurrent(oldGeneration))
        assertTrue(tracker.isCurrent(currentGeneration))
    }

    @Test
    fun oldServiceConnectionDisconnectIsRejectedAfterNewConnectionStarts() {
        val tracker = VehicleClientGenerationTracker()
        tracker.start()
        val oldConnection = tracker.nextConnection()
        tracker.nextConnection()

        assertFalse(tracker.isCurrent(oldConnection))
    }

    @Test
    fun stopRejectsLateCallbacksAndDisconnectEvents() {
        val tracker = VehicleClientGenerationTracker()
        tracker.start()
        val stoppedGeneration = tracker.nextConnection()

        tracker.stop()

        assertFalse(tracker.isStarted)
        assertFalse(tracker.isCurrent(stoppedGeneration))
    }
}
