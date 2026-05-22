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
}
