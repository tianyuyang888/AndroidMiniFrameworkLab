# Mini Image Loader V1 设计规格

## 目标

在现有 mini-image-loader Android Library 中实现第一版可用图片加载器，并在 app 模块用 RecyclerView 展示至少 20 张网络图片。第一版只覆盖直接下载、解码、线程切换、占位和错误显示。

## 公开 API

MiniImageLoader 保持 Kotlin object 外观，对外提供以下方法：

    @JvmOverloads
    @JvmStatic
    fun load(
        url: String?,
        imageView: ImageView,
        @DrawableRes placeholderResId: Int = 0,
        @DrawableRes errorResId: Int = 0,
    )

默认参数确保 MiniImageLoader.load(url, imageView) 可以直接调用。资源 ID 为 0 时跳过相应的占位图或错误图更新。

## Library 架构

- MiniImageLoader：公开入口，持有共享的固定 4 线程 ExecutorService、主线程 Handler、下载器和解码流程。
- ImageUrlValidator：把 null、空串或纯空白 URL 判为无效，并返回 trim 后的有效 URL。
- HttpImageDownloader：通过可替换的 HttpConnectionFactory 创建 HttpURLConnection，便于 JVM 单元测试。
- HttpConnectionFactory：生产实现使用 URL(url).openConnection() 并校验为 HttpURLConnection。
- Library 资源 ID：为 ImageView 保存当前请求 URL。结果回主线程后仅在 tag 仍匹配时更新，防止 RecyclerView 复用时旧请求覆盖新条目。

不提供请求对象、回调、取消 API、缓存 API 或生命周期 API。

## 下载与线程模型

1. load 可从任意线程调用。
2. 通过主线程 Handler 设置请求 tag 和占位图。
3. 空 URL 不提交到线程池；在主线程显示错误图。
4. 有效 URL 提交到固定 4 线程池。
5. HttpURLConnection 设置 10 秒连接超时、15 秒读取超时、允许重定向、禁用 URLConnection 自身缓存，只接受 200 至 299 响应。
6. 输入流通过 use 关闭，字节数组为空视为下载失败，连接始终在 finally 中 disconnect。
7. BitmapFactory.decodeByteArray 返回 null 时视为解析失败。
8. 成功 Bitmap 或错误图通过主线程 Handler 更新；更新前校验 ImageView 的请求 tag。

所有 ImageView 访问，包括 tag、占位图、成功图和错误图，都发生在主线程。

## 错误行为

- null、空串和纯空白 URL：显示错误图，不执行网络任务。
- URL 格式错误、连接异常、读取异常和非 2xx 响应：显示错误图。
- 空响应和 Bitmap 解码失败：显示错误图。
- 未传 errorResId 时不额外改图，保留现有占位状态。
- 第一版不向调用方抛出网络或解码异常，也不提供错误回调。

## app 演示页

现有 ImageLoaderLabFragment 改为 RecyclerView 页面。ImageDemoCatalog 提供至少 20 个 HTTPS 示例地址，ImageDemoAdapter 为每项展示固定高度 ImageView 和 URL 文本，并调用 MiniImageLoader.load 传入本地占位图与错误图。

app Manifest 增加 INTERNET 权限。页面沿用单 Activity 与 Navigation 结构，不新增 Activity。

## 注释要求

公开 API、HTTP 下载器、线程池与主线程调度、RecyclerView 请求复用保护、演示数据和适配器使用中文注释。简单属性和显而易见的 ViewBinding 代码不堆叠注释。

## 测试与验收

- ImageUrlValidator JVM 测试覆盖 null、空白和 trim。
- HttpImageDownloader JVM 测试覆盖 2xx 成功、非 2xx、空响应、异常时 disconnect。
- ImageDemoCatalog JVM 测试覆盖图片数量不少于 20 且全部为 HTTPS。
- 保留并通过现有单元测试。
- Android Lint 不应新增功能性错误。
- 最终执行 gradlew.bat assembleDebug，必须 BUILD SUCCESSFUL 并生成非空 app-debug.apk。

## 明确排除

- 内存缓存
- 磁盘缓存
- 生命周期感知
- 请求取消
- 请求去重或合并
- 图片变换、缩略图和渐进式加载
- 重试、优先级和自定义回调
