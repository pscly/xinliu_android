plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "cc.pscly.onememos.core.settings"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    implementation(project(":core:sync"))
    implementation(project(":core:update"))
    implementation(project(":core:calendar"))
    implementation(project(":core:quicktiles"))
    implementation(project(":core:externalactions"))
    implementation(project(":core:diagnostics"))
}
