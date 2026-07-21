package cc.pscly.onememos.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * AppNavigationHost 对 memo shared-bounds scope 的源码契约：
 * - Reduced Motion 时 LocalMemoSharedTransitionScope 为 null
 * - 与 NavDisplay.sharedTransitionScope 共用同一 nullable 值
 * - 无第二套 reduced 开关
 */
class MemoSharedTransitionHostContractTest {
    companion object {
        private lateinit var projectDir: File
        private lateinit var hostSource: String
        private lateinit var helperSource: String

        @BeforeClass
        @JvmStatic
        fun loadSources() {
            val path = System.getProperty("oneMemos.projectDir")
            require(!path.isNullOrBlank()) {
                "系统属性 oneMemos.projectDir 未设置；请在 app/build.gradle.kts 测试任务中配置"
            }
            projectDir = File(path)
            hostSource =
                projectDir
                    .resolve("app/src/main/java/cc/pscly/onememos/navigation/AppNavigationHost.kt")
                    .readText()
            helperSource =
                projectDir
                    .resolve(
                        "core/navigation/src/main/java/cc/pscly/onememos/navigation/MemoSharedTransition.kt",
                    ).readText()
        }
    }

    @Test
    fun host_providesNullableLocalScope_gatedByReducedMotion() {
        assertTrue(
            "宿主必须 CompositionLocalProvider(LocalMemoSharedTransitionScope ...)",
            hostSource.contains("LocalMemoSharedTransitionScope"),
        )
        assertTrue(
            "宿主必须在 reducedMotion 时提供 null scope",
            hostSource.contains("if (reducedMotion) null else this") ||
                hostSource.contains("if (reducedMotion) null else this@SharedTransitionLayout"),
        )
        // 允许一次提取变量：scope = if (reducedMotion) null else this
        assertTrue(
            "NavDisplay.sharedTransitionScope 必须使用与 Local 相同的 reduced 门控",
            hostSource.contains("sharedTransitionScope"),
        )
        assertFalse(
            "不得发明第二套 shared-element 专用 reduced 开关",
            hostSource.contains("sharedElementEnabled") ||
                hostSource.contains("sharedBoundsEnabled") ||
                hostSource.contains("enableSharedElement"),
        )
    }

    @Test
    fun helper_hasExperimentalOptIn_andEarlyReturnBeforeAnimatedScope() {
        assertTrue(
            "LocalMemoSharedTransitionScope 必须 ExperimentalSharedTransitionApi opt-in",
            helperSource.contains("ExperimentalSharedTransitionApi"),
        )
        assertTrue(
            "memoSharedBounds 必须 with(scope) receiver API",
            helperSource.contains("with(scope)"),
        )
        assertTrue(
            "null scope/key 必须 early return",
            helperSource.contains("if (scope == null || key == null) return this"),
        )
        // early return 必须在读取 LocalNavAnimatedContentScope.current 之前
        // （import 中的符号名不计；只比较函数体内的 .current 用法）
        val earlyIdx = helperSource.indexOf("if (scope == null || key == null) return this")
        val animatedIdx = helperSource.indexOf("LocalNavAnimatedContentScope.current")
        assertTrue("early return 索引应存在", earlyIdx >= 0)
        assertTrue("LocalNavAnimatedContentScope.current 用法应存在", animatedIdx >= 0)
        assertTrue(
            "null scope/key 必须在读取 LocalNavAnimatedContentScope.current 前 early return",
            earlyIdx < animatedIdx,
        )
    }

    @Test
    fun keyFunction_usesMemoSlashUuid_shape() {
        assertTrue(
            helperSource.contains("\"memo/\$it\"") || helperSource.contains("\"memo/\${"),
        )
    }
}
