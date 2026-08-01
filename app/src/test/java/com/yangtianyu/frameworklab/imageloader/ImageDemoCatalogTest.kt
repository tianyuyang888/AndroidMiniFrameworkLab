package com.yangtianyu.frameworklab.imageloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageDemoCatalogTest {

    @Test
    fun containsAtLeastTwentyHttpsImages() {
        val urls = ImageDemoCatalog.urls()

        assertTrue(urls.size >= 20)
        assertTrue(urls.all { url -> url.startsWith("https://") })
        assertEquals(urls.size, urls.distinct().size)
    }
}
