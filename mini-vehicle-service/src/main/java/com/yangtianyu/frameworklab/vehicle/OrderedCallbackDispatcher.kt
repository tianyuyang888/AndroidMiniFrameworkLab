package com.yangtianyu.frameworklab.vehicle

import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException

/** 把注册、注销和广播放到同一条 FIFO 队列，保证客户端看到的快照顺序与状态提交顺序一致。 */
internal class OrderedCallbackDispatcher<C, S>(
    private val executor: Executor,
    private val registerCallback: (C) -> Boolean,
    private val unregisterCallback: (C) -> Boolean,
    private val deliverInitial: (C, S) -> Unit,
    private val broadcastSnapshot: (S) -> Unit,
) {
    fun registerWithInitial(callback: C, snapshot: S) = enqueue {
        if (!registerCallback(callback)) return@enqueue
        try {
            deliverInitial(callback, snapshot)
        } catch (_: Exception) {
            unregisterCallback(callback)
        }
    }

    fun unregister(callback: C) = enqueue { unregisterCallback(callback) }

    fun broadcast(snapshot: S) = enqueue { broadcastSnapshot(snapshot) }

    fun close(cleanup: () -> Unit) {
        enqueue(cleanup)
        (executor as? ExecutorService)?.shutdown()
    }

    private fun enqueue(block: () -> Unit) {
        try {
            executor.execute(block)
        } catch (_: RejectedExecutionException) {
            // Service 销毁后丢弃迟到事件，避免回调线程异常退出。
        }
    }
}
