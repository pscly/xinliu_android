pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    // 新增 core/feature 多模块骨架时使用：避免每个模块都重复声明插件版本。
    // 版本需与 gradle/libs.versions.toml 保持一致。
    plugins {
        id("com.android.library") version "8.4.2"
        id("org.jetbrains.kotlin.android") version "1.9.24"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "1memos"
include(":app")
include(":macrobenchmark")
include(":baselineprofile")

// core
include(":core:model")
include(":core:domain")
include(":core:database")
include(":core:network")
include(":core:data")
include(":core:sync")
include(":core:designsystem")
include(":core:navigation")
include(":core:performance")

// feature
include(":feature:home")
include(":feature:editor")
include(":feature:settings")
include(":feature:sharecard")
include(":feature:quickcapture")
include(":feature:profile")
include(":feature:auth")
include(":feature:welcome")
include(":feature:start")
include(":feature:todo")
