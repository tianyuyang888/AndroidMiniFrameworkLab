package com.yangtianyu.frameworklab.vehicle;

import com.yangtianyu.frameworklab.vehicle.VehicleSnapshot;
import com.yangtianyu.frameworklab.vehicle.IVehicleStateCallback;

interface IVehicleService {
    VehicleSnapshot getCurrentSnapshot();
    void registerCallback(IVehicleStateCallback callback);
    void unregisterCallback(IVehicleStateCallback callback);
    int setTemperature(int temperatureCelsius);
    int setAcEnabled(boolean enabled);
    int unlockAllDoors();
    int setSimulationSnapshot(in VehicleSnapshot snapshot);
    int requestSimulatedProcessDeath();
}
