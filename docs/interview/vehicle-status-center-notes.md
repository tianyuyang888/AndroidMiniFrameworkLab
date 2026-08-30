# Vehicle Status Center 五分钟面试讲解提纲

这份材料用于讲解教学项目中的 Android 跨进程实践。所有车辆数据均为本地模拟数据，不应表述为真实车机项目或量产经验。

## 1. 项目背景

**结论：** 我在单 Activity 教学项目中增加了一个车辆状态中心，用一个独立进程提供模拟车辆快照，App 展示车速、挡位、电量、续航、车门和空调状态。目标是用可运行、可测试的小项目串联 Android Framework 与车载方向常见知识点，而不是复刻完整车机系统。

**代码位置：** `app/src/main/java/com/yangtianyu/frameworklab/home/ExperimentCatalog.kt`、`app/src/main/java/com/yangtianyu/frameworklab/vehicle/VehicleDashboardFragment.kt`、`mini-vehicle-service/src/main/java/com/yangtianyu/frameworklab/vehicle/VehicleSnapshot.kt`。

**可追问问题：** 为什么把它放进现有实验目录？车辆快照为什么用不可变数据类？如果页面继续增加，单 Activity 架构如何组织导航？

## 2. 为什么使用独立进程

**结论：** `VehicleDataService` 声明为 `android:process=":vehicle"`，用于刻意制造真实的跨进程边界，让 App 必须处理序列化、Binder 调用、进程死亡和重连。它同时是 `exported=false` 的私有组件。教学价值在于隔离服务故障和练习 IPC；本项目没有证明这种拆分对所有业务都更优，真实项目仍需权衡内存、启动和维护成本。

**代码位置：** `mini-vehicle-service/src/main/AndroidManifest.xml`、`mini-vehicle-service/src/main/java/com/yangtianyu/frameworklab/vehicle/VehicleDataService.kt`。

**可追问问题：** 多进程会增加哪些成本？`exported=false` 能解决哪些边界、不能解决哪些问题？如何确认两个进程真的存在？

## 3. AIDL 接口

**结论：** `IVehicleService` 提供当前快照查询、回调注册以及温度、空调、车门和 Debug 模拟指令；`IVehicleStateCallback` 用 `oneway` 异步推送快照；`VehicleSnapshot` 手动实现 `Parcelable`。接口返回明确结果码，使参数错误、行驶拒绝、服务不可用和 Debug 限制可被区分。

**代码位置：** `mini-vehicle-service/src/main/aidl/com/yangtianyu/frameworklab/vehicle/IVehicleService.aidl`、`mini-vehicle-service/src/main/aidl/com/yangtianyu/frameworklab/vehicle/IVehicleStateCallback.aidl`、`mini-vehicle-service/src/main/java/com/yangtianyu/frameworklab/vehicle/VehicleSnapshot.kt`、`mini-vehicle-service/src/main/java/com/yangtianyu/frameworklab/vehicle/VehicleCommandResult.kt`。

**可追问问题：** 为什么回调使用 `oneway`？同步 Binder 方法有什么阻塞风险？Parcelable 字段演进时如何保持兼容？为什么不用广播或 Messenger？

## 4. 线程模型

**补充：** 状态写入与同步指令位于状态单线程；注册、注销和广播位于另一条 FIFO 回调单线程。状态线程按提交顺序向回调队列投递事件，因此注册首帧不会被更早的广播倒序覆盖，同时慢回调也不会占用同步指令的两秒等待预算。

**结论：** Service 的 Binder 线程只接收请求，所有状态写入进入单线程 `ScheduledExecutorService`，从而串行化模拟刷新和控制指令。同步指令有两秒超时，超时或中断会取消尚未开始的任务，防止客户端收到失败后排队指令又迟到修改状态。客户端也把同步 AIDL 调用放到可重启的单线程执行器，Binder 回调再投递到主线程；Fragment 只观察 LiveData 并更新 XML View。

**代码位置：** `mini-vehicle-service/src/main/java/com/yangtianyu/frameworklab/vehicle/VehicleDataService.kt`、`mini-vehicle-service/src/main/java/com/yangtianyu/frameworklab/vehicle/TimedVehicleCommandExecutor.kt`、`app/src/main/java/com/yangtianyu/frameworklab/vehicle/VehicleServiceClient.kt`、`app/src/main/java/com/yangtianyu/frameworklab/vehicle/VehicleDashboardViewModel.kt`。

**可追问问题：** 为什么同步 Binder 调用不能放主线程？单线程执行器如何避免竞态？如果命令已经开始执行，`cancel(false)` 有什么限制？回调过慢会怎样？

## 5. Binder 死亡处理

**结论：** 客户端同时使用 `linkToDeath`、`onServiceDisconnected` 和 API 26+ 的 `onBindingDied` 收敛意外断连；API 24～25 依赖前两条路径。每次绑定都有 generation，旧连接的迟到回调会被丢弃。非主动断连固定等待一秒，最多自动重连三次；主动 `stop()` 会清理回调、解除绑定并停止重连。

**代码位置：** `app/src/main/java/com/yangtianyu/frameworklab/vehicle/VehicleServiceClient.kt`、`app/src/main/java/com/yangtianyu/frameworklab/vehicle/VehicleClientGenerationTracker.kt`、`app/src/main/java/com/yangtianyu/frameworklab/vehicle/ReconnectPolicy.kt`、`app/src/main/java/com/yangtianyu/frameworklab/vehicle/VehicleReconnectReducer.kt`。

**可追问问题：** 为什么要同时处理多个断连回调？如何避免同一次死亡触发多次重连？为什么用 generation，而不是只看一个布尔值？重连耗尽后页面如何恢复？

## 6. 客户端与服务端双重安全校验

**补充：** Debug 控制台提供停车、行驶和右后门打开预设，只有停车 P 挡且数据新鲜时可用；Release 整体隐藏。页面还会显示四门开关、总锁状态和 SUCCESS 等各类指令结果，Service 仍负责最终校验。

**结论：** UI reducer 只在连接正常、数据未过期、车速为 0 且挡位为 P 时开放全车解锁，减少误操作；Service 仍以自己的最新快照再次执行 `DrivingRestrictionPolicy`，因为 UI 状态可能陈旧、被绕过或来自其他调用方。温度在 Service 端限制为 16～30，Debug 指令在非 debuggable 构建返回 `DEBUG_ONLY`。这只是教学级防护，不等同于功能安全认证。

**代码位置：** `app/src/main/java/com/yangtianyu/frameworklab/vehicle/VehicleDashboardStateReducer.kt`、`app/src/main/java/com/yangtianyu/frameworklab/vehicle/VehicleDashboardFragment.kt`、`mini-vehicle-service/src/main/java/com/yangtianyu/frameworklab/vehicle/DrivingRestrictionPolicy.kt`、`mini-vehicle-service/src/main/java/com/yangtianyu/frameworklab/vehicle/VehicleDataService.kt`。

**可追问问题：** 为什么 UI 禁用不能代替服务端校验？快照超过三秒为什么要按过期处理？真实车辆控制还需要哪些权限、身份和安全机制？

## 7. 测试策略

**补充：** 新增纯 JVM 测试覆盖回调 FIFO 与注册首帧顺序、四门和门锁显示映射、全部指令结果映射以及三类模拟预设构造。

**结论：** 纯 JVM 单元测试覆盖快照范围、状态存储、模拟序列、驾驶策略、命令超时取消、重连边界、代次过滤、执行器生命周期、UI reducer 和首页导航。仪器测试覆盖真实 Android 环境中的 Service 绑定、首帧、指令结果与 Debug 接口。最后用 `test assembleDebug` 做完整构建；有在线设备时再运行 `connectedDebugAndroidTest`，横屏布局和进程状态仍需手工验收。

**代码位置：** `mini-vehicle-service/src/test/`、`mini-vehicle-service/src/androidTest/java/com/yangtianyu/frameworklab/vehicle/VehicleDataServiceTest.kt`、`app/src/test/java/com/yangtianyu/frameworklab/vehicle/`、`docs/demo/vehicle-status-center-checklist.md`。

**可追问问题：** 哪些逻辑适合 JVM 测试，哪些必须依赖设备？如何让自动模拟不干扰 Service 仪器测试？如何测试三次重连和旧回调丢弃？

## 8. 真实限制

**结论：** 本模块没有连接真实 CAN、Vehicle HAL、CarService，也不代表量产 AAOS 经验。`FakeVehicleDataSource` 只是进程内确定性序列；项目没有厂商信号映射、系统级车辆权限、持久化、诊断、功耗、启动阶段、硬件在环、总线故障、功能安全或法规验证。它能证明的是对 Android IPC、线程、状态管理和失效恢复的理解，以及把这些知识做成可运行实验的能力。

**代码位置：** `mini-vehicle-service/src/main/java/com/yangtianyu/frameworklab/vehicle/FakeVehicleDataSource.kt`、`README.md`、`docs/demo/vehicle-status-center-checklist.md`。

**可追问问题：** 如果接入真实 VHAL，哪些层可以保留？如何把模拟数据源替换成生产数据源？真实 AAOS 中权限、系统签名、车辆属性订阅和故障降级如何设计？
