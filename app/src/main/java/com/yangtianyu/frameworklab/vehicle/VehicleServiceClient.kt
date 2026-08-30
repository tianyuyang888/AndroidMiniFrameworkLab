package com.yangtianyu.frameworklab.vehicle

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * App 进程中的车辆服务客户端。
 *
 * 连接状态只在主线程修改；同步 AIDL 调用统一进入单线程执行器，避免阻塞界面。
 */
class VehicleServiceClient(
    context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onConnectionChanged(status: VehicleConnectionStatus)
        fun onSnapshot(snapshot: VehicleSnapshot)
        fun onCommandResult(result: Int)
    }

    private val applicationContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val controlExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    @Volatile
    private var service: IVehicleService? = null
    private var serviceBinder: IBinder? = null
    private var deathRecipient: IBinder.DeathRecipient? = null
    private var started = false
    private var isBound = false
    private var disconnectHandled = false
    private var attemptsMade = 0
    private var connectionGeneration = 0
    private var retryRunnable: Runnable? = null

    private val callback = object : IVehicleStateCallback.Stub() {
        override fun onVehicleSnapshotChanged(snapshot: VehicleSnapshot?) {
            if (snapshot != null) {
                mainHandler.post {
                    if (started) listener.onSnapshot(snapshot)
                }
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder == null) {
                runOnMain(::handleUnexpectedDisconnect)
                return
            }
            runOnMain { finishConnection(binder) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            runOnMain(::handleUnexpectedDisconnect)
        }

        override fun onBindingDied(name: ComponentName?) {
            // Android 8.0 及以上由系统回调；API 24～25 不主动调用此方法。
            runOnMain(::handleUnexpectedDisconnect)
        }
    }

    fun start() = runOnMain {
        if (started) return@runOnMain
        started = true
        attemptsMade = 0
        beginBind()
    }

    fun stop() = runOnMain {
        started = false
        connectionGeneration += 1
        // Handler 只属于当前客户端，可以一次清除快照、结果和重连等全部待处理消息。
        mainHandler.removeCallbacksAndMessages(null)
        retryRunnable = null

        val oldService = service
        clearBinder()
        safelyUnbind()
        disconnectHandled = false
        notifyStatus(VehicleConnectionStatus.DISCONNECTED)

        if (oldService != null) {
            controlExecutor.execute {
                try {
                    oldService.unregisterCallback(callback)
                } catch (_: RemoteException) {
                    // 主动停止后的远端异常不应触发自动重连。
                }
            }
        }
    }

    fun retryNow() = runOnMain {
        started = true
        attemptsMade = 0
        connectionGeneration += 1
        removePendingRetry()
        clearBinder()
        safelyUnbind()
        beginBind()
    }

    fun setTemperature(value: Int) = executeCommand { it.setTemperature(value) }

    fun setAcEnabled(enabled: Boolean) = executeCommand { it.setAcEnabled(enabled) }

    fun unlockAllDoors() = executeCommand { it.unlockAllDoors() }

    fun setSimulationSnapshot(snapshot: VehicleSnapshot) =
        executeCommand { it.setSimulationSnapshot(snapshot) }

    fun requestSimulatedProcessDeath() =
        executeCommand { it.requestSimulatedProcessDeath() }

    private fun beginBind() {
        if (!started) return
        disconnectHandled = false
        notifyStatus(VehicleConnectionStatus.CONNECTING)
        val intent = Intent(applicationContext, VehicleDataService::class.java)
        isBound = try {
            applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (_: SecurityException) {
            false
        }
        if (!isBound) handleUnexpectedDisconnect()
    }

    private fun finishConnection(binder: IBinder) {
        if (!started) return
        serviceBinder = binder
        service = IVehicleService.Stub.asInterface(binder)
        val generation = ++connectionGeneration
        val recipient = IBinder.DeathRecipient {
            runOnMain {
                if (generation == connectionGeneration && serviceBinder === binder) {
                    handleUnexpectedDisconnect()
                }
            }
        }
        deathRecipient = recipient

        controlExecutor.execute {
            try {
                binder.linkToDeath(recipient, 0)
                val connectedService = IVehicleService.Stub.asInterface(binder)
                connectedService.registerCallback(callback)
                val firstSnapshot = connectedService.currentSnapshot
                mainHandler.post {
                    if (!started || generation != connectionGeneration || serviceBinder !== binder) {
                        return@post
                    }
                    attemptsMade = 0
                    disconnectHandled = false
                    listener.onSnapshot(firstSnapshot)
                    notifyStatus(VehicleConnectionStatus.CONNECTED)
                }
            } catch (_: RemoteException) {
                runOnMain {
                    if (generation == connectionGeneration && serviceBinder === binder) {
                        handleUnexpectedDisconnect()
                    }
                }
            }
        }
    }

    private fun executeCommand(command: (IVehicleService) -> Int) {
        runOnMain {
            val connectedService = service
            if (connectedService == null) {
                listener.onCommandResult(VehicleCommandResult.SERVICE_UNAVAILABLE)
                return@runOnMain
            }
            val generation = connectionGeneration
            controlExecutor.execute {
                try {
                    val result = command(connectedService)
                    mainHandler.post {
                        if (started && generation == connectionGeneration && service === connectedService) {
                            listener.onCommandResult(result)
                        }
                    }
                } catch (_: RemoteException) {
                    mainHandler.post {
                        if (started && generation == connectionGeneration && service === connectedService) {
                            listener.onCommandResult(VehicleCommandResult.SERVICE_UNAVAILABLE)
                            handleUnexpectedDisconnect()
                        }
                    }
                }
            }
        }
    }

    /** 所有非主动断连入口最终都调用此函数，并由 reducer 决定是否继续重连。 */
    private fun handleUnexpectedDisconnect() {
        if (disconnectHandled) return
        disconnectHandled = true
        connectionGeneration += 1
        removePendingRetry()
        clearBinder()
        safelyUnbind()

        val decision = VehicleReconnectReducer.onUnexpectedDisconnect(started, attemptsMade)
        attemptsMade = decision.nextAttempt
        notifyStatus(decision.status)
        val delayMs = decision.delayMs ?: return
        val retry = Runnable {
            retryRunnable = null
            if (started) beginBind()
        }
        retryRunnable = retry
        mainHandler.postDelayed(retry, delayMs)
    }

    private fun clearBinder() {
        try {
            val binder = serviceBinder
            val recipient = deathRecipient
            if (binder != null && recipient != null) binder.unlinkToDeath(recipient, 0)
        } catch (_: NoSuchElementException) {
            // Binder 已经死亡或未成功注册时无需再次解绑死亡通知。
        }
        deathRecipient = null
        serviceBinder = null
        service = null
    }

    private fun safelyUnbind() {
        if (!isBound) return
        try {
            applicationContext.unbindService(connection)
        } catch (_: IllegalArgumentException) {
            // 系统已完成解绑时保持客户端状态收敛即可。
        }
        isBound = false
    }

    private fun removePendingRetry() {
        retryRunnable?.let(mainHandler::removeCallbacks)
        retryRunnable = null
    }

    private fun notifyStatus(status: VehicleConnectionStatus) {
        listener.onConnectionChanged(status)
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
    }
}
