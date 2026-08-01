# MiniImageLoader LruCache Memory Cache Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a cache-first, process-memory `LruCache<String, Bitmap>` to MiniImageLoader without changing its public API.

**Architecture:** A focused internal `BitmapMemoryCache` wraps Android's platform `LruCache`, while a pure `BitmapCacheSizing` object calculates byte-to-KiB sizes for local JVM testing. `MiniImageLoader` checks the cache on the main thread before showing a placeholder, stores successful background decodes, and keeps the existing request-token protection for ImageView updates.

**Tech Stack:** Kotlin, Android XML Views, `android.util.LruCache`, `Bitmap`, existing JUnit 4 tests, Gradle Kotlin DSL.

## Global Constraints

- Keep `MiniImageLoader.load(url, imageView)` and the existing four-argument overload unchanged.
- Use only Android platform APIs and existing project dependencies; do not add a third-party cache or test framework.
- Set the memory-cache maximum to one eighth of `Runtime.getRuntime().maxMemory()`.
- Charge each Bitmap by `allocationByteCount`, rounded up to at least 1 KiB.
- Cache only successfully decoded Bitmaps under the normalized full URL.
- On a cache hit, update the ImageView on the main thread without showing the placeholder or entering the download pool.
- Keep empty-URL, network-error, decode-error, error-drawable, and request-token behavior unchanged.
- Do not add disk caching, lifecycle awareness, cancellation, request deduplication, cache-clearing APIs, statistics APIs, transformations, or Bitmap recycling.
- Add concise Chinese comments to new core cache code and non-obvious cache-flow branches.
- After source or test changes, run both `test` and `assembleDebug` as required by `AGENTS.md`.

---

### Task 1: Cache sizing and Android LruCache wrapper

**Files:**
- Create: `mini-image-loader/src/test/java/com/yangtianyu/frameworklab/imageloader/BitmapCacheSizingTest.kt`
- Create: `mini-image-loader/src/main/java/com/yangtianyu/frameworklab/imageloader/BitmapMemoryCache.kt`

**Interfaces:**
- Produces: `BitmapCacheSizing.maxSizeKilobytes(maxMemoryBytes: Long): Int`
- Produces: `BitmapCacheSizing.entrySizeKilobytes(allocationByteCount: Int): Int`
- Produces: `BitmapMemoryCache(maxSizeKilobytes: Int = ...)`
- Produces: `operator fun BitmapMemoryCache.get(url: String): Bitmap?`
- Produces: `fun BitmapMemoryCache.put(url: String, bitmap: Bitmap)`

- [x] **Step 1: Write the failing size-policy test**

Create `BitmapCacheSizingTest.kt`:

```kotlin
package com.yangtianyu.frameworklab.imageloader

import org.junit.Assert.assertEquals
import org.junit.Test

class BitmapCacheSizingTest {

    @Test
    fun usesOneEighthOfMaximumHeapInKilobytes() {
        val maxMemoryBytes = 64L * 1024L * 1024L

        val result = BitmapCacheSizing.maxSizeKilobytes(maxMemoryBytes)

        assertEquals(8 * 1024, result)
    }

    @Test
    fun keepsCacheCapacityAtLeastOneKilobyte() {
        assertEquals(1, BitmapCacheSizing.maxSizeKilobytes(0L))
    }

    @Test
    fun roundsBitmapAllocationUpToWholeKilobytes() {
        assertEquals(1, BitmapCacheSizing.entrySizeKilobytes(1))
        assertEquals(1, BitmapCacheSizing.entrySizeKilobytes(1024))
        assertEquals(2, BitmapCacheSizing.entrySizeKilobytes(1025))
    }
}
```

- [x] **Step 2: Run the test and verify RED**

Run:

```powershell
.\gradlew.bat :mini-image-loader:testDebugUnitTest --tests "*BitmapCacheSizingTest" --console=plain
```

Expected: compilation fails with an unresolved reference to `BitmapCacheSizing`, proving the new policy does not exist yet.

Because `AGENTS.md` requires a build after every modification, also run:

```powershell
.\gradlew.bat assembleDebug --console=plain
```

Expected: `BUILD SUCCESSFUL`; the failing test source is not part of the Debug APK compilation.

- [x] **Step 3: Implement the minimal sizing policy and cache wrapper**

Create `BitmapMemoryCache.kt`:

```kotlin
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
```

- [x] **Step 4: Run tests and build to verify GREEN**

Run:

```powershell
.\gradlew.bat test assembleDebug --console=plain
```

Expected: `BitmapCacheSizingTest` passes, all existing tests pass, and `BUILD SUCCESSFUL` is printed.

- [x] **Step 5: Commit the focused cache component**

```powershell
git add mini-image-loader/src/main/java/com/yangtianyu/frameworklab/imageloader/BitmapMemoryCache.kt mini-image-loader/src/test/java/com/yangtianyu/frameworklab/imageloader/BitmapCacheSizingTest.kt
git commit -m "feat: add bitmap LruCache component"
```

---

### Task 2: Cache-first MiniImageLoader flow

**Files:**
- Modify: `mini-image-loader/src/test/java/com/yangtianyu/frameworklab/imageloader/MiniImageLoaderTest.kt`
- Modify: `mini-image-loader/src/main/java/com/yangtianyu/frameworklab/imageloader/MiniImageLoader.kt`

**Interfaces:**
- Consumes: `BitmapMemoryCache.get(url: String): Bitmap?`
- Consumes: `BitmapMemoryCache.put(url: String, bitmap: Bitmap)`
- Preserves: `MiniImageLoader.load(String?, ImageView)` and `MiniImageLoader.load(String?, ImageView, Int, Int)`

- [x] **Step 1: Write a failing wiring test**

Add this test to `MiniImageLoaderTest`:

```kotlin
@Test
fun ownsOneBitmapMemoryCache() {
    val loaderClass = Class.forName(
        MiniImageLoader::class.java.name,
        false,
        javaClass.classLoader,
    )
    val memoryCacheFieldCount = loaderClass.declaredFields.count { field ->
        field.type == BitmapMemoryCache::class.java
    }

    assertEquals(1, memoryCacheFieldCount)
}
```

- [x] **Step 2: Run the test and verify RED**

Run:

```powershell
.\gradlew.bat :mini-image-loader:testDebugUnitTest --tests "*MiniImageLoaderTest.ownsOneBitmapMemoryCache" --console=plain
```

Expected: FAIL because `MiniImageLoader` owns zero `BitmapMemoryCache` fields.

Then run the required APK compilation:

```powershell
.\gradlew.bat assembleDebug --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 3: Add the cache instance and cache-first main-thread branch**

Replace `MiniImageLoader.kt` with this complete implementation:

```kotlin
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
 * 轻量图片加载器入口，负责内存缓存、HTTP 下载、Bitmap 解码和线程切换。
 *
 * 当前不包含磁盘缓存、请求取消、请求去重或生命周期感知。
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

    private fun loadInBackground(
        requestToken: ImageRequestToken,
        imageView: ImageView,
        @DrawableRes errorResId: Int,
    ) {
        val bitmap = downloadAndDecode(requestToken.url)
        if (bitmap != null) {
            // 即使当前 ImageView 已复用，成功结果仍可供后续相同 URL 请求使用。
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
}
```

- [x] **Step 4: Run module tests and required build to verify GREEN**

Run:

```powershell
.\gradlew.bat test assembleDebug --console=plain
```

Expected: the new cache-ownership test and all existing tests pass; both modules assemble successfully.

- [x] **Step 5: Commit cache-first loading**

```powershell
git add mini-image-loader/src/main/java/com/yangtianyu/frameworklab/imageloader/MiniImageLoader.kt mini-image-loader/src/test/java/com/yangtianyu/frameworklab/imageloader/MiniImageLoaderTest.kt
git commit -m "feat: use memory cache before downloading"
```

---

### Task 3: Teaching documentation and final verification

**Files:**
- Modify: `README.md`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `docs/superpowers/plans/2026-08-01-mini-image-loader-memory-cache.md`

**Interfaces:**
- Documents: automatic in-process LruCache behavior and explicit exclusions
- Preserves: existing app screen layout and MiniImageLoader public API

- [x] **Step 1: Update the README cache description**

In the library structure list, add:

```text
├── BitmapMemoryCache        基于 LruCache 的 Bitmap 内存缓存
```

In “当前实现”, add:

```markdown
- 使用最大堆内存的 1/8 作为 LruCache 容量，按 Bitmap 实际分配字节数计费
- 缓存命中时直接显示 Bitmap，不进入下载线程池
```

Remove `内存缓存` from “暂不实现”. Keep disk cache, lifecycle awareness, cancellation, request deduplication, retries, priorities, and transformations excluded.

- [x] **Step 2: Update the demo status text without changing layout**

Replace `image_loader_lab_status` in `strings.xml` with:

```xml
<string name="image_loader_lab_status">HttpURLConnection · LruCache · 4 download threads</string>
```

- [x] **Step 3: Mark the plan checklist complete during execution**

Change each completed `- [ ]` item in this plan to `- [x]` only after its command or edit has actually succeeded. Do not pre-check later steps.

- [x] **Step 4: Run full fresh verification**

Run:

```powershell
.\gradlew.bat test assembleDebug --rerun-tasks --console=plain
```

Expected: all unit tests pass, `BUILD SUCCESSFUL` is printed, and `app/build/outputs/apk/debug/app-debug.apk` exists and is non-empty.

- [x] **Step 5: Check scope and whitespace**

Run:

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors; only the memory-cache implementation, tests, README, status string, and this plan are changed or newly committed.

- [x] **Step 6: Commit documentation and verified plan state**

```powershell
git add README.md app/src/main/res/values/strings.xml docs/superpowers/plans/2026-08-01-mini-image-loader-memory-cache.md
git commit -m "docs: explain MiniImageLoader memory cache"
```
