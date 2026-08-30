package com.yangtianyu.frameworklab.vehicle

/** UI 显示所需的四门开关与总锁状态，避免 Fragment 遗漏任一字段。 */
data class VehicleDoorDisplay(
    val isFrontLeftOpen: Boolean,
    val isFrontRightOpen: Boolean,
    val isRearLeftOpen: Boolean,
    val isRearRightOpen: Boolean,
    val areDoorsLocked: Boolean,
)

enum class CommandResultMessage { SUCCESS, INVALID_ARGUMENT, REJECTED_WHILE_DRIVING, SERVICE_UNAVAILABLE, DEBUG_ONLY, UNKNOWN }

/** 将领域状态映射为资源无关的显示模型，便于纯 JVM 单元测试。 */
object VehicleDashboardDisplayMapper {
    fun doorDisplay(snapshot: VehicleSnapshot): VehicleDoorDisplay = VehicleDoorDisplay(
        snapshot.isFrontLeftDoorOpen,
        snapshot.isFrontRightDoorOpen,
        snapshot.isRearLeftDoorOpen,
        snapshot.isRearRightDoorOpen,
        snapshot.areDoorsLocked,
    )

    fun commandResult(result: Int?): CommandResultMessage? = when (result) {
        null -> null
        VehicleCommandResult.SUCCESS -> CommandResultMessage.SUCCESS
        VehicleCommandResult.INVALID_ARGUMENT -> CommandResultMessage.INVALID_ARGUMENT
        VehicleCommandResult.REJECTED_WHILE_DRIVING -> CommandResultMessage.REJECTED_WHILE_DRIVING
        VehicleCommandResult.SERVICE_UNAVAILABLE -> CommandResultMessage.SERVICE_UNAVAILABLE
        VehicleCommandResult.DEBUG_ONLY -> CommandResultMessage.DEBUG_ONLY
        else -> CommandResultMessage.UNKNOWN
    }
}
