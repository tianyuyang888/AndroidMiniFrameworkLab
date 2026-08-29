package com.yangtianyu.frameworklab.imageloader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageRequestTokenTest {

    @Test
    fun sameUrlRequestsUseDifferentIdentity() {
        val first = ImageRequestToken(TEST_URL)
        val second = ImageRequestToken(TEST_URL)

        assertTrue(first.matches(first))
        assertFalse(first.matches(second))
    }

    @Test
    fun recycledTargetRejectsRequestCreatedBeforeRecycle() {
        val requestBeforeRecycle = ImageRequestToken(TEST_URL)
        val tokenAfterRecycle = ImageRequestToken("")

        assertFalse(requestBeforeRecycle.matches(tokenAfterRecycle))
    }

    private companion object {
        const val TEST_URL = "https://example.com/image.jpg"
    }
}
