package com.yangtianyu.frameworklab.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentNavigationPolicyTest {

    private val imageLoaderItem = ExperimentCatalog.all().single()

    @Test
    fun opensImageLoaderWhileHomeIsCurrent() {
        assertTrue(
            ExperimentNavigationPolicy.canOpenImageLoader(
                item = imageLoaderItem,
                isHomeCurrentDestination = true,
            ),
        )
    }

    @Test
    fun ignoresSecondClickAfterLeavingHome() {
        assertFalse(
            ExperimentNavigationPolicy.canOpenImageLoader(
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

        assertFalse(
            ExperimentNavigationPolicy.canOpenImageLoader(
                item = unknownItem,
                isHomeCurrentDestination = true,
            ),
        )
    }
}
