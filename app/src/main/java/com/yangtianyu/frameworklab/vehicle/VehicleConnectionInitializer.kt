package com.yangtianyu.frameworklab.vehicle

/**
 * 完成远端连接的最小初始化步骤。
 *
 * 快照只允许由已注册的 AIDL 回调交付，避免同步查询与异步首帧形成两条竞争数据源。
 */
internal class VehicleConnectionInitializer(
    private val linkToDeath: () -> Unit,
    private val registerCallback: () -> Unit,
) {
    fun initialize() {
        linkToDeath()
        registerCallback()
    }
}
