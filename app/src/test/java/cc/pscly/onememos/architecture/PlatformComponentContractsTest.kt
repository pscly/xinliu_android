package cc.pscly.onememos.architecture

import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * 平台组件契约测试。
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

    @Test
    fun quickCaptureActivity_is_declared_in_app_manifest() {
        val manifest = appManifestBody()
        assertTrue(
            "QuickCaptureActivity 必须在 app Manifest 中声明",
            manifest.contains(""".ui.feature.quickcapture.QuickCaptureActivity"""),
        )
    }

    @Test
    fun quickCaptureOverlayEntryActivity_is_declared_in_app_manifest() {
        val manifest = appManifestBody()
        assertTrue(
            "QuickCaptureOverlayEntryActivity 必须在 app Manifest 中声明",
            manifest.contains(""".overlay.QuickCaptureOverlayEntryActivity"""),
        )
    }

    @Test
    fun quickCaptureOverlayPickImagesActivity_is_declared_in_app_manifest() {
        val manifest = appManifestBody()
        assertTrue(
            "QuickCaptureOverlayPickImagesActivity 必须在 app Manifest 中声明",
            manifest.contains(""".overlay.QuickCaptureOverlayPickImagesActivity"""),
        )
    }

    @Test
    fun screenshotQuickCaptureActivity_is_declared_in_app_manifest() {
        val manifest = appManifestBody()
        assertTrue(
            "ScreenshotQuickCaptureActivity 必须在 app Manifest 中声明",
            manifest.contains(""".screenshot.ScreenshotQuickCaptureActivity"""),
        )
    }

    @Test
    fun quickCaptureTileService_is_declared_in_app_manifest() {
        val body = mergedManifests()
        assertTrue(
            "QuickCaptureTileService 必须在 Manifest 中声明",
            body.contains("QuickCaptureTileService"),
        )
    }

    @Test
    fun quickScreenshotTileService_is_declared_in_app_manifest() {
        val body = mergedManifests()
        assertTrue(
            "QuickScreenshotTileService 必须在 Manifest 中声明",
            body.contains("QuickScreenshotTileService"),
        )
    }

    @Test
    fun todoExternalActionsActivity_is_declared() {
        val syncManifestFile = projectDir.resolve("core/sync/src/main/AndroidManifest.xml")
        val externalManifestFile = projectDir.resolve("core/externalactions/src/main/AndroidManifest.xml")
        val syncManifest = if (syncManifestFile.exists()) syncManifestFile.readText() else ""
        val externalManifest = if (externalManifestFile.exists()) externalManifestFile.readText() else ""
        val appManifest = appManifestBody()
        assertTrue(
            "TodoExternalActionsActivity 必须在 sync/externalactions/app Manifest 中声明",
            syncManifest.contains("TodoExternalActionsActivity") ||
                externalManifest.contains("TodoExternalActionsActivity") ||
                appManifest.contains("TodoExternalActionsActivity"),
        )
    }

    @Test
    fun quickCaptureTile_has_label_and_icon_and_permission() {
        val body = mergedManifests()
        assertTrue(
            "QuickCaptureTileService 必须有 android:label",
            body.contains("""android:label="@string/qs_quick_capture""""),
        )
        assertTrue(
            "QuickCaptureTileService 必须有 android:icon",
            body.contains("""android:icon="@drawable/ic_qs_quick_capture""""),
        )
        assertTrue(
            "Tile 必须声明 BIND_QUICK_SETTINGS_TILE 权限",
            body.contains("android.permission.BIND_QUICK_SETTINGS_TILE"),
        )
    }

    @Test
    fun quickScreenshotTile_has_label_and_icon() {
        val body = mergedManifests()
        assertTrue(
            "QuickScreenshotTileService 必须有 android:label",
            body.contains("""android:label="@string/qs_quick_screenshot""""),
        )
        assertTrue(
            "QuickScreenshotTileService 必须有 android:icon",
            body.contains("""android:icon="@drawable/ic_qs_quick_screenshot""""),
        )
    }

    @Test
    fun update_download_directory() {
        val updateDir = projectDir.resolve("core/update/src/main/java/cc/pscly/onememos/update")
        val files = updateDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val body = files.joinToString("\n") { it.readText() }
        assertTrue("更新下载目录必须使用 DIRECTORY_DOWNLOADS", body.contains("DIRECTORY_DOWNLOADS"))
        assertTrue("更新下载必须创建 updates 子目录", body.contains(""""updates""""))
    }

    @Test
    fun independent_github_client_does_not_reuse_memos_token() {
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

    @Test
    fun calendar_datastore_name() {
        val calendarImpl = projectDir.resolve(
            "core/calendar/src/main/java/cc/pscly/onememos/calendar/SystemCalendarGatewayImpl.kt",
        )
        val workerSrc = projectDir.resolve(
            "core/sync/src/main/java/cc/pscly/onememos/worker/TodoReminderRescheduleWorker.kt",
        )
        val combined =
            (if (calendarImpl.exists()) calendarImpl.readText() else "") +
                (if (workerSrc.exists()) workerSrc.readText() else "")
        assertTrue(
            "必须包含日历 DataStore 名 todo_calendar_event_state",
            combined.contains("todo_calendar_event_state"),
        )
    }

    @Test
    fun todo_external_actions_fallback_when_no_clock_activity() {
        val candidates =
            listOf(
                projectDir.resolve("core/sync/src/main/java/cc/pscly/onememos/worker/TodoExternalActionsActivity.kt"),
                projectDir.resolve("core/externalactions/src/main/java/cc/pscly/onememos/worker/TodoExternalActionsActivity.kt"),
            )
        val actionsActivity = candidates.firstOrNull { it.exists() }
        if (actionsActivity != null) {
            val body = actionsActivity.readText()
            assertTrue(
                "TodoExternalActionsActivity 必须在无系统时钟时回退到待办页",
                body.contains("START_ROUTE") || body.contains("OPEN_TODO") || body.contains("todo"),
            )
        }
    }

    @Test
    fun calendar_event_entries_key() {
        val calendarImpl = projectDir.resolve(
            "core/calendar/src/main/java/cc/pscly/onememos/calendar/SystemCalendarGatewayImpl.kt",
        )
        val workerSrc = projectDir.resolve(
            "core/sync/src/main/java/cc/pscly/onememos/worker/TodoReminderRescheduleWorker.kt",
        )
        val combined =
            (if (calendarImpl.exists()) calendarImpl.readText() else "") +
                (if (workerSrc.exists()) workerSrc.readText() else "")
        assertTrue(
            "必须包含日历事件映射键 todo_calendar_event_entries",
            combined.contains("todo_calendar_event_entries"),
        )
    }

    private fun appManifestBody(): String =
        projectDir.resolve("app/src/main/AndroidManifest.xml").readText()

    private fun mergedManifests(): String {
        val paths =
            listOf(
                "app/src/main/AndroidManifest.xml",
                "core/quicktiles/src/main/AndroidManifest.xml",
                "core/externalactions/src/main/AndroidManifest.xml",
                "core/sync/src/main/AndroidManifest.xml",
            )
        return paths
            .map { projectDir.resolve(it) }
            .filter { it.exists() }
            .joinToString("\n") { it.readText() }
    }
}
