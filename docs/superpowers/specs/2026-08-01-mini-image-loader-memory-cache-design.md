# MiniImageLoader LruCache 内存缓存设计

## 目标

在现有 `mini-image-loader` Android Library 中加入第一版内存缓存，减少同一图片 URL 在进程存活期间的重复下载与解码。实现保持教学项目所需的简单边界，不改变 `MiniImageLoader.load(...)` 的公开 API，也不提前加入磁盘缓存、生命周期或请求合并功能。

## 已确认决策

- 使用独立内部类 `BitmapMemoryCache`，不把缓存细节继续堆入 `MiniImageLoader`。
- 底层使用 Android 原生 `android.util.LruCache<String, Bitmap>`，不新增第三方依赖。
- 最大缓存容量为进程最大堆内存的 `1/8`。
- 缓存键为 `ImageUrlValidator` 返回的 trim 后完整 URL。
- 缓存命中时直接显示 Bitmap，不显示占位图。
- 不新增清理、容量统计或初始化配置等公开 API。

## 组件设计

### BitmapMemoryCache

`BitmapMemoryCache` 只负责 Bitmap 的读取、写入和容量计费：

- 构造时创建 `LruCache<String, Bitmap>`。
- `get(url)` 返回已缓存 Bitmap 或 null。
- `put(url, bitmap)` 保存成功解码的 Bitmap。
- 覆盖 `sizeOf`，按 `Bitmap.allocationByteCount` 计算条目占用。
- 淘汰 Bitmap 时不调用 `recycle()`，避免回收仍可能被 ImageView 使用的对象。

该类为 `internal`，调用方仍只接触 `MiniImageLoader.load(...)`。

### BitmapCacheSizing

同一源码文件内保留一个纯 Kotlin 的内部容量计算对象，负责：

- 把最大堆字节数换算为其 `1/8` 对应的 KiB，并限制在 `1..Int.MAX_VALUE`。
- 把 Bitmap 分配字节数向上换算为 KiB，不足 1 KiB 时按 1 KiB 计费。

容量计算与 Android 运行时对象分离，使核心数学规则可以使用现有 JUnit 在本地 JVM 验证。

## 加载数据流

1. `load()` 沿用现有 URL 归一化和主线程调度。
2. 主线程为本次调用创建唯一 `ImageRequestToken`，并写入 ImageView keyed tag。
3. URL 无效时保持现有行为：按参数显示占位图和错误图，不查询缓存。
4. URL 有效时先调用 `BitmapMemoryCache.get(normalizedUrl)`。
5. 缓存命中时直接调用 `imageView.setImageBitmap()` 并结束，不设置占位图、不提交线程池任务。
6. 缓存未命中时设置占位图，把下载和解码任务提交到现有四线程池。
7. 成功解码后在线程池中把 Bitmap 写入内存缓存，再切换到主线程。
8. 主线程仍必须校验 ImageView keyed tag 中的 token；只有当前请求可以更新 ImageView。

缓存写入不依赖 ImageView 是否仍显示该请求。即使 RecyclerView 条目已经复用，成功下载的 Bitmap 仍可供下一次相同 URL 请求使用。

## 错误和并发边界

- null、空串和纯空白 URL 不缓存。
- 网络异常、非 2xx、空响应和 Bitmap 解码失败不缓存。
- 错误图与异常吞吐行为保持现有实现不变。
- 同一 URL 在第一次下载完成前发起的多个请求仍可能重复下载；本次不实现请求去重或合并。
- `LruCache` 只管理 Bitmap 的强引用与最近最少使用淘汰，不负责磁盘持久化。
- 缓存实例跟随 `MiniImageLoader` object 存活，进程结束后自动释放。

## 测试与验证

新增本地 JVM 单元测试验证：

- 最大堆容量按 `1/8` 换算为 KiB。
- 极小容量至少保留 1 KiB。
- Bitmap 字节数向上换算为 KiB，且至少计为 1 KiB。
- `MiniImageLoader` 的公开 `load()` 重载保持不变。

不引入 Robolectric 或其他测试框架。Android 平台 `LruCache` 的实际淘汰行为不在本地 JVM stub 上重复测试；通过源码审查、Android 编译和现有演示页面验证集成。

最终执行：

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

## 明确排除

- 磁盘缓存
- 生命周期感知
- 请求取消
- 相同 URL 请求去重或合并
- 缓存清理和统计公开 API
- Bitmap 变换、缩略图和采样优化
- 主动回收 Bitmap
