package com.yangtianyu.frameworklab.imageloader

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpImageDownloaderTest {

    @Test
    fun downloadsSuccessfulResponseBody() {
        val expected = byteArrayOf(1, 2, 3, 4)
        val connection = FakeHttpURLConnection(body = expected)
        val downloader = downloaderUsing(connection)

        val actual = downloader.download(TEST_URL)

        assertArrayEquals(expected, actual)
        assertTrue(connection.wasDisconnected)
    }

    @Test
    fun configuresConnectionForImageDownload() {
        val connection = FakeHttpURLConnection(body = byteArrayOf(1))
        val downloader = downloaderUsing(connection)

        downloader.download(TEST_URL)

        assertTrue(connection.instanceFollowRedirects)
        assertFalse(connection.useCaches)
        assertTrue(connection.doInput)
        assertTrue(connection.connectTimeout == 10_000)
        assertTrue(connection.readTimeout == 15_000)
    }

    @Test
    fun rejectsNonSuccessfulResponse() {
        val connection = FakeHttpURLConnection(statusCode = 404)
        val downloader = downloaderUsing(connection)

        assertThrows(ImageDownloadException::class.java) {
            downloader.download(TEST_URL)
        }
        assertTrue(connection.wasDisconnected)
    }

    @Test
    fun rejectsEmptyResponseBody() {
        val connection = FakeHttpURLConnection(body = byteArrayOf())
        val downloader = downloaderUsing(connection)

        assertThrows(ImageDownloadException::class.java) {
            downloader.download(TEST_URL)
        }
        assertTrue(connection.wasDisconnected)
    }

    @Test
    fun disconnectsWhenReadingResponseCodeFails() {
        val connection = FakeHttpURLConnection(
            responseException = IOException("network unavailable"),
        )
        val downloader = downloaderUsing(connection)

        assertThrows(IOException::class.java) {
            downloader.download(TEST_URL)
        }
        assertTrue(connection.wasDisconnected)
    }

    private fun downloaderUsing(
        connection: FakeHttpURLConnection,
    ): HttpImageDownloader {
        return HttpImageDownloader(HttpConnectionFactory { connection })
    }

    private class FakeHttpURLConnection(
        private val statusCode: Int = 200,
        private val body: ByteArray = byteArrayOf(1),
        private val responseException: IOException? = null,
    ) : HttpURLConnection(URL(TEST_URL)) {

        var wasDisconnected = false
            private set

        override fun connect() = Unit

        override fun disconnect() {
            wasDisconnected = true
        }

        override fun usingProxy(): Boolean = false

        override fun getResponseCode(): Int {
            responseException?.let { throw it }
            return statusCode
        }

        override fun getInputStream(): InputStream {
            return ByteArrayInputStream(body)
        }
    }

    private companion object {
        const val TEST_URL = "https://example.com/image.jpg"
    }
}
