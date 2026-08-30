package com.yangtianyu.frameworklab.vehicle

/** 根据当前快照生成确定性的下一帧模拟车辆数据，便于演示和测试。 */
object FakeVehicleDataSource {
    fun next(current: VehicleSnapshot, nowMs: Long): VehicleSnapshot {
        val nextSpeed = if (current.speedKph >= 72) 0 else current.speedKph + 12
        return current.copy(
            speedKph = nextSpeed,
            gear = if (nextSpeed == 0) VehicleGear.PARK else VehicleGear.DRIVE,
            batteryPercent = if (nextSpeed == 0) {
                current.batteryPercent
            } else {
                (current.batteryPercent - 1).coerceAtLeast(0)
            },
            rangeKm = if (nextSpeed == 0) {
                current.rangeKm
            } else {
                (current.rangeKm - 4).coerceAtLeast(0)
            },
            isRearRightDoorOpen = nextSpeed == 0,
            updatedAtElapsedRealtime = nowMs,
        )
    }
}
