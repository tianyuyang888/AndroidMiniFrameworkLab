package com.yangtianyu.frameworklab.imageloader

import android.graphics.Bitmap
import android.util.LruCache

/**
 * 使用 URL 作为键保存已成功解码的 Bitmap，并按实际分配内存执行 LRU 淘汰。
 */
internal class BitmapMemoryCache(
    maxSizeKilobytes: Int = BitmapCacheSizing.maxSizeKilobytes(
        Runtime.getRuntime().maxMemory(),
    ),
) {

    private val cache = object : LruCache<String, Bitmap>(maxSizeKilobytes) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return BitmapCacheSizing.entrySizeKilobytes(value.allocationByteCount)
        }
    }

    operator fun get(url: String): Bitmap? {
        return cache.get(url)
    }

    fun put(url: String, bitmap: Bitmap) {
        cache.put(url, bitmap)
    }
}

/**
 * 把堆内存和 Bitmap 字节数统一换算为 LruCache 使用的 KiB 单位。
 */
internal object BitmapCacheSizing {

    fun maxSizeKilobytes(maxMemoryBytes: Long): Int {
        return (maxMemoryBytes / CACHE_HEAP_FRACTION / BYTES_PER_KIBIBYTE)
            .coerceIn(1L, Int.MAX_VALUE.toLong())
            .toInt()
    }

    fun entrySizeKilobytes(allocationByteCount: Int): Int {
        val positiveBytes = allocationByteCount.toLong().coerceAtLeast(1L)
        return ((positiveBytes + BYTES_PER_KIBIBYTE - 1L) / BYTES_PER_KIBIBYTE)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    private const val CACHE_HEAP_FRACTION = 8L
    private const val BYTES_PER_KIBIBYTE = 1024L
}
