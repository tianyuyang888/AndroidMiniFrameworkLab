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
 * 第一版轻量图片加载器入口。
 *
 * 当前只负责 HTTP 下载、Bitmap 解码和线程切换，不包含缓存、请求取消或生命周期感知。
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
            imageView.setTag(R.id.mini_image_loader_request_url, requestToken)
            if (placeholderResId != NO_DRAWABLE_RESOURCE) {
                imageView.setImageResource(placeholderResId)
            }

            if (normalizedUrl == null) {
                showErrorIfCurrent(
                    imageView = imageView,
                    requestToken = requestToken,
                    errorResId = errorResId,
                )
                return@dispatchOnMain
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

    private fun loadInBackground(
        requestToken: ImageRequestToken,
        imageView: ImageView,
        @DrawableRes errorResId: Int,
    ) {
        val bitmap = downloadAndDecode(requestToken.url)
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
            imageView.getTag(R.id.mini_image_loader_request_url),
        )
    }

    private const val DOWNLOAD_THREAD_COUNT = 4
    private const val NO_DRAWABLE_RESOURCE = 0
}
