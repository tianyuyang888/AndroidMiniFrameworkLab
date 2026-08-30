package com.yangtianyu.frameworklab.vehicle

/**
 * 先创建客户端、后创建 ViewModel 时使用的事件中转器。
 * delegate 尚未设置期间安全忽略事件，设置后集中原样转发。
 */
class ForwardingVehicleListener : VehicleServiceClient.Listener {
    var delegate: VehicleServiceClient.Listener? = null

    override fun onConnectionChanged(status: VehicleConnectionStatus) {
        delegate?.onConnectionChanged(status)
    }

    override fun onSnapshot(snapshot: VehicleSnapshot) {
        delegate?.onSnapshot(snapshot)
    }

    override fun onCommandResult(result: Int) {
        delegate?.onCommandResult(result)
    }
}
