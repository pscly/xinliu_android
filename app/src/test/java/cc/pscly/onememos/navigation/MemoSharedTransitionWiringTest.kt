package cc.pscly.onememos.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * Home ACTIVE 已有 memo ↔ Editor 内容根 shared bounds 接线契约。
 *
 * 断言：
 * - ACTIVE source / Editor non-null target 各恰一处 memoSharedBounds 接线面
 * - Archived / EditorKey(null) / 异步 uiState.uuid 不得作为配对源
 * - Reduced Motion 由 Todo 1 LocalMemoSharedTransitionScope 统一门控
 */
class MemoSharedTransitionWiringTest {
    companion object {
        private lateinit var projectDir: File
        private lateinit var memoItem: String
        private lateinit var homeScreen: String
        private lateinit var editorScreen: String
        private lateinit var editorEntry: String
        private lateinit var host: String
        private lateinit var helper: String

        @BeforeClass
        @JvmStatic
        fun load() {
            val path = System.getProperty("oneMemos.projectDir")
            require(!path.isNullOrBlank()) {
                "系统属性 oneMemos.projectDir 未设置；请在 app/build.gradle.kts 测试任务中配置"
            }
            projectDir = File(path)
            fun read(rel: String): String = projectDir.resolve(rel).readText()
            memoItem = read("feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/MemoItem.kt")
            homeScreen = read("feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeScreen.kt")
            editorScreen = read("feature/editor/src/main/java/cc/pscly/onememos/ui/feature/editor/EditorScreen.kt")
            editorEntry = read("feature/editor/src/main/java/cc/pscly/onememos/ui/feature/editor/EditorEntryContributor.kt")
            host = read("app/src/main/java/cc/pscly/onememos/navigation/AppNavigationHost.kt")
            helper = read("core/navigation/src/main/java/cc/pscly/onememos/navigation/MemoSharedTransition.kt")
        }
    }

    @Test
    fun memoItem_acceptsExternalModifier_andKeepsTestTagOrder() {
        assertTrue(
            "MemoItem 必须接受默认空 modifier 参数",
            memoItem.contains("modifier: Modifier = Modifier"),
        )
        assertTrue(
            "InkCard modifier 必须先 then(selected border) 再 testTag",
            memoItem.contains(".then(if (selected) Modifier.border(selectedBorder, cardShape) else Modifier)") &&
                memoItem.contains(".testTag(\"home_memo_item_\${memo.uuid}\")"),
        )
    }

    @Test
    fun home_activeOnly_wiresMemoSharedBounds() {
        assertTrue(
            "GroupedItemContent 必须 import memoSharedBounds",
            homeScreen.contains("import cc.pscly.onememos.navigation.memoSharedBounds"),
        )
        assertTrue(
            "必须出现 memoSharedBounds(memo.uuid)",
            homeScreen.contains("memoSharedBounds(memo.uuid)"),
        )
        assertTrue(
            "必须使用 Modifier.memoSharedBounds",
            homeScreen.contains("Modifier.memoSharedBounds(memo.uuid)"),
        )
        // 精确锁定 shared bounds 接线块，避免匹配到其它 ACTIVE 分支（如滑动手势）
        val blockStart = homeScreen.indexOf("val sharedModifier =")
        assertTrue("必须有 sharedModifier 局部变量", blockStart >= 0)
        val block = homeScreen.substring(blockStart, (blockStart + 400).coerceAtMost(homeScreen.length))
        assertTrue(
            "sharedModifier 仅在 ACTIVE 时挂 bounds",
            block.contains("if (mode == HomeScreenMode.ACTIVE)") &&
                block.contains("Modifier.memoSharedBounds(memo.uuid)") &&
                block.contains("} else {") &&
                block.contains("Modifier"),
        )
        assertTrue(
            "sharedModifier 必须传入 MemoItem",
            homeScreen.contains("modifier = sharedModifier"),
        )
    }

    @Test
    fun editor_usesRouteUuid_notUiStateUuid() {
        assertTrue(
            "EditorScreen 必须接收 memoUuid 参数",
            editorScreen.contains("memoUuid: String?"),
        )
        assertTrue(
            "内容根必须 memoSharedBounds(memoUuid)",
            editorScreen.contains("memoSharedBounds(memoUuid)"),
        )
        assertFalse(
            "不得用异步 uiState.uuid 作为 shared bounds key",
            editorScreen.contains("memoSharedBounds(uiState.uuid)"),
        )
        assertTrue(
            "EditorEntryContributor 必须传入 EditorKey.uuid",
            editorEntry.contains("memoUuid = editorKey.uuid"),
        )
    }

    @Test
    fun reducedMotion_gatedOnlyByHostLocal() {
        assertTrue(host.contains("LocalMemoSharedTransitionScope"))
        assertTrue(
            host.contains("val memoSharedScope = if (reducedMotion) null else this") ||
                host.contains("if (reducedMotion) null else this"),
        )
        assertTrue(helper.contains("if (scope == null || key == null) return this"))
    }

    @Test
    fun sourceAndTarget_counts_areExactlyOneWiringSurfaceEach() {
        val homeCalls = Regex("""memoSharedBounds\s*\(\s*memo\.uuid\s*\)""").findAll(homeScreen).count()
        assertEquals("Home ACTIVE source memoSharedBounds(memo.uuid) 应恰 1 处", 1, homeCalls)
        val editorCalls = Regex("""memoSharedBounds\s*\(\s*memoUuid\s*\)""").findAll(editorScreen).count()
        assertEquals("Editor target memoSharedBounds(memoUuid) 应恰 1 处", 1, editorCalls)
    }
}
