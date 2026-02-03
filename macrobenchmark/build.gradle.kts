plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "cc.pscly.onememos.macrobenchmark"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        // Macrobenchmark 需要跑在真实设备/模拟器上；minSdk 取 28 便于覆盖更多机型（Android 9+）。
        minSdk = 28
        targetSdk = libs.versions.targetSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // 与 app 的 benchmark buildType 对齐：Macrobenchmark 会对该 buildType 的 app 做跑分。
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // WiFi ADB 安装较大的 benchmark APK 时，UTP/ddmlib 可能因为超时而失败；这里适当拉长超时时间。
    installation {
        timeOutInMs = 10 * 60 * 1000
    }

    // 目标 app 模块
    targetProjectPath = ":app"
    // 让该 test module 自身也可被安装/执行（AGP 推荐配置）
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.rules)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

// 只启用 benchmark 变体，避免每次构建都把 test module 的 debug/release 都打出来。
androidComponents {
    beforeVariants(selector().all()) { variant ->
        variant.enable = variant.buildType == "benchmark"
    }
}
