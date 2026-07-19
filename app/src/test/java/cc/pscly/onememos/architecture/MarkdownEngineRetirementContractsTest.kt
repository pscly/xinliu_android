package cc.pscly.onememos.architecture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * Markdown 引擎退役架构契约测试 —— 永久 GREEN 契约（M4 方案 B）。
 *
 * 契约内容：
 * - MarkdownPaper / useNewMarkdownEngine / USE_NEW_MARKDOWN_ENGINE /
 *   use_new_markdown_engine 已从生产代码彻底移除
 * - MarkdownPreview / markdownToPlainPreview / markdownToPlainText 保留完好
 * - home / profile / collections 的调用方仍使用 MarkdownPreview
 *
 * 全部 7 项契约持续 GREEN，禁止项不出现在生产代码中，保留项始终存在。
 */
class MarkdownEngineRetirementContractsTest {

    companion object {
        private lateinit var projectDir: File

        @BeforeClass
        @JvmStatic
        fun resolveProjectDir() {
            val path = System.getProperty("oneMemos.projectDir")
            require(!path.isNullOrBlank()) {
                "系统属性 oneMemos.projectDir 未设置；请在 app/build.gradle.kts 测试任务中配置"
            }
            projectDir = File(path)
            assertTrue("项目目录不存在: $projectDir", projectDir.isDirectory)
        }
    }

    // ── 生产代码扫描器 ──────────────────────────────────────

    /** 收集所有生产 Kotlin 源码与构建文件（排除测试与构建产物）。 */
    private fun productionSourceRoots(): List<File> {
        val roots = mutableListOf<File>()

        // app/src/main
        val appMain = projectDir.resolve("app/src/main")
        if (appMain.isDirectory) roots.add(appMain)

        // core/*/src/main
        val coreDir = projectDir.resolve("core")
        if (coreDir.isDirectory) {
            coreDir.listFiles()?.filter { it.isDirectory }?.forEach { module ->
                val mainSrc = module.resolve("src/main")
                if (mainSrc.isDirectory) roots.add(mainSrc)
            }
        }

        // feature/*/src/main
        val featureDir = projectDir.resolve("feature")
        if (featureDir.isDirectory) {
            featureDir.listFiles()?.filter { it.isDirectory }?.forEach { module ->
                val mainSrc = module.resolve("src/main")
                if (mainSrc.isDirectory) roots.add(mainSrc)
            }
        }

        return roots
    }

    // 收集所有生产构建文件（root/app/core 与 feature 子模块的 build.gradle.kts + settings.gradle.kts）
    private fun productionBuildFiles(): List<File> {
        val files = mutableListOf<File>()

        // root
        projectDir.resolve("settings.gradle.kts").takeIf { it.isFile }?.let { files.add(it) }
        projectDir.resolve("build.gradle.kts").takeIf { it.isFile }?.let { files.add(it) }

        // app
        projectDir.resolve("app/build.gradle.kts").takeIf { it.isFile }?.let { files.add(it) }

        // core/*/build.gradle.kts
        projectDir.resolve("core").takeIf { it.isDirectory }?.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { it.resolve("build.gradle.kts").takeIf { f -> f.isFile } }
            ?.let { files.addAll(it) }

        // feature/*/build.gradle.kts
        projectDir.resolve("feature").takeIf { it.isDirectory }?.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { it.resolve("build.gradle.kts").takeIf { f -> f.isFile } }
            ?.let { files.addAll(it) }

        return files
    }

    /** 拼接所有生产 Kotlin 源码内容（排除测试与构建产物）。 */
    private fun allProductionKotlinText(): String =
        productionSourceRoots().joinToString("\n") { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .joinToString("\n") { it.readText() }
        }

    /** 拼接所有生产文件内容（Kotlin + 构建文件）。 */
    private fun allProductionText(): String =
        allProductionKotlinText() + "\n" +
            productionBuildFiles().joinToString("\n") { it.readText() }

    // ═══════════════════════════════════════════════════════
    // FORBIDDEN — 退役项不得出现在生产代码中（GREEN 契约）
    // ═══════════════════════════════════════════════════════

    @Test
    fun forbidden_useNewMarkdownEngine_absent() {
        val text = allProductionText()
        assertFalse(
            "useNewMarkdownEngine 不得在任何生产源码与构建文件中出现",
            text.contains("useNewMarkdownEngine"),
        )
    }

    @Test
    fun forbidden_USE_NEW_MARKDOWN_ENGINE_absent() {
        val text = allProductionText()
        assertFalse(
            "USE_NEW_MARKDOWN_ENGINE 常量不得在任何生产代码中出现",
            text.contains("USE_NEW_MARKDOWN_ENGINE"),
        )
    }

    @Test
    fun forbidden_use_new_markdown_engine_absent() {
        val text = allProductionText()
        assertFalse(
            "use_new_markdown_engine 字面量不得在任何生产代码中出现",
            text.contains("use_new_markdown_engine"),
        )
    }

    @Test
    fun forbidden_MarkdownPaper_absent() {
        val text = allProductionText()

        // 断言 1：不得有 MarkdownPaper 函数定义
        assertFalse(
            "MarkdownPaper 函数定义不得在任何生产代码中出现",
            text.contains("fun MarkdownPaper("),
        )

        // 断言 2：不得有 MarkdownPaper 调用或导入
        val hasReference = Regex("\\bMarkdownPaper\\b").containsMatchIn(text)
        assertFalse(
            "MarkdownPaper 的调用或引用不得在任何生产代码中出现",
            hasReference,
        )
    }

    // ═══════════════════════════════════════════════════════
    // KEEP — 保留项必须完好（GREEN 契约）
    // ═══════════════════════════════════════════════════════

    @Test
    fun keeper_MarkdownPreview_and_plain_APIs_exist() {
        val componentDir = projectDir.resolve(
            "core/designsystem/src/main/java/cc/pscly/onememos/ui/component",
        )
        assertTrue("designsystem component 目录不存在: $componentDir", componentDir.isDirectory)

        val text = componentDir.listFiles()
            ?.filter { it.isFile && it.extension == "kt" }
            ?.joinToString("\n") { it.readText() }
            ?: ""

        assertTrue(
            "MarkdownPreview 函数定义必须存在于 designsystem component",
            text.contains("fun MarkdownPreview("),
        )
        assertTrue(
            "markdownToPlainPreview 函数定义必须存在",
            text.contains("fun markdownToPlainPreview("),
        )
        assertTrue(
            "markdownToPlainText 函数定义必须存在",
            text.contains("fun markdownToPlainText("),
        )
    }

    @Test
    fun keeper_home_memoItem_uses_MarkdownPreview() {
        val file = projectDir.resolve(
            "feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/MemoItem.kt",
        )
        assertTrue("MemoItem.kt 不存在: $file", file.isFile)
        val body = file.readText()

        assertTrue(
            "MemoItem.kt 必须导入 MarkdownPreview",
            body.contains("import cc.pscly.onememos.ui.component.MarkdownPreview"),
        )
        assertTrue(
            "MemoItem.kt 必须调用 MarkdownPreview",
            body.contains("MarkdownPreview("),
        )
    }

    @Test
    fun keeper_profile_and_collections_use_MarkdownPreview() {
        val profileFile = projectDir.resolve(
            "feature/profile/src/main/java/cc/pscly/onememos/ui/feature/profile/ProfileScreen.kt",
        )
        assertTrue("ProfileScreen.kt 不存在: $profileFile", profileFile.isFile)
        val profileBody = profileFile.readText()
        assertTrue(
            "ProfileScreen.kt 必须导入 MarkdownPreview",
            profileBody.contains("import cc.pscly.onememos.ui.component.MarkdownPreview"),
        )
        assertTrue(
            "ProfileScreen.kt 必须调用 MarkdownPreview",
            profileBody.contains("MarkdownPreview("),
        )

        val collectionsFile = projectDir.resolve(
            "feature/collections/src/main/java/cc/pscly/onememos/ui/feature/collections/CollectionsScreen.kt",
        )
        assertTrue("CollectionsScreen.kt 不存在: $collectionsFile", collectionsFile.isFile)
        val collectionsBody = collectionsFile.readText()
        assertTrue(
            "CollectionsScreen.kt 必须导入 MarkdownPreview",
            collectionsBody.contains("import cc.pscly.onememos.ui.component.MarkdownPreview"),
        )
        assertTrue(
            "CollectionsScreen.kt 必须调用 MarkdownPreview",
            collectionsBody.contains("MarkdownPreview("),
        )
    }
}
