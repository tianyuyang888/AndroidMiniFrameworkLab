package com.yangtianyu.frameworklab.home

/**
 * 统一判断首页导航条件，避免离开首页后旧点击事件再次触发导航。
 */
object ExperimentNavigationPolicy {

    fun canOpenImageLoader(
        item: ExperimentItem,
        isHomeCurrentDestination: Boolean,
    ): Boolean {
        return isHomeCurrentDestination &&
            item.id == ExperimentCatalog.MINI_IMAGE_LOADER_ID
    }
}
