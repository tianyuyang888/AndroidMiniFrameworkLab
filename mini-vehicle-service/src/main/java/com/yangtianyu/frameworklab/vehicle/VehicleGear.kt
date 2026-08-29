package com.yangtianyu.frameworklab.vehicle

enum class VehicleGear(val code: Int) {
    PARK(0), REVERSE(1), NEUTRAL(2), DRIVE(3);

    companion object {
        fun fromCode(code: Int): VehicleGear = entries.firstOrNull { it.code == code } ?: PARK
    }
}
