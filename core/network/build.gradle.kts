plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "cc.pscly.onememos.core.network"
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

dependencies {
    // AppSettingsState 公开了 domain 层类型，因此用 api 暴露给消费者。
    api(project(":core:domain"))

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // 仅用于编译期：@ApplicationContext / @Inject / @Singleton 等注解类型。
    implementation(libs.hilt.android)

    // 本模块内部使用 CoroutineScope/Dispatchers(IO)。仓库目前未单独声明 coroutines 别名，
    // 这里通过 lifecycle-runtime-ktx 引入 coroutines-android 以保证编译通过。
    implementation(libs.androidx.lifecycle.runtime.ktx)
}
