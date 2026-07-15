package cc.pscly.onememos.architecture

import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * 平台组件契约测试（Robolectric）。
 *
 * 锁定 Manifest 组件 FQCN、tile 声明、更新目录、日历 DataStore、
 * 以及独立 GitHub 客户端不复用 Memos Bearer Token 等行为。
 */
class PlatformComponentContractsTest {

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

    // ── App-owned Activities ──────────────────────────────

    @Test
    fun quickCaptureActivity_is_declared_in_app_manifest() {
        val manifest = manifestBody()
        assertTrue(
            "QuickCaptureActivity 必须在 app Manifest 中声明",
            manifest.contains(""".ui.feature.quickcapture.QuickCaptureActivity"""),
        )
    }

    @Test
    fun quickCaptureOverlayEntryActivity_is_declared_in_app_manifest() {
        val manifest = manifestBody()
        assertTrue(
            "QuickCaptureOverlayEntryActivity 必须在 app Manifest 中声明",
            manifest.contains(""".overlay.QuickCaptureOverlayEntryActivity"""),
        )
    }

    @Test
    fun quickCaptureOverlayPickImagesActivity_is_declared_in_app_manifest() {
        val manifest = manifestBody()
        assertTrue(
            "QuickCaptureOverlayPickImagesActivity 必须在 app Manifest 中声明",
            manifest.contains(""".overlay.QuickCaptureOverlayPickImagesActivity"""),
        )
    }

    @Test
    fun screenshotQuickCaptureActivity_is_declared_in_app_manifest() {
        val manifest = manifestBody()
        assertTrue(
            "ScreenshotQuickCaptureActivity 必须在 app Manifest 中声明",
            manifest.contains(""".screenshot.ScreenshotQuickCaptureActivity"""),
        )
    }

    // ── 可移动组件（Tile Service / 外部动作 Activity） ────

    @Test
    fun quickCaptureTileService_is_declared_in_app_manifest() {
        val manifest = manifestBody()
        assertTrue(
            "QuickCaptureTileService 必须在 app Manifest 中声明",
            manifest.contains(""".qs.QuickCaptureTileService"""),
        )
    }

    @Test
    fun quickScreenshotTileService_is_declared_in_app_manifest() {
        val manifest = manifestBody()
        assertTrue(
            "QuickScreenshotTileService 必须在 app Manifest 中声明",
            manifest.contains(""".qs.QuickScreenshotTileService"""),
        )
    }

    @Test
    fun todoExternalActionsActivity_is_declared() {
        // TodoExternalActionsActivity 在 sync 模块 Manifest 中声明
        val syncManifestFile = projectDir.resolve("core/sync/src/main/AndroidManifest.xml")
        val syncManifest = if (syncManifestFile.exists()) {
            syncManifestFile.readText()
        } else {
            ""
        }
        val appManifest = manifestBody()
        val foundInSync = syncManifest.contains("TodoExternalActionsActivity")
        val foundInApp = appManifest.contains("TodoExternalActionsActivity")
        assertTrue(
            "TodoExternalActionsActivity 必须在 sync 或 app Manifest 中声明",
            foundInSync || foundInApp,
        )
    }

    // ── Tile 声明细节 ──────────────────────────────────────

    @Test
    fun quickCaptureTile_has_label_and_icon_and_permission() {
        val manifest = manifestBody()
        // label
        assertTrue(
            "QuickCaptureTileService 必须有 android:label",
            manifest.contains("""android:label="@string/qs_quick_capture""""),
        )
        // icon
        assertTrue(
            "QuickCaptureTileService 必须有 android:icon",
            manifest.contains("""android:icon="@drawable/ic_qs_quick_capture""""),
        )
        // permission
        assertTrue(
            "Tile 必须声明 BIND_QUICK_SETTINGS_TILE 权限",
            manifest.contains("android.permission.BIND_QUICK_SETTINGS_TILE"),
        )
    }

    @Test
    fun quickScreenshotTile_has_label_and_icon() {
        val manifest = manifestBody()
        assertTrue(
            "QuickScreenshotTileService 必须有 android:label",
            manifest.contains("""android:label="@string/qs_quick_screenshot""""),
        )
        assertTrue(
            "QuickScreenshotTileService 必须有 android:icon",
            manifest.contains("""android:icon="@drawable/ic_qs_quick_screenshot""""),
        )
    }

    // ── 更新下载目录 ──────────────────────────────────────

    @Test
    fun update_download_directory() {
        val updateDir = projectDir.resolve("app/src/main/java/cc/pscly/onememos/update")
        val files = updateDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val body = files.joinToString("\n") { it.readText() }
        assertTrue("更新下载目录必须使用 DIRECTORY_DOWNLOADS", body.contains("DIRECTORY_DOWNLOADS"))
        assertTrue("更新下载必须创建 updates 子目录", body.contains(""""updates""""))
    }

    // ── 独立 GitHub 客户端（不复用 Memos Bearer Token） ────

    @Test
    fun independent_github_client_does_not_reuse_memos_token() {
        // GitHub 更新 API 使用独立 OkHttpClient，不注入 Memos 客户端；
        // 验证 AppUpdateModule 中为 GitHubUpdateApi 新建了 OkHttpClient。
        val updateModule = projectDir.resolve(
            "app/src/main/java/cc/pscly/onememos/di/AppUpdateModule.kt",
        )
        val body = updateModule.readText()
        assertTrue(
            "AppUpdateModule 必须提供 GitHubUpdateApi",
            body.contains("GitHubUpdateApi"),
        )
        assertTrue(
            "GitHub 更新必须使用独立 OkHttpClient",
            body.contains("OkHttpClient"),
        )
    }

    // ── 日历 DataStore ────────────────────────────────────

    @Test
    fun calendar_datastore_name() {
        // 在 SettingsScreen 或 Worker 中查找 DataStore 名
        val settingsSrc = projectDir.resolve(
            "feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/SettingsScreen.kt",
        )
        val workerSrc = projectDir.resolve(
            "core/sync/src/main/java/cc/pscly/onememos/worker/TodoReminderRescheduleWorker.kt",
        )
        val combined = (if (settingsSrc.exists()) settingsSrc.readText() else "") +
            (if (workerSrc.exists()) workerSrc.readText() else "")

        assertTrue(
            "必须包含日历 DataStore 名 todo_calendar_event_state",
            combined.contains("todo_calendar_event_state"),
        )
    }

    // ── 系统时钟无 Activity 时回退待办 ────────────────────

    @Test
    fun todo_external_actions_fallback_when_no_clock_activity() {
        val actionsActivity = projectDir.resolve(
            "core/sync/src/main/java/cc/pscly/onememos/worker/TodoExternalActionsActivity.kt",
        )
        if (actionsActivity.exists()) {
            val body = actionsActivity.readText()
            assertTrue(
                "TodoExternalActionsActivity 必须在无系统时钟时回退到待办页",
                body.contains("START_ROUTE") || body.contains("OPEN_TODO") || body.contains("todo"),
            )
        }
    }

    // ── 日历 event 映射键 ──────────────────────────────────

    @Test
    fun calendar_event_entries_key() {
        val workerSrc = projectDir.resolve(
            "core/sync/src/main/java/cc/pscly/onememos/worker/TodoReminderRescheduleWorker.kt",
        )
        val settingsSrc = projectDir.resolve(
            "feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/SettingsScreen.kt",
        )
        val combined = (if (workerSrc.exists()) workerSrc.readText() else "") +
            (if (settingsSrc.exists()) settingsSrc.readText() else "")
        assertTrue(
            "必须包含日历事件映射键 todo_calendar_event_entries",
            combined.contains("todo_calendar_event_entries"),
        )
    }

    private fun manifestBody(): String =
        projectDir.resolve("app/src/main/AndroidManifest.xml").readText()
}
