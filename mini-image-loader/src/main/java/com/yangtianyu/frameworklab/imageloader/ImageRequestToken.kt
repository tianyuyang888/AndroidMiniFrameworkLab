package com.yangtianyu.frameworklab.imageloader

/**
 * 每次 load 都创建独立 token，即使 URL 相同也能区分请求先后。
 */
internal class ImageRequestToken(
    val url: String,
) {

    fun matches(candidate: Any?): Boolean {
        return candidate === this
    }
}
