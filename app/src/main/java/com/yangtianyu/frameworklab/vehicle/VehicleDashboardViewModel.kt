package com.yangtianyu.frameworklab.vehicle

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * 车辆 Dashboard 的状态持有者。
 * VehicleServiceClient 已将所有回调切到主线程，因此这里可直接更新 LiveData。
 */
class VehicleDashboardViewModel(
    private val client: VehicleServiceClient,
    private val nowMs: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel(), VehicleServiceClient.Listener {
    private val mutableUiState = MutableLiveData(VehicleDashboardUiState())
    val uiState: LiveData<VehicleDashboardUiState> = mutableUiState

    private val refreshHandler = Handler(Looper.getMainLooper())
    private val staleRefresh = object : Runnable {
        override fun run() {
            publish()
            refreshHandler.postDelayed(this, STALE_REFRESH_INTERVAL_MS)
        }
    }
    private var status = VehicleConnectionStatus.DISCONNECTED
    private var snapshot: VehicleSnapshot? = null
    private var commandResult: Int? = null

    fun start() {
        client.start()
        // start 可因生命周期重复进入；先移除可保证任意时刻只有一条刷新链。
        refreshHandler.removeCallbacks(staleRefresh)
        refreshHandler.post(staleRefresh)
    }

    fun stop() {
        refreshHandler.removeCallbacks(staleRefresh)
        client.stop()
    }

    fun retry() = client.retryNow()

    fun increaseTemperature() {
        snapshot?.let { client.setTemperature(it.temperatureCelsius + 1) }
    }

    fun decreaseTemperature() {
        snapshot?.let { client.setTemperature(it.temperatureCelsius - 1) }
    }

    fun toggleAc() {
        snapshot?.let { client.setAcEnabled(!it.isAcOn) }
    }

    fun unlockAllDoors() = client.unlockAllDoors()

    fun simulateProcessDeath() = client.requestSimulatedProcessDeath()

    fun applySimulationPreset(preset: VehicleSimulationPreset) {
        val current = snapshot ?: return
        client.setSimulationSnapshot(VehicleSimulationPresetBuilder.build(preset, current, nowMs()))
    }

    override fun onConnectionChanged(status: VehicleConnectionStatus) {
        this.status = status
        publish()
    }

    override fun onSnapshot(snapshot: VehicleSnapshot) {
        this.snapshot = snapshot
        publish()
    }

    override fun onCommandResult(result: Int) {
        commandResult = result
        publish()
    }

    private fun publish() {
        mutableUiState.value = VehicleDashboardStateReducer.reduce(
            status = status,
            snapshot = snapshot,
            nowMs = nowMs(),
            commandResult = commandResult,
        )
    }

    override fun onCleared() {
        refreshHandler.removeCallbacks(staleRefresh)
        client.stop()
    }

    /** Factory 仅持有 applicationContext，避免 ViewModel 间接泄漏页面。 */
    class Factory(context: Context) : ViewModelProvider.Factory {
        private val applicationContext = context.applicationContext

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            requireDashboardModelClass(modelClass)
            val forwardingListener = ForwardingVehicleListener()
            val client = VehicleServiceClient(applicationContext, forwardingListener)
            val viewModel = VehicleDashboardViewModel(client)
            forwardingListener.delegate = viewModel
            @Suppress("UNCHECKED_CAST")
            return viewModel as T
        }
    }

    private companion object {
        const val STALE_REFRESH_INTERVAL_MS = 1_000L
    }
}

/** 在创建任何客户端资源前拒绝 Factory 不支持的 ViewModel 类型。 */
internal fun requireDashboardModelClass(modelClass: Class<*>) {
    require(modelClass.isAssignableFrom(VehicleDashboardViewModel::class.java)) {
        "VehicleDashboardViewModel.Factory cannot create ${modelClass.name}"
    }
}
