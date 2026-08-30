package com.yangtianyu.frameworklab.home

/**
 * 统一判断首页导航条件，避免离开首页后旧点击事件再次触发导航。
 */
enum class ExperimentDestination {
    IMAGE_LOADER,
    VEHICLE_STATUS,
}

object ExperimentNavigationPolicy {
    fun destinationFor(
        item: ExperimentItem,
        isHomeCurrentDestination: Boolean,
    ): ExperimentDestination? {
        if (!isHomeCurrentDestination) return null
        return when (item.id) {
            ExperimentCatalog.MINI_IMAGE_LOADER_ID -> ExperimentDestination.IMAGE_LOADER
            ExperimentCatalog.VEHICLE_STATUS_CENTER_ID -> ExperimentDestination.VEHICLE_STATUS
            else -> null
        }
    }
}
