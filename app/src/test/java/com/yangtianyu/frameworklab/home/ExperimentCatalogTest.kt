package com.yangtianyu.frameworklab.home

import org.junit.Assert.assertEquals
import org.junit.Test

class ExperimentCatalogTest {

    @Test
    fun containsImageLoaderAndVehicleStatusEntries() {
        assertEquals(
            listOf("mini-image-loader", "vehicle-status-center"),
            ExperimentCatalog.all().map(ExperimentItem::id),
        )
    }
}
