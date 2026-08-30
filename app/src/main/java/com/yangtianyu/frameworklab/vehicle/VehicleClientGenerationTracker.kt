package com.yangtianyu.frameworklab.vehicle

/**
 * 生成单调递增的连接代次，并判断异步事件是否仍属于当前连接。
 * 该类不依赖 Android，便于确定性验证迟到事件的丢弃规则。
 */
internal class VehicleClientGenerationTracker {
    private var generation = 0L

    var isStarted: Boolean = false
        private set

    fun start() {
        isStarted = true
    }

    fun stop() {
        isStarted = false
        generation += 1
    }

    fun nextConnection(): Long {
        check(isStarted) { "客户端停止时不能创建连接代次" }
        generation += 1
        return generation
    }

    fun isCurrent(candidate: Long): Boolean = isStarted && candidate == generation
}
