pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
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
include(":feature:collections")
include(":feature:editor")
include(":feature:settings")
include(":feature:sharecard")
include(":feature:quickcapture")
include(":feature:profile")
include(":feature:auth")
include(":feature:welcome")
include(":feature:start")
include(":feature:todo")
