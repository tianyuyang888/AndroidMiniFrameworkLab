package com.yangtianyu.frameworklab.vehicle

/** 根据车辆快照限制可能影响驾驶安全的操作。 */
object DrivingRestrictionPolicy {
    fun canUnlockAllDoors(snapshot: VehicleSnapshot): Boolean =
        snapshot.speedKph == 0 && snapshot.gear == VehicleGear.PARK

    fun canEditSimulation(snapshot: VehicleSnapshot): Boolean =
        snapshot.speedKph == 0 && snapshot.gear == VehicleGear.PARK

    fun canControlClimate(snapshot: VehicleSnapshot): Boolean = true
}
