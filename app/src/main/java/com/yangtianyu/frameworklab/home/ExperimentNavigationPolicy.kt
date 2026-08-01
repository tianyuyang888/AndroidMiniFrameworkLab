package com.yangtianyu.frameworklab.home

object ExperimentNavigationPolicy {

    fun canOpenImageLoader(
        item: ExperimentItem,
        isHomeCurrentDestination: Boolean,
    ): Boolean {
        return isHomeCurrentDestination &&
            item.id == ExperimentCatalog.MINI_IMAGE_LOADER_ID
    }
}
