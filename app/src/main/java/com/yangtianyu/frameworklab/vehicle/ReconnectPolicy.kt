package com.yangtianyu.frameworklab.vehicle

/** 固定等待一秒，最多自动重连三次。 */
object ReconnectPolicy {
    private const val MAX_ATTEMPTS = 3

    fun delayMs(attempt: Int): Long? =
        if (attempt in 1..MAX_ATTEMPTS) 1_000L else null
}
