# AndroidMiniFrameworkLab Project Skeleton Design

## Goal

Create a minimal Android project that opens directly in Android Studio, compiles with `assembleDebug`, and provides a clean foundation for framework experiments without implementing a real image loader yet.

## Project Constraints

- Project name: `AndroidMiniFrameworkLab`
- Application namespace and ID: `com.yangtianyu.frameworklab`
- Kotlin source code and Gradle Kotlin DSL
- XML Views only; no Jetpack Compose
- Minimum Android SDK: 24
- Single-activity application architecture
- Exactly two modules initially: `app` and `mini-image-loader`
- `compileSdk` and `targetSdk`: 34, matching the locally installed stable Android platform

## Architecture

`MainActivity` hosts a `FragmentContainerView` backed by the Navigation Component. `HomeFragment` is the start destination and owns a `RecyclerView` that lists available experiments. Selecting the Mini Image Loader row navigates within the same activity to `ImageLoaderLabFragment`, which is an explicit placeholder screen.

The `mini-image-loader` Android Library module exposes a minimal `MiniImageLoader` object so the app can prove module wiring at compile time. It performs no network access, caching, decoding, threading, or image rendering.

## Components and Data Flow

- `MainActivity`: the only activity and navigation host.
- `HomeFragment`: constructs the current static experiment list and submits it to the adapter.
- `ExperimentAdapter`: renders experiment title and description and forwards click events.
- `ExperimentItem`: immutable UI model for one experiment entry.
- `ImageLoaderLabFragment`: shows the selected module name and a clear “under construction” message.
- `MiniImageLoader`: minimal library identity API consumed by the placeholder fragment.

The data flow is deliberately one-way: `HomeFragment` creates a list, the adapter renders it, and a click invokes navigation. The destination reads the library identity constant and displays it. There is no persistent or remote data.

## Error Handling

No runtime I/O exists in this skeleton. Navigation uses a generated action declared in the navigation graph. Build-time resource and dependency errors are handled by running the Gradle wrapper and correcting the project until `assembleDebug` succeeds.

## Testing and Acceptance

- A local JVM test verifies the library identity API.
- A local JVM test verifies the app experiment catalog contains the Mini Image Loader entry.
- `gradlew.bat testDebugUnitTest` should pass.
- `gradlew.bat assembleDebug` must pass and produce `app/build/outputs/apk/debug/app-debug.apk`.
- The README documents module structure, prerequisites, Android Studio usage, command-line build instructions, and the intentionally deferred image-loader features.
