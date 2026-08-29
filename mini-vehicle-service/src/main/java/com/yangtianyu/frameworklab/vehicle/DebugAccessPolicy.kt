package com.yangtianyu.frameworklab.vehicle

/** 调试能力只允许在 debuggable 构建中使用。 */
object DebugAccessPolicy {
    fun isAllowed(isDebuggable: Boolean): Boolean = isDebuggable
}
