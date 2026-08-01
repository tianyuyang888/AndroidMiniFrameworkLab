# Mini Image Loader V1 Implementation Plan

> Historical plan: this file records the V1 implementation process; its checklist and workspace notes reflect the environment when the plan was written.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Implement a first usable HttpURLConnection-based image loader and demonstrate it with at least 20 network images in the existing single-activity app.

**Architecture:** MiniImageLoader remains the public object facade and coordinates a shared four-thread ExecutorService, a main-thread Handler, HttpImageDownloader, BitmapFactory decoding, and an ImageView request tag. Pure URL and HTTP behavior stays in focused internal classes that can be verified with local JVM tests.

**Tech Stack:** Kotlin, Android XML Views, HttpURLConnection, ExecutorService, Handler/Looper, BitmapFactory, RecyclerView, ViewBinding, JUnit 4, Gradle Kotlin DSL.

## Global Constraints

- Public API must support MiniImageLoader.load(url, imageView).
- Placeholder and error images use optional Drawable resource IDs.
- Downloads must use HttpURLConnection on a fixed four-thread pool.
- Every ImageView access must happen on the main thread.
- null, blank URL, network errors, non-2xx responses, empty bodies, and decode failure must not escape to callers.
- The app demo must show at least 20 HTTPS network images in RecyclerView.
- Key implementation classes and non-obvious concurrency code require Chinese comments.
- Do not add memory caching, disk caching, lifecycle awareness, cancellation, request deduplication, retries, transformations, or callbacks.
- Keep the existing app and mini-image-loader modules; add no Gradle module or Activity.
- At the time this plan was written, the workspace was not a Git repository, so no commit step was included.

---

### Task 1: URL normalization

**Files:**
- Create: mini-image-loader/src/test/java/com/yangtianyu/frameworklab/imageloader/ImageUrlValidatorTest.kt
- Create: mini-image-loader/src/main/java/com/yangtianyu/frameworklab/imageloader/ImageUrlValidator.kt

**Interfaces:**
- Produces: internal object ImageUrlValidator.
- Produces: fun normalize(url: String?): String?, returning null for null or blank input and a trimmed URL otherwise.

- [ ] **Step 1: Write the failing validator tests**

    class ImageUrlValidatorTest {
        @Test fun rejectsNull() = assertNull(ImageUrlValidator.normalize(null))
        @Test fun rejectsBlank() = assertNull(ImageUrlValidator.normalize("   "))
        @Test fun trimsValidUrl() {
            assertEquals(
                "https://example.com/image.jpg",
                ImageUrlValidator.normalize("  https://example.com/image.jpg  "),
            )
        }
    }

- [ ] **Step 2: Verify RED**

Run: .\gradlew.bat :mini-image-loader:testDebugUnitTest
Expected: FAIL because ImageUrlValidator is unresolved.

- [ ] **Step 3: Implement the minimal validator**

    internal object ImageUrlValidator {
        fun normalize(url: String?): String? = url?.trim()?.takeIf(String::isNotEmpty)
    }

- [ ] **Step 4: Verify GREEN**

Run: .\gradlew.bat :mini-image-loader:testDebugUnitTest
Expected: BUILD SUCCESSFUL.

### Task 2: HttpURLConnection downloader

**Files:**
- Create: mini-image-loader/src/test/java/com/yangtianyu/frameworklab/imageloader/HttpImageDownloaderTest.kt
- Create: mini-image-loader/src/main/java/com/yangtianyu/frameworklab/imageloader/HttpConnectionFactory.kt
- Create: mini-image-loader/src/main/java/com/yangtianyu/frameworklab/imageloader/HttpImageDownloader.kt
- Create: mini-image-loader/src/main/java/com/yangtianyu/frameworklab/imageloader/ImageDownloadException.kt

**Interfaces:**
- Produces: internal fun interface HttpConnectionFactory with open(url: String): HttpURLConnection.
- Produces: internal object DefaultHttpConnectionFactory.
- Produces: internal class HttpImageDownloader with download(url: String): ByteArray.
- Produces: internal class ImageDownloadException(message: String) extending IOException.

- [ ] **Step 1: Write fake-connection tests**

Use a FakeHttpURLConnection subclass to cover:

    assertArrayEquals(body, downloader.download(url))
    assertTrue(connection.wasDisconnected)
    assertThrows(ImageDownloadException::class.java) { downloader.download(url) }

The tests must separately verify a 200 body, a 404 response, an empty 200 body, and disconnect when responseCode throws IOException.

- [ ] **Step 2: Verify RED**

Run: .\gradlew.bat :mini-image-loader:testDebugUnitTest
Expected: FAIL because downloader types are unresolved.

- [ ] **Step 3: Implement the downloader**

download must configure:

    connectTimeout = 10_000
    readTimeout = 15_000
    instanceFollowRedirects = true
    useCaches = false
    doInput = true

It accepts responseCode in 200..299, reads inputStream.use { it.readBytes() }, throws ImageDownloadException for non-2xx or empty bytes, and always calls disconnect in finally.

- [ ] **Step 4: Verify GREEN**

Run: .\gradlew.bat :mini-image-loader:testDebugUnitTest
Expected: all validator and downloader tests pass.

### Task 3: Public loader, thread pool, decoding, and main-thread rendering

**Files:**
- Modify: mini-image-loader/src/test/java/com/yangtianyu/frameworklab/imageloader/MiniImageLoaderTest.kt
- Modify: mini-image-loader/src/main/java/com/yangtianyu/frameworklab/imageloader/MiniImageLoader.kt
- Create: mini-image-loader/src/main/res/values/ids.xml

**Interfaces:**
- Preserves: const val NAME = "Mini Image Loader".
- Produces two- and four-argument JVM overloads of load.

- [ ] **Step 1: Add a failing public API reflection test**

Load MiniImageLoader without class initialization and assert declared static load methods exist with parameter counts 2 and 4. This validates the required two-argument API without constructing Android ImageView in a local JVM test.

- [ ] **Step 2: Verify RED**

Run: .\gradlew.bat :mini-image-loader:testDebugUnitTest
Expected: FAIL because no load overload exists.

- [ ] **Step 3: Implement MiniImageLoader**

The public signature is:

    @JvmStatic
    @JvmOverloads
    fun load(
        url: String?,
        imageView: ImageView,
        @DrawableRes placeholderResId: Int = 0,
        @DrawableRes errorResId: Int = 0,
    )

Create one fixed four-thread ExecutorService, Handler(Looper.getMainLooper()), and HttpImageDownloader. A dispatchOnMain helper executes immediately when already on the main Looper and posts otherwise.

On main: create a unique ImageRequestToken for every load call, store it in R.id.mini_image_loader_request_token, then set the placeholder when nonzero. Invalid URL immediately sets the error when nonzero.

In the pool: download bytes, decode with BitmapFactory.decodeByteArray, treat null as failure, catch Exception, and dispatch the result to main. Before rendering, require the keyed tag to reference the same request token. Set the Bitmap on success or error resource on failure.

- [ ] **Step 4: Verify library tests and compilation**

Run: .\gradlew.bat :mini-image-loader:testDebugUnitTest :mini-image-loader:assembleDebug
Expected: BUILD SUCCESSFUL.

### Task 4: Twenty-image RecyclerView demo

**Files:**
- Create: app/src/test/java/com/yangtianyu/frameworklab/imageloader/ImageDemoCatalogTest.kt
- Create: app/src/main/java/com/yangtianyu/frameworklab/imageloader/ImageDemoCatalog.kt
- Create: app/src/main/java/com/yangtianyu/frameworklab/imageloader/ImageDemoAdapter.kt
- Modify: app/src/main/java/com/yangtianyu/frameworklab/imageloader/ImageLoaderLabFragment.kt
- Modify: app/src/main/AndroidManifest.xml
- Modify: app/src/main/res/layout/fragment_image_loader_lab.xml
- Create: app/src/main/res/layout/item_image_demo.xml
- Create: app/src/main/res/drawable/image_placeholder.xml
- Create: app/src/main/res/drawable/image_error.xml
- Modify: app/src/main/res/values/strings.xml
- Modify: README.md

**Interfaces:**
- Produces: object ImageDemoCatalog with fun urls(): List<String>.
- Produces: ImageDemoAdapter using MiniImageLoader.load with placeholder and error resources.

- [ ] **Step 1: Write the failing demo catalog test**

    @Test fun containsAtLeastTwentyHttpsImages() {
        val urls = ImageDemoCatalog.urls()
        assertTrue(urls.size >= 20)
        assertTrue(urls.all { it.startsWith("https://") })
        assertEquals(urls.size, urls.distinct().size)
    }

- [ ] **Step 2: Verify RED**

Run: .\gradlew.bat :app:testDebugUnitTest
Expected: FAIL because ImageDemoCatalog is unresolved.

- [ ] **Step 3: Implement deterministic demo URLs**

Return 20 seeded Lorem Picsum URLs:

    (1..20).map { index ->
        "https://picsum.photos/seed/framework-lab-$index/600/400"
    }

- [ ] **Step 4: Verify catalog GREEN**

Run: .\gradlew.bat :app:testDebugUnitTest
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Build the RecyclerView UI**

Add INTERNET permission. Replace the centered placeholder layout with a vertical header and RecyclerView. Each MaterialCardView row has a 220dp centerCrop ImageView and URL TextView. The adapter submits ImageDemoCatalog.urls() and calls:

    MiniImageLoader.load(
        url = url,
        imageView = binding.demoImage,
        placeholderResId = R.drawable.image_placeholder,
        errorResId = R.drawable.image_error,
    )

ImageLoaderLabFragment configures LinearLayoutManager, clears the adapter in onDestroyView, and retains the existing ViewBinding pattern.

- [ ] **Step 6: Update documentation**

README documents the load API, HttpURLConnection, four-thread pool, main-thread rendering, 20-image demo, and explicitly deferred cache/lifecycle/cancellation features.

### Task 5: Full verification

**Files:**
- Verify all modified files and generated artifacts.

- [ ] **Step 1: Run all tests**

Run: .\gradlew.bat testDebugUnitTest --rerun-tasks
Expected: BUILD SUCCESSFUL with zero failures.

- [ ] **Step 2: Run Android Lint**

Run: .\gradlew.bat lintDebug
Expected: BUILD SUCCESSFUL with no new functional issue.

- [ ] **Step 3: Build the debug APK**

Run: .\gradlew.bat assembleDebug --rerun-tasks
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Inspect the APK**

Verify app/build/outputs/apk/debug/app-debug.apk exists, has nonzero size, package com.yangtianyu.frameworklab, minSdk 24, targetSdk 34, and one launcher MainActivity.
