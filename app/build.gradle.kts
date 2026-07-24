plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

val isWindows: Boolean = System.getProperty("os.name").lowercase().contains("windows")

val releaseKeystorePath = providers.environmentVariable("ANDROID_RELEASE_KEYSTORE_PATH").orNull
val releaseStorePassword = providers.environmentVariable("ANDROID_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("ANDROID_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("ANDROID_RELEASE_KEY_PASSWORD").orNull
val releaseSigningValues =
    listOf(releaseKeystorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
val hasReleaseSigning = releaseSigningValues.all { !it.isNullOrBlank() }
val hasPartialReleaseSigning = releaseSigningValues.any { !it.isNullOrBlank() } && !hasReleaseSigning
val requireReleaseSigning = providers.environmentVariable("REQUIRE_RELEASE_SIGNING").orNull == "true"

if (hasPartialReleaseSigning || (requireReleaseSigning && !hasReleaseSigning)) {
    throw GradleException("发布签名配置不完整，必须同时提供 keystore 路径、store 密码、alias 和 key 密码")
}

android {
    namespace = "cc.pscly.onememos"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        applicationId = "cc.pscly.onememos"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()

        versionCode = 170
        versionName = "1.16.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "FLOW_BACKEND_BASE_URL", "\"https://xl.pscly.cc/\"")

        // 仅保留 arm64-v8a ABI，减小包体积
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file(".gradle-keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("benchmarkRelease") {
            if (hasReleaseSigning) {
                storeFile = rootProject.file(requireNotNull(releaseKeystorePath))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            } else {
                storeFile = rootProject.file(".gradle-keystore/debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        // debug 专用包名后缀：cc.pscly.onememos.dev，应用名"心流·内测"
        getByName("debug") {
            applicationIdSuffix = ".dev"
            resValue("string", "app_name", "心流·内测")
        }

        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }

        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("benchmarkRelease")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    installation {
        timeOutInMs = 10 * 60 * 1000
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:sync"))
    implementation(project(":core:navigation"))
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(project(":core:designsystem"))
    implementation(project(":core:settings"))
    implementation(project(":core:update"))
    implementation(project(":core:calendar"))
    implementation(project(":core:quicktiles"))
    implementation(project(":core:externalactions"))
    implementation(project(":core:diagnostics"))

    implementation(project(":feature:home"))
    implementation(project(":feature:collections"))
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
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.material)

    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

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
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui)
    androidTestImplementation(libs.androidx.compose.ui.graphics)
    androidTestImplementation(libs.androidx.compose.material3)
    androidTestImplementation(libs.androidx.activity.compose)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.withType<Test>().configureEach {
    maxParallelForks = 1
    maxHeapSize = "384m"
    jvmArgs("-XX:ReservedCodeCacheSize=64m")

    val testHome = rootProject.layout.buildDirectory.dir("test-home").get().asFile
    testHome.mkdirs()
    systemProperty("user.home", testHome.absolutePath)
    systemProperty("maven.repo.local", testHome.resolve(".m2/repository").absolutePath)
    systemProperty("oneMemos.projectDir", rootProject.projectDir.absolutePath)
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
                .resolve(if (isWindows) "keytool.exe" else "keytool")

        val process = ProcessBuilder(
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
        ).inheritIO().start()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException("keytool failed with exit code $exitCode")
        }
    }
}

tasks.matching {
    it.name.startsWith("validateSigningDebug") ||
        (!hasReleaseSigning && it.name.startsWith("validateSigning"))
}.configureEach {
    dependsOn(ensureDebugKeystore)
}
