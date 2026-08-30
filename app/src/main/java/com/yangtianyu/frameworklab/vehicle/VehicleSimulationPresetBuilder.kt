package com.yangtianyu.frameworklab.vehicle

enum class VehicleSimulationPreset { PARKED, DRIVING, REAR_RIGHT_DOOR_OPEN }

/** 构造 Debug 演示快照；保留当前电量、续航和空调等无关字段。 */
object VehicleSimulationPresetBuilder {
    fun build(preset: VehicleSimulationPreset, current: VehicleSnapshot, nowMs: Long): VehicleSnapshot =
        when (preset) {
            VehicleSimulationPreset.PARKED -> current.copy(speedKph = 0, gear = VehicleGear.PARK, isRearRightDoorOpen = false, updatedAtElapsedRealtime = nowMs)
            VehicleSimulationPreset.DRIVING -> current.copy(speedKph = 40, gear = VehicleGear.DRIVE, isRearRightDoorOpen = false, updatedAtElapsedRealtime = nowMs)
            VehicleSimulationPreset.REAR_RIGHT_DOOR_OPEN -> current.copy(speedKph = 0, gear = VehicleGear.PARK, isRearRightDoorOpen = true, updatedAtElapsedRealtime = nowMs)
        }
}
