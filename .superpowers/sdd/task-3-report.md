# Task 3 实施报告：有效状态存储与确定性模拟序列

## 范围

本次仅实现车辆状态存储和确定性模拟数据源，不扩展后续服务或 UI 功能。

## RED 证据

先新增以下测试：

- `VehicleStateStoreTest.invalidUpdateKeepsLastValidSnapshot`
- `VehicleStateStoreTest.validUpdateBecomesCurrent`
- `FakeVehicleDataSourceTest.nextSnapshotIsValidAndUsesProvidedClock`

执行：

```text
JAVA_HOME=D:\android studio\jbr .\gradlew.bat :mini-vehicle-service:testDebugUnitTest --tests "*.VehicleStateStoreTest" --tests "*.FakeVehicleDataSourceTest"
```

结果：失败，`compileDebugUnitTestKotlin` 报告 `VehicleStateStore` 和 `FakeVehicleDataSource` 未定义，证明测试覆盖的是尚未实现的行为。

## GREEN 证据

新增最小实现后执行同一目标测试，结果：

```text
BUILD SUCCESSFUL in 4s
17 actionable tasks: 5 executed, 12 up-to-date
```

随后执行模块全量单测和构建：

```text
JAVA_HOME=D:\android studio\jbr .\gradlew.bat :mini-vehicle-service:testDebugUnitTest assembleDebug
```

结果：

```text
BUILD SUCCESSFUL in 4s
99 actionable tasks: 5 executed, 94 up-to-date
```

实现行为：

- `VehicleStateStore` 用 `@Volatile` 暴露最新快照，使用 `@Synchronized` 原子校验并更新，仅接受 `VehicleSnapshotValidator` 判定有效的候选值。
- `FakeVehicleDataSource.next` 根据当前车速、挡位、电量、续航和车门状态生成固定的下一帧，并使用调用方传入的时间戳，便于重复测试。

## 文件变更

- `mini-vehicle-service/src/main/java/com/yangtianyu/frameworklab/vehicle/VehicleStateStore.kt`
- `mini-vehicle-service/src/main/java/com/yangtianyu/frameworklab/vehicle/FakeVehicleDataSource.kt`
- `mini-vehicle-service/src/test/java/com/yangtianyu/frameworklab/vehicle/VehicleStateStoreTest.kt`
- `mini-vehicle-service/src/test/java/com/yangtianyu/frameworklab/vehicle/FakeVehicleDataSourceTest.kt`

## 已知限制

- 模拟序列是确定性演示数据源，不模拟真实车辆时钟、传感器噪声或持久化存储。
- 构造 `VehicleStateStore` 时假定初始快照由调用方提供；当前实现不会自动替换无效初始值。

## 面试知识点

- `@Volatile` 保证跨线程读取最新引用，`@Synchronized` 保证校验与写入的临界区原子性。
- 快照不可变对象（data class copy）和校验后更新可避免部分字段写入造成的不一致。
- 确定性数据源与显式时间参数有助于单元测试稳定、可重复。
