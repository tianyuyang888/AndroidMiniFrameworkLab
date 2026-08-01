package com.yangtianyu.frameworklab.imageloader

/**
 * 提供固定种子的演示图片地址，保证每次启动都能观察同一组加载结果。
 */
object ImageDemoCatalog {

    fun urls(): List<String> {
        return (1..IMAGE_COUNT).map { index ->
            "https://picsum.photos/seed/framework-lab-$index/600/400"
        }
    }

    private const val IMAGE_COUNT = 20
}
