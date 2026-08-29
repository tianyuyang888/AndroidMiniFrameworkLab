package com.yangtianyu.frameworklab.vehicle

object VehicleSnapshotValidator {
    fun isValid(snapshot: VehicleSnapshot): Boolean =
        snapshot.speedKph in 0..240 &&
            snapshot.batteryPercent in 0..100 &&
            snapshot.rangeKm in 0..2000 &&
            snapshot.temperatureCelsius in 16..30 &&
            snapshot.fanSpeed in 0..7 &&
            snapshot.updatedAtElapsedRealtime >= 0L
}
