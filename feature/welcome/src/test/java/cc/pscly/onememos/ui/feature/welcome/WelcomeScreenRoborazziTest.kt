package cc.pscly.onememos.ui.feature.welcome

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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class WelcomeScreenRoborazziTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val roborazziRule =
        RoborazziRule(
            options = RoborazziRule.Options(outputDirectoryPath = "src/test/screenshots"),
        )

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun welcome_compact_light() = capture(dark = false, fontScale = 1f)

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun welcome_compact_dark() = capture(dark = true, fontScale = 1f)

    @Test
    @Config(qualifiers = "w600dp-h840dp-xxhdpi")
    fun welcome_expanded_light() = capture(dark = false, fontScale = 1f)

    @Test
    @Config(qualifiers = "w600dp-h840dp-xxhdpi")
    fun welcome_expanded_dark() = capture(dark = true, fontScale = 1f)

    @Test
    @Config(qualifiers = "w360dp-h250dp-xxhdpi")
    fun welcome_compactHeight_light() = capture(dark = false, fontScale = 1f)

    @Test
    @Config(qualifiers = "w360dp-h250dp-xxhdpi")
    fun welcome_compactHeight_dark() = capture(dark = true, fontScale = 1f)

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun welcome_largeFont_light() = capture(dark = false, fontScale = 2f)

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun welcome_largeFont_dark() = capture(dark = true, fontScale = 2f)

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
                        WelcomeScreenContent(onEnterLocal = {}, onGoBindServer = {})
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(HOST_TAG).captureRoboImage()
    }

    companion object {
        private const val HOST_TAG = "welcome_roborazzi_host"
    }
}
