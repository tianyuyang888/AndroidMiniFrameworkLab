package com.yangtianyu.frameworklab.imageloader

import java.io.IOException

/**
 * 表示服务器响应或响应内容不满足图片下载要求。
 */
internal class ImageDownloadException(
    message: String,
) : IOException(message)
