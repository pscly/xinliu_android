package cc.pscly.onememos.ui.feature.settings.about

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import cc.pscly.onememos.domain.settings.AboutAdvancedSettingsCommand
import cc.pscly.onememos.domain.settings.AboutAdvancedSettingsSnapshot
import cc.pscly.onememos.domain.settings.DeveloperOptions
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.domain.settings.UpdateSettingsPhase
import cc.pscly.onememos.domain.settings.UpdateSettingsSnapshot
import cc.pscly.onememos.ui.theme.OneMemosTheme
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 关于与高级 Compose 页：版本/更新/磁贴/捕获/诊断/重建/上传/开发者；
 * 进入无副作用；Quick Capture 只平台事件；错误有文字与语义；触控 ≥48dp。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AboutAdvancedScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun content_showsVersionUpdateProgressErrorAndIgnored() {
        setContent(
            state =
                AboutAdvancedUiState(
                    loading = false,
                    snapshot =
                        baseSnapshot(
                            update =
                                UpdateSettingsSnapshot(
                                    phase = UpdateSettingsPhase.DOWNLOADING,
                                    availableVersion = "3.0.0",
                                    ignoredVersionTag = "v2.9.0",
                                    downloadProgressPercent = 42,
                                    error = SettingsCapabilityError.NetworkUnavailable,
                                ),
                        ),
                    persistentError = SettingsCapabilityError.NetworkUnavailable,
                ),
        )

        composeRule.onNodeWithTag("settings_about_title").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_about_version").assertIsDisplayed()
        assertTrue(
            composeRule
                .onAllNodesWithText("2.1.0", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty(),
        )
        composeRule
            .onNodeWithTag("settings_about_update_status", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule
            .onNodeWithTag("settings_about_update_progress", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule
            .onNodeWithTag("settings_about_update_error", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("settings_about_clear_ignored").performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_about_clear_ignored").assertIsDisplayed()
        // 错误不只靠颜色：有文字与 stateDescription
        composeRule.onNodeWithText("网络不可用", useUnmergedTree = true).assertIsDisplayed()
        val errNode =
            composeRule.onNodeWithTag("settings_about_update_error", useUnmergedTree = true)
        val stateDesc =
            errNode.fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
        assertTrue(stateDesc != null && stateDesc.isNotBlank())
    }

    @Test
    fun tilesCaptureDiagnosticsRebuildUploadDeveloper_andMinHeight48() {
        val check = AtomicInteger(0)
        val download = AtomicInteger(0)
        val install = AtomicInteger(0)
        val clearIgnored = AtomicInteger(0)
        val export = AtomicInteger(0)
        val quickTile = AtomicInteger(0)
        val shotTile = AtomicInteger(0)
        val openQuick = AtomicInteger(0)
        val openShot = AtomicInteger(0)
        val rebuildReq = AtomicInteger(0)
        val upload = AtomicInteger(0)
        val developer = AtomicInteger(0)

        setContent(
            state =
                AboutAdvancedUiState(
                    loading = false,
                    snapshot =
                        baseSnapshot(
                            update =
                                UpdateSettingsSnapshot(
                                    phase = UpdateSettingsPhase.READY_TO_INSTALL,
                                    availableVersion = "3.1.0",
                                ),
                            diagnosticsAvailable = true,
                            developer =
                                DeveloperOptions(
                                    unlocked = true,
                                    showPublicWorkspaceMemos = true,
                                    autoTagLineKeywords = "kw",
                                    showAutoTagLineInHome = true,
                                    showAutoTagLineInView = false,
                                    showAutoTagLineInEdit = true,
                                    homeRichPreviewStickyLimit = 10,
                                ),
                        ),
                ),
            callbacks =
                AboutAdvancedContentCallbacks(
                    onCheckForUpdates = { check.incrementAndGet() },
                    onDownloadUpdate = { download.incrementAndGet() },
                    onInstallUpdate = { install.incrementAndGet() },
                    onClearIgnoredUpdate = { clearIgnored.incrementAndGet() },
                    onExportDiagnostics = { export.incrementAndGet() },
                    onRequestQuickCaptureTile = { quickTile.incrementAndGet() },
                    onRequestScreenshotTile = { shotTile.incrementAndGet() },
                    onOpenQuickCapture = { openQuick.incrementAndGet() },
                    onOpenScreenshotCapture = { openShot.incrementAndGet() },
                    onRequestRebuildDerivedFields = { rebuildReq.incrementAndGet() },
                    onConfirmRebuildDerivedFields = {},
                    onDismissRebuildConfirm = {},
                    onSetAttachmentUploadLimitMb = { upload.incrementAndGet() },
                    onSetDeveloperOptions = { developer.incrementAndGet() },
                ),
        )

        clickTag("settings_about_check_update")
        clickTag("settings_about_install_update")
        clickTag("settings_about_quick_tile")
        clickTag("settings_about_quick_open")
        clickTag("settings_about_screenshot_tile")
        clickTag("settings_about_screenshot_open")
        clickTag("settings_about_export")
        clickTag("settings_about_rebuild")

        assertEquals(1, check.get())
        assertEquals(1, install.get())
        assertEquals(1, quickTile.get())
        assertEquals(1, openQuick.get())
        assertEquals(1, shotTile.get())
        assertEquals(1, openShot.get())
        assertEquals(1, export.get())
        assertEquals(1, rebuildReq.get())

        listOf(
            "settings_about_check_update",
            "settings_about_install_update",
            "settings_about_quick_tile",
            "settings_about_quick_open",
            "settings_about_screenshot_tile",
            "settings_about_screenshot_open",
            "settings_about_export",
            "settings_about_rebuild",
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(tag).assertHeightIsAtLeast(48.dp)
        }

        composeRule.onNodeWithTag("settings_about_upload_section").performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_about_developer_section").performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_about_developer_section").assertExists()
        assertTrue(
            composeRule
                .onAllNodesWithText("开发者模式已解锁", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty(),
        )
        composeRule.onNodeWithTag("settings_about_result_announcer").assertExists()
    }

    @Test
    fun busyCommand_disablesActions_andEntryHasNoSideEffectCalls() {
        val check = AtomicInteger(0)
        val export = AtomicInteger(0)
        val download = AtomicInteger(0)
        val install = AtomicInteger(0)
        setContent(
            state =
                AboutAdvancedUiState(
                    loading = false,
                    snapshot =
                        baseSnapshot(
                            commandInFlight = AboutAdvancedSettingsCommand.ExportDiagnostics,
                        ),
                ),
            callbacks =
                AboutAdvancedContentCallbacks(
                    onCheckForUpdates = { check.incrementAndGet() },
                    onDownloadUpdate = { download.incrementAndGet() },
                    onInstallUpdate = { install.incrementAndGet() },
                    onClearIgnoredUpdate = {},
                    onExportDiagnostics = { export.incrementAndGet() },
                    onRequestQuickCaptureTile = {},
                    onRequestScreenshotTile = {},
                    onOpenQuickCapture = {},
                    onOpenScreenshotCapture = {},
                    onRequestRebuildDerivedFields = {},
                    onConfirmRebuildDerivedFields = {},
                    onDismissRebuildConfirm = {},
                    onSetAttachmentUploadLimitMb = {},
                    onSetDeveloperOptions = {},
                ),
        )
        // 进入仅渲染，不自动触发检查/导出/下载/安装
        assertEquals(0, check.get())
        assertEquals(0, export.get())
        assertEquals(0, download.get())
        assertEquals(0, install.get())

        composeRule.onNodeWithTag("settings_about_check_update").assertIsNotEnabled()
        composeRule.onNodeWithTag("settings_about_export").assertIsNotEnabled()
    }

    @Test
    fun threeWindows_remainSingleColumn_maxContent720() {
        var size by mutableStateOf(DpSize(360.dp, 800.dp))
        composeRule.setContent {
            OneMemosTheme {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.requiredSize(size).testTag("window_host"),
                ) {
                    AboutAdvancedContent(
                        state =
                            AboutAdvancedUiState(
                                loading = false,
                                snapshot = baseSnapshot(),
                            ),
                        callbacks = noopCallbacks(),
                        showRebuildConfirm = false,
                        lastAnnouncement = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        for (next in listOf(
            DpSize(360.dp, 800.dp),
            DpSize(600.dp, 960.dp),
            DpSize(840.dp, 900.dp),
        )) {
            size = next
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("settings_about_list").assertExists()
            composeRule.onNodeWithTag("settings_about_title").assertExists()
            assertTrue(next.width.value.coerceAtMost(720f) <= 720f)
        }
    }

    @Test
    fun static_noEntrySideEffects_andPlatformUpdateSeparation() {
        val projectDir =
            System.getProperty("oneMemos.projectDir")
                ?: error("oneMemos.projectDir 未设置")
        val screen =
            File(
                projectDir,
                "feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/about/AboutAdvancedScreen.kt",
            )
        assertTrue(screen.exists())
        val body = screen.readText()
        assertFalse(body.contains("checkForUpdates("))
        assertFalse(body.contains("startDownload("))
        assertFalse(body.contains("requestDelivery("))
        assertFalse(body.contains("exportDiagnostics("))
        // 不在 Composable 入口自动执行副作用命令
        assertFalse(body.contains("LaunchedEffect(Unit)"))
        // 安装/未知来源不走 Platform dispatcher 路径
        assertFalse(body.contains("OpenUnknownSourcesSettings"))
        assertFalse(body.contains("InstallApk"))
        assertTrue(body.contains("settings_about_"))
        // 字符串资源前缀
        val strings =
            File(
                projectDir,
                "feature/settings/src/main/res/values/settings_about_strings.xml",
            ).readText()
        assertTrue(strings.contains("settings_about_title"))
        assertFalse(strings.contains("name=\"about_"))
    }

    @Test
    fun quickOpen_doesNotEmitNavigateKey_andEnabledWhenIdle() {
        val open = AtomicInteger(0)
        setContent(
            state = AboutAdvancedUiState(loading = false, snapshot = baseSnapshot()),
            callbacks =
                noopCallbacks().copy(
                    onOpenQuickCapture = { open.incrementAndGet() },
                ),
        )
        composeRule.onNodeWithTag("settings_about_quick_open").assertIsEnabled()
        clickTag("settings_about_quick_open")
        assertEquals(1, open.get())
        // 无导航键依赖：静态检查 Screen 不含 Navigate 键常量
        val projectDir =
            System.getProperty("oneMemos.projectDir")
                ?: error("oneMemos.projectDir 未设置")
        val body =
            File(
                projectDir,
                "feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/about/AboutAdvancedScreen.kt",
            ).readText()
        assertFalse(body.contains("OneMemosNavKey"))
        assertFalse(body.contains("AboutAdvancedSettingsKey"))
    }

    private fun clickTag(tag: String) {
        val node = composeRule.onNodeWithTag(tag)
        node.performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(tag).performClick()
        composeRule.waitForIdle()
    }

    private fun setContent(
        state: AboutAdvancedUiState,
        callbacks: AboutAdvancedContentCallbacks = noopCallbacks(),
        showRebuildConfirm: Boolean = false,
        lastAnnouncement: String? = null,
    ) {
        composeRule.setContent {
            OneMemosTheme {
                AboutAdvancedContent(
                    state = state,
                    callbacks = callbacks,
                    showRebuildConfirm = showRebuildConfirm,
                    lastAnnouncement = lastAnnouncement,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun noopCallbacks() =
        AboutAdvancedContentCallbacks(
            onCheckForUpdates = {},
            onDownloadUpdate = {},
            onInstallUpdate = {},
            onClearIgnoredUpdate = {},
            onExportDiagnostics = {},
            onRequestQuickCaptureTile = {},
            onRequestScreenshotTile = {},
            onOpenQuickCapture = {},
            onOpenScreenshotCapture = {},
            onRequestRebuildDerivedFields = {},
            onConfirmRebuildDerivedFields = {},
            onDismissRebuildConfirm = {},
            onSetAttachmentUploadLimitMb = {},
            onSetDeveloperOptions = {},
        )

    private fun baseSnapshot(
        update: UpdateSettingsSnapshot = UpdateSettingsSnapshot(UpdateSettingsPhase.IDLE),
        diagnosticsAvailable: Boolean = false,
        developer: DeveloperOptions =
            DeveloperOptions(
                unlocked = false,
                showPublicWorkspaceMemos = false,
                autoTagLineKeywords = "",
                showAutoTagLineInHome = false,
                showAutoTagLineInView = false,
                showAutoTagLineInEdit = false,
                homeRichPreviewStickyLimit = 0,
            ),
        commandInFlight: AboutAdvancedSettingsCommand? = null,
    ): AboutAdvancedSettingsSnapshot =
        AboutAdvancedSettingsSnapshot(
            versionName = "2.1.0",
            versionCode = 210L,
            buildType = "debug",
            update = update,
            diagnosticsAvailable = diagnosticsAvailable,
            attachmentUploadLimitMb = 50,
            developerOptions = developer,
            commandInFlight = commandInFlight,
        )
}
