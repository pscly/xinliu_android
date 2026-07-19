plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    // M1.9：提供 recordRoborazziDebug / verifyRoborazziDebug
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "cc.pscly.onememos.core.designsystem"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // Roborazzi + Compose 截图需要系统属性；默认不自动 record，避免 CI 漂移
            all {
                it.systemProperties["robolectric.pixelCopyRenderMode"] = "hardware"
            }
        }
    }
}

// 截图产物：src/test/screenshots（相对模块根）；录制用 :core:designsystem:recordRoborazziDebug
roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.ui.tooling.preview)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)

    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.coil.compose)
    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.autolink)
    implementation(libs.commonmark.ext.gfm.strikethrough)
    implementation(libs.commonmark.ext.gfm.tables)

    // 编辑页全量阅读：mikepenz Markdown 渲染器（列表预览/纯文本仍依赖上方 commonmark）
    implementation(libs.multiplatform.markdown.renderer.m3.android)
    implementation(libs.multiplatform.markdown.renderer.coil2.android)

    implementation(libs.hilt.android)

    implementation(project(":core:model"))
    implementation(project(":core:domain"))

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    // M1.9 Roborazzi（版本仅来自 catalog）
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
