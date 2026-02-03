plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "cc.pscly.onememos.baselineprofile"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = 28
        targetSdk = libs.versions.targetSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // 与 app 的 benchmark buildType 对齐：Baseline Profile 生成会跑在该 buildType 上。
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }

        // app 侧生成任务以 release 维度执行（merge/copy）。
        // 这里补一个同名 buildType 供变体匹配；同时允许回退到 app 的 benchmark，避免依赖 release 签名。
        create("release") {
            initWith(getByName("benchmark"))
            matchingFallbacks += listOf("benchmark")
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

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.rules)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

// 只启用 benchmark/release 变体，避免无意义构建。
androidComponents {
    beforeVariants(selector().all()) { variant ->
        variant.enable = variant.buildType == "benchmark" || variant.buildType == "release"
    }
}

// app 侧 baseline profile 任务解析的是 Usage=baselineProfile 的变体；
// 但 com.android.test 暴露的是 *TestedApks (java-runtime)。这里额外发布一个只用于被 app 消费的配置，
// 避免直接改 TestedApks 的属性导致其解析 targetProject(:app) 变体时出现循环/不匹配。
val baselineProfileAgpVersionAttr = Attribute.of(
    "androidx.baselineprofile.gradle.configuration.attribute.BaselineProfilePluginAgpVersionAttr",
    String::class.java,
)
val baselineProfilePluginVersionAttr = Attribute.of(
    "androidx.baselineprofile.gradle.configuration.attribute.BaselineProfilePluginVersionAttr",
    String::class.java,
)

val releaseBaselineProfileElements by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false

    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, "baselineProfile"))
        attribute(
            TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
            objects.named(TargetJvmEnvironment::class.java, "android"),
        )

        attribute(baselineProfileAgpVersionAttr, libs.versions.agp.get())
        attribute(baselineProfilePluginVersionAttr, "alpha1")
        attribute(
            com.android.build.api.attributes.BuildTypeAttr.ATTRIBUTE,
            objects.named(com.android.build.api.attributes.BuildTypeAttr::class.java, "release"),
        )
    }
}

afterEvaluate {
    // 复用 com.android.test 生成的 TestedApks zip（不引入额外依赖关系）
    val zipTask = tasks.getByName("zipApksForRelease")
    artifacts.add(
        releaseBaselineProfileElements.name,
        mapOf(
            "file" to zipTask.outputs.files.singleFile,
            "builtBy" to zipTask,
        ),
    )
}
