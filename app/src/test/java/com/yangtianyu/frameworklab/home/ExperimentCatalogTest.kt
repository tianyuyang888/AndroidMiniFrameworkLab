package com.yangtianyu.frameworklab.home

import org.junit.Assert.assertEquals
import org.junit.Test

class ExperimentCatalogTest {

    @Test
    fun containsMiniImageLoaderEntry() {
        assertEquals(
            listOf("mini-image-loader"),
            ExperimentCatalog.all().map(ExperimentItem::id),
        )
    }
}
