plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "cc.pscly.onememos.baselineprofile"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        minSdk = 28
        targetSdk = libs.versions.targetSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }

        create("release") {
            initWith(getByName("benchmark"))
            matchingFallbacks += listOf("benchmark")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

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

androidComponents {
    beforeVariants(selector().all()) { variant ->
        variant.enable = variant.buildType == "benchmark" || variant.buildType == "release"
    }
}

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
    val zipTask = tasks.getByName("zipApksForRelease")
    artifacts.add(
        releaseBaselineProfileElements.name,
        mapOf(
            "file" to zipTask.outputs.files.singleFile,
            "builtBy" to zipTask,
        ),
    )
}
