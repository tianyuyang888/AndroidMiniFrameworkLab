package com.yangtianyu.frameworklab.imageloader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import androidx.annotation.DrawableRes
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 轻量图片加载器入口，包含内存缓存。
 *
 * 当前负责 HTTP 下载、Bitmap 解码、线程切换和内存缓存；不包含磁盘缓存、请求取消、请求去重或生命周期感知。
 */
object MiniImageLoader {

    const val NAME = "Mini Image Loader"

    private val threadCounter = AtomicInteger()

    /**
     * 所有网络请求共用固定四线程池，避免为每张图片创建新线程。
     */
    private val downloadExecutor: ExecutorService = Executors.newFixedThreadPool(
        DOWNLOAD_THREAD_COUNT,
    ) { runnable ->
        Thread(
            runnable,
            "mini-image-loader-${threadCounter.incrementAndGet()}",
        )
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val downloader = HttpImageDownloader()
    private val memoryCache = BitmapMemoryCache()

    /**
     * 加载网络图片到指定 ImageView。
     *
     * @param url 图片地址；null、空串或纯空白地址会直接显示错误图。
     * @param imageView 接收占位图、成功图片或错误图的目标控件。
     * @param placeholderResId 可选占位图资源 ID，0 表示不设置。
     * @param errorResId 可选错误图资源 ID，0 表示不设置。
     */
    @JvmStatic
    @JvmOverloads
    fun load(
        url: String?,
        imageView: ImageView,
        @DrawableRes placeholderResId: Int = 0,
        @DrawableRes errorResId: Int = 0,
    ) {
        val normalizedUrl = ImageUrlValidator.normalize(url)
        dispatchOnMain {
            val requestToken = ImageRequestToken(normalizedUrl.orEmpty())

            // 唯一 token 不占用普通 tag，也能区分同一个 URL 的连续两次请求。
            imageView.setTag(R.id.mini_image_loader_request_token, requestToken)

            if (normalizedUrl == null) {
                if (placeholderResId != NO_DRAWABLE_RESOURCE) {
                    imageView.setImageResource(placeholderResId)
                }
                showErrorIfCurrent(
                    imageView = imageView,
                    requestToken = requestToken,
                    errorResId = errorResId,
                )
                return@dispatchOnMain
            }

            // 命中内存缓存时直接显示 Bitmap，避免占位图闪烁和重复网络任务。
            val cachedBitmap = memoryCache[normalizedUrl]
            if (cachedBitmap != null) {
                imageView.setImageBitmap(cachedBitmap)
                return@dispatchOnMain
            }

            if (placeholderResId != NO_DRAWABLE_RESOURCE) {
                imageView.setImageResource(placeholderResId)
            }

            downloadExecutor.execute {
                loadInBackground(
                    requestToken = requestToken,
                    imageView = imageView,
                    errorResId = errorResId,
                )
            }
        }
    }

    /**
     * 回收或停止展示图片时，使旧请求失效并立即清空目标控件。
     *
     * 该操作不会取消后台下载；成功结果仍可写入内存缓存。
     * 若 clear() 与 load() 存在顺序依赖，必须从主线程按顺序调用；后台线程调用 clear()
     * 会异步投递到主线程 Looper，若随后在主线程直接调用 load()，两者的实际执行先后取决于
     * Looper 入队顺序。
     */
    @JvmStatic
    fun clear(imageView: ImageView) {
        dispatchOnMain {
            // 新 token 会让所有旧请求在回写前的身份校验中失败。
            imageView.setTag(
                R.id.mini_image_loader_request_token,
                ImageRequestToken(CLEARED_REQUEST_URL),
            )
            imageView.setImageDrawable(null)
        }
    }

    private fun loadInBackground(
        requestToken: ImageRequestToken,
        imageView: ImageView,
        @DrawableRes errorResId: Int,
    ) {
        val bitmap = downloadAndDecode(requestToken.url)
        if (bitmap != null) {
            // 成功解码的结果可供后续相同 URL 的请求直接复用。
            memoryCache.put(requestToken.url, bitmap)
        }
        dispatchOnMain {
            if (!isCurrentRequest(imageView, requestToken)) {
                return@dispatchOnMain
            }

            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
            } else {
                showErrorIfCurrent(
                    imageView = imageView,
                    requestToken = requestToken,
                    errorResId = errorResId,
                )
            }
        }
    }

    private fun downloadAndDecode(url: String): Bitmap? {
        return try {
            val bytes = downloader.download(url)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 所有 ImageView 访问都通过这里进入主线程；调用方本就在主线程时直接执行。
     */
    private fun dispatchOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    private fun showErrorIfCurrent(
        imageView: ImageView,
        requestToken: ImageRequestToken,
        @DrawableRes errorResId: Int,
    ) {
        if (
            errorResId != NO_DRAWABLE_RESOURCE &&
            isCurrentRequest(imageView, requestToken)
        ) {
            imageView.setImageResource(errorResId)
        }
    }

    private fun isCurrentRequest(
        imageView: ImageView,
        requestToken: ImageRequestToken,
    ): Boolean {
        return requestToken.matches(
            imageView.getTag(R.id.mini_image_loader_request_token),
        )
    }

    private const val DOWNLOAD_THREAD_COUNT = 4
    private const val NO_DRAWABLE_RESOURCE = 0
    private const val CLEARED_REQUEST_URL = ""
}
