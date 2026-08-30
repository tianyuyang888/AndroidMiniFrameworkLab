package com.yangtianyu.frameworklab.vehicle

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** 管理可随客户端 start/stop 安全重建的单线程控制执行器。 */
internal class RestartableExecutorOwner(
    private val factory: () -> ExecutorService = { Executors.newSingleThreadExecutor() },
) {
    private var executor: ExecutorService? = null

    @Synchronized
    fun acquire(): ExecutorService {
        val current = executor
        if (current != null && !current.isShutdown) return current
        return factory().also { executor = it }
    }

    /** 清理任务排在已有控制指令之后，完成后终止旧执行器。 */
    fun shutdownAfter(cleanup: () -> Unit) {
        val oldExecutor = synchronized(this) {
            executor.also { executor = null }
        } ?: return
        oldExecutor.execute {
            try {
                cleanup()
            } finally {
                oldExecutor.shutdown()
            }
        }
    }
}
