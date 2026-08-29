package com.yangtianyu.frameworklab.vehicle

/** 车辆挡位及其对外传输的整数 code 映射。 */
enum class VehicleGear(val code: Int) {
    PARK(0), REVERSE(1), NEUTRAL(2), DRIVE(3);

    companion object {
        // 收到未知 code 时安全回退到 PARK，避免产生无效挡位。
        fun fromCode(code: Int): VehicleGear = entries.firstOrNull { it.code == code } ?: PARK
    }
}
