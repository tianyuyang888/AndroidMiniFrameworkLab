package com.yangtianyu.frameworklab.vehicle

/** 线程安全地保存最近一次通过校验的车辆状态快照。 */
class VehicleStateStore(initial: VehicleSnapshot) {
    @Volatile
    private var latest: VehicleSnapshot = initial

    fun current(): VehicleSnapshot = latest

    /** 只有完整快照有效时才替换当前状态，避免非法数据污染存储。 */
    @Synchronized
    fun updateIfValid(candidate: VehicleSnapshot): Boolean {
        if (!VehicleSnapshotValidator.isValid(candidate)) return false
        latest = candidate
        return true
    }
}
