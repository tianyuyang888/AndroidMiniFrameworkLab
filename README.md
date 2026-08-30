# AndroidMiniFrameworkLab

一个用于拆解和练习 Android 小型框架设计的可运行实验项目。首页使用 RecyclerView 展示实验入口，目前包含 **Mini Image Loader** 和 **Vehicle Status Center** 两个实验。

## 技术栈

- Kotlin（Android Gradle Plugin 9.1.1 内置 Kotlin 支持）
- XML View + ViewBinding，不使用 Jetpack Compose
- Gradle Kotlin DSL / Gradle 9.3.1
- minSdk 24，compileSdk / targetSdk 34
- 单 Activity + AndroidX Navigation
- RecyclerView
- Android Service + AIDL/Binder
- LiveData / ViewModel

## 项目结构

    AndroidMiniFrameworkLab/
    ├── app/                         Android 应用与实验入口
    │   ├── MainActivity             唯一 Activity / Navigation Host
    │   ├── home/                    RecyclerView 首页与实验目录
    │   └── imageloader/             20 张网络图片演示页
    ├── mini-image-loader/           第一版图片加载 Android Library
        ├── MiniImageLoader          对外 API、线程池和主线程更新
        ├── BitmapMemoryCache        基于 LruCache 的 Bitmap 内存缓存
        ├── HttpImageDownloader      HttpURLConnection 下载
        └── ImageUrlValidator        空 URL 与空白 URL 处理
    └── mini-vehicle-service/        车辆模型、AIDL 契约、模拟数据源和独立进程 Service
        ├── VehicleSnapshot          可跨进程传输的车辆快照
        ├── IVehicleService          同步查询、控制和回调注册接口
        ├── FakeVehicleDataSource    本地确定性模拟数据源
        └── VehicleDataService       运行在 :vehicle 进程的私有 Service

Gradle 工程包含 `app`、`mini-image-loader` 和 `mini-vehicle-service` 三个模块。`app` 仍是唯一应用模块；两个 `mini-*` 模块都是 Android Library。

## Vehicle Status Center

Vehicle Status Center 用本地模拟车辆数据演示 Android 跨进程架构。数据流如下：

    FakeVehicleDataSource
        → VehicleDataService（独立 :vehicle 进程）
        → AIDL/Binder
        → VehicleServiceClient（app 进程）
        → VehicleDashboardViewModel
        → XML View

`VehicleSnapshot` 包含车速、挡位、电量、续航、四门开关、门锁和空调状态。Service 每秒生成一次模拟快照，并通过 `IVehicleStateCallback` 推送给 App。客户端把同步 Binder 调用放到单线程执行器，回调切回主线程；ViewModel 再将连接状态、快照新鲜度和指令结果归约为页面状态。

### 驾驶限制与断连恢复

Debug 构建的实验控制台可以设置停车、行驶、右后门打开三类预设并模拟服务断连；Release 会隐藏整个控制台。预设只在数据新鲜、已连接、车辆静止且处于 P 挡时启用，切换到行驶后需要等待自动模拟器回到停车状态。页面会同时显示四门开关、总锁状态以及最近一次指令的明确结果。

- 页面仅在数据新鲜、车速为 0 且挡位为 P 时启用全车解锁，先避免用户发出明显危险操作。
- Service 以自己的最新快照再次校验危险指令，避免把 UI 禁用状态当作安全边界。
- 非主动断连后固定等待 1 秒重连，最多自动尝试 3 次；耗尽后显示手动重试入口。
- 连接不是 `CONNECTED`，或快照超过 3 秒未更新时，页面显示数据过期并禁用依赖新鲜车辆状态的操作。
- Debug 包提供“模拟服务断连”按钮，用于杀死远端 `:vehicle` 进程并观察过期与重连；非 debuggable 构建隐藏按钮，Service 也会拒绝 Debug 专用接口。

### 横屏运行与断连演示

1. 在 Android Studio 中创建或选择 API 24 及以上的模拟器，建议设置为 **1280×720 横屏**。
2. 运行 `app`，从 RecyclerView 首页进入 **Vehicle Status Center**。
3. 等待页面由“连接中”变为“已连接”，观察车速、挡位、电量和续航每秒变化。
4. 车辆行驶时确认“解锁所有车门”不可用；模拟序列停车后再执行解锁。
5. 在 Debug 包中点击“模拟服务断连”，观察旧数据过期、等待 1 秒以及自动重连过程。
6. 若连续三次都无法连接，使用页面上的“手动重试”。完整记录项见 `docs/demo/vehicle-status-center-checklist.md`。

### 真实性边界

**本模块没有连接真实 CAN、Vehicle HAL、CarService，也不代表量产 AAOS 经验。** 当前车辆数据完全来自应用内的 `FakeVehicleDataSource`，项目只用于练习 AIDL/Binder、独立进程 Service、线程切换、状态归约、驾驶限制和断连恢复。真实车机还需要适配厂商信号、权限体系、系统签名、故障降级、车辆法规、安全审计和硬件在环测试等生产能力。

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
- 每次加载使用唯一请求 token，结果回到主线程后只有 token 仍匹配时才允许更新 ImageView
- ViewHolder 回收时主动清空 ImageView 并替换 token，阻止回收池中的旧请求回写
- 使用最大堆内存的 1/8 作为 LruCache 容量，按 Bitmap 实际分配字节数计费
- 缓存命中时直接显示 Bitmap，不进入下载线程池

## RecyclerView 图片为什么会错位

RecyclerView 会复用 ViewHolder。同一个 ImageView 先为位置 A 发起异步下载，随后可能被重新绑定到位置 B；如果 A 的结果较晚返回并直接写入控件，B 就会显示 A 的图片。

本项目使用两层保护：

1. 每次 `load()` 都写入唯一请求 token，旧请求回到主线程时必须验证 token，避免覆盖已经重新绑定的新位置。
2. `onViewRecycled()` 调用 `MiniImageLoader.clear()`，立即清空旧图片并替换 token，避免回收池阶段仍被旧请求更新。

`clear()` 不取消后台下载。成功结果仍可进入 LruCache，但已经回收或重新绑定的 ImageView 不会接收它。

## 暂不实现

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
6. 若要演示 Vehicle Status Center，建议把设备旋转为 1280×720 横屏。

演示页访问网络，需要设备或模拟器能够连接 https://picsum.photos。

## 命令行构建

Windows PowerShell：

    $env:JAVA_HOME = "<Android Studio 安装目录>\jbr"
    .\gradlew.bat test assembleDebug --rerun-tasks --console=plain

设备或模拟器在线时再运行仪器测试：

    adb devices -l
    .\gradlew.bat connectedDebugAndroidTest --rerun-tasks --console=plain

可选的静态检查：

    .\gradlew.bat lintDebug

APK 输出：

    app\build\outputs\apk\debug\app-debug.apk

`connectedDebugAndroidTest` 需要在线设备；没有设备时不能将仪器测试或手工横屏演示视为已通过。
