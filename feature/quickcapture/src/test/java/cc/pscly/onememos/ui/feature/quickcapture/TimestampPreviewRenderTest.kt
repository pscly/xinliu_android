package cc.pscly.onememos.ui.feature.quickcapture

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette
import cc.pscly.onememos.ui.theme.OneMemosTheme
import cc.pscly.onememos.ui.theme.OneMemosThemeConfig
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 视觉验证工具（**默认跳过**，不参与金图基线）：把“输入框里已带自动时间戳”的真实渲染
 * 结果写成 PNG，供人工确认“浅色半透明”的实际观感。
 *
 * 为什么默认跳过：CI 会跑 `verifyRoborazziDebug`，本类没有金图基线，若默认执行会误报失败。
 *
 * 需要看图时手动执行：
 * ```
 * .\gradlew.bat :feature:quickcapture:recordRoborazziDebug `
 *   --tests "*TimestampPreviewRenderTest" "-Dtimestamp.preview=true"
 * ```
 * 产物写到 `build/timestamp-preview/`（已在 .gitignore 内），不覆盖 `src/test/screenshots`。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class TimestampPreviewRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun requireOptIn() {
        // 默认跳过：CI 的 verifyRoborazziDebug 不应执行本类（没有金图基线）。
        assumeTrue(
            "时间戳预览渲染默认跳过；需要看图请加 -Dtimestamp.preview=true",
            System.getProperty("timestamp.preview") == "true",
        )
    }

    private fun onlyStampState() =
        QuickCaptureUiState(
            content = TextFieldValue("> 2026-11-02 04:53:29\n\n"),
            error = null,
            draftBannerVisible = false,
            draftOverwriteDialogVisible = false,
        )

    private fun stampWithBodyState() =
        QuickCaptureUiState(
            content = TextFieldValue("> 2026-11-02 04:53:29\n\n今天试了一下新的时间戳显示，应该比之前清爽。"),
            error = null,
            draftBannerVisible = false,
            draftOverwriteDialogVisible = false,
        )

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun preview_onlyStamp_light() = render(dark = false, state = onlyStampState(), name = "only_stamp_light")

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun preview_stampWithBody_light() = render(dark = false, state = stampWithBodyState(), name = "stamp_with_body_light")

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun preview_stampWithBody_dark() = render(dark = true, state = stampWithBodyState(), name = "stamp_with_body_dark")

    private fun render(
        dark: Boolean,
        state: QuickCaptureUiState,
        name: String,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1f)) {
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
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .testTag(HOST_TAG),
                    ) {
                        QuickCaptureScreen(
                            uiState = state,
                            focusRequester = FocusRequester(),
                            onClose = {},
                            onContentChange = {},
                            onInsertTime = {},
                            onSave = {},
                            onEditPrevious = {},
                            onRefreshHistory = {},
                            onLoadForEdit = {},
                            onRestoreDraft = {},
                            onClearDraft = {},
                            onConfirmOverwrite = {},
                            onDismissOverwrite = {},
                            showStamp = false,
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(HOST_TAG)
            .captureRoboImage("build/timestamp-preview/$name.png")
    }

    companion object {
        private const val HOST_TAG = "ts_preview_host"
    }
}
