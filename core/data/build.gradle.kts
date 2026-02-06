plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "cc.pscly.onememos.core.data"
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
    implementation(project(":core:domain"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)

    // 仅用于编译期：@ApplicationContext / @Inject / @Singleton 等注解类型。
    implementation(libs.hilt.android)

    // 本模块使用 kotlinx.coroutines.*；仓库目前未单独声明 coroutines 别名，沿用 lifecycle-runtime-ktx 引入。
    implementation(libs.androidx.lifecycle.runtime.ktx)
}
