package cc.pscly.onememos.di

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * 七个设置能力唯一装配契约。
 *
 * 运行时完整 Hilt 组件解析依赖 instrumentation / hilt-android-testing；
 * 本仓库单元测试采用源码契约 + 组合根编译门禁验证：
 * 1) app 组合根唯一绑定七个接口；
 * 2) 无重复默认绑定；
 * 3) feature/settings 不拥有 Module/Provides/Binds/基础设施实现；
 * 4) 七个实现类的构造依赖在 app 组合根可解析（通过已有模块与实现类构造签名交叉检查）。
 */
class SettingsCapabilityInjectionTest {
    companion object {
        private lateinit var projectDir: File

        private val capabilityInterfaces =
            listOf(
                "SettingsHubCapability",
                "AccountSyncSettingsCapability",
                "RecordEditingSettingsCapability",
                "ReminderCalendarSettingsCapability",
                "StorageOfflineSettingsCapability",
                "AppearanceInteractionSettingsCapability",
                "AboutAdvancedSettingsCapability",
            )

        private val capabilityImpls =
            listOf(
                "SettingsHubCapabilityImpl",
                "AccountSyncSettingsCapabilityImpl",
                "RecordEditingSettingsCapabilityImpl",
                "ReminderCalendarSettingsCapabilityImpl",
                "StorageOfflineSettingsCapabilityImpl",
                "AppearanceInteractionSettingsCapabilityImpl",
                "AboutAdvancedSettingsCapabilityImpl",
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
    fun settingsCapabilityModule_bindsSevenInterfacesExactlyOnce() {
        val module = projectDir.resolve(
            "app/src/main/java/cc/pscly/onememos/di/SettingsCapabilityModule.kt",
        )
        assertTrue("SettingsCapabilityModule 必须存在", module.isFile)
        val body = module.readText()

        assertTrue(body.contains("@Module"))
        assertTrue(body.contains("@InstallIn(SingletonComponent::class)"))
        assertTrue(body.contains("abstract class SettingsCapabilityModule"))

        capabilityInterfaces.forEach { iface ->
            val bindCount = Regex(""":\s*$iface\b""").findAll(body).count()
            assertEquals("$iface 必须在 SettingsCapabilityModule 中恰好绑定一次", 1, bindCount)
        }
        capabilityImpls.forEach { impl ->
            assertTrue("$impl 必须出现在 SettingsCapabilityModule", body.contains(impl))
        }

        val bindsCount = Regex("""@Binds""").findAll(body).count()
        assertEquals("SettingsCapabilityModule 应恰好 7 个 @Binds", 7, bindsCount)
        val singletonCount = Regex("""@Singleton""").findAll(body).count()
        assertEquals("每个绑定都应是 @Singleton", 7, singletonCount)
    }

    @Test
    fun noDuplicateDefaultBindingsForSettingsCapabilitiesOutsideModule() {
        val diDir = projectDir.resolve("app/src/main/java/cc/pscly/onememos/di")
        val others =
            diDir.listFiles()
                ?.filter { it.isFile && it.extension == "kt" && it.name != "SettingsCapabilityModule.kt" }
                .orEmpty()
        val body = others.joinToString("\n") { it.readText() }

        capabilityInterfaces.forEach { iface ->
            assertFalse(
                "$iface 不得在其他 app DI 模块中再次绑定",
                body.contains(iface),
            )
        }
        capabilityImpls.forEach { impl ->
            assertFalse(
                "$impl 不得在其他 app DI 模块中绑定",
                body.contains(impl),
            )
        }
    }

    @Test
    fun featureSettings_hasNoDiOrInfrastructureProviders() {
        val featureMain = projectDir.resolve("feature/settings/src/main")
        assertTrue(featureMain.isDirectory)
        val sources =
            featureMain.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .toList()
        assertTrue("feature/settings 至少应有源文件", sources.isNotEmpty())
        val body = sources.joinToString("\n") { it.readText() }

        assertFalse(body.contains("@Module"))
        assertFalse(body.contains("@Provides"))
        assertFalse(body.contains("@Binds"))
        assertFalse(body.contains("@InstallIn"))
        assertFalse(body.contains("dagger.Module"))
        assertFalse(body.contains("dagger.Provides"))
        assertFalse(body.contains("dagger.Binds"))
        assertFalse(Regex("""\bProvider\s*<""").containsMatchIn(body))
        assertFalse(body.contains("SettingsRepositoryImpl"))
        assertFalse(body.contains("AppUpdateManager"))
        assertFalse(body.contains("SystemCalendarGatewayImpl"))
        assertFalse(body.contains("DiagnosticsExporterImpl"))
        assertFalse(body.contains("WorkManagerSyncScheduler"))
    }

    @Test
    fun sevenImplementations_existAndExposeInjectConstructors() {
        val roots =
            listOf(
                "core/settings/src/main/java/cc/pscly/onememos/settings/SettingsHubCapabilityImpl.kt",
                "core/settings/src/main/java/cc/pscly/onememos/settings/account/AccountSyncSettingsCapabilityImpl.kt",
                "core/settings/src/main/java/cc/pscly/onememos/settings/record/RecordEditingSettingsCapabilityImpl.kt",
                "core/settings/src/main/java/cc/pscly/onememos/settings/reminder/ReminderCalendarSettingsCapabilityImpl.kt",
                "core/settings/src/main/java/cc/pscly/onememos/settings/storage/StorageOfflineSettingsCapabilityImpl.kt",
                "core/settings/src/main/java/cc/pscly/onememos/settings/appearance/AppearanceInteractionSettingsCapabilityImpl.kt",
                "core/settings/src/main/java/cc/pscly/onememos/settings/about/AboutAdvancedSettingsCapabilityImpl.kt",
            )
        roots.forEach { rel ->
            val file = projectDir.resolve(rel)
            assertTrue("$rel 必须存在", file.isFile)
            val body = file.readText()
            assertTrue("$rel 必须有 @Inject constructor", body.contains("@Inject constructor"))
            assertTrue("$rel 必须是 @Singleton", body.contains("@Singleton"))
        }
    }

    @Test
    fun settingsCapabilityModule_doesNotReprovideUpdateDependencies() {
        val module = projectDir.resolve(
            "app/src/main/java/cc/pscly/onememos/di/SettingsCapabilityModule.kt",
        ).readText()
        assertFalse(module.contains("GitHubUpdateApi"))
        assertFalse(module.contains("@Provides"))
        assertFalse(module.contains("AppUpdateManager"))
        assertFalse(module.contains("OkHttpClient"))
        assertFalse(module.contains("Retrofit"))
    }

    @Test
    fun compositionRoot_alreadyProvidesSharedDependenciesUsedByCapabilities() {
        val diBody =
            projectDir.resolve("app/src/main/java/cc/pscly/onememos/di")
                .listFiles()
                ?.filter { it.isFile && it.extension == "kt" }
                .orEmpty()
                .joinToString("\n") { it.readText() }

        // 七个实现共享依赖应已在组合根存在，避免 missing binding。
        assertTrue(diBody.contains("SettingsRepository"))
        assertTrue(diBody.contains("SyncStatusMonitor"))
        assertTrue(diBody.contains("SyncScheduler"))
        assertTrue(diBody.contains("TodoReminderScheduler"))
        assertTrue(diBody.contains("CacheRepository"))
        assertTrue(diBody.contains("SystemCalendarGateway"))
        assertTrue(diBody.contains("OverlayPermissionGateway"))
        assertTrue(diBody.contains("QuickTileRequester"))
        assertTrue(diBody.contains("DiagnosticsExporter"))
        assertTrue(diBody.contains("AppIdentityPort"))
        assertTrue(diBody.contains("GitHubUpdateApi"))
    }
}
