package cc.pscly.onememos.ui.feature.auth

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
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
 * Auth 截图矩阵：4 配置 × 2 主题 = 8 金图。
 * 录制：./gradlew :feature:auth:recordRoborazziDebug
 * 校验：./gradlew :feature:auth:verifyRoborazziDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class AuthScreenRoborazziTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val roborazziRule =
        RoborazziRule(
            options = RoborazziRule.Options(outputDirectoryPath = "src/test/screenshots"),
        )

    private val fixture =
        AuthUiState(
            error = "测试用错误消息",
            tab = AuthTab.BACKEND,
            username = "testuser",
            password = "",
        )

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun auth_compact_light() = capture(dark = false, fontScale = 1f)

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun auth_compact_dark() = capture(dark = true, fontScale = 1f)

    @Test
    @Config(qualifiers = "w600dp-h840dp-xxhdpi")
    fun auth_expanded_light() = capture(dark = false, fontScale = 1f)

    @Test
    @Config(qualifiers = "w600dp-h840dp-xxhdpi")
    fun auth_expanded_dark() = capture(dark = true, fontScale = 1f)

    @Test
    @Config(qualifiers = "w360dp-h250dp-xxhdpi")
    fun auth_compactHeight_light() = capture(dark = false, fontScale = 1f)

    @Test
    @Config(qualifiers = "w360dp-h250dp-xxhdpi")
    fun auth_compactHeight_dark() = capture(dark = true, fontScale = 1f)

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun auth_largeFont_light() = capture(dark = false, fontScale = 2f)

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun auth_largeFont_dark() = capture(dark = true, fontScale = 2f)

    private fun capture(dark: Boolean, fontScale: Float) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = fontScale)) {
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
                        AuthScreenContent(
                            uiState = fixture,
                            onBack = {},
                            onTabSelected = {},
                            onUsernameChanged = {},
                            onPasswordChanged = {},
                            onConfirmPasswordChanged = {},
                            onPasswordVisibleToggle = {},
                            onBackendFormSwitch = {},
                            onBackendSubmit = {},
                            onTokenChanged = {},
                            onTokenVisibleToggle = {},
                            onCustomServerUrlChanged = {},
                            onCustomSave = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(HOST_TAG).captureRoboImage()
    }

    companion object {
        private const val HOST_TAG = "auth_roborazzi_host"
    }
}
