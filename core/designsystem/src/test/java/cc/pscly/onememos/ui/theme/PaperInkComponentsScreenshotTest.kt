package cc.pscly.onememos.ui.theme

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * M2.10：M3 组件纸墨化截图。
 *
 * - matrix：TopAppBar / Snackbar / BottomSheet 拖拽柄+容器 / Dialog 令牌表面（明暗各一）
 * - dialog：真实 [AlertDialog] 弹窗，验证全局 shapes/colorScheme 路径无 M3 默认残留
 *
 * 录制：`./gradlew :core:designsystem:recordRoborazziDebug`
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h640dp-xxhdpi")
class PaperInkComponentsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val roborazziRule = RoborazziRule(
        options = RoborazziRule.Options(
            outputDirectoryPath = "src/test/screenshots",
        ),
    )

    @Test
    fun paperInkComponents_light_matrix() {
        composeRule.setContent {
            OneMemosTheme(config = themeConfig(dark = false)) {
                ComponentMatrix()
            }
        }
        composeRule.onNodeWithTag(MATRIX_TAG).captureRoboImage()
    }

    @Test
    fun paperInkComponents_dark_matrix() {
        composeRule.setContent {
            OneMemosTheme(config = themeConfig(dark = true)) {
                ComponentMatrix()
            }
        }
        composeRule.onNodeWithTag(MATRIX_TAG).captureRoboImage()
    }

    @Test
    fun paperInkAlertDialog_light_captures() {
        composeRule.setContent {
            OneMemosTheme(config = themeConfig(dark = false)) {
                Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                    AlertDialog(
                        onDismissRequest = {},
                        confirmButton = { TextButton(onClick = {}) { Text("确定") } },
                        dismissButton = { TextButton(onClick = {}) { Text("取消") } },
                        title = { Text("归档这条备忘？") },
                        text = {
                            Text(
                                "归档后可在归档页找回。",
                                modifier = Modifier.testTag(DIALOG_TAG),
                            )
                        },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(DIALOG_TAG).captureRoboImage()
    }

    @Composable
    private fun ComponentMatrix() {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .testTag(MATRIX_TAG),
            verticalArrangement = Arrangement.spacedBy(InkSpacing.CardPadding),
        ) {
            PaperInkTopAppBar(
                title = { Text("文墨备忘") },
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                    }
                },
            )

            Snackbar(
                modifier = Modifier.padding(horizontal = InkSpacing.CardPadding),
                containerColor = PaperInkComponentDefaults.snackbarContainerColor(),
                contentColor = PaperInkComponentDefaults.snackbarContentColor(),
                actionContentColor = PaperInkComponentDefaults.snackbarActionColor(),
                action = {
                    Text(
                        text = "撤销",
                        color = PaperInkComponentDefaults.snackbarActionColor(),
                    )
                },
            ) {
                Text("已归档 1 条备忘")
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = PaperInkComponentDefaults.bottomSheetContainerColor(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = InkSpacing.CardPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BottomSheetDefaults.DragHandle(
                        color = PaperInkComponentDefaults.bottomSheetDragHandleColor(),
                    )
                    Text(
                        text = "移动到处…",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = InkSpacing.CardPadding),
                shape = AlertDialogDefaults.shape,
                color = AlertDialogDefaults.containerColor,
            ) {
                Column(Modifier.padding(InkSpacing.CardPadding)) {
                    Text(
                        text = "对话框令牌面",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = {}) { Text("取消") }
                        TextButton(onClick = {}) { Text("确定") }
                    }
                }
            }
        }
    }

    private fun themeConfig(dark: Boolean) = OneMemosThemeConfig(
        palette = ThemePalette.PAPER_INK,
        themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
    )

    private companion object {
        const val MATRIX_TAG = "paper_ink_component_matrix"
        const val DIALOG_TAG = "paper_ink_dialog"
    }
}
