package com.yangtianyu.frameworklab.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExperimentNavigationPolicyTest {

    private val imageLoaderItem = ExperimentCatalog.all().first()
    private val vehicleStatusItem = ExperimentCatalog.all().last()

    @Test
    fun mapsRegisteredExperimentsWhileHomeIsCurrent() {
        assertEquals(
            ExperimentDestination.IMAGE_LOADER,
            ExperimentNavigationPolicy.destinationFor(
                item = imageLoaderItem,
                isHomeCurrentDestination = true,
            ),
        )
        assertEquals(
            ExperimentDestination.VEHICLE_STATUS,
            ExperimentNavigationPolicy.destinationFor(vehicleStatusItem, true),
        )
    }

    @Test
    fun ignoresSecondClickAfterLeavingHome() {
        assertNull(
            ExperimentNavigationPolicy.destinationFor(
                item = imageLoaderItem,
                isHomeCurrentDestination = false,
            ),
        )
    }

    @Test
    fun ignoresUnknownExperimentWhileHomeIsCurrent() {
        val unknownItem = ExperimentItem(
            id = "unknown",
            title = "Unknown",
            description = "Not registered",
        )

        assertNull(
            ExperimentNavigationPolicy.destinationFor(
                item = unknownItem,
                isHomeCurrentDestination = true,
            ),
        )
    }
}
