plugins {
    id("com.android.library")
}

android {
    namespace = "com.yangtianyu.frameworklab.imageloader"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api("androidx.annotation:annotation:1.6.0")

    testImplementation("junit:junit:4.13.2")
}
