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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette
import cc.pscly.onememos.ui.component.InkChip
import cc.pscly.onememos.ui.component.TagChip
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 纸墨系统组件 + Chip 截图矩阵（UI 债务收口 Todo 3）。
 *
 * - matrix：TopAppBar / Snackbar / BottomSheet 表面 / Dialog 令牌面 / TagChip / InkChip
 * - dialog：[PaperInkAlertDialog] 显式包装 + 全局默认 AlertDialog 令牌路径
 * - 矩阵覆盖 light、dark、fontScale=2.0
 *
 * 录制：`./gradlew :core:designsystem:recordRoborazziDebug`
 * 校验：`./gradlew :core:designsystem:verifyRoborazziDebug`
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h640dp-xxhdpi")
class PaperInkComponentsScreenshotTest {

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
    fun paperInkComponents_light_matrix() {
        captureMatrix(dark = false, fontScale = 1f)
    }

    @Test
    fun paperInkComponents_dark_matrix() {
        captureMatrix(dark = true, fontScale = 1f)
    }

    @Test
    fun paperInkComponents_largeFont_matrix() {
        captureMatrix(dark = false, fontScale = 2f)
    }

    @Test
    fun paperInkAlertDialog_light_captures() {
        composeRule.setContent {
            OneMemosTheme(config = themeConfig(dark = false)) {
                Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                    PaperInkAlertDialog(
                        onDismissRequest = {},
                        confirmButton = { TextButton(onClick = {}) { Text("确定") } },
                        dismissButton = { TextButton(onClick = {}) { Text("取消") } },
                        title = { Text("归档这条备忘？") },
                        text = {
                            Text(
                                "归档后可在归档页找回。",
                                modifier = Modifier.testTag(PAPER_INK_DIALOG_TAG),
                            )
                        },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PAPER_INK_DIALOG_TAG).captureRoboImage()
    }

    @Test
    fun defaultAlertDialog_tokenSurface_light_captures() {
        // 保留全局默认 AlertDialog 路径：形状/颜色走主题令牌，不强制全量改包装。
        composeRule.setContent {
            OneMemosTheme(config = themeConfig(dark = false)) {
                Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                    AlertDialog(
                        onDismissRequest = {},
                        confirmButton = { TextButton(onClick = {}) { Text("确定") } },
                        dismissButton = { TextButton(onClick = {}) { Text("取消") } },
                        title = { Text("删除这条备忘？") },
                        text = {
                            Text(
                                "删除后不可恢复。",
                                modifier = Modifier.testTag(DEFAULT_DIALOG_TAG),
                            )
                        },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(DEFAULT_DIALOG_TAG).captureRoboImage()
    }

    private fun captureMatrix(
        dark: Boolean,
        fontScale: Float,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = fontScale),
            ) {
                OneMemosTheme(config = themeConfig(dark = dark)) {
                    ComponentMatrix()
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(MATRIX_TAG).captureRoboImage()
    }

    @Composable
    private fun ComponentMatrix() {
        Column(
            modifier =
                Modifier
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

            // Snackbar 令牌面：与 PaperInkSnackbar 同色，避免依赖 SnackbarData 宿主。
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

            // Chip 矩阵：可点击/静态 TagChip + 选中/未选/禁用 InkChip
            // 外层 48dp 触控区不得把可见色块撑成巨型胶囊。
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = InkSpacing.CardPadding),
                verticalArrangement = Arrangement.spacedBy(InkSpacing.X8),
            ) {
                Text(
                    text = "标签与筛选",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(InkSpacing.X8)) {
                    TagChip(tag = "工作", selected = false, onClick = {})
                    TagChip(tag = "灵感", selected = true, onClick = {})
                    TagChip(tag = "静态")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(InkSpacing.X8)) {
                    InkChip(label = "全部", selected = false, onClick = {})
                    InkChip(label = "进行中", selected = true, onClick = {})
                    InkChip(label = "禁用", selected = false, onClick = {}, enabled = false)
                }
            }
        }
    }

    private fun themeConfig(dark: Boolean) =
        OneMemosThemeConfig(
            palette = ThemePalette.PAPER_INK,
            themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
        )

    private companion object {
        const val MATRIX_TAG = "paper_ink_component_matrix"
        const val PAPER_INK_DIALOG_TAG = "paper_ink_dialog"
        const val DEFAULT_DIALOG_TAG = "default_token_dialog"
    }
}
