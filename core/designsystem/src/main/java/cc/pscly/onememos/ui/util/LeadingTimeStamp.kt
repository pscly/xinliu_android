package cc.pscly.onememos.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * 识别正文最开头的“自动时间戳行”。
 *
 * 自动时间戳由 [QuickInsertTimeFormatter.withLeadingStamp] 生成，形如：
 * ```
 * > 2026-10-27 10:56:37
 *
 * 正文……
 * ```
 *
 * 需求（1.18.0）：时间戳既要真实存在于正文里（可保存、可手动删除），
 * 又要在输入框里**以浅色/半透明显示**，不能和用户正文抢注意力。
 * 因此这里只做“识别”，不改变文本；着色交给 [TimestampMuteTransformation]。
 *
 * 识别规则（宁可少认，不可误伤）：
 * - 必须位于文本最开头，且以 `> ` 开头（Markdown 引用）；
 * - 该行内容只由数字、日期时间分隔符与中文日期/时段字构成，且至少含 4 个数字；
 * - `> 引用正文` 这类普通引用不会被识别为时间戳。
 */
object LeadingTimeStamp {
    /**
     * 时间戳在输入框里的透明度：看得见（提示已记录时间），但不与正文抢注意力。
     * 编辑器与极速记录共用，避免两处观感不一致。
     *
     * 取值依据（对纸墨配色实测，`onSurface` 叠加到卡片底色后算对比度）：
     * - 0.30 → 浅色 1.77:1 / 深色 2.33:1（偏淡，略费劲）
     * - 0.38 → 浅色 2.11:1 / 深色 2.92:1（偏淡一端）
     * - **0.45 → 浅色 2.49:1 / 深色 3.53:1（当前取值，两边都“看得见但不抢眼”）**
     * - 0.50 → 浅色 2.78:1 / 深色 4.00:1（浅色开始接近正文一半，会抢注意力）
     *
     * 注意：这是**刻意弱化**的辅助信息，不追求 WCAG 4.5:1（那是正文标准）；
     * 但它仍可点选、可删除，用户需要时能看清。
     */
    const val MUTED_ALPHA = 0.45f

    /** 日期时间行允许出现的字符（含中文日期/时段单位）。 */
    private val ALLOWED_CHARS = Regex("^[0-9:/.,\\-\\s年月日时分秒星期周上午下午（）()]+$")

    /**
     * 时间戳行的结束下标（不含换行符本身）；不是时间戳时返回 null。
     */
    fun lineEndOrNull(text: String): Int? {
        if (!text.startsWith(STAMP_PREFIX)) return null
        val newline = text.indexOf('\n')
        val lineEnd = if (newline < 0) text.length else newline
        if (lineEnd <= STAMP_PREFIX.length) return null
        val body = text.substring(STAMP_PREFIX.length, lineEnd).trim()
        if (body.count { it.isDigit() } < MIN_DIGITS) return null
        if (!ALLOWED_CHARS.matches(body)) return null
        return lineEnd
    }

    /**
     * 正文（用户真正要写的部分）的起始下标：跳过时间戳行与其后的空行。
     * 不是时间戳时返回 0。
     */
    fun bodyStart(text: String): Int {
        val lineEnd = lineEndOrNull(text) ?: return 0
        var index = lineEnd
        while (index < text.length && (text[index] == '\n' || text[index] == '\r')) {
            index += 1
        }
        return index
    }

    /** 正文是否为空（只有时间戳或全空白）。用于决定是否显示“写点什么…”占位提示。 */
    fun bodyIsBlank(text: String): Boolean = text.substring(bodyStart(text)).isBlank()

    /**
     * 时间戳行占比的行数（含其后的空行），供占位提示做垂直偏移，避免与时间戳重叠。
     */
    fun bodyStartLineOffset(text: String): Int {
        if (lineEndOrNull(text) == null) return 0
        val bodyStart = bodyStart(text)
        return text.substring(0, bodyStart).count { it == '\n' }
    }

    private const val STAMP_PREFIX = "> "
    private const val MIN_DIGITS = 4
}

/**
 * 把开头的时间戳行渲染成浅色（半透明），其余文本原样保留。
 *
 * 只附加 SpanStyle、不增删字符，因此 `OffsetMapping.Identity`，光标与选区不会错位。
 */
class TimestampMuteTransformation(
    private val mutedColor: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val end = LeadingTimeStamp.lineEndOrNull(text.text)
            ?: return TransformedText(text, OffsetMapping.Identity)
        val styled =
            buildAnnotatedString {
                append(text)
                addStyle(SpanStyle(color = mutedColor), start = 0, end = end)
            }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}
