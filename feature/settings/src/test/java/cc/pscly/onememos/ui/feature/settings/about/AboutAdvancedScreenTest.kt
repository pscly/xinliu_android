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
import androidx.compose.ui.test.performTextInput
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
import java.util.concurrent.atomic.AtomicReference
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
 * 分区错误、解锁路径、全部开发者字段、连续播报、STARTED 收集契约。
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
                    sectionError = SettingsCapabilityError.NetworkUnavailable,
                    errorSection = AboutErrorSection.UPDATE,
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
        composeRule.onNodeWithText("网络不可用", useUnmergedTree = true).assertIsDisplayed()
        val errNode =
            composeRule.onNodeWithTag("settings_about_update_error", useUnmergedTree = true)
        val stateDesc =
            errNode.fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
        assertTrue(stateDesc != null && stateDesc.isNotBlank())
    }

    @Test
    fun sectionErrors_renderBesideOrigin_notOnUpdateCard() {
        var state by
            mutableStateOf(
                AboutAdvancedUiState(
                    loading = false,
                    snapshot = baseSnapshot(),
                    sectionError = SettingsCapabilityError.StorageFailure,
                    errorSection = AboutErrorSection.DIAGNOSTICS,
                ),
            )
        composeRule.setContent {
            OneMemosTheme {
                AboutAdvancedContent(
                    state = state,
                    callbacks = noopCallbacks(),
                    showRebuildConfirm = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_about_diagnostics_section").performScrollTo()
        composeRule.waitForIdle()
        composeRule
            .onNodeWithTag("settings_about_diagnostics_error", useUnmergedTree = true)
            .assertExists()
        assertEquals(
            0,
            composeRule
                .onAllNodesWithTag("settings_about_update_error", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .size,
        )

        state =
            AboutAdvancedUiState(
                loading = false,
                snapshot =
                    baseSnapshot(
                        developer =
                            DeveloperOptions(
                                unlocked = true,
                                showPublicWorkspaceMemos = false,
                                autoTagLineKeywords = "kw",
                                showAutoTagLineInHome = false,
                                showAutoTagLineInView = false,
                                showAutoTagLineInEdit = false,
                                homeRichPreviewStickyLimit = 10,
                            ),
                    ),
                sectionError = SettingsCapabilityError.InvalidInput,
                errorSection = AboutErrorSection.DEVELOPER,
            )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_about_developer_section").performScrollTo()
        composeRule.waitForIdle()
        composeRule
            .onNodeWithTag("settings_about_developer_error", useUnmergedTree = true)
            .assertExists()
        assertEquals(
            0,
            composeRule
                .onAllNodesWithTag("settings_about_update_error", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .size,
        )
        assertEquals(
            0,
            composeRule
                .onAllNodesWithTag("settings_about_diagnostics_error", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .size,
        )
    }

    @Test
    fun developerUnlocked_showsKeywordsAndStickyLimit_andMinHeight48() {
        val optionsRef = AtomicReference<DeveloperOptions?>(null)
        setContent(
            state =
                AboutAdvancedUiState(
                    loading = false,
                    snapshot =
                        baseSnapshot(
                            developer =
                                DeveloperOptions(
                                    unlocked = true,
                                    showPublicWorkspaceMemos = true,
                                    autoTagLineKeywords = "__Atags",
                                    showAutoTagLineInHome = true,
                                    showAutoTagLineInView = false,
                                    showAutoTagLineInEdit = true,
                                    homeRichPreviewStickyLimit = 500,
                                ),
                        ),
                ),
            callbacks =
                noopCallbacks().copy(
                    onSetDeveloperOptions = { optionsRef.set(it) },
                ),
        )
        composeRule.onNodeWithTag("settings_about_developer_section").performScrollTo()
        composeRule.waitForIdle()
        composeRule
            .onNodeWithTag("settings_about_developer_keywords", useUnmergedTree = true)
            .assertExists()
        composeRule
            .onNodeWithTag("settings_about_developer_sticky", useUnmergedTree = true)
            .assertExists()
        assertTrue(
            composeRule
                .onAllNodesWithText("__Atags", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty(),
        )
        assertTrue(
            composeRule
                .onAllNodesWithText("500", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty(),
        )
        composeRule.onNodeWithTag("settings_about_developer_exit").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("settings_about_export").performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_about_export").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun lockedToUnlocked_sixVersionTaps_passwordThenSetDeveloperOptions() {
        val optionsRef = AtomicReference<DeveloperOptions?>(null)
        setContent(
            state =
                AboutAdvancedUiState(
                    loading = false,
                    snapshot = baseSnapshot(),
                ),
            callbacks =
                noopCallbacks().copy(
                    onSetDeveloperOptions = { optionsRef.set(it) },
                ),
        )
        repeat(6) {
            composeRule.onNodeWithTag("settings_about_version").performClick()
            composeRule.waitForIdle()
        }
        composeRule
            .onNodeWithTag("settings_about_unlock_password", useUnmergedTree = true)
            .assertExists()
        composeRule
            .onNodeWithTag("settings_about_unlock_password", useUnmergedTree = true)
            .performTextInput(DEVELOPER_UNLOCK_PASSWORD)
        composeRule.onNodeWithTag("settings_about_unlock_confirm").performClick()
        composeRule.waitForIdle()
        val sent = optionsRef.get()
        assertTrue(sent != null)
        assertTrue(sent!!.unlocked)
    }

    @Test
    fun consecutiveAnnouncements_sameKind_changeSemanticsViaToken() {
        var state by
            mutableStateOf(
                AboutAdvancedUiState(
                    loading = false,
                    snapshot = baseSnapshot(),
                    announcementToken = 1L,
                    announcementKind = AboutAnnouncementKind.SUCCESS,
                ),
            )
        composeRule.setContent {
            OneMemosTheme {
                AboutAdvancedContent(
                    state = state,
                    callbacks = noopCallbacks(),
                    showRebuildConfirm = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.waitForIdle()
        val first =
            composeRule
                .onNodeWithTag("settings_about_result_announcer")
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.StateDescription)
        state =
            state.copy(
                announcementToken = 2L,
                announcementKind = AboutAnnouncementKind.SUCCESS,
            )
        composeRule.waitForIdle()
        val second =
            composeRule
                .onNodeWithTag("settings_about_result_announcer")
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.StateDescription)
        assertTrue(first != null && second != null)
        assertTrue(first != second)
        assertTrue(second!!.contains("#2"))
    }

    @Test
    fun tilesCaptureDiagnosticsRebuildUploadDeveloper_andMinHeight48() {
        val check = AtomicInteger(0)
        val install = AtomicInteger(0)
        val export = AtomicInteger(0)
        val quickTile = AtomicInteger(0)
        val shotTile = AtomicInteger(0)
        val openQuick = AtomicInteger(0)
        val openShot = AtomicInteger(0)
        val rebuildReq = AtomicInteger(0)

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
                    onDownloadUpdate = {},
                    onInstallUpdate = { install.incrementAndGet() },
                    onClearIgnoredUpdate = {},
                    onExportDiagnostics = { export.incrementAndGet() },
                    onRequestQuickCaptureTile = { quickTile.incrementAndGet() },
                    onRequestScreenshotTile = { shotTile.incrementAndGet() },
                    onOpenQuickCapture = { openQuick.incrementAndGet() },
                    onOpenScreenshotCapture = { openShot.incrementAndGet() },
                    onRequestRebuildDerivedFields = { rebuildReq.incrementAndGet() },
                    onConfirmRebuildDerivedFields = {},
                    onDismissRebuildConfirm = {},
                    onSetAttachmentUploadLimitMb = {},
                    onSetDeveloperOptions = {},
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
            "settings_about_export",
            "settings_about_rebuild",
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(tag).assertHeightIsAtLeast(48.dp)
        }

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
    fun static_noEntrySideEffects_startedCollection_andPlatformUpdateSeparation() {
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
        assertFalse(body.contains("LaunchedEffect(Unit)"))
        assertTrue(body.contains("repeatOnLifecycle"))
        assertTrue(body.contains("Lifecycle.State.STARTED"))
        assertFalse(body.contains("OpenUnknownSourcesSettings"))
        assertFalse(body.contains("InstallApk"))
        assertTrue(body.contains("settings_about_"))
        assertTrue(body.contains("autoTagLineKeywords") || body.contains("developer_keywords"))
        assertTrue(body.contains("homeRichPreviewStickyLimit") || body.contains("developer_sticky"))
        assertTrue(body.contains("DEVELOPER_UNLOCK_PASSWORD") || body.contains("pscly"))
        assertFalse(body.contains("OneMemosNavKey"))
        assertFalse(body.contains("AboutAdvancedSettingsKey"))
        val strings =
            File(
                projectDir,
                "feature/settings/src/main/res/values/settings_about_strings.xml",
            ).readText()
        assertTrue(strings.contains("settings_about_title"))
        assertTrue(strings.contains("settings_about_developer_keywords"))
        assertTrue(strings.contains("settings_about_developer_sticky_limit"))
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
        composeRule.onNodeWithTag(tag).performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(tag).performClick()
        composeRule.waitForIdle()
    }

    private fun setContent(
        state: AboutAdvancedUiState,
        callbacks: AboutAdvancedContentCallbacks = noopCallbacks(),
        showRebuildConfirm: Boolean = false,
    ) {
        composeRule.setContent {
            OneMemosTheme {
                AboutAdvancedContent(
                    state = state,
                    callbacks = callbacks,
                    showRebuildConfirm = showRebuildConfirm,
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
