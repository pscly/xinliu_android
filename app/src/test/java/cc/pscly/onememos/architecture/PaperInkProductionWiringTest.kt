package cc.pscly.onememos.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * PaperInk 生产接线全仓契约。
 *
 * 扫描根：app 的 main、每个 feature 的 main、core designsystem 的 main。
 * 排除 build 与 test 或 androidTest。
 * 词法清洗后禁止 raw TopAppBar、SnackbarHost、ModalBottomSheet 调用。
 * 豁免 PaperInkComponents（三模式）与 QuickCaptureOverlayService（仅 ModalBottomSheet）。
 * 目标 wrapper 计数：8 TopAppBar、5 BottomSheet、1 SnackbarHost。
 */
class PaperInkProductionWiringTest {
    companion object {
        private lateinit var projectDir: File
        private lateinit var productionFiles: List<File>

        private val paperInkComponentsRel =
            "core/designsystem/src/main/java/cc/pscly/onememos/ui/theme/PaperInkComponents.kt"
        private val overlayRel =
            "app/src/main/java/cc/pscly/onememos/overlay/QuickCaptureOverlayService.kt"

        private val topAppBarRx = Regex("\\bTopAppBar\\s*\\(")
        private val snackbarHostRx = Regex("\\bSnackbarHost\\s*\\(")
        private val bottomSheetRx = Regex("\\bModalBottomSheet\\s*\\(")
        private val paperTopRx = Regex("\\bPaperInkTopAppBar\\s*\\(")
        private val paperSnackRx = Regex("\\bPaperInkSnackbarHost\\s*\\(")
        private val paperSheetRx = Regex("\\bPaperInkModalBottomSheet\\s*\\(")

        @BeforeClass
        @JvmStatic
        fun load() {
            val path = System.getProperty("oneMemos.projectDir")
            require(!path.isNullOrBlank()) {
                "系统属性 oneMemos.projectDir 未设置；请在 app/build.gradle.kts 测试任务中配置"
            }
            projectDir = File(path)
            productionFiles = collectProductionKtFiles(projectDir)
        }

        private fun collectProductionKtFiles(root: File): List<File> {
            val roots = mutableListOf<File>()
            roots += root.resolve("app/src/main")
            root.resolve("feature").listFiles()?.filter { it.isDirectory }?.forEach { feature ->
                roots += feature.resolve("src/main")
            }
            roots += root.resolve("core/designsystem/src/main")
            return roots
                .filter { it.isDirectory }
                .flatMap { dir ->
                    dir
                        .walkTopDown()
                        .filter { it.isFile && it.extension == "kt" }
                        .filter { !it.path.contains("${File.separator}build${File.separator}") }
                        .filter {
                            !it.path.contains("${File.separator}src${File.separator}test${File.separator}") &&
                                !it.path.contains(
                                    "${File.separator}src${File.separator}androidTest${File.separator}",
                                )
                        }
                        .toList()
                }
                .sortedBy { it.relativeTo(root).path }
        }

        /**
         * 确定性 Kotlin 词法清洗：行注释、嵌套块注释、普通字符串、
         * 三引号字符串、字符字面量的非换行字符替换为空格，保留换行。
         */
        internal fun stripKotlinNoise(source: String): String {
            val out = StringBuilder(source.length)
            var i = 0
            val n = source.length
            while (i < n) {
                val c = source[i]
                if (c == '/' && i + 1 < n && source[i + 1] == '/') {
                    out.append(' ')
                    out.append(' ')
                    i += 2
                    while (i < n && source[i] != '\n') {
                        out.append(' ')
                        i++
                    }
                    continue
                }
                if (c == '/' && i + 1 < n && source[i + 1] == '*') {
                    out.append(' ')
                    out.append(' ')
                    i += 2
                    var depth = 1
                    while (i < n && depth > 0) {
                        if (source[i] == '\n') {
                            out.append('\n')
                            i++
                        } else if (i + 1 < n && source[i] == '/' && source[i + 1] == '*') {
                            out.append(' ')
                            out.append(' ')
                            i += 2
                            depth++
                        } else if (i + 1 < n && source[i] == '*' && source[i + 1] == '/') {
                            out.append(' ')
                            out.append(' ')
                            i += 2
                            depth--
                        } else {
                            out.append(' ')
                            i++
                        }
                    }
                    continue
                }
                if (c == '"' && i + 2 < n && source[i + 1] == '"' && source[i + 2] == '"') {
                    repeat(3) { out.append(' ') }
                    i += 3
                    while (i < n) {
                        if (i + 2 < n && source[i] == '"' && source[i + 1] == '"' && source[i + 2] == '"') {
                            repeat(3) { out.append(' ') }
                            i += 3
                            break
                        }
                        if (source[i] == '\n') out.append('\n') else out.append(' ')
                        i++
                    }
                    continue
                }
                if (c == '"') {
                    out.append(' ')
                    i++
                    while (i < n) {
                        val ch = source[i]
                        if (ch == '\\' && i + 1 < n) {
                            out.append(' ')
                            out.append(' ')
                            i += 2
                            continue
                        }
                        if (ch == '"') {
                            out.append(' ')
                            i++
                            break
                        }
                        if (ch == '\n') out.append('\n') else out.append(' ')
                        i++
                    }
                    continue
                }
                if (c == '\'') {
                    out.append(' ')
                    i++
                    while (i < n) {
                        val ch = source[i]
                        if (ch == '\\' && i + 1 < n) {
                            out.append(' ')
                            out.append(' ')
                            i += 2
                            continue
                        }
                        if (ch == '\'') {
                            out.append(' ')
                            i++
                            break
                        }
                        if (ch == '\n') out.append('\n') else out.append(' ')
                        i++
                    }
                    continue
                }
                out.append(c)
                i++
            }
            return out.toString()
        }

        private fun findCallLines(cleaned: String, pattern: Regex): List<Int> =
            pattern.findAll(cleaned).map { match ->
                cleaned.substring(0, match.range.first).count { it == '\n' } + 1
            }.toList()

        private fun relative(file: File): String = file.relativeTo(projectDir).invariantSeparatorsPath
    }

    @Test
    fun scanRoots_areAppFeaturesAndDesignsystemMainOnly() {
        val rels = productionFiles.map { relative(it) }
        assertTrue(rels.any { it.startsWith("app/src/main/") })
        assertTrue(rels.any { it.startsWith("feature/") && it.contains("/src/main/") })
        assertTrue(rels.any { it.startsWith("core/designsystem/src/main/") })
        assertTrue(
            "不得扫描其它 core 模块 main（除 designsystem）",
            rels.none {
                it.startsWith("core/") &&
                    !it.startsWith("core/designsystem/") &&
                    it.contains("/src/main/")
            },
        )
        assertTrue(
            "不得包含 test 源",
            rels.none { it.contains("/src/test/") || it.contains("/src/androidTest/") },
        )
    }

    @Test
    fun rawSystemSurfaces_areExemptedOrAbsent() {
        val topOffenders = mutableListOf<String>()
        val snackOffenders = mutableListOf<String>()
        val sheetOffenders = mutableListOf<String>()

        productionFiles.forEach { file ->
            val rel = relative(file)
            val cleaned = stripKotlinNoise(file.readText())
            val topLines = findCallLines(cleaned, topAppBarRx)
            val snackLines = findCallLines(cleaned, snackbarHostRx)
            val sheetLines = findCallLines(cleaned, bottomSheetRx)

            if (rel != paperInkComponentsRel) {
                topLines.forEach { topOffenders += "$rel:$it" }
                snackLines.forEach { snackOffenders += "$rel:$it" }
            }
            if (rel != paperInkComponentsRel && rel != overlayRel) {
                sheetLines.forEach { sheetOffenders += "$rel:$it" }
            }
        }

        assertTrue("生产代码不得有 raw TopAppBar，发现: $topOffenders", topOffenders.isEmpty())
        assertTrue("生产代码不得有 raw SnackbarHost，发现: $snackOffenders", snackOffenders.isEmpty())
        assertTrue(
            "生产代码不得有 raw ModalBottomSheet（除 overlay 豁免），发现: $sheetOffenders",
            sheetOffenders.isEmpty(),
        )
    }

    @Test
    fun paperInkWrappers_hitRequiredProductionCounts() {
        var top = 0
        var snack = 0
        var sheet = 0
        val topSites = mutableListOf<String>()
        val snackSites = mutableListOf<String>()
        val sheetSites = mutableListOf<String>()

        productionFiles.forEach { file ->
            val rel = relative(file)
            if (rel == paperInkComponentsRel) return@forEach
            val cleaned = stripKotlinNoise(file.readText())
            findCallLines(cleaned, paperTopRx).forEach {
                top++
                topSites += "$rel:$it"
            }
            findCallLines(cleaned, paperSnackRx).forEach {
                snack++
                snackSites += "$rel:$it"
            }
            findCallLines(cleaned, paperSheetRx).forEach {
                sheet++
                sheetSites += "$rel:$it"
            }
        }

        assertEquals("业务 PaperInkTopAppBar 调用应为 8，实际=$top sites=$topSites", 8, top)
        assertEquals("业务 PaperInkSnackbarHost 调用应为 1，实际=$snack sites=$snackSites", 1, snack)
        assertEquals("业务 PaperInkModalBottomSheet 调用应为 5，实际=$sheet sites=$sheetSites", 5, sheet)
    }

    @Test
    fun exemptions_areExactlyPaperInkComponentsAndOverlaySheet() {
        assertTrue(projectDir.resolve(paperInkComponentsRel).isFile)
        assertTrue(projectDir.resolve(overlayRel).isFile)
        val overlayCleaned = stripKotlinNoise(projectDir.resolve(overlayRel).readText())
        assertTrue(
            "overlay 仍应保留 raw ModalBottomSheet 作为基线豁免",
            bottomSheetRx.containsMatchIn(overlayCleaned),
        )
        val paperCleaned = stripKotlinNoise(projectDir.resolve(paperInkComponentsRel).readText())
        assertTrue(topAppBarRx.containsMatchIn(paperCleaned))
        assertTrue(snackbarHostRx.containsMatchIn(paperCleaned))
        assertTrue(bottomSheetRx.containsMatchIn(paperCleaned))
    }
}
