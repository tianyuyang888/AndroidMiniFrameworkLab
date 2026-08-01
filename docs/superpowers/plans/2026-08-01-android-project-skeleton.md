# AndroidMiniFrameworkLab Project Skeleton Implementation Plan

> Historical plan: this file records the completed skeleton phase and is not the current feature boundary or completion checklist.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Build a directly openable, XML-based Kotlin Android project with app and mini-image-loader modules and a working single-activity experiment browser.

**Architecture:** MainActivity hosts a Navigation Component fragment container. HomeFragment renders a static experiment catalog in a RecyclerView and navigates to an ImageLoaderLabFragment placeholder; the library exposes only an identity API.

**Tech Stack:** Android Gradle Plugin 9.1.1 with built-in Kotlin, Gradle 9.3.1, Kotlin/JVM, AndroidX XML Views, ViewBinding, RecyclerView, Navigation Component, JUnit 4.

## Global Constraints

- Project name: AndroidMiniFrameworkLab.
- Application namespace and ID: com.yangtianyu.frameworklab.
- Kotlin source code and Gradle Kotlin DSL.
- XML Views only; no Jetpack Compose.
- Minimum Android SDK: 24.
- compileSdk and targetSdk: 34.
- Single-activity architecture.
- Exactly two modules initially: app and mini-image-loader.
- Do not implement networking, caching, decoding, threading, or image rendering.
- Final acceptance command: .\gradlew.bat assembleDebug.

---

### Task 1: Gradle and Android module scaffolding

**Files:**
- Create: settings.gradle.kts
- Create: build.gradle.kts
- Create: gradle.properties
- Create: .gitignore
- Create: app/build.gradle.kts
- Create: app/proguard-rules.pro
- Create: app/src/main/AndroidManifest.xml
- Create: mini-image-loader/build.gradle.kts
- Create: mini-image-loader/consumer-rules.pro
- Create: mini-image-loader/src/main/AndroidManifest.xml
- Generate: gradlew, gradlew.bat, gradle/wrapper/gradle-wrapper.jar, gradle/wrapper/gradle-wrapper.properties

**Interfaces:**
- Produces an Android application module depending on project(:mini-image-loader).
- Produces an Android library namespace com.yangtianyu.frameworklab.imageloader.

- [ ] **Step 1: Create root build configuration**

    pluginManagement {
        repositories {
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }
    dependencyResolutionManagement {
        repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
        repositories {
            google()
            mavenCentral()
        }
    }
    rootProject.name = "AndroidMiniFrameworkLab"
    include(":app", ":mini-image-loader")

    plugins {
        id("com.android.application") version "9.1.1" apply false
        id("com.android.library") version "9.1.1" apply false
    }

- [ ] **Step 2: Configure both Android modules**

Use namespace/application ID com.yangtianyu.frameworklab for app, namespace com.yangtianyu.frameworklab.imageloader for the library, compileSdk/targetSdk 34, minSdk 24, ViewBinding in app, Java 17 bytecode, and project(":mini-image-loader") as an app dependency.

- [ ] **Step 3: Generate the Gradle 9.3.1 wrapper**

Run the cached Gradle 9.3.1 executable with the wrapper task and --gradle-version 9.3.1.
Expected: wrapper scripts, JAR, and properties are generated.

- [ ] **Step 4: Verify project discovery**

Run: .\gradlew.bat projects
Expected: SUCCESS and both :app and :mini-image-loader appear.

### Task 2: Library identity API with test-first development

**Files:**
- Create: mini-image-loader/src/test/java/com/yangtianyu/frameworklab/imageloader/MiniImageLoaderTest.kt
- Create: mini-image-loader/src/main/java/com/yangtianyu/frameworklab/imageloader/MiniImageLoader.kt

**Interfaces:**
- Produces: object MiniImageLoader with const val NAME: String equal to "Mini Image Loader".

- [ ] **Step 1: Write the failing identity test**

    class MiniImageLoaderTest {
        @Test
        fun exposesModuleName() {
            assertEquals("Mini Image Loader", MiniImageLoader.NAME)
        }
    }

- [ ] **Step 2: Verify the test fails for the missing API**

Run: .\gradlew.bat :mini-image-loader:testDebugUnitTest
Expected: FAIL because MiniImageLoader is unresolved.

- [ ] **Step 3: Add the minimal production API**

    object MiniImageLoader {
        const val NAME = "Mini Image Loader"
    }

- [ ] **Step 4: Verify the library test passes**

Run: .\gradlew.bat :mini-image-loader:testDebugUnitTest
Expected: SUCCESS with one passing test.

### Task 3: Experiment catalog with test-first development

**Files:**
- Create: app/src/test/java/com/yangtianyu/frameworklab/home/ExperimentCatalogTest.kt
- Create: app/src/main/java/com/yangtianyu/frameworklab/home/ExperimentItem.kt
- Create: app/src/main/java/com/yangtianyu/frameworklab/home/ExperimentCatalog.kt

**Interfaces:**
- Produces: data class ExperimentItem(val id: String, val title: String, val description: String).
- Produces: object ExperimentCatalog with fun all(): List<ExperimentItem>.

- [ ] **Step 1: Write the failing catalog test**

    class ExperimentCatalogTest {
        @Test
        fun containsMiniImageLoaderEntry() {
            assertEquals(
                listOf("mini-image-loader"),
                ExperimentCatalog.all().map(ExperimentItem::id),
            )
        }
    }

- [ ] **Step 2: Verify the test fails for missing catalog types**

Run: .\gradlew.bat :app:testDebugUnitTest
Expected: FAIL because ExperimentCatalog and ExperimentItem are unresolved.

- [ ] **Step 3: Add the immutable item and static catalog**

Implement ExperimentItem with the three specified String properties and return one entry with ID "mini-image-loader", title "Mini Image Loader", and a description that identifies the page as a project skeleton.

- [ ] **Step 4: Verify the app unit test passes**

Run: .\gradlew.bat :app:testDebugUnitTest
Expected: SUCCESS with one passing test.

### Task 4: Single-activity XML UI and documentation

**Files:**
- Create: app/src/main/java/com/yangtianyu/frameworklab/MainActivity.kt
- Create: app/src/main/java/com/yangtianyu/frameworklab/home/HomeFragment.kt
- Create: app/src/main/java/com/yangtianyu/frameworklab/home/ExperimentAdapter.kt
- Create: app/src/main/java/com/yangtianyu/frameworklab/imageloader/ImageLoaderLabFragment.kt
- Create: app/src/main/res/layout/activity_main.xml
- Create: app/src/main/res/layout/fragment_home.xml
- Create: app/src/main/res/layout/item_experiment.xml
- Create: app/src/main/res/layout/fragment_image_loader_lab.xml
- Create: app/src/main/res/navigation/main_nav_graph.xml
- Create: app/src/main/res/values/strings.xml
- Create: app/src/main/res/values/colors.xml
- Create: app/src/main/res/values/themes.xml
- Create: app/src/main/res/values-night/themes.xml
- Create: README.md

**Interfaces:**
- MainActivity is the only launcher activity.
- HomeFragment calls ExperimentCatalog.all(), renders it with ExperimentAdapter, and navigates through action_homeFragment_to_imageLoaderLabFragment.
- ImageLoaderLabFragment displays MiniImageLoader.NAME and a clear under-construction message.

- [ ] **Step 1: Create XML resources and navigation graph**

Use a FragmentContainerView in activity_main, a RecyclerView in fragment_home, MaterialCardView rows in item_experiment, a centered title/status layout in fragment_image_loader_lab, and a two-destination navigation graph with HomeFragment as start destination.

- [ ] **Step 2: Implement activity, fragments, and adapter**

Use generated ViewBinding classes, clear fragment bindings in onDestroyView, use RecyclerView ListAdapter with DiffUtil, and handle the only catalog ID by navigating to the placeholder destination.

- [ ] **Step 3: Document setup and boundaries**

README must state Android Studio import steps, JDK 17 or newer, Android SDK 34, the two-module tree, .\gradlew.bat assembleDebug, APK path, and the deferred image-loader features.

- [ ] **Step 4: Run all local unit tests**

Run: .\gradlew.bat testDebugUnitTest
Expected: SUCCESS with two passing tests.

- [ ] **Step 5: Run final debug build**

Run: .\gradlew.bat assembleDebug
Expected: BUILD SUCCESSFUL and app/build/outputs/apk/debug/app-debug.apk exists.

- [ ] **Step 6: Inspect the final module tree and APK**

Run file listing and APK existence checks.
Expected: only app and mini-image-loader are included as Gradle modules, README is present, and the debug APK has nonzero size.
