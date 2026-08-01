package com.yangtianyu.frameworklab.imageloader

/**
 * 使用 HttpURLConnection 完成单次图片字节下载。
 *
 * 本类只负责网络读取，不包含线程调度、图片解码或缓存逻辑。
 */
internal class HttpImageDownloader(
    private val connectionFactory: HttpConnectionFactory = DefaultHttpConnectionFactory,
) {

    fun download(url: String): ByteArray {
        val connection = connectionFactory.open(url)
        return try {
            connection.apply {
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
                instanceFollowRedirects = true
                useCaches = false
                doInput = true
                requestMethod = HTTP_GET
            }
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode !in SUCCESSFUL_RESPONSE_CODES) {
                throw ImageDownloadException("图片请求失败，HTTP 状态码：$responseCode")
            }

            val bytes = connection.inputStream.use { inputStream ->
                inputStream.readBytes()
            }
            if (bytes.isEmpty()) {
                throw ImageDownloadException("图片响应内容为空")
            }
            bytes
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val READ_TIMEOUT_MILLIS = 15_000
        const val HTTP_GET = "GET"
        val SUCCESSFUL_RESPONSE_CODES = 200..299
    }
}
