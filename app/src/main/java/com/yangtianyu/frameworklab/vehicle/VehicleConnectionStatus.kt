package com.yangtianyu.frameworklab.vehicle

/** App 与独立车辆进程之间的连接状态。 */
enum class VehicleConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RETRY_WAITING,
}
