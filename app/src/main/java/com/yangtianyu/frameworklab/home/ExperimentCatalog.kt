package com.yangtianyu.frameworklab.home

/**
 * 集中提供首页展示的实验入口，后续模块可以按相同结构追加。
 */
object ExperimentCatalog {

    const val MINI_IMAGE_LOADER_ID = "mini-image-loader"

    fun all(): List<ExperimentItem> = listOf(
        ExperimentItem(
            id = MINI_IMAGE_LOADER_ID,
            title = "Mini Image Loader",
            description = "Load 20 network images with a lightweight HttpURLConnection-based loader.",
        ),
    )
}
