# RecyclerView 图片复用错位修复设计

## 目标

解决图片列表快速滚动时，RecyclerView 复用 ViewHolder 造成的图片错位与旧图片残留问题。同时保留当前教学项目的简单边界，补充可讲解的测试和原因说明。

## 错位原因

RecyclerView 会让不同列表位置复用同一个 ImageView。位置 A 发起异步下载后，ViewHolder 可能已经被回收并重新绑定到位置 B。如果 A 的请求稍后完成并直接更新这个 ImageView，位置 B 就会短暂或持续显示 A 的图片。

现有 `MiniImageLoader` 已在每次 `load()` 时创建唯一 `ImageRequestToken`，并在结果回到主线程后验证 ImageView 的 keyed tag。这可以阻止“已经重新绑定到 B”之后，A 的旧结果覆盖 B。

仍需补充回收阶段保护：ViewHolder 进入回收池但尚未重新绑定时，旧请求仍是当前 token，也可能把结果写入池中的 ImageView；ImageView 中原有 Bitmap 也会继续保留。快速滚动和重新附着时，这会形成旧图残留或闪现窗口。

## 方案

### MiniImageLoader.clear

在现有公开入口中新增：

```kotlin
MiniImageLoader.clear(imageView)
```

该方法统一切换到主线程，然后完成两件事：

1. 为 ImageView 写入一个新的唯一 token，使之前所有请求都无法通过身份校验。
2. 调用 `setImageDrawable(null)`，立即移除回收控件中的旧图片。

`clear()` 只阻止旧请求更新目标 ImageView，不取消线程池任务或网络连接。已经成功下载并解码的 Bitmap 仍可写入 LruCache，供后续相同 URL 使用。

### RecyclerView 回收处理

`ImageDemoAdapter` 覆盖 `onViewRecycled()`，调用 ViewHolder 的 `recycle()`。ViewHolder 再调用 `MiniImageLoader.clear(binding.demoImage)`，随后 Adapter 调用父类实现。

绑定流程保持不变：每次 `bind()` 仍调用 `MiniImageLoader.load()`，设置新的请求 token，并在缓存未命中时显示占位图。

## 数据流

1. 位置 A 绑定 ImageView，`load()` 写入 token A 并开始下载。
2. ViewHolder 被回收，`clear()` 写入 token clear，并清空当前 Drawable。
3. A 的请求完成后仍可把成功 Bitmap 放入内存缓存。
4. A 回到主线程准备更新 ImageView 时，token A 与 token clear 不同，因此放弃 UI 更新。
5. ViewHolder 绑定位置 B，`load()` 写入 token B；之后只有 token B 对应的结果可以更新该 ImageView。

## 测试策略

- 扩展 `ImageRequestTokenTest`，明确验证回收时换入新 token 后，旧 token 无法匹配。
- 扩展 `MiniImageLoaderTest`，通过不初始化 Android object 的反射方式验证公开 `clear(ImageView)` API 存在。
- 不新增 Robolectric 或其他测试框架，不编写只能验证 mock 调用的空洞测试。
- 运行完整 `test` 和 `assembleDebug`。

本地 JVM 测试可以验证请求身份规则与 API 形状，但不能直接驱动真实 ImageView、主线程 Looper 和 RecyclerView 回收过程；真实快速滚动仍作为演示页冒烟验证项。

## 文档与注释

- 为 `clear()`、回收处理和 token 校验添加简洁中文注释。
- README 增加“RecyclerView 为什么会图片错位”说明，解释重新绑定保护和主动回收失效两层机制。

## 明确不实现

- 不取消正在执行的网络请求或线程池任务。
- 不增加请求去重、生命周期感知、磁盘缓存或 Bitmap 回收。
- 不清除 LruCache 中已经成功加载的图片。
- 不改变现有 `load()` 重载、占位图和错误图行为。
- 不新增第三方依赖或测试框架。

## 验收标准

- ViewHolder 回收时立即清空图片并使旧请求失效。
- 旧请求完成后不能更新已回收或已重新绑定的 ImageView。
- 成功结果仍能写入并复用现有 LruCache。
- `MiniImageLoader.load(...)` 的现有公开 API 和错误处理保持不变。
- 单元测试和 `assembleDebug` 均通过。
