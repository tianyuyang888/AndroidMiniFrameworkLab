package com.yangtianyu.frameworklab.imageloader

/**
 * 统一清理外部传入的图片地址，避免无效请求进入下载线程池。
 */
internal object ImageUrlValidator {

    fun normalize(url: String?): String? {
        return url?.trim()?.takeIf(String::isNotEmpty)
    }
}
