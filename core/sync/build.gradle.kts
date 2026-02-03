plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.hilt)
    kotlin("kapt")
}

android {
    namespace = "cc.pscly.onememos.core.sync"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

kapt {
    correctErrorTypes = true
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    kapt(libs.androidx.hilt.compiler)

    // 本模块使用 kotlinx.coroutines.*；仓库目前未单独声明 coroutines 别名，沿用 lifecycle-runtime-ktx 引入。
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // MemosSyncWorker 直接引用 retrofit2.HttpException（core:network 未通过 api 暴露 retrofit）。
    implementation(libs.retrofit)

    // MemosApi 方法签名使用 com.google.gson.JsonObject（core:network 未通过 api 暴露 gson）。
    implementation(libs.retrofit.converter.gson)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.room.runtime)
    testImplementation(libs.retrofit.converter.gson)
}
