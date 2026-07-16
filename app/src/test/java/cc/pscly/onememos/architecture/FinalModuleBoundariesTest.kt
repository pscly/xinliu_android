package cc.pscly.onememos.architecture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * 最终模块边界契约（Navigation 3 + Settings 重构完成后的硬门禁）。
 *
 * 读取 settings.gradle.kts 与各模块 build 文件，锁定：
 * 六个新 Core 模块、Feature 互不依赖、Core 不反向依赖 app/Feature、
 * :feature:settings 依赖白名单、归档归属 Home、ViewModel 无 Navigator、
 * app 只聚合 contributor、七个 Settings 能力唯一绑定、benchmark 仍指向 :app。
 */
class FinalModuleBoundariesTest {

    companion object {
        private lateinit var projectDir: File

        private val sixCoreModules =
            listOf(
                "core:settings",
                "core:update",
                "core:calendar",
                "core:quicktiles",
                "core:externalactions",
                "core:diagnostics",
            )

        private val settingsCapabilityBinds =
            listOf(
                "bindSettingsHubCapability",
                "bindAccountSyncSettingsCapability",
                "bindRecordEditingSettingsCapability",
                "bindReminderCalendarSettingsCapability",
                "bindStorageOfflineSettingsCapability",
                "bindAppearanceInteractionSettingsCapability",
                "bindAboutAdvancedSettingsCapability",
            )

        private val settingsAllowedProjects =
            setOf(
                ":core:domain",
                ":core:navigation",
                ":core:designsystem",
            )

        @BeforeClass
        @JvmStatic
        fun resolveProjectDir() {
            val path = System.getProperty("oneMemos.projectDir")
            require(!path.isNullOrBlank()) {
                "系统属性 oneMemos.projectDir 未设置；请在 app/build.gradle.kts 测试任务中配置"
            }
            projectDir = File(path)
            assertTrue("项目目录不存在: $projectDir", projectDir.isDirectory)
        }
    }

    @Test
    fun six_new_core_modules_are_registered() {
        val settings = projectDir.resolve("settings.gradle.kts").readText()
        sixCoreModules.forEach { module ->
            assertTrue(
                "settings.gradle.kts 必须 include(\":$module\")",
                settings.contains("""include(":$module")"""),
            )
            val buildFile =
                projectDir.resolve(module.replace(':', '/') + "/build.gradle.kts")
            assertTrue("$module 的 build.gradle.kts 必须存在", buildFile.isFile)
        }
    }

    @Test
    fun feature_modules_do_not_depend_on_each_other() {
        val featureRoots =
            projectDir.resolve("feature").listFiles()?.filter { it.isDirectory }.orEmpty()
        featureRoots.forEach { featureDir ->
            val build = featureDir.resolve("build.gradle.kts")
            if (!build.isFile) return@forEach
            val body = build.readText()
            val projectDeps = projectDependencies(body)
            val featureDeps = projectDeps.filter { it.startsWith(":feature:") }
            assertTrue(
                "${featureDir.name} 不得依赖其他 Feature，发现: $featureDeps",
                featureDeps.isEmpty(),
            )
        }
    }

    @Test
    fun core_modules_do_not_depend_on_app_or_feature() {
        val coreRoots =
            projectDir.resolve("core").listFiles()?.filter { it.isDirectory }.orEmpty()
        coreRoots.forEach { coreDir ->
            val build = coreDir.resolve("build.gradle.kts")
            if (!build.isFile) return@forEach
            val body = build.readText()
            val projectDeps = projectDependencies(body)
            val forbidden =
                projectDeps.filter {
                    it == ":app" || it.startsWith(":feature:")
                }
            assertTrue(
                "core/${coreDir.name} 不得依赖 app/Feature，发现: $forbidden",
                forbidden.isEmpty(),
            )
        }
    }

    @Test
    fun feature_settings_depends_only_on_whitelist_projects() {
        val build = projectDir.resolve("feature/settings/build.gradle.kts").readText()
        val projectDeps = projectDependencies(build)
        val extras = projectDeps - settingsAllowedProjects
        assertTrue(
            ":feature:settings 项目依赖白名单仅为 $settingsAllowedProjects，发现额外: $extras",
            extras.isEmpty(),
        )
        assertTrue(
            ":feature:settings 必须依赖 :core:domain",
            projectDeps.contains(":core:domain"),
        )
        assertTrue(
            ":feature:settings 必须依赖 :core:navigation",
            projectDeps.contains(":core:navigation"),
        )
        assertTrue(
            ":feature:settings 必须依赖 :core:designsystem",
            projectDeps.contains(":core:designsystem"),
        )
        assertFalse(
            ":feature:settings 不得依赖 :core:data",
            build.contains("""project(":core:data")"""),
        )
        assertFalse(
            ":feature:settings 不得依赖 :core:network",
            build.contains("""project(":core:network")"""),
        )
        assertFalse(
            ":feature:settings 不得依赖 :core:sync",
            build.contains("""project(":core:sync")"""),
        )
        assertFalse(
            ":feature:settings 不得依赖 retrofit",
            build.contains("retrofit", ignoreCase = true),
        )
        assertFalse(
            ":feature:settings 不得依赖 workmanager",
            build.contains("work", ignoreCase = true) &&
                build.contains("workmanager", ignoreCase = true),
        )
    }

    @Test
    fun home_owns_archived_and_feature_archived_does_not_exist() {
        val homeEntry =
            projectDir.resolve(
                "feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeEntryContributor.kt",
            )
        val body = homeEntry.readText()
        assertTrue(
            "HomeEntryContributor 必须拥有 ArchivedKey",
            body.contains("ArchivedKey") && body.contains("key is HomeKey || key is ArchivedKey"),
        )
        val settingsText = projectDir.resolve("settings.gradle.kts").readText()
        assertFalse(
            "不得存在 :feature:archived 模块",
            settingsText.contains("""include(":feature:archived")"""),
        )
        assertFalse(
            "feature/archived 目录不得存在",
            projectDir.resolve("feature/archived").exists(),
        )
    }

    @Test
    fun viewmodel_sources_do_not_reference_oneMemosNavigator() {
        val featureSrc = projectDir.resolve("feature")
        val viewModels =
            featureSrc
                .walkTopDown()
                .filter { file ->
                    file.isFile &&
                        file.extension == "kt" &&
                        file.name.endsWith("ViewModel.kt") &&
                        !file.path.contains("${File.separator}build${File.separator}") &&
                        file.path.contains("${File.separator}src${File.separator}main${File.separator}")
                }
                .toList()
        assertTrue("必须至少发现一个 ViewModel 源文件", viewModels.isNotEmpty())
        val offenders =
            viewModels.filter { file ->
                file.readText().contains("OneMemosNavigator")
            }
        assertTrue(
            "ViewModel 源码不得引用 OneMemosNavigator，发现: ${offenders.map { it.relativeTo(projectDir) }}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun app_aggregates_contributors_without_calling_feature_screens() {
        val contributors =
            projectDir.resolve(
                "app/src/main/java/cc/pscly/onememos/navigation/AppEntryContributors.kt",
            )
        val contributorsBody = contributors.readText()
        assertTrue(
            "app 必须聚合 appEntryContributors",
            contributorsBody.contains("appEntryContributors"),
        )
        assertTrue(
            "app 必须包含 HomeEntryContributor",
            contributorsBody.contains("HomeEntryContributor"),
        )
        assertTrue(
            "app 必须包含 SettingsEntryContributor",
            contributorsBody.contains("SettingsEntryContributor"),
        )

        val host =
            projectDir.resolve(
                "app/src/main/java/cc/pscly/onememos/navigation/AppNavigationHost.kt",
            )
        val hostBody = host.readText()
        assertTrue(
            "AppNavigationHost 必须通过 contributor 解析 entry",
            hostBody.contains("contributors") || hostBody.contains("FeatureEntryContributor"),
        )
        assertFalse(
            "AppNavigationHost 不得直接调用 Feature Screen 构造",
            Regex("""\b\w+Screen\s*\(""").containsMatchIn(hostBody),
        )

        val appMainKt =
            projectDir
                .resolve("app/src/main/java")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .toList()
        val screenCallOffenders =
            appMainKt.filter { file ->
                val text = file.readText()
                // Feature 页面 Screen 的直接调用（排除 Activity/Service 等平台入口）
                Regex("""import\s+cc\.pscly\.onememos\.ui\.feature\.\w+\.\w+Screen\b""")
                    .containsMatchIn(text) ||
                    (
                        file.name != "AppEntryContributors.kt" &&
                            Regex("""\b\w+Screen\s*\(""").containsMatchIn(text) &&
                            text.contains("ui.feature")
                    )
            }
        assertTrue(
            "app 不得直接 import/调用 Feature *Screen，发现: ${screenCallOffenders.map { it.relativeTo(projectDir) }}",
            screenCallOffenders.isEmpty(),
        )
    }

    @Test
    fun seven_settings_capabilities_have_unique_app_binds() {
        val diDir = projectDir.resolve("app/src/main/java/cc/pscly/onememos/di")
        val diSources =
            diDir
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .toList()
        val allDi = diSources.joinToString("\n") { it.readText() }
        settingsCapabilityBinds.forEach { bindName ->
            val count = Regex("""\bfun\s+$bindName\b""").findAll(allDi).count()
            assertTrue(
                "能力绑定 $bindName 必须恰好出现一次，实际 $count",
                count == 1,
            )
        }

        // Feature / Core 不得再提供这七个默认绑定
        val forbiddenRoots =
            listOf(
                projectDir.resolve("feature"),
                projectDir.resolve("core"),
            )
        forbiddenRoots.forEach { root ->
            val offenders =
                root
                    .walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .filter { file ->
                        val text = file.readText()
                        settingsCapabilityBinds.any { bind ->
                            text.contains("fun $bind") ||
                                (
                                    text.contains("@Module") &&
                                        text.contains("SettingsHubCapability")
                                )
                        }
                    }
                    .toList()
            assertTrue(
                "${root.name} 不得重复绑定 Settings 能力，发现: ${offenders.map { it.relativeTo(projectDir) }}",
                offenders.isEmpty(),
            )
        }

        val moduleFile =
            projectDir.resolve(
                "app/src/main/java/cc/pscly/onememos/di/SettingsCapabilityModule.kt",
            )
        assertTrue("SettingsCapabilityModule 必须存在", moduleFile.isFile)
        val moduleBody = moduleFile.readText()
        assertTrue(moduleBody.contains("@Module"))
        assertTrue(moduleBody.contains("@InstallIn"))
    }

    @Test
    fun benchmark_modules_still_target_app() {
        val baseline = projectDir.resolve("baselineprofile/build.gradle.kts").readText()
        val macro = projectDir.resolve("macrobenchmark/build.gradle.kts").readText()
        assertTrue(
            "baselineprofile targetProjectPath 必须为 :app",
            baseline.contains("""targetProjectPath = ":app""""),
        )
        assertTrue(
            "macrobenchmark targetProjectPath 必须为 :app",
            macro.contains("""targetProjectPath = ":app""""),
        )
    }

    @Test
    fun legacy_routes_and_nav_controller_are_gone_from_app_and_features() {
        // 仅扫 main；测试源码会字面量提到这些符号作为断言文案。
        val roots =
            listOf(
                projectDir.resolve("app/src/main"),
                projectDir.resolve("feature"),
            )
        roots.forEach { root ->
            if (!root.exists()) return@forEach
            val offenders =
                root
                    .walkTopDown()
                    .filter { it.isFile && (it.extension == "kt" || it.extension == "kts") }
                    .filter { !it.path.contains("${File.separator}build${File.separator}") }
                    .filter { it.path.contains("${File.separator}src${File.separator}main${File.separator}") || root.name == "main" }
                    .filter { file ->
                        val text = file.readText()
                        text.contains("object Routes") ||
                            text.contains("class Routes") ||
                            text.contains("androidx.navigation.compose") ||
                            text.contains("NavController")
                    }
                    .toList()
            assertTrue(
                "${root.relativeTo(projectDir)} 不得残留旧导航 Routes 或 Compose NavHost 控制器，发现: " +
                    offenders.map { it.relativeTo(projectDir) },
                offenders.isEmpty(),
            )
        }
        assertFalse(
            "Routes.kt 不得存在",
            projectDir.resolve("app/src/main/java/cc/pscly/onememos/ui/Routes.kt").exists(),
        )
    }

    @Test
    fun feature_modules_have_no_hilt_provider_or_binding_modules() {
        val featureRoot = projectDir.resolve("feature")
        val offenders =
            featureRoot
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { !it.path.contains("${File.separator}build${File.separator}") }
                .filter { it.path.contains("${File.separator}src${File.separator}main${File.separator}") }
                .filter { file ->
                    val text = file.readText()
                    text.contains("@Module") ||
                        text.contains("@Provides") ||
                        text.contains("@Binds") ||
                        text.contains("@InstallIn")
                }
                .toList()
        assertTrue(
            "Feature 不得声明 Hilt Module/Provides/Binds/InstallIn，发现: " +
                offenders.map { it.relativeTo(projectDir) },
            offenders.isEmpty(),
        )
    }

    private fun projectDependencies(buildBody: String): Set<String> {
        val regex = Regex("""project\(\s*["']([^"']+)["']\s*\)""")
        return regex.findAll(buildBody).map { it.groupValues[1] }.toSet()
    }
}
