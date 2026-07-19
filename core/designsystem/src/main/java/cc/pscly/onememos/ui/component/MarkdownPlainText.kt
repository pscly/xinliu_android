package cc.pscly.onememos.ui.component

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Code
import org.commonmark.node.HardLineBreak
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text as MdText
import org.commonmark.parser.Parser

/**
 * Markdown → 纯文本工具（列表摘要 / 搜索预览等场景）。
 *
 * 解析：commonmark（与 [MarkdownPreview] 同源扩展集，保证语义一致）。
 * 输出：剥离样式标记，仅保留可读文本与换行。
 */
fun markdownToPlainPreview(markdown: String, maxChars: Int = 320): String {
    val raw = markdownToPlainText(markdown)
    val normalized = raw.replace(Regex("\\s+"), " ").trim()
    if (normalized.length <= maxChars) return normalized
    return normalized.take(maxChars).trimEnd() + "…"
}

fun markdownToPlainText(markdown: String): String {
    if (markdown.isBlank()) return ""
    return runCatching {
        val doc = plainMarkdownParser.parse(markdown)
        val sb = StringBuilder()

        doc.accept(
            object : AbstractVisitor() {
                override fun visit(text: MdText) {
                    sb.append(text.literal)
                }

                override fun visit(code: Code) {
                    sb.append(code.literal)
                }

                override fun visit(softLineBreak: SoftLineBreak) {
                    sb.append('\n')
                }

                override fun visit(hardLineBreak: HardLineBreak) {
                    sb.append('\n')
                }
            },
        )
        sb.toString()
    }.getOrElse {
        // 稳定性兜底：解析失败时退回原文，避免列表/详情因为极端内容直接崩溃。
        markdown
    }
}

private val plainMarkdownParser: Parser =
    Parser
        .builder()
        .extensions(
            listOf(
                AutolinkExtension.create(),
                StrikethroughExtension.create(),
                TablesExtension.create(),
            ),
        )
        .build()
