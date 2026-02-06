package cc.pscly.onememos.domain.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Todo/Occurrence 使用的“本地时间字符串”工具。
 *
 * 约定格式：yyyy-MM-dd HH:mm:ss（长度 19，与后端 schema 对齐）。
 */
object LocalDateTimes {
    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun format(local: LocalDateTime): String = local.format(formatter)

    fun parseOrNull(raw: String?): LocalDateTime? {
        val v = raw?.trim().orEmpty()
        if (v.isBlank()) return null
        return runCatching { LocalDateTime.parse(v, formatter) }.getOrNull()
    }

    fun nowString(tzid: String?): String {
        val zone =
            runCatching { if (tzid.isNullOrBlank()) null else ZoneId.of(tzid) }
                .getOrNull()
                ?: ZoneId.systemDefault()
        return LocalDateTime.ofInstant(Instant.now(), zone).format(formatter)
    }
}

