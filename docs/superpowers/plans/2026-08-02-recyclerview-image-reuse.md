# RecyclerView Image Reuse Protection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent recycled RecyclerView ImageViews from displaying stale asynchronous image results, clear recycled content immediately, and document why image misplacement happens.

**Architecture:** Keep the existing unique `ImageRequestToken` check for every `load()` call, and add a public `MiniImageLoader.clear(ImageView)` operation that replaces the keyed tag and clears the drawable on the main thread. `ImageDemoAdapter.onViewRecycled()` delegates to that operation; background work is not cancelled and successful results may still populate the existing LruCache.

**Tech Stack:** Kotlin, Android XML Views, RecyclerView, existing `MiniImageLoader`, JUnit 4, Gradle Kotlin DSL.

## Global Constraints

- Keep the existing `MiniImageLoader.load(...)` overloads, placeholder behavior, error behavior, and LruCache behavior unchanged.
- `MiniImageLoader.clear(imageView)` must invalidate every older request for that ImageView and call `setImageDrawable(null)` on the main thread.
- `ImageDemoAdapter.onViewRecycled()` must clear the recycled row through the public MiniImageLoader API.
- A completed background request may still populate the memory cache, but it must not update a recycled or rebound ImageView.
- Do not add real network cancellation, request deduplication, lifecycle awareness, disk caching, Bitmap recycling, Robolectric, or another test framework.
- Add concise Chinese comments to new core code and explain the misplacement cause in README.
- After source or documentation changes, run `assembleDebug`; because unit tests change, also run `test`.

---

### Task 1: Add the public ImageView clear operation

**Files:**
- Modify: `mini-image-loader/src/test/java/com/yangtianyu/frameworklab/imageloader/MiniImageLoaderTest.kt`
- Modify: `mini-image-loader/src/test/java/com/yangtianyu/frameworklab/imageloader/ImageRequestTokenTest.kt`
- Modify: `mini-image-loader/src/main/java/com/yangtianyu/frameworklab/imageloader/MiniImageLoader.kt`

**Interfaces:**
- Produces: `@JvmStatic fun MiniImageLoader.clear(imageView: ImageView)`
- Preserves: `MiniImageLoader.load(String?, ImageView)` and `MiniImageLoader.load(String?, ImageView, Int, Int)`
- Uses: `ImageRequestToken.matches(candidate: Any?): Boolean`

- [ ] **Step 1: Write the failing public API test**

Add the Android import to `MiniImageLoaderTest.kt`:

```kotlin
import android.widget.ImageView
```

Add this test:

```kotlin
@Test
fun exposesImageViewClearMethod() {
    val loaderClass = Class.forName(
        MiniImageLoader::class.java.name,
        false,
        javaClass.classLoader,
    )
    val exposesClear = loaderClass.declaredMethods.any { method ->
        method.name == "clear" &&
            Modifier.isStatic(method.modifiers) &&
            method.parameterTypes.contentEquals(arrayOf(ImageView::class.java))
    }

    assertTrue(exposesClear)
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
$env:JAVA_HOME = "D:\android studio\jbr"
.\gradlew.bat :mini-image-loader:testDebugUnitTest --tests "*MiniImageLoaderTest.exposesImageViewClearMethod" --console=plain
```

Expected: the test fails because `MiniImageLoader` has no static `clear(ImageView)` method.

Because `AGENTS.md` requires a build after every modification, also run:

```powershell
.\gradlew.bat assembleDebug --console=plain
```

Expected: `BUILD SUCCESSFUL`; the Debug APK does not depend on unit-test success.

- [ ] **Step 3: Add a characterization test for recycled-token identity**

Add to `ImageRequestTokenTest.kt`:

```kotlin
@Test
fun recycledTargetRejectsRequestCreatedBeforeRecycle() {
    val requestBeforeRecycle = ImageRequestToken(TEST_URL)
    val tokenAfterRecycle = ImageRequestToken("")

    assertFalse(requestBeforeRecycle.matches(tokenAfterRecycle))
}
```

This documents the already-existing identity rule used by `clear()`. It is characterization coverage, so it may pass before production code changes; the new public API test remains the required RED test for this task.

- [ ] **Step 4: Implement the minimal clear operation**

Add to `MiniImageLoader`, after the existing `load()` function:

```kotlin
/**
 * 回收或停止展示图片时，使旧请求失效并立即清空目标控件。
 *
 * 该操作不会取消后台下载；成功结果仍可写入内存缓存。
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
```

Add beside the existing constants:

```kotlin
private const val CLEARED_REQUEST_URL = ""
```

Do not remove the token check in `loadInBackground()`, and do not remove successful cache insertion before that check.

- [ ] **Step 5: Run focused and complete verification**

Run:

```powershell
.\gradlew.bat :mini-image-loader:testDebugUnitTest --tests "*MiniImageLoaderTest.exposesImageViewClearMethod" --tests "*ImageRequestTokenTest" --console=plain
.\gradlew.bat test assembleDebug --console=plain
```

Expected: both focused test classes pass, all existing tests pass, and `BUILD SUCCESSFUL` is printed.

- [ ] **Step 6: Commit the clear API**

```powershell
git add mini-image-loader/src/main/java/com/yangtianyu/frameworklab/imageloader/MiniImageLoader.kt mini-image-loader/src/test/java/com/yangtianyu/frameworklab/imageloader/MiniImageLoaderTest.kt mini-image-loader/src/test/java/com/yangtianyu/frameworklab/imageloader/ImageRequestTokenTest.kt
git commit -m "feat: clear recycled image requests"
```

---

### Task 2: Invalidate requests when RecyclerView recycles a row

**Files:**
- Create: `app/src/test/java/com/yangtianyu/frameworklab/imageloader/ImageDemoAdapterTest.kt`
- Modify: `app/src/main/java/com/yangtianyu/frameworklab/imageloader/ImageDemoAdapter.kt`

**Interfaces:**
- Consumes: `MiniImageLoader.clear(imageView: ImageView)`
- Produces: `override fun ImageDemoAdapter.onViewRecycled(holder: ImageDemoViewHolder)`
- Produces: `fun ImageDemoViewHolder.recycle()`

- [ ] **Step 1: Write the failing Adapter wiring test**

Create `ImageDemoAdapterTest.kt`:

```kotlin
package com.yangtianyu.frameworklab.imageloader

import org.junit.Assert.assertTrue
import org.junit.Test

class ImageDemoAdapterTest {

    @Test
    fun declaresRecycleHookAndViewHolderCleanup() {
        val adapterClass = Class.forName(
            ImageDemoAdapter::class.java.name,
            false,
            javaClass.classLoader,
        )
        val viewHolderClass = Class.forName(
            ImageDemoAdapter.ImageDemoViewHolder::class.java.name,
            false,
            javaClass.classLoader,
        )

        assertTrue(
            adapterClass.declaredMethods.any { method ->
                method.name == "onViewRecycled" && method.parameterCount == 1
            },
        )
        assertTrue(
            viewHolderClass.declaredMethods.any { method ->
                method.name == "recycle" && method.parameterCount == 0
            },
        )
    }
}
```

- [ ] **Step 2: Run the Adapter test and verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*ImageDemoAdapterTest" --console=plain
```

Expected: FAIL because neither `ImageDemoAdapter.onViewRecycled()` nor `ImageDemoViewHolder.recycle()` is declared.

Then run the required source build:

```powershell
.\gradlew.bat assembleDebug --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Add the RecyclerView recycle hook**

Add to `ImageDemoAdapter`, immediately after `onBindViewHolder()`:

```kotlin
override fun onViewRecycled(holder: ImageDemoViewHolder) {
    holder.recycle()
    super.onViewRecycled(holder)
}
```

Add to `ImageDemoViewHolder`, after `bind()`:

```kotlin
/**
 * ViewHolder 进入回收池时立即清空图片，并阻止旧请求继续回写。
 */
fun recycle() {
    MiniImageLoader.clear(binding.demoImage)
}
```

Keep `bind()` calling `MiniImageLoader.load()` with the existing placeholder and error resources.

- [ ] **Step 4: Run tests and build to verify GREEN**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*ImageDemoAdapterTest" --console=plain
.\gradlew.bat test assembleDebug --console=plain
```

Expected: the new Adapter test passes, all existing tests pass, and `BUILD SUCCESSFUL` is printed.

- [ ] **Step 5: Commit the Adapter integration**

```powershell
git add app/src/main/java/com/yangtianyu/frameworklab/imageloader/ImageDemoAdapter.kt app/src/test/java/com/yangtianyu/frameworklab/imageloader/ImageDemoAdapterTest.kt
git commit -m "fix: invalidate recycled image rows"
```

---

### Task 3: Explain the bug and perform final verification

**Files:**
- Modify: `README.md`

**Interfaces:**
- Documents: why RecyclerView reuse causes stale image writes
- Documents: unique-token protection during rebind and explicit invalidation during recycle
- Preserves: all production APIs and behavior delivered by Tasks 1 and 2

- [ ] **Step 1: Update the current implementation list**

Replace the existing keyed-tag bullet with these two bullets:

```markdown
- 每次加载使用唯一请求 token，结果回到主线程后只有 token 仍匹配时才允许更新 ImageView
- ViewHolder 回收时主动清空 ImageView 并替换 token，阻止回收池中的旧请求回写
```

- [ ] **Step 2: Add the teaching explanation**

Add this section before “暂不实现”:

```markdown
## RecyclerView 图片为什么会错位

RecyclerView 会复用 ViewHolder。同一个 ImageView 先为位置 A 发起异步下载，随后可能被重新绑定到位置 B；如果 A 的结果较晚返回并直接写入控件，B 就会显示 A 的图片。

本项目使用两层保护：

1. 每次 `load()` 都写入唯一请求 token，旧请求回到主线程时必须验证 token，避免覆盖已经重新绑定的新位置。
2. `onViewRecycled()` 调用 `MiniImageLoader.clear()`，立即清空旧图片并替换 token，避免回收池阶段仍被旧请求更新。

`clear()` 不取消后台下载。成功结果仍可进入 LruCache，但已经回收或重新绑定的 ImageView 不会接收它。
```

- [ ] **Step 3: Run required build after documentation change**

Run:

```powershell
.\gradlew.bat assembleDebug --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run full fresh verification**

Run:

```powershell
.\gradlew.bat test assembleDebug --rerun-tasks --console=plain
```

Expected: all unit tests pass, `BUILD SUCCESSFUL` is printed, and `app/build/outputs/apk/debug/app-debug.apk` exists and is non-empty.

- [ ] **Step 5: Check scope and whitespace**

Run:

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors; only the README change remains uncommitted because Tasks 1 and 2 were already committed.

- [ ] **Step 6: Commit the explanation**

```powershell
git add README.md
git commit -m "docs: explain RecyclerView image reuse"
```

## Manual Smoke Test

On an API 24 or newer emulator/device with network access:

1. Open **Mini Image Loader**.
2. Rapidly scroll from the first rows to the last rows and back while images are still loading.
3. Verify recycled rows become empty immediately, rebound rows show the placeholder or the correct cached image, and a late result never displays beside another URL.

This manual check supplements the local JVM tests because they do not run a real RecyclerView, ImageView, or main Looper.
