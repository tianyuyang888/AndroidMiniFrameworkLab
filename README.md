# AndroidMiniFrameworkLab

一个用于拆解和练习 Android 小型框架设计的可运行实验项目。首页使用 RecyclerView 展示实验入口；进入 **Mini Image Loader** 后，会加载 20 张网络图片。

## 技术栈

- Kotlin（Android Gradle Plugin 9.1.1 内置 Kotlin 支持）
- XML View + ViewBinding，不使用 Jetpack Compose
- Gradle Kotlin DSL / Gradle 9.3.1
- minSdk 24，compileSdk / targetSdk 34
- 单 Activity + AndroidX Navigation
- RecyclerView

## 项目结构

    AndroidMiniFrameworkLab/
    ├── app/                         Android 应用与实验入口
    │   ├── MainActivity             唯一 Activity / Navigation Host
    │   ├── home/                    RecyclerView 首页与实验目录
    │   └── imageloader/             20 张网络图片演示页
    └── mini-image-loader/           第一版图片加载 Android Library
        ├── MiniImageLoader          对外 API、线程池和主线程更新
        ├── HttpImageDownloader      HttpURLConnection 下载
        └── ImageUrlValidator        空 URL 与空白 URL 处理

Gradle 工程只包含 app 和 mini-image-loader 两个模块。

## 第一版图片加载器

Kotlin 调用：

    MiniImageLoader.load(
        url = imageUrl,
        imageView = imageView,
        placeholderResId = R.drawable.image_placeholder,
        errorResId = R.drawable.image_error,
    )

也可以直接使用两参数形式：

    MiniImageLoader.load(imageUrl, imageView)

当前实现：

- 使用 HttpURLConnection 下载图片
- 使用固定 4 线程池执行网络任务
- 使用 BitmapFactory 解码
- 所有 ImageView 更新切换到主线程
- 处理空 URL、网络异常、非 2xx、空响应和解码失败
- 支持占位图与错误图资源 ID
- 使用 keyed tag 防止 RecyclerView 复用后旧请求覆盖新图片

## 暂不实现

- 内存缓存
- 磁盘缓存
- 生命周期感知
- 请求取消
- 请求去重、重试、优先级和图片变换

## 使用 Android Studio 运行

1. 使用 Android Studio 打开项目根目录。
2. 在 Gradle JDK 中选择 JDK 17 或更新版本。
3. 确保 Android SDK Platform 34 已安装。
4. 等待 Gradle Sync 完成。
5. 选择 app 运行配置，在 API 24 或更新设备上运行。

演示页访问网络，需要设备或模拟器能够连接 https://picsum.photos。

## 命令行构建

Windows PowerShell：

    $env:JAVA_HOME = "<Android Studio 安装目录>\jbr"
    .\gradlew.bat testDebugUnitTest
    .\gradlew.bat lintDebug
    .\gradlew.bat assembleDebug

APK 输出：

    app\build\outputs\apk\debug\app-debug.apk
