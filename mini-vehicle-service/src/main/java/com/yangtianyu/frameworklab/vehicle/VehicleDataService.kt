package com.yangtianyu.frameworklab.vehicle

import android.app.Service
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.IBinder
import android.os.Process
import android.os.RemoteCallbackList
import android.os.SystemClock
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * 运行在独立进程中的车辆数据服务。
 *
 * Binder 线程只负责接收调用；所有状态写入都串行投递到 stateExecutor，避免并发写竞争。
 */
class VehicleDataService : Service() {
    private val callbacks = RemoteCallbackList<IVehicleStateCallback>()
    private val stateExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val callbackExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val callbackDispatcher = OrderedCallbackDispatcher(
        executor = callbackExecutor,
        registerCallback = callbacks::register,
        unregisterCallback = callbacks::unregister,
        deliverInitial = { callback, snapshot -> callback.onVehicleSnapshotChanged(snapshot) },
        broadcastSnapshot = ::broadcast,
    )
    private val commandExecutor = TimedVehicleCommandExecutor(
        executor = stateExecutor,
        timeout = 2,
        timeoutUnit = TimeUnit.SECONDS,
    )
    @Volatile
    private var isAutoSimulationPaused = false
    private val store = VehicleStateStore(
        VehicleSnapshot.stoppedDefault(SystemClock.elapsedRealtime()),
    )

    private val binder = object : IVehicleService.Stub() {
        override fun getCurrentSnapshot(): VehicleSnapshot = store.current()

        override fun registerCallback(callback: IVehicleStateCallback?) {
            if (callback == null) return
            stateExecutor.execute {
                // 在状态线程捕获快照，再按同一状态顺序排入回调队列，避免首帧倒序。
                callbackDispatcher.registerWithInitial(callback, store.current())
            }
        }

        override fun unregisterCallback(callback: IVehicleStateCallback?) {
            if (callback != null) {
                stateExecutor.execute { callbackDispatcher.unregister(callback) }
            }
        }

        override fun setTemperature(value: Int): Int = command {
            if (value !in 16..30) return@command VehicleCommandResult.INVALID_ARGUMENT
            publish(
                store.current().copy(
                    temperatureCelsius = value,
                    updatedAtElapsedRealtime = SystemClock.elapsedRealtime(),
                ),
            )
            VehicleCommandResult.SUCCESS
        }

        override fun setAcEnabled(enabled: Boolean): Int = command {
            publish(
                store.current().copy(
                    isAcOn = enabled,
                    updatedAtElapsedRealtime = SystemClock.elapsedRealtime(),
                ),
            )
            VehicleCommandResult.SUCCESS
        }

        override fun unlockAllDoors(): Int = command {
            if (!DrivingRestrictionPolicy.canUnlockAllDoors(store.current())) {
                return@command VehicleCommandResult.REJECTED_WHILE_DRIVING
            }
            publish(
                store.current().copy(
                    areDoorsLocked = false,
                    updatedAtElapsedRealtime = SystemClock.elapsedRealtime(),
                ),
            )
            VehicleCommandResult.SUCCESS
        }

        override fun setSimulationSnapshot(snapshot: VehicleSnapshot?): Int = command {
            if (!DebugAccessPolicy.isAllowed(isDebuggable())) {
                return@command VehicleCommandResult.DEBUG_ONLY
            }
            if (!DrivingRestrictionPolicy.canEditSimulation(store.current())) {
                return@command VehicleCommandResult.REJECTED_WHILE_DRIVING
            }
            if (snapshot == null || !VehicleSnapshotValidator.isValid(snapshot)) {
                return@command VehicleCommandResult.INVALID_ARGUMENT
            }
            publish(snapshot)
            VehicleCommandResult.SUCCESS
        }

        override fun requestSimulatedProcessDeath(): Int = command {
            if (!DebugAccessPolicy.isAllowed(isDebuggable())) {
                return@command VehicleCommandResult.DEBUG_ONLY
            }
            stateExecutor.schedule(
                { Process.killProcess(Process.myPid()) },
                100,
                TimeUnit.MILLISECONDS,
            )
            VehicleCommandResult.SUCCESS
        }
    }

    override fun onCreate() {
        super.onCreate()
        stateExecutor.scheduleAtFixedRate(
            {
                if (!isAutoSimulationPaused) {
                    publish(FakeVehicleDataSource.next(store.current(), SystemClock.elapsedRealtime()))
                }
            },
            1,
            1,
            TimeUnit.SECONDS,
        )
    }

    override fun onBind(intent: Intent?): IBinder {
        // 仅 Debug 测试绑定可暂停自动刷新；Release 即使携带 extra 也必须忽略。
        isAutoSimulationPaused = isDebuggable() &&
            intent?.getBooleanExtra(EXTRA_PAUSE_AUTO_SIMULATION, false) == true
        return binder
    }

    override fun onDestroy() {
        // 取消定时刷新和排队指令，避免 Service 销毁后线程继续存活。
        stateExecutor.shutdownNow()
        callbackDispatcher.close(callbacks::kill)
        super.onDestroy()
    }

    /**
     * Binder 调用线程等待串行状态线程的结果，并设置超时避免客户端无限阻塞。
     */
    private fun command(block: () -> Int): Int = commandExecutor.execute(block)

    private fun publish(snapshot: VehicleSnapshot) {
        if (!store.updateIfValid(snapshot)) return

        // 状态提交完成即可返回命令结果；独立回调队列不会拖长 Binder 同步等待。
        callbackDispatcher.broadcast(snapshot)
    }

    private fun broadcast(snapshot: VehicleSnapshot) {
        val count = callbacks.beginBroadcast()
        try {
            repeat(count) { index ->
                try {
                    callbacks.getBroadcastItem(index).onVehicleSnapshotChanged(snapshot)
                } catch (_: Exception) {
                    // 一次异常回调不能阻断本轮其他客户端的通知。
                }
            }
        } finally {
            // beginBroadcast 与 finishBroadcast 必须严格配对。
            callbacks.finishBroadcast()
        }
    }

    private fun isDebuggable(): Boolean =
        applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    companion object {
        const val EXTRA_PAUSE_AUTO_SIMULATION =
            "com.yangtianyu.frameworklab.vehicle.extra.PAUSE_AUTO_SIMULATION"
    }
}
