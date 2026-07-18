plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "cc.pscly.onememos.core.data"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))

    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)

    implementation(libs.hilt.android)

    implementation(libs.androidx.lifecycle.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}
