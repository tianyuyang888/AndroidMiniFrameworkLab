package com.yangtianyu.frameworklab.vehicle

/** 车辆指令执行结果码，供客户端稳定判断成功或失败原因。 */
object VehicleCommandResult {
    const val SUCCESS = 0
    const val INVALID_ARGUMENT = 1
    const val REJECTED_WHILE_DRIVING = 2
    const val SERVICE_UNAVAILABLE = 3
    const val DEBUG_ONLY = 4
}
