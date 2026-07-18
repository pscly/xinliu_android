package cc.pscly.onememos.ui.theme

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * M1.9 最小 Roborazzi 冒烟：证明插件、Robolectric hardware 与 capture 路径可跑通。
 *
 * 录制：`./gradlew :core:designsystem:recordRoborazziDebug`
 * 校验：`./gradlew :core:designsystem:verifyRoborazziDebug`
 * 金图目录：`core/designsystem/src/test/screenshots/`（刻意只留 1 张，避免 binary 膨胀）
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h640dp-xxhdpi")
class RoborazziSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val roborazziRule = RoborazziRule(
        options = RoborazziRule.Options(
            outputDirectoryPath = "src/test/screenshots",
        ),
    )

    @Test
    fun paperInkLight_primaryLabel_captures() {
        composeRule.setContent {
            OneMemosTheme {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .padding(16.dp)
                        .testTag("roborazzi_smoke"),
                ) {
                    Text(
                        text = "文墨·朱砂",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
        composeRule.onNodeWithTag("roborazzi_smoke").captureRoboImage()
    }
}
