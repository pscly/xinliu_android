plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.baselineprofile)
    kotlin("kapt")
}

import org.gradle.internal.os.OperatingSystem

android {
    namespace = "cc.pscly.onememos"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "cc.pscly.onememos"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()

        versionCode = 149
        versionName = "1.8.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Flow Backend：账号登录/注册用（App 拿到 token + server_url 后直接连接 Memos，不经 Backend 代理数据）。
        buildConfigField("String", "FLOW_BACKEND_BASE_URL", "\"https://xl.pscly.cc/\"")
    }

    // 统一 debug/benchmark 签名来源：不依赖 ~/.android/debug.keystore，便于在容器/CI/只读 HOME 环境下构建。
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file(".gradle-keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }

        // 用于 Macrobenchmark / Baseline Profile：继承 release，但用 debug 签名，避免本机签名依赖。
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    // 无线 adb（5555）安装较大的 benchmark APK 时，ddmlib push 可能超时；这里适当拉长超时时间。
    installation {
        timeOutInMs = 10 * 60 * 1000
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kapt {
    correctErrorTypes = true
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:sync"))
    implementation(project(":core:navigation"))
    implementation(project(":core:designsystem"))

    implementation(project(":feature:home"))
    implementation(project(":feature:editor"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:welcome"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:sharecard"))
    implementation(project(":feature:quickcapture"))
    implementation(project(":feature:todo"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.material)

    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    kapt(libs.androidx.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.androidx.paging.compose)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    implementation(libs.coil.compose)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.autolink)
    implementation(libs.commonmark.ext.gfm.strikethrough)
    implementation(libs.commonmark.ext.gfm.tables)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))

    // Baseline Profile 生成模块（运行 instrumentation 生成 profile；最终文件会被复制到 app 并随 Release 打包）。
    baselineProfile(project(":baselineprofile"))
}

// 单元测试在 Windows 机器上容易因并发与 JVM 默认内存导致 OOM/创建进程失败；
// 这里收紧并发与堆上限，保证 CI/本机更稳定。
tasks.withType<Test>().configureEach {
    maxParallelForks = 1
    maxHeapSize = "384m"
    jvmArgs("-XX:ReservedCodeCacheSize=64m")

    // Robolectric 会用 MavenDependencyResolver 拉取 android-all 等依赖，
    // 默认落在 ~/.m2；为避免容器/CI/只读 HOME 触发 FileNotFound/AccessDenied，
    // 单测 JVM 统一使用项目 build 目录作为 home + m2。
    val testHome = rootProject.layout.buildDirectory.dir("test-home").get().asFile
    testHome.mkdirs()
    systemProperty("user.home", testHome.absolutePath)
    systemProperty("maven.repo.local", testHome.resolve(".m2/repository").absolutePath)
}

val debugKeystoreFile = rootProject.file(".gradle-keystore/debug.keystore")
val ensureDebugKeystore by tasks.registering {
    outputs.file(debugKeystoreFile)
    doLast {
        if (debugKeystoreFile.exists()) return@doLast

        debugKeystoreFile.parentFile?.mkdirs()
        val keytool =
            File(System.getProperty("java.home"))
                .resolve("bin")
                .resolve(if (OperatingSystem.current().isWindows) "keytool.exe" else "keytool")

        exec {
            commandLine(
                keytool.absolutePath,
                "-genkeypair",
                "-keystore",
                debugKeystoreFile.absolutePath,
                "-storepass",
                "android",
                "-alias",
                "androiddebugkey",
                "-keypass",
                "android",
                "-keyalg",
                "RSA",
                "-keysize",
                "2048",
                "-validity",
                "10000",
                "-dname",
                "CN=Android Debug,O=Android,C=US",
                "-storetype",
                "JKS",
            )
        }
    }
}

tasks.matching { it.name.startsWith("validateSigning") }.configureEach {
    dependsOn(ensureDebugKeystore)
}
