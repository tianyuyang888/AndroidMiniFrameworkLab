package com.yangtianyu.frameworklab.vehicle;

import com.yangtianyu.frameworklab.vehicle.VehicleSnapshot;

oneway interface IVehicleStateCallback {
    void onVehicleSnapshotChanged(in VehicleSnapshot snapshot);
}
