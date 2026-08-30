package com.yangtianyu.frameworklab.vehicle

/** Dashboard 渲染所需的完整不可变状态，Fragment 无需自行拼接业务条件。 */
data class VehicleDashboardUiState(
    val connectionStatus: VehicleConnectionStatus = VehicleConnectionStatus.DISCONNECTED,
    val snapshot: VehicleSnapshot? = null,
    val isDataStale: Boolean = true,
    val canUnlockAllDoors: Boolean = false,
    val canEditSimulation: Boolean = false,
    val commandResult: Int? = null,
)
