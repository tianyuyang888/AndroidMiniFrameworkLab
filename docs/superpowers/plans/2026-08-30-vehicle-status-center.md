# Vehicle Status Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有单 Activity 教学项目中增加一个可运行、可测试的车辆状态中心，通过 AIDL/Binder 连接独立进程车辆服务，并展示车载横屏 UI、驾驶限制和断连重连。

**Architecture:** 新增 `mini-vehicle-service` Android Library，集中放置 Parcelable 车辆模型、AIDL 契约、模拟数据源和运行在 `:vehicle` 进程的 Service。`app` 通过 `VehicleServiceClient` 绑定服务，ViewModel 将车辆快照和连接状态归约为单一 UI 状态，Fragment 只负责生命周期转发和 XML View 渲染。

**Tech Stack:** Kotlin、XML View、ViewBinding、Android Service、AIDL/Binder、LiveData/ViewModel、JUnit 4、AndroidX Test、Gradle Kotlin DSL。

## Global Constraints

- 包名保持 `com.yangtianyu.frameworklab`，新增车辆代码使用 `com.yangtianyu.frameworklab.vehicle`。
- `minSdk = 24`，`compileSdk = 34`，Java 17；普通 Android 横屏模拟器即可运行。
- 使用 Kotlin 和 XML View，不使用 Jetpack Compose。
- 只新增 `mini-vehicle-service` 一个模块，不引入第三方业务框架。
- 车辆数据是本地模拟数据；README 必须明确未接入 CAN、Vehicle HAL 或真实 CarService。
- App 与车辆 Service 必须运行在不同进程并通过 AIDL 通信。
- UI 限制危险操作，Service 使用自己的最新快照再次校验。
- 非主动断连固定等待 1 秒，最多自动重连 3 次；API 26+ 处理 `onBindingDied`，API 24～25 依赖其他断连回调。
- 核心类添加中文注释；每次修改后执行 `./gradlew assembleDebug`，涉及单元测试同时执行 `./gradlew test`。

## File Map

- `settings.gradle.kts`：注册新模块。
- `mini-vehicle-service/build.gradle.kts`：Android Library、AIDL 和测试配置。
- `mini-vehicle-service/src/main/aidl/com/yangtianyu/frameworklab/vehicle/`：AIDL 服务、回调和 Parcelable 声明。
- `mini-vehicle-service/src/main/java/com/yangtianyu/frameworklab/vehicle/`：车辆模型、校验、驾驶策略、模拟序列和 Service。
- `mini-vehicle-service/src/main/AndroidManifest.xml`：声明私有 `:vehicle` 进程 Service。
- `app/src/main/java/com/yangtianyu/frameworklab/vehicle/`：客户端、重连策略、UI 状态、ViewModel 和 Fragment。
- `app/src/main/res/layout/fragment_vehicle_dashboard.xml`：竖屏保底布局。
- `app/src/main/res/layout-land/fragment_vehicle_dashboard.xml`：1280×720 横屏主布局。
- `app/src/main/java/com/yangtianyu/frameworklab/home/` 与 `main_nav_graph.xml`：新增首页入口和导航。
- `README.md` 与 `docs/interview/vehicle-status-center-notes.md`：运行说明、真实性边界和面试讲解材料。

---

### Task 1: 新模块与车辆快照模型

**Files:**
- Modify: `settings.gradle.kts`
- Create: `mini-vehicle-service/build.gradle.kts`
- Create: `mini-vehicle-service/consumer-rules.pro`
- Create: `mini-vehicle-service/src/main/AndroidManifest.xml`
- Create: `mini-vehicle-service/src/main/java/com/yangtianyu/frameworklab/vehicle/VehicleGear.kt`
- Create: `mini-vehicle-service/src/main/java/com/yangtianyu/frameworklab/vehicle/VehicleSnapshot.kt`
- Create: `mini-vehicle-service/src/main/java/com/yangtianyu/frameworklab/vehicle/VehicleSnapshotValidator.kt`
- Test: `mini-vehicle-service/src/test/java/com/yangtianyu/frameworklab/vehicle/VehicleSnapshotValidatorTest.kt`

**Interfaces:**
- Consumes: 无。
- Produces: `VehicleGear.fromCode(Int): VehicleGear`、`VehicleSnapshot`、`VehicleSnapshotValidator.isValid(VehicleSnapshot): Boolean`、`VehicleSnapshot.stoppedDefault()`。

- [ ] **Step 1: 注册最小可测试模块**

在 `settings.gradle.kts` 追加：

```kotlin
include(":mini-vehicle-service")
```

创建 `mini-vehicle-service/build.gradle.kts`：

```kotlin
plugins {
    id("com.android.library")
}

android {
    namespace = "com.yangtianyu.frameworklab.vehicle"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api("androidx.annotation:annotation:1.6.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.1")
}
```

创建空的 `consumer-rules.pro` 和以下 Manifest：

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

- [ ] **Step 2: 写车辆快照范围的失败测试**

```kotlin
package com.yangtianyu.frameworklab.vehicle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleSnapshotValidatorTest {
    private val valid = VehicleSnapshot.stoppedDefault()

    @Test fun acceptsBoundaryValues() {
        assertTrue(VehicleSnapshotValidator.isValid(valid.copy(speedKph = 0, batteryPercent = 0)))
        assertTrue(VehicleSnapshotValidator.isValid(valid.copy(speedKph = 240, batteryPercent = 100)))
    }

    @Test fun rejectsOutOfRangeValues() {
        assertFalse(VehicleSnapshotValidator.isValid(valid.copy(speedKph = -1)))
        assertFalse(VehicleSnapshotValidator.isValid(valid.copy(rangeKm = 2001)))
        assertFalse(VehicleSnapshotValidator.isValid(valid.copy(temperatureCelsius = 31)))
        assertFalse(VehicleSnapshotValidator.isValid(valid.copy(fanSpeed = 8)))
    }
}
```

- [ ] **Step 3: 运行测试确认失败**

Run: `.\gradlew.bat :mini-vehicle-service:testDebugUnitTest --tests "*.VehicleSnapshotValidatorTest"`

Expected: FAIL，提示 `VehicleSnapshot` 或 `VehicleSnapshotValidator` 未定义。

- [ ] **Step 4: 实现最小车辆模型和验证器**

`VehicleGear.kt`：

```kotlin
package com.yangtianyu.frameworklab.vehicle

enum class VehicleGear(val code: Int) {
    PARK(0), REVERSE(1), NEUTRAL(2), DRIVE(3);

    companion object {
        fun fromCode(code: Int): VehicleGear = entries.firstOrNull { it.code == code } ?: PARK
    }
}
```

`VehicleSnapshot.kt`：

```kotlin
package com.yangtianyu.frameworklab.vehicle

import android.os.Parcel
import android.os.Parcelable

data class VehicleSnapshot(
    val speedKph: Int,
    val gear: VehicleGear,
    val batteryPercent: Int,
    val rangeKm: Int,
    val isFrontLeftDoorOpen: Boolean,
    val isFrontRightDoorOpen: Boolean,
    val isRearLeftDoorOpen: Boolean,
    val isRearRightDoorOpen: Boolean,
    val areDoorsLocked: Boolean,
    val temperatureCelsius: Int,
    val fanSpeed: Int,
    val isAcOn: Boolean,
    val updatedAtElapsedRealtime: Long,
) : Parcelable {
    private constructor(parcel: Parcel) : this(
        speedKph = parcel.readInt(),
        gear = VehicleGear.fromCode(parcel.readInt()),
        batteryPercent = parcel.readInt(),
        rangeKm = parcel.readInt(),
        isFrontLeftDoorOpen = parcel.readByte().toInt() != 0,
        isFrontRightDoorOpen = parcel.readByte().toInt() != 0,
        isRearLeftDoorOpen = parcel.readByte().toInt() != 0,
        isRearRightDoorOpen = parcel.readByte().toInt() != 0,
        areDoorsLocked = parcel.readByte().toInt() != 0,
        temperatureCelsius = parcel.readInt(),
        fanSpeed = parcel.readInt(),
        isAcOn = parcel.readByte().toInt() != 0,
        updatedAtElapsedRealtime = parcel.readLong(),
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(speedKph)
        parcel.writeInt(gear.code)
        parcel.writeInt(batteryPercent)
        parcel.writeInt(rangeKm)
        parcel.writeByte(if (isFrontLeftDoorOpen) 1 else 0)
        parcel.writeByte(if (isFrontRightDoorOpen) 1 else 0)
        parcel.writeByte(if (isRearLeftDoorOpen) 1 else 0)
        parcel.writeByte(if (isRearRightDoorOpen) 1 else 0)
        parcel.writeByte(if (areDoorsLocked) 1 else 0)
        parcel.writeInt(temperatureCelsius)
        parcel.writeInt(fanSpeed)
        parcel.writeByte(if (isAcOn) 1 else 0)
        parcel.writeLong(updatedAtElapsedRealtime)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<VehicleSnapshot> {
        override fun createFromParcel(parcel: Parcel): VehicleSnapshot = VehicleSnapshot(parcel)
        override fun newArray(size: Int): Array<VehicleSnapshot?> = arrayOfNulls(size)

        fun stoppedDefault(updatedAtElapsedRealtime: Long = 0L): VehicleSnapshot = VehicleSnapshot(
            speedKph = 0,
            gear = VehicleGear.PARK,
            batteryPercent = 68,
            rangeKm = 320,
            isFrontLeftDoorOpen = false,
            isFrontRightDoorOpen = false,
            isRearLeftDoorOpen = false,
            isRearRightDoorOpen = false,
            areDoorsLocked = true,
            temperatureCelsius = 22,
            fanSpeed = 3,
            isAcOn = true,
            updatedAtElapsedRealtime = updatedAtElapsedRealtime,
        )
    }
}
```

`VehicleSnapshotValidator.kt`：

```kotlin
package com.yangtianyu.frameworklab.vehicle

object VehicleSnapshotValidator {
    fun isValid(snapshot: VehicleSnapshot): Boolean =
        snapshot.speedKph in 0..240 &&
            snapshot.batteryPercent in 0..100 &&
            snapshot.rangeKm in 0..2000 &&
            snapshot.temperatureCelsius in 16..30 &&
            snapshot.fanSpeed in 0..7 &&
            snapshot.updatedAtElapsedRealtime >= 0L
}
```

- [ ] **Step 5: 验证测试、构建并提交**

Run: `.\gradlew.bat :mini-vehicle-service:testDebugUnitTest assembleDebug`

Expected: `BUILD SUCCESSFUL`，上述测试全部 PASS。

```bash
git add settings.gradle.kts mini-vehicle-service
git commit -m "feat: add vehicle service model module"
```

---

### Task 2: 驾驶规则、指令结果与 AIDL 契约

**Files:**
- Create: `mini-vehicle-service/src/main/java/com/yangtianyu/frameworklab/vehicle/VehicleCommandResult.kt`
- Create: `mini-vehicle-service/src/main/java/com/yangtianyu/frameworklab/vehicle/DrivingRestrictionPolicy.kt`
- Create: `mini-vehicle-service/src/main/java/com/yangtianyu/frameworklab/vehicle/DebugAccessPolicy.kt`
- Create: `mini-vehicle-service/src/main/aidl/com/yangtianyu/frameworklab/vehicle/VehicleSnapshot.aidl`
- Create: `mini-vehicle-service/src/main/aidl/com/yangtianyu/frameworklab/vehicle/IVehicleStateCallback.aidl`
- Create: `mini-vehicle-service/src/main/aidl/com/yangtianyu/frameworklab/vehicle/IVehicleService.aidl`
- Test: `mini-vehicle-service/src/test/java/com/yangtianyu/frameworklab/vehicle/DrivingRestrictionPolicyTest.kt`

**Interfaces:**
- Consumes: `VehicleSnapshot`、`VehicleGear`。
- Produces: `DrivingRestrictionPolicy.canUnlockAllDoors`、`canEditSimulation`、`VehicleCommandResult` 常量、`IVehicleService`、`IVehicleStateCallback`。

- [ ] **Step 1: 写驾驶限制失败测试**

```kotlin
package com.yangtianyu.frameworklab.vehicle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingRestrictionPolicyTest {
    private val parked = VehicleSnapshot.stoppedDefault()

    @Test fun parkedVehicleAllowsDoorUnlockAndSimulationEditing() {
        assertTrue(DrivingRestrictionPolicy.canUnlockAllDoors(parked))
        assertTrue(DrivingRestrictionPolicy.canEditSimulation(parked))
    }

    @Test fun movingOrDriveGearRejectsDangerousCommands() {
        assertFalse(DrivingRestrictionPolicy.canUnlockAllDoors(parked.copy(speedKph = 1)))
        assertFalse(DrivingRestrictionPolicy.canUnlockAllDoors(parked.copy(gear = VehicleGear.DRIVE)))
        assertFalse(DrivingRestrictionPolicy.canEditSimulation(parked.copy(gear = VehicleGear.REVERSE)))
    }
}
```

同时创建 `DebugAccessPolicyTest`：

```kotlin
package com.yangtianyu.frameworklab.vehicle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugAccessPolicyTest {
    @Test fun allowsOnlyDebuggableBuild() {
        assertTrue(DebugAccessPolicy.isAllowed(isDebuggable = true))
        assertFalse(DebugAccessPolicy.isAllowed(isDebuggable = false))
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :mini-vehicle-service:testDebugUnitTest --tests "*.DrivingRestrictionPolicyTest"`

Expected: FAIL，提示 `DrivingRestrictionPolicy` 未定义。

- [ ] **Step 3: 实现策略和结果码**

```kotlin
package com.yangtianyu.frameworklab.vehicle

object DrivingRestrictionPolicy {
    fun canUnlockAllDoors(snapshot: VehicleSnapshot): Boolean =
        snapshot.speedKph == 0 && snapshot.gear == VehicleGear.PARK

    fun canEditSimulation(snapshot: VehicleSnapshot): Boolean =
        snapshot.speedKph == 0 && snapshot.gear == VehicleGear.PARK

    fun canControlClimate(snapshot: VehicleSnapshot): Boolean = true
}
```

```kotlin
package com.yangtianyu.frameworklab.vehicle

object VehicleCommandResult {
    const val SUCCESS = 0
    const val INVALID_ARGUMENT = 1
    const val REJECTED_WHILE_DRIVING = 2
    const val SERVICE_UNAVAILABLE = 3
    const val DEBUG_ONLY = 4
}
```

```kotlin
package com.yangtianyu.frameworklab.vehicle

object DebugAccessPolicy {
    fun isAllowed(isDebuggable: Boolean): Boolean = isDebuggable
}
```

- [ ] **Step 4: 添加可编译的 AIDL 契约**

`VehicleSnapshot.aidl`：

```aidl
package com.yangtianyu.frameworklab.vehicle;
parcelable VehicleSnapshot;
```

`IVehicleStateCallback.aidl`：

```aidl
package com.yangtianyu.frameworklab.vehicle;
import com.yangtianyu.frameworklab.vehicle.VehicleSnapshot;
oneway interface IVehicleStateCallback {
    void onVehicleSnapshotChanged(in VehicleSnapshot snapshot);
}
```

`IVehicleService.aidl`：

```aidl
package com.yangtianyu.frameworklab.vehicle;
import com.yangtianyu.frameworklab.vehicle.VehicleSnapshot;
import com.yangtianyu.frameworklab.vehicle.IVehicleStateCallback;
interface IVehicleService {
    VehicleSnapshot getCurrentSnapshot();
    void registerCallback(IVehicleStateCallback callback);
    void unregisterCallback(IVehicleStateCallback callback);
    int setTemperature(int temperatureCelsius);
    int setAcEnabled(boolean enabled);
    int unlockAllDoors();
    int setSimulationSnapshot(in VehicleSnapshot snapshot);
    int requestSimulatedProcessDeath();
}
```

- [ ] **Step 5: 验证并提交**

Run: `.\gradlew.bat :mini-vehicle-service:testDebugUnitTest :mini-vehicle-service:assembleDebug assembleDebug`

Expected: `BUILD SUCCESSFUL`，AIDL 生成代码并且策略测试 PASS。

```bash
git add mini-vehicle-service
git commit -m "feat: define vehicle aidl contract"
```

---

### Task 3: 有效状态存储与确定性模拟序列

**Files:**
- Create: `mini-vehicle-service/src/main/java/com/yangtianyu/frameworklab/vehicle/VehicleStateStore.kt`
- Create: `mini-vehicle-service/src/main/java/com/yangtianyu/frameworklab/vehicle/FakeVehicleDataSource.kt`
- Test: `mini-vehicle-service/src/test/java/com/yangtianyu/frameworklab/vehicle/VehicleStateStoreTest.kt`
- Test: `mini-vehicle-service/src/test/java/com/yangtianyu/frameworklab/vehicle/FakeVehicleDataSourceTest.kt`

**Interfaces:**
- Consumes: `VehicleSnapshot`、`VehicleSnapshotValidator`。
- Produces: `VehicleStateStore.current()`、`updateIfValid(VehicleSnapshot)`、`FakeVehicleDataSource.next(VehicleSnapshot, Long)`。

- [ ] **Step 1: 写存储与模拟序列失败测试**

```kotlin
package com.yangtianyu.frameworklab.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleStateStoreTest {
    @Test fun invalidUpdateKeepsLastValidSnapshot() {
        val initial = VehicleSnapshot.stoppedDefault()
        val store = VehicleStateStore(initial)
        assertFalse(store.updateIfValid(initial.copy(speedKph = 241)))
        assertEquals(initial, store.current())
    }

    @Test fun validUpdateBecomesCurrent() {
        val initial = VehicleSnapshot.stoppedDefault()
        val updated = initial.copy(temperatureCelsius = 23)
        val store = VehicleStateStore(initial)
        assertTrue(store.updateIfValid(updated))
        assertEquals(updated, store.current())
    }
}
```

```kotlin
package com.yangtianyu.frameworklab.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeVehicleDataSourceTest {
    @Test fun nextSnapshotIsValidAndUsesProvidedClock() {
        val next = FakeVehicleDataSource.next(VehicleSnapshot.stoppedDefault(), nowMs = 99L)
        assertTrue(VehicleSnapshotValidator.isValid(next))
        assertEquals(99L, next.updatedAtElapsedRealtime)
        assertEquals(VehicleGear.DRIVE, next.gear)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :mini-vehicle-service:testDebugUnitTest --tests "*.VehicleStateStoreTest" --tests "*.FakeVehicleDataSourceTest"`

Expected: FAIL，提示两个实现类未定义。

- [ ] **Step 3: 实现线程安全存储和可重复序列**

```kotlin
package com.yangtianyu.frameworklab.vehicle

class VehicleStateStore(initial: VehicleSnapshot) {
    @Volatile private var latest: VehicleSnapshot = initial

    fun current(): VehicleSnapshot = latest

    @Synchronized
    fun updateIfValid(candidate: VehicleSnapshot): Boolean {
        if (!VehicleSnapshotValidator.isValid(candidate)) return false
        latest = candidate
        return true
    }
}
```

```kotlin
package com.yangtianyu.frameworklab.vehicle

object FakeVehicleDataSource {
    fun next(current: VehicleSnapshot, nowMs: Long): VehicleSnapshot {
        val nextSpeed = if (current.speedKph >= 72) 0 else current.speedKph + 12
        return current.copy(
            speedKph = nextSpeed,
            gear = if (nextSpeed == 0) VehicleGear.PARK else VehicleGear.DRIVE,
            batteryPercent = if (nextSpeed == 0) current.batteryPercent else (current.batteryPercent - 1).coerceAtLeast(0),
            rangeKm = if (nextSpeed == 0) current.rangeKm else (current.rangeKm - 4).coerceAtLeast(0),
            isRearRightDoorOpen = nextSpeed == 0,
            updatedAtElapsedRealtime = nowMs,
        )
    }
}
```

- [ ] **Step 4: 验证并提交**

Run: `.\gradlew.bat :mini-vehicle-service:testDebugUnitTest assembleDebug`

Expected: `BUILD SUCCESSFUL`，四个车辆领域测试类全部 PASS。

```bash
git add mini-vehicle-service
git commit -m "feat: add simulated vehicle state source"
```

---

### Task 4: 独立进程 VehicleDataService 与绑定仪器测试

**Files:**
- Modify: `mini-vehicle-service/src/main/AndroidManifest.xml`
- Create: `mini-vehicle-service/src/main/java/com/yangtianyu/frameworklab/vehicle/VehicleDataService.kt`
- Test: `mini-vehicle-service/src/androidTest/java/com/yangtianyu/frameworklab/vehicle/VehicleDataServiceTest.kt`

**Interfaces:**
- Consumes: Task 1～3 的模型、策略、存储、模拟序列和 AIDL Stub。
- Produces: 可绑定组件 `VehicleDataService`；进程名 `:vehicle`；全部 `IVehicleService` 方法。

- [ ] **Step 1: 先声明 Service 并写绑定失败测试**

Manifest：

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <service
            android:name=".VehicleDataService"
            android:exported="false"
            android:process=":vehicle" />
    </application>
</manifest>
```

仪器测试：

```kotlin
package com.yangtianyu.frameworklab.vehicle

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VehicleDataServiceTest {
    @Test fun bindsAndReceivesFirstSnapshot() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val connected = CountDownLatch(1)
        val snapshotReceived = CountDownLatch(1)
        var service: IVehicleService? = null
        val callback = object : IVehicleStateCallback.Stub() {
            override fun onVehicleSnapshotChanged(snapshot: VehicleSnapshot) {
                snapshotReceived.countDown()
            }
        }
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                service = IVehicleService.Stub.asInterface(binder)
                service?.registerCallback(callback)
                connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }

        val bound = context.bindService(
            Intent(context, VehicleDataService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
        try {
            assertTrue(bound)
            assertTrue(connected.await(3, TimeUnit.SECONDS))
            assertNotNull(service?.currentSnapshot)
            assertTrue(snapshotReceived.await(3, TimeUnit.SECONDS))
        } finally {
            service?.unregisterCallback(callback)
            if (bound) context.unbindService(connection)
        }
    }
}
```

- [ ] **Step 2: 运行仪器测试确认失败**

Run: `.\gradlew.bat :mini-vehicle-service:connectedDebugAndroidTest`

Expected: FAIL，Manifest 找不到 `VehicleDataService` 实现或无法绑定。

- [ ] **Step 3: 实现 Service 主体**

创建 `VehicleDataService.kt`，严格实现以下结构：

```kotlin
package com.yangtianyu.frameworklab.vehicle

import android.app.Service
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.IBinder
import android.os.Process
import android.os.RemoteCallbackList
import android.os.SystemClock
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class VehicleDataService : Service() {
    private val callbacks = RemoteCallbackList<IVehicleStateCallback>()
    private val stateExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val store = VehicleStateStore(VehicleSnapshot.stoppedDefault(SystemClock.elapsedRealtime()))

    private val binder = object : IVehicleService.Stub() {
        override fun getCurrentSnapshot(): VehicleSnapshot = store.current()

        override fun registerCallback(callback: IVehicleStateCallback?) {
            if (callback == null) return
            stateExecutor.execute {
                callbacks.register(callback)
                callback.onVehicleSnapshotChanged(store.current())
            }
        }

        override fun unregisterCallback(callback: IVehicleStateCallback?) {
            if (callback != null) stateExecutor.execute { callbacks.unregister(callback) }
        }

        override fun setTemperature(value: Int): Int = command {
            if (value !in 16..30) return@command VehicleCommandResult.INVALID_ARGUMENT
            publish(store.current().copy(temperatureCelsius = value, updatedAtElapsedRealtime = SystemClock.elapsedRealtime()))
            VehicleCommandResult.SUCCESS
        }

        override fun setAcEnabled(enabled: Boolean): Int = command {
            publish(store.current().copy(isAcOn = enabled, updatedAtElapsedRealtime = SystemClock.elapsedRealtime()))
            VehicleCommandResult.SUCCESS
        }

        override fun unlockAllDoors(): Int = command {
            if (!DrivingRestrictionPolicy.canUnlockAllDoors(store.current())) {
                return@command VehicleCommandResult.REJECTED_WHILE_DRIVING
            }
            publish(store.current().copy(areDoorsLocked = false, updatedAtElapsedRealtime = SystemClock.elapsedRealtime()))
            VehicleCommandResult.SUCCESS
        }

        override fun setSimulationSnapshot(snapshot: VehicleSnapshot?): Int = command {
            if (!DebugAccessPolicy.isAllowed(isDebuggable())) return@command VehicleCommandResult.DEBUG_ONLY
            if (!DrivingRestrictionPolicy.canEditSimulation(store.current())) {
                return@command VehicleCommandResult.REJECTED_WHILE_DRIVING
            }
            if (snapshot == null || !VehicleSnapshotValidator.isValid(snapshot)) {
                return@command VehicleCommandResult.INVALID_ARGUMENT
            }
            publish(snapshot)
            VehicleCommandResult.SUCCESS
        }

        override fun requestSimulatedProcessDeath(): Int = command {
            if (!DebugAccessPolicy.isAllowed(isDebuggable())) return@command VehicleCommandResult.DEBUG_ONLY
            stateExecutor.schedule({ Process.killProcess(Process.myPid()) }, 100, TimeUnit.MILLISECONDS)
            VehicleCommandResult.SUCCESS
        }
    }

    override fun onCreate() {
        super.onCreate()
        stateExecutor.scheduleAtFixedRate(
            { publish(FakeVehicleDataSource.next(store.current(), SystemClock.elapsedRealtime())) },
            1,
            1,
            TimeUnit.SECONDS,
        )
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        callbacks.kill()
        stateExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun command(block: () -> Int): Int = try {
        stateExecutor.submit(Callable(block)).get(2, TimeUnit.SECONDS)
    } catch (error: Exception) {
        VehicleCommandResult.SERVICE_UNAVAILABLE
    }

    private fun publish(snapshot: VehicleSnapshot) {
        if (!store.updateIfValid(snapshot)) return
        val count = callbacks.beginBroadcast()
        try {
            repeat(count) { index -> callbacks.getBroadcastItem(index).onVehicleSnapshotChanged(snapshot) }
        } finally {
            callbacks.finishBroadcast()
        }
    }

    private fun isDebuggable(): Boolean =
        applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}
```

- [ ] **Step 4: 增加服务端行为测试**

在已经连接的测试体中追加以下精确断言；`finally` 仍执行注销和解绑：

```kotlin
val parked = requireNotNull(service?.currentSnapshot).copy(
    speedKph = 0,
    gear = VehicleGear.PARK,
    areDoorsLocked = true,
)
assertEquals(VehicleCommandResult.SUCCESS, service?.setSimulationSnapshot(parked))
assertEquals(VehicleCommandResult.SUCCESS, service?.unlockAllDoors())
assertFalse(requireNotNull(service?.currentSnapshot).areDoorsLocked)

val moving = requireNotNull(service?.currentSnapshot).copy(
    speedKph = 1,
    gear = VehicleGear.DRIVE,
)
assertEquals(VehicleCommandResult.SUCCESS, service?.setSimulationSnapshot(moving))
assertEquals(VehicleCommandResult.REJECTED_WHILE_DRIVING, service?.unlockAllDoors())
```

- [ ] **Step 5: 验证并提交**

Run: `.\gradlew.bat test assembleDebug`

Expected: `BUILD SUCCESSFUL`，本地单元测试 PASS。

Run（模拟器在线时）: `.\gradlew.bat :mini-vehicle-service:connectedDebugAndroidTest`

Expected: `BUILD SUCCESSFUL`，绑定、首帧和服务端驾驶校验测试 PASS。若没有模拟器，记录为待执行的环境验证，不声称仪器测试通过。

```bash
git add mini-vehicle-service
git commit -m "feat: add remote vehicle data service"
```

---

### Task 5: App 端连接状态与三次重连客户端

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/yangtianyu/frameworklab/vehicle/VehicleConnectionStatus.kt`
- Create: `app/src/main/java/com/yangtianyu/frameworklab/vehicle/ReconnectPolicy.kt`
- Create: `app/src/main/java/com/yangtianyu/frameworklab/vehicle/VehicleServiceClient.kt`
- Create: `app/src/main/java/com/yangtianyu/frameworklab/vehicle/VehicleReconnectReducer.kt`
- Test: `app/src/test/java/com/yangtianyu/frameworklab/vehicle/ReconnectPolicyTest.kt`

**Interfaces:**
- Consumes: `IVehicleService`、`IVehicleStateCallback`、`VehicleSnapshot`、`VehicleCommandResult`。
- Produces: `VehicleServiceClient.start()`、`stop()`、控制方法；`Listener.onConnectionChanged`、`onSnapshot`、`onCommandResult`。

- [ ] **Step 1: 添加模块依赖并写重连失败测试**

在 `app/build.gradle.kts` 添加：

```kotlin
implementation(project(":mini-vehicle-service"))
implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.2")
implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.2")
```

测试：

```kotlin
package com.yangtianyu.frameworklab.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReconnectPolicyTest {
    @Test fun returnsOneSecondForFirstThreeAttempts() {
        assertEquals(1_000L, ReconnectPolicy.delayMs(attempt = 1))
        assertEquals(1_000L, ReconnectPolicy.delayMs(attempt = 3))
    }

    @Test fun stopsAfterThreeAttempts() {
        assertNull(ReconnectPolicy.delayMs(attempt = 4))
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*.ReconnectPolicyTest"`

Expected: FAIL，提示 `ReconnectPolicy` 未定义。

- [ ] **Step 3: 实现连接枚举和重连策略**

```kotlin
package com.yangtianyu.frameworklab.vehicle

enum class VehicleConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, RETRY_WAITING }
```

```kotlin
package com.yangtianyu.frameworklab.vehicle

object ReconnectPolicy {
    private const val MAX_ATTEMPTS = 3
    fun delayMs(attempt: Int): Long? = if (attempt in 1..MAX_ATTEMPTS) 1_000L else null
}
```

- [ ] **Step 4: 实现 VehicleServiceClient**

实现以下公开契约和状态规则：

```kotlin
class VehicleServiceClient(
    context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onConnectionChanged(status: VehicleConnectionStatus)
        fun onSnapshot(snapshot: VehicleSnapshot)
        fun onCommandResult(result: Int)
    }

    fun start()
    fun stop()
    fun retryNow()
    fun setTemperature(value: Int)
    fun setAcEnabled(enabled: Boolean)
    fun unlockAllDoors()
    fun setSimulationSnapshot(snapshot: VehicleSnapshot)
    fun requestSimulatedProcessDeath()
}
```

实现必须逐项满足以下确定规则：

1. 保存 `applicationContext`，不保存 Activity 或 Fragment。
2. `start()` 只允许从停止状态进入 `CONNECTING`，使用显式 `Intent(context, VehicleDataService::class.java)` 绑定。
3. `onServiceConnected` 设置 `IVehicleService`、调用 `linkToDeath`、注册一个 AIDL callback、读取首份快照、清零重连次数并发出 `CONNECTED`。
4. AIDL callback 使用主线程 `Handler` 转发 `onSnapshot`；同步控制调用统一放入单线程 `ExecutorService`，完成后回主线程通知结果。
5. `onServiceDisconnected`、`onBindingDied`、`DeathRecipient` 和 `RemoteException` 汇聚到同一个 `handleUnexpectedDisconnect()`；该函数最多按 `ReconnectPolicy` 安排三次重连。
6. `stop()` 设置主动停止标志、移除 Handler 回调、注销 AIDL callback、`unlinkToDeath`、解绑、清空 Binder 并发出 `DISCONNECTED`；主动停止后不得重连。
7. `retryNow()` 清零次数并立即重新绑定。
8. 覆盖 `onBindingDied` 并汇聚到同一断连函数；Android 只会在 API 26+ 回调该方法，代码不得在 API 24～25 主动调用它。

- [ ] **Step 5: 实现并使用可单测的断连 reducer**

```kotlin
package com.yangtianyu.frameworklab.vehicle

data class ReconnectDecision(
    val status: VehicleConnectionStatus,
    val delayMs: Long?,
    val nextAttempt: Int,
)

object VehicleReconnectReducer {
    fun onUnexpectedDisconnect(started: Boolean, attemptsMade: Int): ReconnectDecision {
        if (!started) return ReconnectDecision(VehicleConnectionStatus.DISCONNECTED, null, attemptsMade)
        val nextAttempt = attemptsMade + 1
        val delay = ReconnectPolicy.delayMs(nextAttempt)
        return if (delay == null) {
            ReconnectDecision(VehicleConnectionStatus.DISCONNECTED, null, attemptsMade)
        } else {
            ReconnectDecision(VehicleConnectionStatus.RETRY_WAITING, delay, nextAttempt)
        }
    }
}
```

创建 `VehicleReconnectReducerTest`，精确断言首次断连返回 `(RETRY_WAITING, 1000, 1)`、第三次返回 `(RETRY_WAITING, 1000, 3)`、第四次返回 `DISCONNECTED` 且无 delay、`started=false` 时不重连。`VehicleServiceClient.handleUnexpectedDisconnect()` 必须调用该 reducer，禁止复制判断条件。

- [ ] **Step 6: 验证并提交**

Run: `.\gradlew.bat :app:testDebugUnitTest test assembleDebug`

Expected: `BUILD SUCCESSFUL`，重连边界测试 PASS，App 不在主线程执行同步 AIDL 控制。

```bash
git add app/build.gradle.kts app/src/main/java/com/yangtianyu/frameworklab/vehicle app/src/test/java/com/yangtianyu/frameworklab/vehicle
git commit -m "feat: add reconnecting vehicle service client"
```

---

### Task 6: Dashboard UI 状态归约与 ViewModel

**Files:**
- Create: `app/src/main/java/com/yangtianyu/frameworklab/vehicle/VehicleDashboardUiState.kt`
- Create: `app/src/main/java/com/yangtianyu/frameworklab/vehicle/VehicleDashboardStateReducer.kt`
- Create: `app/src/main/java/com/yangtianyu/frameworklab/vehicle/VehicleDashboardViewModel.kt`
- Create: `app/src/main/java/com/yangtianyu/frameworklab/vehicle/ForwardingVehicleListener.kt`
- Test: `app/src/test/java/com/yangtianyu/frameworklab/vehicle/VehicleDashboardStateReducerTest.kt`

**Interfaces:**
- Consumes: `VehicleServiceClient.Listener`、`VehicleSnapshot`、`VehicleConnectionStatus`、`DrivingRestrictionPolicy`。
- Produces: `VehicleDashboardViewModel.uiState: LiveData<VehicleDashboardUiState>` 和所有页面意图方法。

- [ ] **Step 1: 写 UI 状态失败测试**

```kotlin
package com.yangtianyu.frameworklab.vehicle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleDashboardStateReducerTest {
    private val snapshot = VehicleSnapshot.stoppedDefault().copy(updatedAtElapsedRealtime = 1_000L)

    @Test fun connectedFreshParkedStateAllowsDoorUnlock() {
        val state = VehicleDashboardStateReducer.reduce(
            status = VehicleConnectionStatus.CONNECTED,
            snapshot = snapshot,
            nowMs = 2_000L,
            commandResult = null,
        )
        assertFalse(state.isDataStale)
        assertTrue(state.canUnlockAllDoors)
    }

    @Test fun disconnectedOrOldSnapshotIsStale() {
        assertTrue(VehicleDashboardStateReducer.reduce(
            VehicleConnectionStatus.DISCONNECTED, snapshot, 2_000L, null,
        ).isDataStale)
        assertTrue(VehicleDashboardStateReducer.reduce(
            VehicleConnectionStatus.CONNECTED, snapshot, 4_001L, null,
        ).isDataStale)
    }

    @Test fun movingStateDisablesDoorUnlock() {
        val moving = snapshot.copy(speedKph = 20, gear = VehicleGear.DRIVE)
        assertFalse(VehicleDashboardStateReducer.reduce(
            VehicleConnectionStatus.CONNECTED, moving, 2_000L, null,
        ).canUnlockAllDoors)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*.VehicleDashboardStateReducerTest"`

Expected: FAIL，提示 UI 状态或 reducer 未定义。

- [ ] **Step 3: 实现不可变 UI 状态与 reducer**

```kotlin
package com.yangtianyu.frameworklab.vehicle

data class VehicleDashboardUiState(
    val connectionStatus: VehicleConnectionStatus = VehicleConnectionStatus.DISCONNECTED,
    val snapshot: VehicleSnapshot? = null,
    val isDataStale: Boolean = true,
    val canUnlockAllDoors: Boolean = false,
    val canEditSimulation: Boolean = false,
    val commandResult: Int? = null,
)
```

```kotlin
package com.yangtianyu.frameworklab.vehicle

object VehicleDashboardStateReducer {
    private const val STALE_AFTER_MS = 3_000L

    fun reduce(
        status: VehicleConnectionStatus,
        snapshot: VehicleSnapshot?,
        nowMs: Long,
        commandResult: Int?,
    ): VehicleDashboardUiState {
        val stale = status != VehicleConnectionStatus.CONNECTED ||
            snapshot == null ||
            nowMs - snapshot.updatedAtElapsedRealtime > STALE_AFTER_MS
        return VehicleDashboardUiState(
            connectionStatus = status,
            snapshot = snapshot,
            isDataStale = stale,
            canUnlockAllDoors = !stale && snapshot?.let(DrivingRestrictionPolicy::canUnlockAllDoors) == true,
            canEditSimulation = !stale && snapshot?.let(DrivingRestrictionPolicy::canEditSimulation) == true,
            commandResult = commandResult,
        )
    }
}
```

- [ ] **Step 4: 实现 ViewModel 并集中转发客户端事件**

```kotlin
package com.yangtianyu.frameworklab.vehicle

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class VehicleDashboardViewModel(
    private val client: VehicleServiceClient,
    private val nowMs: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel(), VehicleServiceClient.Listener {
    private val mutableUiState = MutableLiveData(VehicleDashboardUiState())
    val uiState: LiveData<VehicleDashboardUiState> = mutableUiState
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val staleRefresh = object : Runnable {
        override fun run() {
            publish()
            refreshHandler.postDelayed(this, 1_000L)
        }
    }
    private var status = VehicleConnectionStatus.DISCONNECTED
    private var snapshot: VehicleSnapshot? = null
    private var commandResult: Int? = null

    fun start() {
        client.start()
        refreshHandler.removeCallbacks(staleRefresh)
        refreshHandler.post(staleRefresh)
    }
    fun stop() {
        refreshHandler.removeCallbacks(staleRefresh)
        client.stop()
    }
    fun retry() = client.retryNow()
    fun increaseTemperature() = snapshot?.let { client.setTemperature(it.temperatureCelsius + 1) }
    fun decreaseTemperature() = snapshot?.let { client.setTemperature(it.temperatureCelsius - 1) }
    fun toggleAc() = snapshot?.let { client.setAcEnabled(!it.isAcOn) }
    fun unlockAllDoors() = client.unlockAllDoors()
    fun simulateProcessDeath() = client.requestSimulatedProcessDeath()

    override fun onConnectionChanged(status: VehicleConnectionStatus) {
        this.status = status
        publish()
    }

    override fun onSnapshot(snapshot: VehicleSnapshot) {
        this.snapshot = snapshot
        publish()
    }

    override fun onCommandResult(result: Int) {
        commandResult = result
        publish()
    }

    private fun publish() {
        mutableUiState.value = VehicleDashboardStateReducer.reduce(status, snapshot, nowMs(), commandResult)
    }

    override fun onCleared() {
        refreshHandler.removeCallbacks(staleRefresh)
        client.stop()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val forwardingListener = ForwardingVehicleListener()
            val client = VehicleServiceClient(context.applicationContext, forwardingListener)
            val viewModel = VehicleDashboardViewModel(client)
            forwardingListener.delegate = viewModel
            @Suppress("UNCHECKED_CAST")
            return viewModel as T
        }
    }
}
```

`ForwardingVehicleListener.kt`：

```kotlin
package com.yangtianyu.frameworklab.vehicle

class ForwardingVehicleListener : VehicleServiceClient.Listener {
    var delegate: VehicleServiceClient.Listener? = null
    override fun onConnectionChanged(status: VehicleConnectionStatus) {
        delegate?.onConnectionChanged(status)
    }
    override fun onSnapshot(snapshot: VehicleSnapshot) {
        delegate?.onSnapshot(snapshot)
    }
    override fun onCommandResult(result: Int) {
        delegate?.onCommandResult(result)
    }
}
```

创建 `ForwardingVehicleListenerTest`：先在 `delegate == null` 时调用三个方法并确认不抛异常；再设置记录型 delegate，确认三种事件各转发一次。

- [ ] **Step 5: 验证并提交**

Run: `.\gradlew.bat :app:testDebugUnitTest test assembleDebug`

Expected: `BUILD SUCCESSFUL`，新鲜度、断连、驾驶限制和转发 Listener 测试 PASS。

```bash
git add app/src/main/java/com/yangtianyu/frameworklab/vehicle app/src/test/java/com/yangtianyu/frameworklab/vehicle
git commit -m "feat: add vehicle dashboard state model"
```

---

### Task 7: 首页导航与横屏 Dashboard 页面

**Files:**
- Modify: `app/src/main/java/com/yangtianyu/frameworklab/home/ExperimentCatalog.kt`
- Modify: `app/src/main/java/com/yangtianyu/frameworklab/home/ExperimentNavigationPolicy.kt`
- Modify: `app/src/main/java/com/yangtianyu/frameworklab/home/HomeFragment.kt`
- Modify: `app/src/test/java/com/yangtianyu/frameworklab/home/ExperimentCatalogTest.kt`
- Modify: `app/src/test/java/com/yangtianyu/frameworklab/home/ExperimentNavigationPolicyTest.kt`
- Modify: `app/src/main/res/navigation/main_nav_graph.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values/colors.xml`
- Create: `app/src/main/java/com/yangtianyu/frameworklab/vehicle/VehicleDashboardFragment.kt`
- Create: `app/src/main/res/layout/fragment_vehicle_dashboard.xml`
- Create: `app/src/main/res/layout-land/fragment_vehicle_dashboard.xml`

**Interfaces:**
- Consumes: `VehicleDashboardViewModel.uiState` 和页面意图方法。
- Produces: 首页车辆入口、Navigation destination、可运行横屏车辆状态页面。

- [ ] **Step 1: 先扩展首页失败测试**

`ExperimentCatalogTest` 期望：

```kotlin
assertEquals(
    listOf("mini-image-loader", "vehicle-status-center"),
    ExperimentCatalog.all().map(ExperimentItem::id),
)
```

将导航策略测试改为验证：图片入口映射 `IMAGE_LOADER`，车辆入口映射 `VEHICLE_STATUS`，离开首页和未知 ID 返回 `null`。

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*.ExperimentCatalogTest" --tests "*.ExperimentNavigationPolicyTest"`

Expected: FAIL，车辆入口和目的地类型尚不存在。

- [ ] **Step 3: 实现通用导航策略**

```kotlin
enum class ExperimentDestination { IMAGE_LOADER, VEHICLE_STATUS }

object ExperimentNavigationPolicy {
    fun destinationFor(item: ExperimentItem, isHomeCurrentDestination: Boolean): ExperimentDestination? {
        if (!isHomeCurrentDestination) return null
        return when (item.id) {
            ExperimentCatalog.MINI_IMAGE_LOADER_ID -> ExperimentDestination.IMAGE_LOADER
            ExperimentCatalog.VEHICLE_STATUS_CENTER_ID -> ExperimentDestination.VEHICLE_STATUS
            else -> null
        }
    }
}
```

在 Catalog 增加：

```kotlin
const val VEHICLE_STATUS_CENTER_ID = "vehicle-status-center"

ExperimentItem(
    id = VEHICLE_STATUS_CENTER_ID,
    title = "Vehicle Status Center",
    description = "AIDL vehicle service, live state, driving restrictions and reconnect handling.",
)
```

`HomeFragment.openExperiment` 使用 `destinationFor` 后分别导航到两个 action；`null` 时不导航。

- [ ] **Step 4: 添加 Navigation destination**

在首页 Fragment 下增加 action：

```xml
<action
    android:id="@+id/action_homeFragment_to_vehicleDashboardFragment"
    app:destination="@id/vehicleDashboardFragment" />
```

在图末尾增加：

```xml
<fragment
    android:id="@+id/vehicleDashboardFragment"
    android:name="com.yangtianyu.frameworklab.vehicle.VehicleDashboardFragment"
    android:label="@string/vehicle_dashboard_title" />
```

- [ ] **Step 5: 创建两个布局变体并保持相同 View ID**

两个布局都必须包含以下绑定 ID，不能在 Fragment 中使用 `findViewById`：

```xml
@+id/connection_status_text
@+id/stale_banner
@+id/speed_text
@+id/gear_text
@+id/battery_text
@+id/range_text
@+id/door_status_text
@+id/temperature_text
@+id/fan_text
@+id/ac_button
@+id/temperature_decrease_button
@+id/temperature_increase_button
@+id/unlock_doors_button
@+id/restriction_text
@+id/retry_button
@+id/simulate_disconnect_button
```

默认布局使用 `ScrollView + vertical LinearLayout` 保证窄屏不裁切；`layout-land` 使用根 `LinearLayout(horizontal)`，左侧权重 3 展示速度/挡位/电量/车门，右侧权重 2 展示空调、限制和 Debug 控件。根背景使用新增的 `vehicle_background = #0C1422`，主要卡片使用 `vehicle_card = #162238`，正常/警告分别使用 `vehicle_ok = #7EE2B8`、`vehicle_warning = #FFB36B`。所有用户文本放入 `strings.xml`。

- [ ] **Step 6: 实现 Fragment 生命周期、渲染和点击事件**

```kotlin
package com.yangtianyu.frameworklab.vehicle

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.yangtianyu.frameworklab.BuildConfig
import com.yangtianyu.frameworklab.R
import com.yangtianyu.frameworklab.databinding.FragmentVehicleDashboardBinding

class VehicleDashboardFragment : Fragment(R.layout.fragment_vehicle_dashboard) {
    private var _binding: FragmentVehicleDashboardBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: VehicleDashboardViewModel by viewModels {
        VehicleDashboardViewModel.Factory(requireContext().applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentVehicleDashboardBinding.bind(view)
        binding.temperatureDecreaseButton.setOnClickListener { viewModel.decreaseTemperature() }
        binding.temperatureIncreaseButton.setOnClickListener { viewModel.increaseTemperature() }
        binding.acButton.setOnClickListener { viewModel.toggleAc() }
        binding.unlockDoorsButton.setOnClickListener { viewModel.unlockAllDoors() }
        binding.retryButton.setOnClickListener { viewModel.retry() }
        binding.simulateDisconnectButton.setOnClickListener { viewModel.simulateProcessDeath() }
        binding.simulateDisconnectButton.visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
        viewModel.uiState.observe(viewLifecycleOwner, ::render)
    }

    override fun onStart() {
        super.onStart()
        viewModel.start()
    }

    override fun onStop() {
        viewModel.stop()
        super.onStop()
    }

    private fun render(state: VehicleDashboardUiState) {
        binding.connectionStatusText.text = state.connectionStatus.name
        binding.staleBanner.visibility = if (state.isDataStale) View.VISIBLE else View.GONE
        binding.retryButton.visibility = if (state.connectionStatus == VehicleConnectionStatus.DISCONNECTED) View.VISIBLE else View.GONE
        binding.unlockDoorsButton.isEnabled = state.canUnlockAllDoors
        binding.restrictionText.visibility = if (state.canUnlockAllDoors) View.GONE else View.VISIBLE
        state.snapshot?.let { snapshot ->
            binding.speedText.text = getString(R.string.vehicle_speed_value, snapshot.speedKph)
            binding.gearText.text = snapshot.gear.name
            binding.batteryText.text = getString(R.string.vehicle_battery_value, snapshot.batteryPercent)
            binding.rangeText.text = getString(R.string.vehicle_range_value, snapshot.rangeKm)
            binding.temperatureText.text = getString(R.string.vehicle_temperature_value, snapshot.temperatureCelsius)
            binding.fanText.text = getString(R.string.vehicle_fan_value, snapshot.fanSpeed)
            binding.acButton.text = if (snapshot.isAcOn) getString(R.string.vehicle_ac_on) else getString(R.string.vehicle_ac_off)
            binding.doorStatusText.text = if (snapshot.isRearRightDoorOpen) getString(R.string.vehicle_rear_right_door_open) else getString(R.string.vehicle_all_doors_closed)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
```

在 `strings.xml` 明确定义代码引用的 11 个资源：`vehicle_dashboard_title`、`vehicle_speed_value`、`vehicle_battery_value`、`vehicle_range_value`、`vehicle_temperature_value`、`vehicle_fan_value`、`vehicle_ac_on`、`vehicle_ac_off`、`vehicle_rear_right_door_open`、`vehicle_all_doors_closed`、`vehicle_data_stale`。格式分别使用 `%1$d km/h`、`%1$d%%`、`%1$d km`、`%1$d°` 和 `Fan %1$d`。

- [ ] **Step 7: 验证并提交**

Run: `.\gradlew.bat :app:testDebugUnitTest test assembleDebug`

Expected: `BUILD SUCCESSFUL`，首页目录和两个导航目标测试 PASS，两个布局均生成同一个 ViewBinding 类型。

```bash
git add app
git commit -m "feat: add vehicle status dashboard"
```

---

### Task 8: 文档、真实边界、最终验证与演示材料

**Files:**
- Modify: `README.md`
- Create: `docs/interview/vehicle-status-center-notes.md`
- Create: `docs/demo/vehicle-status-center-checklist.md`

**Interfaces:**
- Consumes: 完整车辆状态中心实现。
- Produces: Android Studio 运行步骤、演示脚本、面试讲解提纲和限制说明。

- [ ] **Step 1: 更新 README**

README 必须新增：

1. 三模块目录树和 `mini-vehicle-service` 职责。
2. AIDL 数据流：`FakeVehicleDataSource → VehicleDataService(:vehicle) → VehicleServiceClient → ViewModel → XML View`。
3. 横屏模拟器运行步骤和 Debug 断连按钮使用方式。
4. 驾驶限制、服务端二次校验、三次重连规则。
5. 明确声明“本模块没有连接真实 CAN、Vehicle HAL、CarService，也不代表量产 AAOS 经验”。
6. `assembleDebug`、`test`、`connectedDebugAndroidTest` 命令。

- [ ] **Step 2: 写五分钟面试讲解提纲**

`docs/interview/vehicle-status-center-notes.md` 固定包含：项目背景、为什么使用独立进程、AIDL 接口、线程模型、Binder 死亡处理、客户端与服务端双重安全校验、测试策略、真实限制八节。每节列出“结论、代码位置、可追问问题”，不得把模拟实现写成量产经历。

- [ ] **Step 3: 写手工演示清单**

`docs/demo/vehicle-status-center-checklist.md` 使用复选框列出：

- 首页进入车辆状态中心。
- 连接中变为已连接并收到首帧。
- 速度周期变化，行驶时解锁按钮禁用。
- 停车后解锁成功，门锁状态改变。
- 空调温度边界 16～30，越界返回参数错误。
- Debug 模拟远端进程死亡，页面显示旧数据过期并自动重连。
- 连续失败三次后出现手动重试。
- 旋转到 1280×720 横屏，无裁切和重叠。

- [ ] **Step 4: 执行完整自动验证**

Run: `.\gradlew.bat test assembleDebug --rerun-tasks --console=plain`

Expected: `BUILD SUCCESSFUL`，所有模块单元测试 0 failures，Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

Run（模拟器在线时）: `.\gradlew.bat connectedDebugAndroidTest --rerun-tasks --console=plain`

Expected: `BUILD SUCCESSFUL`，绑定 Service 和首帧仪器测试 PASS。若环境没有模拟器，必须明确报告未执行，不能写“全部测试通过”。

- [ ] **Step 5: 按清单完成横屏手工验证**

逐项记录设备 API、分辨率、结果和异常。重点使用 `adb shell ps -A | findstr frameworklab` 证明 App 与 `:vehicle` 是两个进程，并在断连演示时记录连接状态变化。

- [ ] **Step 6: 检查范围和提交**

Run: `git diff --check`

Expected: 无输出，退出码 0。

Run: `git status --short`

Expected: 只包含本任务的 README 和两个文档文件。

```bash
git add README.md docs/interview/vehicle-status-center-notes.md docs/demo/vehicle-status-center-checklist.md
git commit -m "docs: explain vehicle status center"
```

## Final Acceptance Checklist

- [ ] 只新增 `mini-vehicle-service` 一个 Gradle 模块。
- [ ] `app` 和 `:vehicle` 进程通过 AIDL 通信。
- [ ] 车辆快照包含车速、挡位、电量、续航、四门开关、门锁和空调状态。
- [ ] 所有同步 Binder 控制调用离开主线程。
- [ ] UI 和 Service 都执行驾驶限制，Service 返回明确结果码。
- [ ] API 24～25 与 API 26+ 断连路径均有明确实现。
- [ ] 自动重连固定 1 秒，最多三次，主动停止不重连。
- [ ] 数据超过 3 秒或非 CONNECTED 时显示过期。
- [ ] Debug 模拟接口在非 debuggable 构建返回 `DEBUG_ONLY`。
- [ ] `test assembleDebug` 新鲜运行成功。
- [ ] 有模拟器时 `connectedDebugAndroidTest` 成功；否则如实记录未执行。
- [ ] README 和面试材料不夸大为真实 CAN、VHAL 或量产经验。
