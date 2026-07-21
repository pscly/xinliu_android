package cc.pscly.onememos.ui.feature.home

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.domain.model.MemoAttachment
import cc.pscly.onememos.domain.model.MemoServerState
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.SyncStatus
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette
import cc.pscly.onememos.ui.theme.OneMemosTheme
import cc.pscly.onememos.ui.theme.OneMemosThemeConfig
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Home MemoItem 截图矩阵（UI 债务收口 Todo 10）。
 *
 * 覆盖 light / dark / fontScale=2.0；样本含正文、标签、附件计数、失败同步与更多操作。
 * 不加载网络图/动态时间；shared bounds scope 缺省 null 时 no-op。
 *
 * 录制：`./gradlew :feature:home:recordRoborazziDebug`
 * 校验：`./gradlew :feature:home:verifyRoborazziDebug`
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h640dp-xxhdpi")
class MemoItemScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val roborazziRule =
        RoborazziRule(
            options =
                RoborazziRule.Options(
                    outputDirectoryPath = "src/test/screenshots",
                ),
        )

    @Test
    fun memoItem_light_captures() {
        capture(dark = false, fontScale = 1f)
    }

    @Test
    fun memoItem_dark_captures() {
        capture(dark = true, fontScale = 1f)
    }

    @Test
    fun memoItem_largeFont_captures() {
        capture(dark = false, fontScale = 2f)
    }

    private fun capture(
        dark: Boolean,
        fontScale: Float,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = fontScale),
            ) {
                OneMemosTheme(
                    config =
                        OneMemosThemeConfig(
                            palette = ThemePalette.PAPER_INK,
                            themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
                        ),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .background(MaterialTheme.colorScheme.background)
                                .padding(12.dp)
                                .fillMaxWidth()
                                .testTag(HOST_TAG),
                    ) {
                        // LocalMemoSharedTransitionScope 缺省 null → memoSharedBounds no-op
                        MemoItem(
                            memo = sampleMemo(),
                            serverBase = null,
                            devKeywordsRaw = "",
                            showAutoTagLineInHome = true,
                            enableRichPreview = false,
                            selectionMode = false,
                            selected = false,
                            onOpenMemo = {},
                            onLongShare = {},
                            onToggleTag = {},
                            onMoreActions = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(HOST_TAG).captureRoboImage()
    }

    private fun sampleMemo(): Memo =
        Memo(
            uuid = "screenshot-memo-1",
            serverId = "srv-1",
            creator = null,
            content = "今日记录：完成纸墨 Chip 触控收口，并核对列表→编辑转场。#工作 #灵感",
            plainPreview = "今日记录：完成纸墨 Chip 触控收口，并核对列表→编辑转场。",
            tags = listOf("工作", "灵感"),
            // 固定时间戳，避免截图中日期漂移
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L,
            serverState = MemoServerState.NORMAL,
            visibility = MemoVisibility.PRIVATE,
            pinned = false,
            syncStatus = SyncStatus.FAILED,
            attachments =
                listOf(
                    MemoAttachment(
                        id = 1L,
                        localUri = null,
                        cacheUri = null,
                        remoteName = null,
                        filename = "notes.pdf",
                        mimeType = "application/pdf",
                        createdAt = 1_700_000_000_000L,
                    ),
                ),
            lastSyncError = "网络超时",
        )

    private companion object {
        const val HOST_TAG = "memo_item_screenshot_host"
    }
}
