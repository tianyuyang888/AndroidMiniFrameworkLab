package com.yangtianyu.frameworklab.home

object ExperimentCatalog {

    const val MINI_IMAGE_LOADER_ID = "mini-image-loader"

    fun all(): List<ExperimentItem> = listOf(
        ExperimentItem(
            id = MINI_IMAGE_LOADER_ID,
            title = "Mini Image Loader",
            description = "Explore the compile-ready skeleton for a future image loading framework.",
        ),
    )
}
