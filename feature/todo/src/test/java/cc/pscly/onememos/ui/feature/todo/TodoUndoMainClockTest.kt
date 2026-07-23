package cc.pscly.onememos.ui.feature.todo

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette
import cc.pscly.onememos.ui.theme.OneMemosTheme
import cc.pscly.onememos.ui.theme.OneMemosThemeConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 验证 TodoUndoHost 的时间驱动状态机：6s 自动过期 + 手动撤销 + 重新删除重置。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TodoUndoMainClockTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `case1 render advanceFrame InkCard displays`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            OneMemosTheme(config = themeConfig(dark = false)) {
                TodoUndoHost(
                    deletedItemId = "item1",
                    onUndo = {},
                    onExpired = {},
                )
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText("已删除 1 项").assertIsDisplayed()
        composeRule.onNodeWithText("撤销").assertIsDisplayed()
    }

    @Test
    fun `case2 advanceTimeBy 5999ms ignoreFrameDuration true still displayed`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            OneMemosTheme(config = themeConfig(dark = false)) {
                TodoUndoHost(
                    deletedItemId = "item1",
                    onUndo = {},
                    onExpired = {},
                )
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(5_999L, ignoreFrameDuration = true)
        composeRule.onNodeWithText("已删除 1 项").assertIsDisplayed()
    }

    @Test
    fun `case3 plus 1L advanceFrame waitForIdle gone onExpired called`() {
        composeRule.mainClock.autoAdvance = false
        var expiredId: String? = null
        composeRule.setContent {
            OneMemosTheme(config = themeConfig(dark = false)) {
                TodoUndoHost(
                    deletedItemId = "item1",
                    onUndo = {},
                    onExpired = { expiredId = it },
                )
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(5_999L, ignoreFrameDuration = true)
        composeRule.onNodeWithText("已删除 1 项").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(1L, ignoreFrameDuration = true)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("已删除 1 项").assertCountEquals(0)
        assertEquals("item1", expiredId)
    }

    @Test
    fun `case4 manual undo click onUndu NOT onExpired`() {
        composeRule.mainClock.autoAdvance = false
        var undoCalled = false
        var expiredCalled = false
        composeRule.setContent {
            OneMemosTheme(config = themeConfig(dark = false)) {
                TodoUndoHost(
                    deletedItemId = "item1",
                    onUndo = { undoCalled = true },
                    onExpired = { expiredCalled = true },
                )
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText("撤销").performClick()
        assertTrue(undoCalled)
        assertFalse(expiredCalled)
    }

    @Test
    fun `case5 id1 partial advance render id2 visibleId resets to id2 timer restarts for id2 not id1`() {
        composeRule.mainClock.autoAdvance = false
        var currentId by mutableStateOf<String?>("id1")
        var expired1 = false
        var expired2 = false

        composeRule.setContent {
            OneMemosTheme(config = themeConfig(dark = false)) {
                TodoUndoHost(
                    deletedItemId = currentId,
                    onUndo = {},
                    onExpired = { id ->
                        when (id) {
                            "id1" -> expired1 = true
                            "id2" -> expired2 = true
                        }
                    },
                )
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText("已删除 1 项").assertIsDisplayed()

        // 部分前进 3s 后切换为 id2
        composeRule.mainClock.advanceTimeBy(3_000L, ignoreFrameDuration = true)
        applyState { currentId = "id2" }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("已删除 1 项").assertIsDisplayed()

        // 从 id2 开始计时起，前进超过 6s
        composeRule.mainClock.advanceTimeBy(6_001L, ignoreFrameDuration = true)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        assertFalse(expired1) // id1 不应过期
        assertTrue(expired2) // id2 应过期
    }

    @Test
    fun `case6 same id redelete after expiry renders again`() {
        composeRule.mainClock.autoAdvance = false
        var currentId by mutableStateOf<String?>("item1")
        var expiredCount = 0

        composeRule.setContent {
            OneMemosTheme(config = themeConfig(dark = false)) {
                TodoUndoHost(
                    deletedItemId = currentId,
                    onUndo = {},
                    onExpired = {
                        expiredCount++
                        currentId = null
                    },
                )
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText("已删除 1 项").assertIsDisplayed()

        composeRule.mainClock.advanceTimeBy(6_001L, ignoreFrameDuration = true)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        assertEquals(1, expiredCount)
        composeRule.onAllNodesWithText("已删除 1 项").assertCountEquals(0)

        // 父级 null 边界后 same-id 重删：强制 Snapshot 通知 + 推进帧
        applyState { currentId = "item1" }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("已删除 1 项").assertIsDisplayed()

        composeRule.mainClock.advanceTimeBy(6_001L, ignoreFrameDuration = true)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        assertEquals(2, expiredCount)
    }

    /** autoAdvance=false 时，测试线程写 State 后需 sendApplyNotifications 才能触发重组。 */
    private inline fun applyState(block: () -> Unit) {
        block()
        Snapshot.sendApplyNotifications()
    }

    private fun themeConfig(dark: Boolean) =
        OneMemosThemeConfig(
            palette = ThemePalette.PAPER_INK,
            themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
        )
}
