package com.yangtianyu.frameworklab.vehicle

/** 将连接、快照和指令结果归约为页面唯一可信状态。 */
object VehicleDashboardStateReducer {
    private const val STALE_AFTER_MS = 3_000L

    fun reduce(
        status: VehicleConnectionStatus,
        snapshot: VehicleSnapshot?,
        nowMs: Long,
        commandResult: Int?,
    ): VehicleDashboardUiState {
        val ageMs = snapshot?.let { nowMs - it.updatedAtElapsedRealtime }
        // 未来时间戳不能证明数据新鲜，必须按过期处理以免意外开放危险操作。
        val stale = status != VehicleConnectionStatus.CONNECTED ||
            ageMs == null ||
            ageMs < 0L ||
            ageMs > STALE_AFTER_MS
        return VehicleDashboardUiState(
            connectionStatus = status,
            snapshot = snapshot,
            isDataStale = stale,
            canUnlockAllDoors = !stale &&
                snapshot?.let(DrivingRestrictionPolicy::canUnlockAllDoors) == true,
            canEditSimulation = !stale &&
                snapshot?.let(DrivingRestrictionPolicy::canEditSimulation) == true,
            commandResult = commandResult,
        )
    }
}
