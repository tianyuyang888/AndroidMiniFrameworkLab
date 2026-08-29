package com.yangtianyu.frameworklab.vehicle

import android.os.Parcel
import android.os.Parcelable

data class VehicleSnapshot(
    val speedKph: Int,
    val gear: VehicleGear,
    val batteryPercent: Int,
    val rangeKm: Int,
    val isFrontLeftDoorOpen: Boolean,
    val isFrontRightDoorOpen: Boolean,
    val isRearLeftDoorOpen: Boolean,
    val isRearRightDoorOpen: Boolean,
    val areDoorsLocked: Boolean,
    val temperatureCelsius: Int,
    val fanSpeed: Int,
    val isAcOn: Boolean,
    val updatedAtElapsedRealtime: Long,
) : Parcelable {
    private constructor(parcel: Parcel) : this(
        speedKph = parcel.readInt(),
        gear = VehicleGear.fromCode(parcel.readInt()),
        batteryPercent = parcel.readInt(),
        rangeKm = parcel.readInt(),
        isFrontLeftDoorOpen = parcel.readByte().toInt() != 0,
        isFrontRightDoorOpen = parcel.readByte().toInt() != 0,
        isRearLeftDoorOpen = parcel.readByte().toInt() != 0,
        isRearRightDoorOpen = parcel.readByte().toInt() != 0,
        areDoorsLocked = parcel.readByte().toInt() != 0,
        temperatureCelsius = parcel.readInt(),
        fanSpeed = parcel.readInt(),
        isAcOn = parcel.readByte().toInt() != 0,
        updatedAtElapsedRealtime = parcel.readLong(),
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(speedKph)
        parcel.writeInt(gear.code)
        parcel.writeInt(batteryPercent)
        parcel.writeInt(rangeKm)
        parcel.writeByte(if (isFrontLeftDoorOpen) 1 else 0)
        parcel.writeByte(if (isFrontRightDoorOpen) 1 else 0)
        parcel.writeByte(if (isRearLeftDoorOpen) 1 else 0)
        parcel.writeByte(if (isRearRightDoorOpen) 1 else 0)
        parcel.writeByte(if (areDoorsLocked) 1 else 0)
        parcel.writeInt(temperatureCelsius)
        parcel.writeInt(fanSpeed)
        parcel.writeByte(if (isAcOn) 1 else 0)
        parcel.writeLong(updatedAtElapsedRealtime)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<VehicleSnapshot> {
        override fun createFromParcel(parcel: Parcel): VehicleSnapshot = VehicleSnapshot(parcel)
        override fun newArray(size: Int): Array<VehicleSnapshot?> = arrayOfNulls(size)

        fun stoppedDefault(updatedAtElapsedRealtime: Long = 0L): VehicleSnapshot = VehicleSnapshot(
            speedKph = 0,
            gear = VehicleGear.PARK,
            batteryPercent = 68,
            rangeKm = 320,
            isFrontLeftDoorOpen = false,
            isFrontRightDoorOpen = false,
            isRearLeftDoorOpen = false,
            isRearRightDoorOpen = false,
            areDoorsLocked = true,
            temperatureCelsius = 22,
            fanSpeed = 3,
            isAcOn = true,
            updatedAtElapsedRealtime = updatedAtElapsedRealtime,
        )
    }
}
