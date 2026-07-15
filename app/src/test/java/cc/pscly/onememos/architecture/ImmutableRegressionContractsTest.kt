package cc.pscly.onememos.architecture

import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * 字面量回归契约测试（规格 §10.1）。
 *
 * 读取仓库源码与资源，逐项断言关键标识符、路径、数据库名、Room 版本、
 * benchmark 模块 targetProjectPath 与 Application 接口实现不变。
 */
class ImmutableRegressionContractsTest {

    companion object {
        private lateinit var projectDir: File

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

    // ── 应用标识 ───────────────────────────────────────────

    @Test
    fun applicationId_is_cc_pscly_onememos() {
        val appBuild = projectDir.resolve("app/build.gradle.kts").readText()
        assertTrue(
            "applicationId 必须为 cc.pscly.onememos",
            appBuild.contains(""""cc.pscly.onememos""""),
        )
    }

    // ── Sync / WorkManager 标识 ────────────────────────────

    @Test
    fun sync_worker_identifiers() {
        val syncDir = projectDir.resolve("core/sync/src/main/java/cc/pscly/onememos/worker")
        val files = syncDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val body = files.joinToString("\n") { it.readText() }

        assertTrue("必须包含 one_memos_sync", body.contains("one_memos_sync"))
        assertTrue("必须包含 force_full_sync", body.contains("force_full_sync"))
        assertTrue("必须包含 is_periodic", body.contains("is_periodic"))
        assertTrue("必须包含 followup_sync", body.contains("followup_sync"))
        assertTrue("必须包含 one_memos_periodic_sync", body.contains("one_memos_periodic_sync"))
        assertTrue("必须包含 one_memos_rebuild_memo_derived_fields", body.contains("one_memos_rebuild_memo_derived_fields"))
        assertTrue("必须包含 one_memos_attachment_prefetch", body.contains("one_memos_attachment_prefetch"))
    }

    // ── Room 数据库 ───────────────────────────────────────

    @Test
    fun room_database_version() {
        val dbFile = projectDir.resolve(
            "core/database/src/main/java/cc/pscly/onememos/core/database/OneMemosDatabase.kt",
        )
        val body = dbFile.readText()
        // 版本号声明形式：version = 11
        assertTrue("Room 版本必须为 11", body.contains(Regex("""version\s*=\s*11""")))
    }

    @Test
    fun room_database_name() {
        val dbFile = projectDir.resolve(
            "core/database/src/main/java/cc/pscly/onememos/core/database/OneMemosDatabase.kt",
        )
        val body = dbFile.readText()
        // 数据库名声明形式：entities = [...], version = 11, exportSchema = false
        // 实际数据库文件名为 one_memos.db，在 AppModule.kt 中指定
        val appModule = projectDir.resolve(
            "app/src/main/java/cc/pscly/onememos/di/AppModule.kt",
        )
        val moduleBody = appModule.readText()
        assertTrue("数据库名必须为 one_memos.db", moduleBody.contains("one_memos.db"))
    }

    // ── FileProvider ───────────────────────────────────────

    @Test
    fun fileprovider_authority() {
        val manifest = projectDir.resolve("app/src/main/AndroidManifest.xml").readText()
        assertTrue(
            "FileProvider authority 必须为 \${applicationId}.fileprovider",
            manifest.contains("\${applicationId}.fileprovider"),
        )
    }

    // ── 共享/导出目录 ──────────────────────────────────────

    @Test
    fun shared_directories() {
        val filePathsXml = projectDir.resolve("app/src/main/res/xml/file_paths.xml").readText()
        assertTrue("必须包含 share_cards/", filePathsXml.contains("share_cards/"))
        assertTrue("必须包含 screenshots/", filePathsXml.contains("screenshots/"))
        assertTrue("必须包含 shared/", filePathsXml.contains("shared/"))
    }

    // ── 外部编辑器 extra ────────────────────────────────────

    @Test
    fun start_editor_uuid_extra() {
        val mainActivity = projectDir.resolve(
            "app/src/main/java/cc/pscly/onememos/MainActivity.kt",
        )
        val body = mainActivity.readText()
        assertTrue(
            "必须包含 cc.pscly.onememos.extra.START_EDITOR_UUID",
            body.contains("START_EDITOR_UUID"),
        )
    }

    // ── Benchmark 模块 targetProjectPath ──────────────────

    @Test
    fun baselineprofile_targetProjectPath_is_app() {
        val build = projectDir.resolve("baselineprofile/build.gradle.kts").readText()
        assertTrue(
            "baselineprofile targetProjectPath 必须为 :app",
            build.contains("""targetProjectPath = ":app""""),
        )
    }

    @Test
    fun macrobenchmark_targetProjectPath_is_app() {
        val build = projectDir.resolve("macrobenchmark/build.gradle.kts").readText()
        assertTrue(
            "macrobenchmark targetProjectPath 必须为 :app",
            build.contains("""targetProjectPath = ":app""""),
        )
    }

    // ── Application 接口实现 ──────────────────────────────

    @Test
    fun oneMemosApplication_implements_configuration_provider() {
        val app = projectDir.resolve(
            "app/src/main/java/cc/pscly/onememos/OneMemosApplication.kt",
        )
        val body = app.readText()
        assertTrue(
            "OneMemosApplication 必须实现 Configuration.Provider",
            body.contains("Configuration.Provider"),
        )
    }

    @Test
    fun oneMemosApplication_implements_image_loader_factory() {
        val app = projectDir.resolve(
            "app/src/main/java/cc/pscly/onememos/OneMemosApplication.kt",
        )
        val body = app.readText()
        assertTrue(
            "OneMemosApplication 必须实现 ImageLoaderFactory",
            body.contains("ImageLoaderFactory"),
        )
    }

    @Test
    fun oneMemosApplication_holds_hilt_worker_factory() {
        val app = projectDir.resolve(
            "app/src/main/java/cc/pscly/onememos/OneMemosApplication.kt",
        )
        val body = app.readText()
        assertTrue(
            "OneMemosApplication 必须持有 HiltWorkerFactory",
            body.contains("HiltWorkerFactory"),
        )
    }

    // ── :core:model 与 :core:domain 纯度 ─────────────────────

    @Test
    fun core_model_manifest_does_not_exist() {
        val manifest = projectDir.resolve("core/model/src/main/AndroidManifest.xml")
        assertFalse(
            ":core:model 的 AndroidManifest.xml 必须不存在（纯 JVM 模块）",
            manifest.exists(),
        )
    }

    @Test
    fun core_domain_manifest_does_not_exist() {
        val manifest = projectDir.resolve("core/domain/src/main/AndroidManifest.xml")
        assertFalse(
            ":core:domain 的 AndroidManifest.xml 必须不存在（纯 JVM 模块）",
            manifest.exists(),
        )
    }

    @Test
    fun core_domain_src_has_no_android_or_androidx_imports() {
        val domainSrc = projectDir.resolve("core/domain/src")
        val files = domainSrc.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val allImports = files.flatMap { f ->
            f.readLines().filter { line ->
                val trimmed = line.trim()
                trimmed.startsWith("import ") &&
                    (trimmed.contains("android.") || trimmed.contains("androidx."))
            }
        }
        assertTrue(
            ":core:domain 源码不得包含 android. 或 androidx. import，但发现: ${allImports.take(5)}",
            allImports.isEmpty(),
        )
    }

    @Test
    fun core_domain_build_has_no_android_plugin_or_androidx_paging_deps() {
        val build = projectDir.resolve("core/domain/build.gradle.kts").readText()
        assertFalse(
            ":core:domain 构建不得包含 Android plugin",
            build.contains("com.android.library") || build.contains("com.android.application"),
        )
        assertFalse(
            ":core:domain 构建不得包含 android {} 块",
            build.contains(Regex("""\bandroid\s*\{""")),
        )
        assertFalse(
            ":core:domain 构建不得包含 androidx 或 paging 依赖",
            build.contains("androidx") || build.contains("paging"),
        )
    }

    @Test
    fun memoRepository_has_no_paging_data() {
        val repo = projectDir.resolve(
            "core/domain/src/main/java/cc/pscly/onememos/domain/repository/MemoRepository.kt",
        )
        val body = repo.readText()
        assertFalse(
            "MemoRepository 不得泄露 PagingData（AndroidX Paging 类型）",
            body.contains("PagingData") || body.contains("pagingMemos") || body.contains("pagingArchivedMemos"),
        )
    }

    @Test
    fun memo_browse_scope_exists_as_top_level_sealed_interface() {
        val scopeFile = projectDir.resolve(
            "core/domain/src/main/java/cc/pscly/onememos/domain/repository/MemoBrowseScope.kt",
        )
        assertTrue("MemoBrowseScope.kt 必须存在", scopeFile.isFile)
        val body = scopeFile.readText()
        assertTrue(
            "MemoBrowseScope 必须是纯 Kotlin sealed interface",
            body.contains("sealed interface MemoBrowseScope"),
        )
        assertTrue("MemoBrowseScope 必须包含 All", body.contains("data object All"))
        assertTrue("MemoBrowseScope 必须包含 LocalOnly", body.contains("data object LocalOnly"))
        assertTrue("MemoBrowseScope 必须包含 Creator", body.contains("data class Creator"))
    }
}
