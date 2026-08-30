package com.yangtianyu.frameworklab.vehicle

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** 在指定执行器上串行提交车辆命令，并为 Binder 同步调用提供超时保护。 */
internal class TimedVehicleCommandExecutor(
    private val executor: ExecutorService,
    private val timeout: Long,
    private val timeoutUnit: TimeUnit,
) {
    fun execute(block: () -> Int): Int {
        val future = try {
            executor.submit(Callable(block))
        } catch (_: RejectedExecutionException) {
            return VehicleCommandResult.SERVICE_UNAVAILABLE
        }

        return try {
            future.get(timeout, timeoutUnit)
        } catch (_: TimeoutException) {
            // 取消尚未开始的排队命令，避免客户端收到失败后状态仍被迟到修改。
            future.cancel(false)
            VehicleCommandResult.SERVICE_UNAVAILABLE
        } catch (_: InterruptedException) {
            future.cancel(false)
            // Binder 调用线程被中断时必须恢复标记，交由上层线程策略继续处理。
            Thread.currentThread().interrupt()
            VehicleCommandResult.SERVICE_UNAVAILABLE
        } catch (_: ExecutionException) {
            VehicleCommandResult.SERVICE_UNAVAILABLE
        }
    }
}
