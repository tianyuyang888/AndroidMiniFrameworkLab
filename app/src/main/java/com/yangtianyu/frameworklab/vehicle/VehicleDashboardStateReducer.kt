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
        // 只有已连接且时间差严格超过三秒时才算过期，三秒边界仍视为新鲜。
        val stale = status != VehicleConnectionStatus.CONNECTED ||
            snapshot == null ||
            nowMs - snapshot.updatedAtElapsedRealtime > STALE_AFTER_MS
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
