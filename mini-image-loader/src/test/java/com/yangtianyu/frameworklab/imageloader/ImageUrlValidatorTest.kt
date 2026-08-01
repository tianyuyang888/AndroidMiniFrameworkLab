package com.yangtianyu.frameworklab.imageloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageUrlValidatorTest {

    @Test
    fun rejectsNullUrl() {
        assertNull(ImageUrlValidator.normalize(null))
    }

    @Test
    fun rejectsBlankUrl() {
        assertNull(ImageUrlValidator.normalize("   "))
    }

    @Test
    fun trimsValidUrl() {
        assertEquals(
            "https://example.com/image.jpg",
            ImageUrlValidator.normalize("  https://example.com/image.jpg  "),
        )
    }
}
