package com.yangtianyu.frameworklab.vehicle

/** 一次意外断连后，客户端下一步应采取的动作。 */
data class ReconnectDecision(
    val status: VehicleConnectionStatus,
    val delayMs: Long?,
    val nextAttempt: Int,
)

/** 将断连状态归约为纯数据，便于独立验证重连次数边界。 */
object VehicleReconnectReducer {
    fun onUnexpectedDisconnect(started: Boolean, attemptsMade: Int): ReconnectDecision {
        if (!started) {
            return ReconnectDecision(
                status = VehicleConnectionStatus.DISCONNECTED,
                delayMs = null,
                nextAttempt = attemptsMade,
            )
        }

        val nextAttempt = attemptsMade + 1
        val delay = ReconnectPolicy.delayMs(nextAttempt)
        return if (delay == null) {
            ReconnectDecision(
                status = VehicleConnectionStatus.DISCONNECTED,
                delayMs = null,
                nextAttempt = attemptsMade,
            )
        } else {
            ReconnectDecision(
                status = VehicleConnectionStatus.RETRY_WAITING,
                delayMs = delay,
                nextAttempt = nextAttempt,
            )
        }
    }
}
