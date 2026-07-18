package cc.pscly.onememos.ui.theme

import android.app.Application
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.test.junit4.createComposeRule
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * M2.10：M3 组件纸墨令牌断言。
 *
 * 两类检查：
 * 1. 包装默认值（[PaperInkComponentDefaults]）= 主题色板令牌，不允许漂移；
 * 2. 组件关键槽位与 stock M3 默认值（`lightColorScheme()`/`darkColorScheme()` 裸构造）
 *    不同 —— 防止未覆盖槽位把 M3 默认蓝紫带回来。
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PaperInkComponentsTokenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val lightScheme = oneMemosLightColorScheme(ThemePalette.PAPER_INK)
    private val darkScheme = oneMemosDarkColorScheme(ThemePalette.PAPER_INK)

    // ---------- 包装默认值 = 色板令牌 ----------

    @Test
    fun topAppBarColors_matchThemeTokens_light() = assertTopAppBarColors(dark = false)

    @Test
    fun topAppBarColors_matchThemeTokens_dark() = assertTopAppBarColors(dark = true)

    private fun assertTopAppBarColors(dark: Boolean) {
        var scheme: ColorScheme? = null
        var colors: TopAppBarColors? = null
        composeRule.setContent {
            OneMemosTheme(config = themeConfig(dark)) {
                scheme = MaterialTheme.colorScheme
                colors = PaperInkComponentDefaults.topAppBarColors()
            }
        }
        composeRule.waitForIdle()
        assertEquals(scheme!!.surface, colors!!.containerColor)
        assertEquals(scheme.surface, colors.scrolledContainerColor)
        assertEquals(scheme.onSurface, colors.titleContentColor)
        assertEquals(scheme.onSurface, colors.navigationIconContentColor)
        assertEquals(scheme.onSurface, colors.actionIconContentColor)
    }

    @Test
    fun snackbarColors_matchInverseTokens() {
        var scheme: ColorScheme? = null
        var container: Color? = null
        var content: Color? = null
        var action: Color? = null
        composeRule.setContent {
            OneMemosTheme(config = themeConfig(dark = false)) {
                scheme = MaterialTheme.colorScheme
                container = PaperInkComponentDefaults.snackbarContainerColor()
                content = PaperInkComponentDefaults.snackbarContentColor()
                action = PaperInkComponentDefaults.snackbarActionColor()
            }
        }
        composeRule.waitForIdle()
        assertEquals(scheme!!.inverseSurface, container)
        assertEquals(scheme.inverseOnSurface, content)
        assertEquals(scheme.inversePrimary, action)
    }

    @Test
    fun dialogDefaults_matchTokensAndInkShape() {
        var scheme: ColorScheme? = null
        var wrapperShape: Shape? = null
        var m3DefaultShape: Shape? = null
        var wrapperContainer: Color? = null
        var m3DefaultContainer: Color? = null
        composeRule.setContent {
            OneMemosTheme(config = themeConfig(dark = false)) {
                scheme = MaterialTheme.colorScheme
                wrapperShape = PaperInkComponentDefaults.dialogShape
                // 全局路径：M3 AlertDialog 默认形状直接读 MaterialTheme.shapes.extraLarge
                m3DefaultShape = AlertDialogDefaults.shape
                wrapperContainer = PaperInkComponentDefaults.dialogContainerColor()
                m3DefaultContainer = AlertDialogDefaults.containerColor
            }
        }
        composeRule.waitForIdle()
        assertEquals(InkShape.Card, wrapperShape)
        assertEquals(InkShape.Card, m3DefaultShape)
        assertEquals(scheme!!.surfaceContainerHigh, wrapperContainer)
        assertEquals(scheme.surfaceContainerHigh, m3DefaultContainer)
    }

    @Test
    fun bottomSheetDefaults_matchTokens() {
        var scheme: ColorScheme? = null
        var container: Color? = null
        var dragHandle: Color? = null
        composeRule.setContent {
            OneMemosTheme(config = themeConfig(dark = false)) {
                scheme = MaterialTheme.colorScheme
                container = PaperInkComponentDefaults.bottomSheetContainerColor()
                dragHandle = PaperInkComponentDefaults.bottomSheetDragHandleColor()
            }
        }
        composeRule.waitForIdle()
        assertEquals(scheme!!.surfaceContainerLow, container)
        assertEquals(scheme.outlineVariant, dragHandle)
    }

    // ---------- 无 M3 默认配色残留 ----------

    @Test
    fun componentSlots_divergeFromStockM3Defaults() {
        val stockLight = lightColorScheme()
        val stockDark = darkColorScheme()

        // TopAppBar 滚动态容器、Dialog 高容器、BottomSheet 低容器
        assertNotEquals(stockLight.surfaceContainer, lightScheme.surfaceContainer)
        assertNotEquals(stockDark.surfaceContainer, darkScheme.surfaceContainer)
        assertNotEquals(stockLight.surfaceContainerHigh, lightScheme.surfaceContainerHigh)
        assertNotEquals(stockDark.surfaceContainerHigh, darkScheme.surfaceContainerHigh)
        assertNotEquals(stockLight.surfaceContainerLow, lightScheme.surfaceContainerLow)
        assertNotEquals(stockDark.surfaceContainerLow, darkScheme.surfaceContainerLow)

        // Snackbar 反色三件套
        assertNotEquals(stockLight.inverseSurface, lightScheme.inverseSurface)
        assertNotEquals(stockDark.inverseSurface, darkScheme.inverseSurface)
        assertNotEquals(stockLight.inverseOnSurface, lightScheme.inverseOnSurface)
        assertNotEquals(stockDark.inverseOnSurface, darkScheme.inverseOnSurface)

        // 拖拽柄目标令牌
        assertNotEquals(stockLight.outlineVariant, lightScheme.outlineVariant)
        assertNotEquals(stockDark.outlineVariant, darkScheme.outlineVariant)
    }

    @Test
    fun curatedPalettes_noStockSlotResidue() {
        // 所有策展色板（非 DYNAMIC）不得残留任何 stock M3 表面阶
        val stockLight = lightColorScheme()
        val stockDark = darkColorScheme()
        val slots: List<Pair<String, (ColorScheme) -> Color>> = listOf(
            "surfaceContainer" to { it.surfaceContainer },
            "surfaceContainerHigh" to { it.surfaceContainerHigh },
            "surfaceContainerLow" to { it.surfaceContainerLow },
            "surfaceVariant" to { it.surfaceVariant },
            "inverseSurface" to { it.inverseSurface },
            "inverseOnSurface" to { it.inverseOnSurface },
            "outlineVariant" to { it.outlineVariant },
        )
        for (palette in ThemePalette.entries.filter { it != ThemePalette.DYNAMIC }) {
            val light = oneMemosLightColorScheme(palette)
            val dark = oneMemosDarkColorScheme(palette)
            for ((name, pick) in slots) {
                assertNotEquals("${palette.name}/light $name 残留 stock 默认值", pick(stockLight), pick(light))
                assertNotEquals("${palette.name}/dark $name 残留 stock 默认值", pick(stockDark), pick(dark))
            }
        }
    }

    private fun themeConfig(dark: Boolean) = OneMemosThemeConfig(
        palette = ThemePalette.PAPER_INK,
        themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
    )
}
