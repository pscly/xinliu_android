package cc.pscly.onememos.ui.util

import cc.pscly.onememos.domain.model.QuickInsertTimeFormat

object QuickInsertTimeFormatter {
    fun format(
        epochMillis: Long,
        format: QuickInsertTimeFormat,
    ): String =
        when (format) {
            QuickInsertTimeFormat.FULL_DATETIME -> DateTimeFormatter.formatYmdHms(epochMillis)
            QuickInsertTimeFormat.TIME_ONLY -> DateTimeFormatter.formatHms(epochMillis)
        }

    fun buildQuotedLine(
        epochMillis: Long,
        format: QuickInsertTimeFormat,
    ): String = "> ${format(epochMillis = epochMillis, format = format)}"

    /**
     * 在正文最开头插入时间戳行（设置里的“自动添加当前时间”）。
     *
     * 行为约定：
     * - 结果形如 `> 2026-10-22 13:05:00` + 空行 + 原正文；
     * - 如果正文已经以同一行开头，则不重复插入（避免重复保存时叠加）；
     * - 插入的是普通 Markdown 文本，用户可在编辑器里手动删除。
     */
    fun withLeadingStamp(
        content: String,
        epochMillis: Long,
        format: QuickInsertTimeFormat,
    ): String {
        val stampLine = buildQuotedLine(epochMillis = epochMillis, format = format)
        val normalized = content.trimStart('\n', '\r')
        if (normalized.isEmpty()) return stampLine
        if (normalized.startsWith(stampLine)) return normalized
        return "$stampLine\n\n$normalized"
    }
}
