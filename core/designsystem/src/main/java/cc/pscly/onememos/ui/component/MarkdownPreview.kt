package cc.pscly.onememos.ui.component

import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import cc.pscly.onememos.ui.theme.InkBorder
import cc.pscly.onememos.ui.theme.InkShape
import cc.pscly.onememos.ui.theme.InkSpacing
import cc.pscly.onememos.ui.theme.InkTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.ThematicBreak
import org.commonmark.node.Text as MdText
import org.commonmark.parser.Parser

/**
 * 列表/卡片场景的 Markdown 预览：
 * - 不引入内部滚动（避免与 LazyColumn 冲突）
 * - 保留 Markdown 的结构与样式（标题/列表/引用/代码块/表格/删除线/链接样式）
 * - 预览模式不支持点击链接（阅读详情里支持）
 */
@Composable
fun MarkdownPreview(
    markdown: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    maxBlocks: Int = 4,
    maxLines: Int = 6,
) {
    if (markdown.isBlank()) {
        Text(
            text = placeholder,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline,
            modifier = modifier,
        )
        return
    }

    val maxBlocksSafe = maxBlocks.coerceAtLeast(1)
    val fallbackText = remember(markdown) { fastPreviewText(markdown, maxChars = 400) }

    val blocksState by
        produceState<List<MarkdownBlock>?>(initialValue = null, key1 = markdown, key2 = maxBlocksSafe) {
            val cached = MarkdownBlocksCache.getPreview(markdown, maxBlocksSafe)
            if (cached != null) {
                value = cached
                return@produceState
            }

            value =
                withContext(markdownParseDispatcher) {
                    val parsed = parseMarkdownBlocks(markdown, maxBlocks = maxBlocksSafe)
                    MarkdownBlocksCache.putPreview(markdown, maxBlocksSafe, parsed)
                    parsed
                }
        }

    val blocks = blocksState
    if (blocks == null) {
        Text(
            text = fallbackText,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier.clipToBounds(),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.MarkdownPreviewGap),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val style =
                        when (block.level) {
                            1 -> MaterialTheme.typography.titleLarge
                            2 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        }
                    Text(
                        text = block.text,
                        style = style,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = block.text,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = maxLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                is MarkdownBlock.Quote -> {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .width(InkSpacing.QuoteBarWidth)
                                .heightIn(min = InkSpacing.QuoteBarMinHeight)
                                .clip(InkShape.QuoteBar)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = InkBorder.QuoteBar)),
                        )
                        Spacer(modifier = Modifier.width(InkSpacing.QuoteGap))
                        Text(
                            text = block.text,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = InkBorder.QuoteText),
                            ),
                            maxLines = maxLines,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                is MarkdownBlock.CodeBlock -> {
                    val shape = InkShape.MarkdownSub
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(InkSpacing.CodeBlockPadding),
                    ) {
                        Text(
                            text = block.code.trimEnd(),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                lineHeight = InkSpacing.CodeLineHeight,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = maxLines,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                is MarkdownBlock.ThematicBreak -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = InkBorder.Hairline)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = InkBorder.OutlineSoft)),
                    )
                }

                is MarkdownBlock.Table -> {
                    MarkdownTable(block = block, maxRows = 3)
                }
            }
        }
    }
}

private fun fastPreviewText(markdown: String, maxChars: Int): String {
    if (markdown.isBlank()) return ""
    val sb = StringBuilder(minOf(maxChars, markdown.length))
    var lastWasSpace = false

    for (ch in markdown) {
        if (ch == '\r') continue
        val c = if (ch == '\n' || ch == '\t') ' ' else ch

        if (c.isWhitespace()) {
            if (!lastWasSpace) {
                sb.append(' ')
                lastWasSpace = true
            }
        } else {
            sb.append(c)
            lastWasSpace = false
        }

        if (sb.length >= maxChars) break
    }

    return sb.toString().trim()
}

private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: AnnotatedString) : MarkdownBlock
    data class Paragraph(val text: AnnotatedString) : MarkdownBlock
    data class Quote(val text: AnnotatedString) : MarkdownBlock
    data class CodeBlock(val code: String, val info: String? = null) : MarkdownBlock
    data object ThematicBreak : MarkdownBlock
    data class Table(
        val header: List<AnnotatedString>?,
        val rows: List<List<AnnotatedString>>,
    ) : MarkdownBlock
}

@OptIn(ExperimentalCoroutinesApi::class)
private val markdownParseDispatcher = Dispatchers.Default.limitedParallelism(2)

private object MarkdownBlocksCache {
    private val previewCache = LruCache<PreviewCacheKey, List<MarkdownBlock>>(96)

    fun getPreview(markdown: String, maxBlocks: Int): List<MarkdownBlock>? =
        synchronized(previewCache) {
            previewCache.get(PreviewCacheKey(maxBlocks = maxBlocks, markdown = markdown))
        }

    fun putPreview(markdown: String, maxBlocks: Int, blocks: List<MarkdownBlock>) {
        synchronized(previewCache) {
            previewCache.put(PreviewCacheKey(maxBlocks = maxBlocks, markdown = markdown), blocks)
        }
    }
}

private data class PreviewCacheKey(
    val maxBlocks: Int,
    val markdown: String,
)

private fun parseMarkdownBlocks(markdown: String, maxBlocks: Int? = null): List<MarkdownBlock> {
    if (markdown.isBlank()) return emptyList()
    val limit = maxBlocks?.coerceAtLeast(1)
    return runCatching {
        val doc = markdownParser.parse(markdown)
        val out = mutableListOf<MarkdownBlock>()

        var n = doc.firstChild
        while (n != null) {
            appendBlock(n, out, indentLevel = 0, maxBlocks = limit)
            if (limit != null && out.size >= limit) break
            n = n.next
        }

        out
    }.getOrElse {
        // 稳定性兜底：解析失败时按纯文本段落展示，保证不白屏/不闪退。
        listOf(MarkdownBlock.Paragraph(text = AnnotatedString(markdown)))
    }
}

private fun appendBlock(
    node: Node,
    out: MutableList<MarkdownBlock>,
    indentLevel: Int,
    maxBlocks: Int?,
) {
    if (maxBlocks != null && out.size >= maxBlocks) return
    when (node) {
        is Heading -> out += MarkdownBlock.Heading(level = node.level, text = buildInline(node))
        is Paragraph -> out += MarkdownBlock.Paragraph(text = buildInline(node, indentLevel = indentLevel))
        is BlockQuote -> out += MarkdownBlock.Quote(text = buildBlockQuote(node))
        is FencedCodeBlock -> out += MarkdownBlock.CodeBlock(code = node.literal.orEmpty(), info = node.info)
        is IndentedCodeBlock -> out += MarkdownBlock.CodeBlock(code = node.literal.orEmpty(), info = null)
        is ThematicBreak -> out += MarkdownBlock.ThematicBreak
        is TableBlock -> out += buildTableBlock(node)
        is BulletList -> appendList(node, out, indentLevel = indentLevel, ordered = false, maxBlocks = maxBlocks)
        is OrderedList -> appendList(node, out, indentLevel = indentLevel, ordered = true, maxBlocks = maxBlocks)
        else -> {
            // 兜底：尝试把该节点当作“可内联文本”渲染为段落，避免内容丢失
            val fallback = buildInline(node, indentLevel = indentLevel)
            if (fallback.text.isNotBlank()) out += MarkdownBlock.Paragraph(text = fallback)
        }
    }
}

private fun appendList(
    list: Node,
    out: MutableList<MarkdownBlock>,
    indentLevel: Int,
    ordered: Boolean,
    maxBlocks: Int?,
) {
    var index = 1
    var child = list.firstChild
    while (child != null) {
        if (maxBlocks != null && out.size >= maxBlocks) return
        if (child is ListItem) {
            appendListItem(child, out, indentLevel = indentLevel, ordered = ordered, index = index, maxBlocks = maxBlocks)
            index += 1
        }
        child = child.next
    }
}

private fun appendListItem(
    item: ListItem,
    out: MutableList<MarkdownBlock>,
    indentLevel: Int,
    ordered: Boolean,
    index: Int,
    maxBlocks: Int?,
) {
    if (maxBlocks != null && out.size >= maxBlocks) return
    val prefixIndent = buildString { repeat(indentLevel) { append("  ") } }
    val basePrefix = if (ordered) "$index. " else "• "

    // ListItem 可能包含多个子块（Paragraph / nested list），这里做一个“够用且不丢内容”的渲染策略：
    var child = item.firstChild
    var emittedFirstLine = false
    while (child != null) {
        if (maxBlocks != null && out.size >= maxBlocks) return
        when (child) {
            is Paragraph -> {
                val inline = buildInline(child, indentLevel = 0)

                // 任务列表（GFM）：- [ ] / - [x]。不依赖扩展，直接按前缀文本处理，避免“渲染不出来”的割裂感。
                val m = taskListPrefixRegex.find(inline.text)
                val (prefix, content) =
                    if (m != null && m.range.first == 0) {
                        val checked = m.groupValues.getOrNull(1).orEmpty().equals("x", ignoreCase = true)
                        val cut = m.value.length.coerceAtMost(inline.length)
                        val rest = inline.subSequence(cut, inline.length)
                        val box = if (checked) "☑ " else "☐ "
                        (prefixIndent + box) to rest
                    } else {
                        (prefixIndent + basePrefix) to inline
                    }

                val text = AnnotatedString(prefix) + content
                out += MarkdownBlock.Paragraph(text = text)
                emittedFirstLine = true
                if (maxBlocks != null && out.size >= maxBlocks) return
            }

            is BulletList -> {
                if (!emittedFirstLine) {
                    out += MarkdownBlock.Paragraph(text = AnnotatedString((prefixIndent + basePrefix).trimEnd()))
                    emittedFirstLine = true
                    if (maxBlocks != null && out.size >= maxBlocks) return
                }
                appendList(child, out, indentLevel = indentLevel + 1, ordered = false, maxBlocks = maxBlocks)
            }

            is OrderedList -> {
                if (!emittedFirstLine) {
                    out += MarkdownBlock.Paragraph(text = AnnotatedString((prefixIndent + basePrefix).trimEnd()))
                    emittedFirstLine = true
                    if (maxBlocks != null && out.size >= maxBlocks) return
                }
                appendList(child, out, indentLevel = indentLevel + 1, ordered = true, maxBlocks = maxBlocks)
            }

            else -> {
                // 其他节点：尽量转为段落
                val inline = buildInline(child, indentLevel = 0)
                if (inline.text.isNotBlank()) {
                    val text = AnnotatedString(prefixIndent + basePrefix) + inline
                    out += MarkdownBlock.Paragraph(text = text)
                    emittedFirstLine = true
                    if (maxBlocks != null && out.size >= maxBlocks) return
                }
            }
        }
        child = child.next
    }

    if (!emittedFirstLine) {
        if (maxBlocks != null && out.size >= maxBlocks) return
        out += MarkdownBlock.Paragraph(text = AnnotatedString((prefixIndent + basePrefix).trimEnd()))
    }
}

private fun buildBlockQuote(quote: BlockQuote): AnnotatedString {
    val b = AnnotatedString.Builder()
    var child = quote.firstChild
    while (child != null) {
        when (child) {
            is Paragraph -> {
                b.append(buildInline(child))
                b.append('\n')
            }

            else -> {
                val inline = buildInline(child)
                if (inline.text.isNotBlank()) {
                    b.append(inline)
                    b.append('\n')
                }
            }
        }
        child = child.next
    }
    // 不做 trimEnd：避免丢失 span（粗体/斜体/代码等）信息
    return b.toAnnotatedString()
}

private fun buildInline(node: Node, indentLevel: Int = 0): AnnotatedString {
    val b = AnnotatedString.Builder()

    if (indentLevel > 0) {
        repeat(indentLevel) { b.append("  ") }
    }

    appendInlineRec(node, b)
    return b.toAnnotatedString()
}

private fun appendInlineRec(node: Node?, b: AnnotatedString.Builder) {
    if (node == null) return

    when (node) {
        is MdText -> b.append(node.literal)
        is SoftLineBreak -> b.append('\n')
        is HardLineBreak -> b.append('\n')
        is Emphasis -> {
            b.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
            var c = node.firstChild
            while (c != null) {
                appendInlineRec(c, b)
                c = c.next
            }
            b.pop()
        }

        is StrongEmphasis -> {
            b.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            var c = node.firstChild
            while (c != null) {
                appendInlineRec(c, b)
                c = c.next
            }
            b.pop()
        }

        is Code -> {
            b.pushStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = InkTone.InlineCodeBg,
                ),
            )
            b.append(node.literal)
            b.pop()
        }

        is Link -> {
            b.pushStringAnnotation(tag = "URL", annotation = node.destination.orEmpty())
            b.pushStyle(
                SpanStyle(
                    textDecoration = TextDecoration.Underline,
                ),
            )
            var c = node.firstChild
            while (c != null) {
                appendInlineRec(c, b)
                c = c.next
            }
            b.pop()
            b.pop()
        }

        is Strikethrough -> {
            b.pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
            var c = node.firstChild
            while (c != null) {
                appendInlineRec(c, b)
                c = c.next
            }
            b.pop()
        }

        is Image -> {
            // memos 图片通常会作为附件出现，这里避免把图片语法原样显示得过于“硬核”
            b.append("[图片]")
        }

        else -> {
            var c = node.firstChild
            while (c != null) {
                appendInlineRec(c, b)
                c = c.next
            }
        }
    }
}

private val markdownParser: Parser =
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

private val taskListPrefixRegex = Regex("^\\s*\\[( |x|X)]\\s+")

private fun buildTableBlock(table: TableBlock): MarkdownBlock.Table {
    var head: TableHead? = null
    val bodyRows = mutableListOf<TableRow>()

    var c = table.firstChild
    while (c != null) {
        when (c) {
            is TableHead -> head = c
            is TableRow -> bodyRows += c
            else -> {
                // TableBody 在不同版本里可能存在，这里兜底：如果内部还有 TableRow 就继续吃掉。
                var n = c.firstChild
                while (n != null) {
                    if (n is TableRow) bodyRows += n
                    n = n.next
                }
            }
        }
        c = c.next
    }

    val headerRow = head?.firstChild as? TableRow
    val headerCells =
        headerRow
            ?.let { row -> tableRowCells(row) }
            ?.takeIf { it.isNotEmpty() }

    val rows =
        bodyRows
            .asSequence()
            .filterNot { row -> row === headerRow }
            .map { row -> tableRowCells(row) }
            .filter { it.isNotEmpty() }
            .toList()

    return MarkdownBlock.Table(
        header = headerCells,
        rows = rows,
    )
}

private fun tableRowCells(row: TableRow): List<AnnotatedString> {
    val out = mutableListOf<AnnotatedString>()
    var c = row.firstChild
    while (c != null) {
        if (c is TableCell) {
            out += buildInline(c)
        }
        c = c.next
    }
    return out
}

@Composable
private fun MarkdownTable(
    block: MarkdownBlock.Table,
    maxRows: Int,
) {
    val cols =
        maxOf(
            block.header?.size ?: 0,
            block.rows.maxOfOrNull { it.size } ?: 0,
        ).coerceAtLeast(1)

    val hScroll = rememberScrollState()
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = InkBorder.TableOutline)
    val headerBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = InkBorder.TableHeaderFill)
    val cellMinWidth = InkSpacing.TableCellMinWidth

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(hScroll),
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = cellMinWidth * cols)
                .border(width = InkBorder.Hairline, color = borderColor, shape = InkShape.MarkdownSub)
                .clip(InkShape.MarkdownSub),
        ) {
            block.header?.let { header ->
                Row(modifier = Modifier.background(headerBg)) {
                    for (i in 0 until cols) {
                        val cell = header.getOrNull(i) ?: AnnotatedString("")
                        Box(
                            modifier = Modifier
                                .widthIn(min = cellMinWidth)
                                .border(width = InkBorder.TableCell, color = borderColor)
                                .padding(
                                    horizontal = InkSpacing.TableCellPaddingH,
                                    vertical = InkSpacing.TableCellPaddingV,
                                ),
                        ) {
                            Text(
                                text = cell,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            val rows = block.rows.take(maxRows.coerceAtLeast(0))
            rows.forEach { row ->
                Row {
                    for (i in 0 until cols) {
                        val cell = row.getOrNull(i) ?: AnnotatedString("")
                        Box(
                            modifier = Modifier
                                .widthIn(min = cellMinWidth)
                                .border(width = InkBorder.TableCell, color = borderColor)
                                .padding(
                                    horizontal = InkSpacing.TableCellPaddingH,
                                    vertical = InkSpacing.TableCellPaddingV,
                                ),
                        ) {
                            Text(
                                text = cell,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
