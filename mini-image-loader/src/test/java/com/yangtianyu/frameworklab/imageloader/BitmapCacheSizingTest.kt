package com.yangtianyu.frameworklab.imageloader

import org.junit.Assert.assertEquals
import org.junit.Test

class BitmapCacheSizingTest {

    @Test
    fun usesOneEighthOfMaximumHeapInKilobytes() {
        val maxMemoryBytes = 64L * 1024L * 1024L

        val result = BitmapCacheSizing.maxSizeKilobytes(maxMemoryBytes)

        assertEquals(8 * 1024, result)
    }

    @Test
    fun keepsCacheCapacityAtLeastOneKilobyte() {
        assertEquals(1, BitmapCacheSizing.maxSizeKilobytes(0L))
    }

    @Test
    fun roundsBitmapAllocationUpToWholeKilobytes() {
        assertEquals(1, BitmapCacheSizing.entrySizeKilobytes(1))
        assertEquals(1, BitmapCacheSizing.entrySizeKilobytes(1024))
        assertEquals(2, BitmapCacheSizing.entrySizeKilobytes(1025))
    }
}
