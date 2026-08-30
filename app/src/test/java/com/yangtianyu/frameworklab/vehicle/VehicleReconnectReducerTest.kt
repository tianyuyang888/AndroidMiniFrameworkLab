package com.yangtianyu.frameworklab.vehicle

import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleReconnectReducerTest {
    @Test
    fun firstUnexpectedDisconnectSchedulesFirstRetry() {
        assertEquals(
            ReconnectDecision(VehicleConnectionStatus.RETRY_WAITING, 1_000L, 1),
            VehicleReconnectReducer.onUnexpectedDisconnect(started = true, attemptsMade = 0),
        )
    }

    @Test
    fun thirdUnexpectedDisconnectSchedulesThirdRetry() {
        assertEquals(
            ReconnectDecision(VehicleConnectionStatus.RETRY_WAITING, 1_000L, 3),
            VehicleReconnectReducer.onUnexpectedDisconnect(started = true, attemptsMade = 2),
        )
    }

    @Test
    fun fourthUnexpectedDisconnectStopsRetrying() {
        assertEquals(
            ReconnectDecision(VehicleConnectionStatus.DISCONNECTED, null, 3),
            VehicleReconnectReducer.onUnexpectedDisconnect(started = true, attemptsMade = 3),
        )
    }

    @Test
    fun stoppedClientDoesNotRetry() {
        assertEquals(
            ReconnectDecision(VehicleConnectionStatus.DISCONNECTED, null, 2),
            VehicleReconnectReducer.onUnexpectedDisconnect(started = false, attemptsMade = 2),
        )
    }
}
