# 车辆状态中心设计规范

## 1. 背景与目标

`AndroidMiniFrameworkLab` 当前包含通用 Android 实验应用和 `mini-image-loader` 图片加载库。下一阶段面向工作复习，在两周求职冲刺期内增加一个能够真实展示车载 Android 应用能力的实验模块。

用户的近期目标是应聘上海地区的车载 Android 应用或车载系统应用岗位；五年后的长期目标是具备独立开发车机相关产品的能力。本阶段只服务近期求职，不尝试一次性覆盖 Android Framework、Vehicle HAL、CAN、BSP 或量产流程。

新增“车辆状态中心”实验，形成一个可以在普通 Android Studio 环境中编译、运行、测试和演示的车机横屏原型。它需要证明：

- 使用 Kotlin、XML View 和 ViewModel 构建状态驱动界面。
- 使用 Android Service、AIDL 和 Binder 完成真实的跨进程通信。
- 订阅车辆状态，并把回调转换为稳定、可观察的 UI 状态。
- 处理绑定失败、Binder 断连、远端进程死亡、数据过期和自动重连。
- 根据车速和挡位限制危险操作，并在服务端重复执行安全校验。
- 使用纯 Kotlin 单元测试覆盖核心规则，使用少量 Android 测试验证 Service 绑定链路。
- 保持代码简单、可运行、易于讲解，不引入第三方框架。

## 2. 非目标

- 不接入真实 CAN 总线、MCU 或 OBD 设备。
- 不实现或修改真实 Vehicle HAL、CarService、AOSP Framework、Launcher 或 SystemUI。
- 不把模拟车辆数据描述为量产 AAOS 项目经验。
- 不实现导航、媒体、蓝牙电话、语音、OTA、多屏联动等独立子系统。
- 不引入 Compose、RxJava、依赖注入框架或车载第三方 SDK。
- 不修改 `mini-image-loader` 的职责，也不让车辆模块依赖图片加载模块。

## 3. 项目模块

### 3.1 `app`

- 在首页增加“车辆状态中心”入口。
- 增加 `VehicleDashboardFragment` 横屏演示页面。
- 增加 `VehicleDashboardViewModel`，只暴露单一 UI 状态。
- 增加 `VehicleServiceClient`，负责绑定服务、转发回调、监听死亡和安排重连。
- 增加 Debug 专用实验控制台，用于稳定复现不同车速、挡位、车门和断连场景。

### 3.2 `mini-vehicle-service`

新增一个 Android Library，承担：

- AIDL 服务接口和回调接口。
- 可跨进程传输的车辆快照模型。
- 运行在应用私有 `:vehicle` 进程中的 `VehicleDataService`。
- `FakeVehicleDataSource` 模拟车辆信号。
- 车辆数据范围验证。
- 服务端驾驶安全校验和明确的指令结果码。

Service 不导出给其他应用，只允许当前应用绑定。Library Manifest 声明 Service，App 构建时通过 Manifest 合并生效。

### 3.3 `mini-image-loader`

保持现状，继续作为线程池、网络、LruCache 和 RecyclerView 异步竞态实验，与车辆模块无依赖关系。

## 4. 核心数据模型

### 4.1 `VehicleSnapshot`

车辆服务发布不可变 Parcelable 快照，保证同一时刻的状态一致：

- `speedKph: Int`，范围 0～240。
- `gear: VehicleGear`，取值 `PARK`、`REVERSE`、`NEUTRAL`、`DRIVE`。
- `batteryPercent: Int`，范围 0～100。
- `rangeKm: Int`，范围 0～2000。
- 四个车门的开关状态。
- `areDoorsLocked: Boolean`，表示全部车门当前是否上锁。
- `temperatureCelsius: Int`，范围 16～30。
- `fanSpeed: Int`，范围 0～7。
- `isAcOn: Boolean`。
- `updatedAtElapsedRealtime: Long`，使用单调时钟判断数据是否过期。

App 在连接状态不是 `Connected`，或当前单调时间距离快照时间超过 3 秒时，将快照标记为过期。

服务端只发布通过范围检查的快照。发现异常值时保留上一份有效数据并记录日志，不发布部分非法状态。

### 4.2 连接状态

App 维护 `Disconnected`、`Connecting`、`Connected`、`RetryWaiting` 四种状态。连接错误作为状态原因表达，不把 `RemoteException` 或 Binder 对象暴露给 Fragment。

### 4.3 UI 状态

`VehicleDashboardUiState` 聚合连接状态、最近有效快照、数据是否过期、当前允许的控制操作和最近一次指令结果。Fragment 只渲染状态并转发用户意图，不直接调用 AIDL。

## 5. AIDL 契约

`IVehicleService` 提供：

- 获取当前车辆快照。
- 注册和取消车辆状态回调。
- 设置空调温度。
- 开启或关闭 A/C。
- 请求解锁全部车门。
- 修改模拟车辆状态或触发模拟断连；Service 只在应用带有 debuggable 标志时执行。

`IVehicleStateCallback` 每次回调完整快照。Binder 回调线程只负责转交数据，不更新 View。

控制指令返回明确结果码：`SUCCESS`、`INVALID_ARGUMENT`、`REJECTED_WHILE_DRIVING`、`SERVICE_UNAVAILABLE`、`DEBUG_ONLY`。Debug 控制入口不得出现在 Release 用户界面；即使 Release 客户端绕过 UI 调用模拟接口，Service 也必须返回 `DEBUG_ONLY` 且不改变数据。

Service 使用单一后台状态线程串行处理模拟数据和控制指令，并使用 `RemoteCallbackList` 管理跨进程回调。客户端不得在主线程执行同步 AIDL 控制调用；Binder 回调先转交给应用状态处理线程，再由主线程渲染 View。

## 6. 数据流

### 6.1 状态发布

1. `FakeVehicleDataSource` 在 `:vehicle` 进程产生模拟数据。
2. `VehicleDataService` 验证并保存最新有效快照。
3. Service 使用 AIDL 回调通知已注册客户端。
4. `VehicleServiceClient` 接收快照并切换到应用状态处理线程。
5. ViewModel 将车辆快照、连接状态和限制策略归约为 UI 状态。
6. Fragment 在主线程更新 XML View。

### 6.2 控制指令

1. Fragment 将用户事件交给 ViewModel。
2. ViewModel 根据当前状态做即时交互限制。
3. 允许发起的事件通过 `VehicleServiceClient` 调用 AIDL。
4. Service 根据自己的最新车速和挡位再次校验，不能信任客户端判断。
5. Service 返回结果码；成功后发布新快照，拒绝或失败时返回可展示结果。

## 7. 驾驶安全规则

- `speedKph > 0` 或挡位为 `DRIVE`/`REVERSE` 时，禁止“解锁全部车门”。
- 行驶过程中允许查看全部车辆状态。
- 行驶过程中允许小范围调整空调温度和 A/C。
- Debug 实验参数只能在车辆静止且挡位为 `PARK` 时编辑。

UI 禁用控件是体验层保护，Service 的重复校验才是执行保护。规则只用于教学演示，不宣称符合真实车规或 OEM 安全规范。

## 8. 横屏界面

目标尺寸为 1280×720：

- 顶部显示标题、车辆服务连接状态和时间。
- 左侧突出显示车速、挡位、电量、续航和四门状态。
- 右侧显示空调温度、风量、A/C 及驾驶限制提示。
- 底部标明数据来自本地模拟器，Debug 构建显示实验控制台入口。

连接中显示占位状态并禁止控制。断连时保留最后数据，但明确标记“数据可能已过期”。

## 9. 断连与重连

`onServiceDisconnected`、`DeathRecipient` 通知和 `RemoteException` 都视为非主动断连。API 26 及以上额外处理 `onBindingDied`；API 24～25 依赖其他三条路径：

1. 清除远端 Binder 引用和回调注册状态。
2. UI 进入 `RetryWaiting`，保留最近有效快照并标记过期。
3. 固定延迟 1 秒重连，最多连续尝试 3 次。
4. 成功后取消待执行任务、重新注册回调并读取最新快照。
5. 三次失败后保持断开，并提供手动重试。

Service 连接只在页面处于 STARTED 状态时保持，进入 STOPPED 后主动注销回调、解绑并取消重连任务，避免持有 Fragment 或后台无意义绑定。本阶段不实现指数退避。

## 10. 错误处理

- AIDL 和 Binder 异常在 `VehicleServiceClient` 边界转换为应用状态。
- 非法模拟数据由 Service 拒绝发布，上一份有效快照继续生效。
- 指令参数错误通过结果码反馈，不使用异常表达正常业务拒绝。
- 日志只记录连接状态、指令类型、结果码和必要异常，不包含个人信息。
- 首次连接失败时展示不可用状态，不展示伪造的默认车辆数据。

## 11. 测试策略

### 11.1 纯 Kotlin 单元测试

- 车辆数据各字段范围和边界值。
- 驾驶状态下允许与禁止的指令。
- 快照、连接状态到 UI 状态的归约。
- 数据过期判断使用单调时间。
- 重连最多三次、成功后停止、主动解绑后不再重试。
- 指令结果码到用户提示的映射。

### 11.2 模块集成测试

- 回调注册与取消不会重复或泄漏。
- 模拟数据变化发布完整快照。
- 非法数据不会覆盖上一份有效快照。
- 调用方绕过 UI 时，Service 仍拒绝危险指令。

### 11.3 Android 仪器测试

首版只要求一个稳定关键路径：启动测试应用、绑定 `VehicleDataService` 并在超时前收到第一份车辆快照。远端进程强杀和自动重连作为手工演示场景，不编写脆弱的进程控制测试。

## 12. 验证与手工验收

每次代码修改后执行：

```powershell
.\gradlew.bat assembleDebug
```

涉及单元测试时同时执行：

```powershell
.\gradlew.bat test
```

存在可用模拟器时执行：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

手工验证：

1. 首页能够进入车辆状态中心。
2. 页面从连接中进入已连接并持续刷新数据。
3. 行驶中危险操作被 UI 禁用，Service 也拒绝绕过 UI 的调用。
4. 远端服务断连后保留旧值、标记过期并尝试重连。
5. 重连成功后恢复最新状态。

## 13. 两周交付边界

- 第一批：模块骨架、AIDL 契约、车辆模型和基础服务绑定。
- 第二批：模拟数据、状态回调、ViewModel 和横屏 Dashboard。
- 第三批：驾驶限制、指令结果、断连重连和错误状态。
- 第四批：单元测试、一个仪器测试、README、架构图和演示材料。

求职投递从第一天开始，不等待所有批次完成。只有已经实现、验证并能解释的能力才写入车载版简历。

## 14. 完成标准

- 只新增一个 `mini-vehicle-service` 模块。
- 普通 Android API 24 及以上横屏设备可以运行，不依赖真实车辆硬件。
- App 与车辆服务运行在不同进程并通过 AIDL 通信。
- 页面展示已定义的车辆状态和连接/交互状态。
- 服务端执行数据验证和驾驶限制校验。
- 断连重连符合三次固定延迟重试规则。
- 核心纯 Kotlin 规则具有单元测试。
- `assembleDebug` 和 `test` 通过；有模拟器时关键仪器测试通过。
- README 清楚区分模拟能力与真实 AAOS、Vehicle HAL、CAN 经验。
