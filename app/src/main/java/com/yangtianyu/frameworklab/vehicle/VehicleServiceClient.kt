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

/**
 * App 进程中的车辆服务客户端。
 *
 * 每次绑定都有独立代次、ServiceConnection 和 AIDL callback。旧代次事件只会被丢弃，
 * 同步 AIDL 调用统一进入可重启的单线程执行器，避免阻塞主线程。
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

    private class BindingSession(
        val generation: Long,
        val connection: ServiceConnection,
    ) {
        var isBound: Boolean = false
        var disconnectHandled: Boolean = false
        var binder: IBinder? = null
        var service: IVehicleService? = null
        var callback: IVehicleStateCallback? = null
        var deathRecipient: IBinder.DeathRecipient? = null
    }

    private data class RemoteRegistration(
        val service: IVehicleService,
        val callback: IVehicleStateCallback,
    )

    private val applicationContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val generationTracker = VehicleClientGenerationTracker()
    private val executorOwner = RestartableExecutorOwner()
    private var activeSession: BindingSession? = null
    private var attemptsMade = 0
    private var retryRunnable: Runnable? = null

    fun start() = runOnMain {
        if (generationTracker.isStarted) return@runOnMain
        generationTracker.start()
        attemptsMade = 0
        executorOwner.acquire()
        beginBind(generationTracker.nextConnection())
    }

    fun stop() = runOnMain {
        generationTracker.stop()
        // Handler 只属于当前客户端，可清除快照、结果和重连等全部待处理消息。
        mainHandler.removeCallbacksAndMessages(null)
        retryRunnable = null

        val session = activeSession
        activeSession = null
        val registration = session?.let(::detachSession)
        notifyStatus(VehicleConnectionStatus.DISCONNECTED)

        // 注销排在已提交指令之后；迟到结果会因代次失效而被丢弃。
        executorOwner.shutdownAfter {
            registration?.let(::unregisterQuietly)
        }
    }

    fun retryNow() = runOnMain {
        if (!generationTracker.isStarted) generationTracker.start()
        attemptsMade = 0
        removePendingRetry()

        // 先产生新 token 使旧事件失效，再清理旧连接并开始新绑定。
        val generation = generationTracker.nextConnection()
        val oldSession = activeSession
        activeSession = null
        val registration = oldSession?.let(::detachSession)
        val executor = executorOwner.acquire()
        registration?.let { executor.execute { unregisterQuietly(it) } }
        beginBind(generation)
    }

    fun setTemperature(value: Int) = executeCommand { it.setTemperature(value) }

    fun setAcEnabled(enabled: Boolean) = executeCommand { it.setAcEnabled(enabled) }

    fun unlockAllDoors() = executeCommand { it.unlockAllDoors() }

    fun setSimulationSnapshot(snapshot: VehicleSnapshot) =
        executeCommand { it.setSimulationSnapshot(snapshot) }

    fun requestSimulatedProcessDeath() =
        executeCommand { it.requestSimulatedProcessDeath() }

    private fun beginBind(generation: Long) {
        if (!generationTracker.isCurrent(generation)) return
        executorOwner.acquire()
        notifyStatus(VehicleConnectionStatus.CONNECTING)

        lateinit var session: BindingSession
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                runOnMain {
                    if (!isActive(session)) return@runOnMain
                    if (binder == null) {
                        handleUnexpectedDisconnect(session.generation)
                    } else {
                        finishConnection(session, binder)
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                runOnMain { handleUnexpectedDisconnect(session.generation) }
            }

            override fun onBindingDied(name: ComponentName?) {
                // Android 8.0 及以上由系统回调；API 24～25 不主动调用此方法。
                runOnMain { handleUnexpectedDisconnect(session.generation) }
            }
        }
        session = BindingSession(generation, connection)
        activeSession = session

        val intent = Intent(applicationContext, VehicleDataService::class.java)
        session.isBound = try {
            applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (_: SecurityException) {
            false
        }
        if (!session.isBound) handleUnexpectedDisconnect(generation)
    }

    private fun finishConnection(session: BindingSession, binder: IBinder) {
        if (!isActive(session)) return
        val connectedService = IVehicleService.Stub.asInterface(binder)
        val generation = session.generation
        val callback = object : IVehicleStateCallback.Stub() {
            override fun onVehicleSnapshotChanged(snapshot: VehicleSnapshot?) {
                if (snapshot == null) return
                mainHandler.post {
                    if (isActive(session) && session.binder === binder) {
                        listener.onSnapshot(snapshot)
                    }
                }
            }
        }
        val deathRecipient = IBinder.DeathRecipient {
            runOnMain { handleUnexpectedDisconnect(generation) }
        }
        session.binder = binder
        session.service = connectedService
        session.callback = callback
        session.deathRecipient = deathRecipient

        val executor = executorOwner.acquire()
        executor.execute {
            try {
                binder.linkToDeath(deathRecipient, 0)
                connectedService.registerCallback(callback)
                val firstSnapshot = connectedService.currentSnapshot
                mainHandler.post {
                    if (!isActive(session) || session.binder !== binder) return@post
                    attemptsMade = 0
                    session.disconnectHandled = false
                    listener.onSnapshot(firstSnapshot)
                    notifyStatus(VehicleConnectionStatus.CONNECTED)
                }
            } catch (_: RemoteException) {
                runOnMain { handleUnexpectedDisconnect(generation) }
            }
        }
    }

    private fun executeCommand(command: (IVehicleService) -> Int) {
        runOnMain {
            val session = activeSession
            val connectedService = session?.service
            if (session == null || connectedService == null || !isActive(session)) {
                listener.onCommandResult(VehicleCommandResult.SERVICE_UNAVAILABLE)
                return@runOnMain
            }
            val generation = session.generation
            val executor: ExecutorService = executorOwner.acquire()
            executor.execute {
                try {
                    val result = command(connectedService)
                    mainHandler.post {
                        if (isActive(session) && session.service === connectedService) {
                            listener.onCommandResult(result)
                        }
                    }
                } catch (_: RemoteException) {
                    mainHandler.post {
                        if (isActive(session) && session.service === connectedService) {
                            listener.onCommandResult(VehicleCommandResult.SERVICE_UNAVAILABLE)
                            handleUnexpectedDisconnect(generation)
                        }
                    }
                }
            }
        }
    }

    /** 所有当前代次的非主动断连都由 reducer 决定是否继续重连。 */
    private fun handleUnexpectedDisconnect(generation: Long) {
        val session = activeSession ?: return
        if (!isActive(session) || session.generation != generation || session.disconnectHandled) return
        session.disconnectHandled = true
        activeSession = null
        detachSession(session)
        removePendingRetry()

        val decision = VehicleReconnectReducer.onUnexpectedDisconnect(
            started = generationTracker.isStarted,
            attemptsMade = attemptsMade,
        )
        attemptsMade = decision.nextAttempt
        notifyStatus(decision.status)
        val delayMs = decision.delayMs ?: return
        val retry = Runnable {
            retryRunnable = null
            if (generationTracker.isStarted) {
                beginBind(generationTracker.nextConnection())
            }
        }
        retryRunnable = retry
        mainHandler.postDelayed(retry, delayMs)
    }

    private fun isActive(session: BindingSession): Boolean =
        generationTracker.isCurrent(session.generation) && activeSession === session

    /** 主线程解除当前代次的死亡监听和 ServiceConnection，并返回远程注册信息。 */
    private fun detachSession(session: BindingSession): RemoteRegistration? {
        val binder = session.binder
        val recipient = session.deathRecipient
        try {
            if (binder != null && recipient != null) binder.unlinkToDeath(recipient, 0)
        } catch (_: NoSuchElementException) {
            // Binder 已死亡或未完成 linkToDeath 时无需重复解除。
        }

        if (session.isBound) {
            try {
                applicationContext.unbindService(session.connection)
            } catch (_: IllegalArgumentException) {
                // 系统已解绑时只需收敛本地状态。
            }
            session.isBound = false
        }

        val service = session.service
        val callback = session.callback
        session.binder = null
        session.service = null
        session.callback = null
        session.deathRecipient = null
        return if (service != null && callback != null) RemoteRegistration(service, callback) else null
    }

    private fun unregisterQuietly(registration: RemoteRegistration) {
        try {
            registration.service.unregisterCallback(registration.callback)
        } catch (_: RemoteException) {
            // 清理旧代次失败不应影响当前连接或触发自动重连。
        }
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
