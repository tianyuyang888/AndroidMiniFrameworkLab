package com.yangtianyu.frameworklab.imageloader

import java.net.HttpURLConnection
import java.net.URL

/**
 * 隔离 URLConnection 的创建边界，使 HTTP 下载行为可以在 JVM 测试中验证。
 */
internal fun interface HttpConnectionFactory {
    fun open(url: String): HttpURLConnection
}

internal object DefaultHttpConnectionFactory : HttpConnectionFactory {

    override fun open(url: String): HttpURLConnection {
        val connection = URL(url).openConnection()
        return connection as? HttpURLConnection
            ?: throw ImageDownloadException("图片地址不是 HTTP 或 HTTPS 协议")
    }
}
