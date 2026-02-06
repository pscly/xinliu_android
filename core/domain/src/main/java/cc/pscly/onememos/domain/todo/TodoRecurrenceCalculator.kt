package cc.pscly.onememos.domain.todo

import cc.pscly.onememos.domain.util.LocalDateTimes
import org.dmfs.rfc5545.DateTime
import org.dmfs.rfc5545.recur.RecurrenceRule
import org.dmfs.rfc5545.recur.RecurrenceRuleIterator
import java.time.LocalDateTime

/**
 * 循环任务（RRULE）相关工具。
 *
 * 约定：
 * - todo 的 dtstart_local / due_at_local / recurrence_id_local 都是“本地时间字符串”（yyyy-MM-dd HH:mm:ss）
 * - tzid 仅作为元数据保存与传输；本地计算以“浮动时间（floating）”展开（更贴近 RFC 里 local time 的语义）
 */
object TodoRecurrenceCalculator {
    /**
     * 计算“下一次 occurrence”的 recurrence_id_local。
     *
     * 返回：
     * - 成功：yyyy-MM-dd HH:mm:ss
     * - 失败：null（rrule 或 dtstart 不合法）
     */
    fun nextRecurrenceIdLocal(
        rrule: String?,
        dtstartLocal: String?,
        nowLocal: String,
    ): String? {
        val ruleText = rrule?.trim().orEmpty()
        if (ruleText.isBlank()) return null

        val start = LocalDateTimes.parseOrNull(dtstartLocal) ?: return null
        val now = LocalDateTimes.parseOrNull(nowLocal) ?: return null

        val rule =
            runCatching { RecurrenceRule(ruleText, RecurrenceRule.RfcMode.RFC5545_LAX) }
                .getOrNull()
                ?: return null

        val startDt = toDmfsDateTime(start)
        val nowDt = toDmfsDateTime(now)

        val iterator: RecurrenceRuleIterator =
            runCatching { rule.iterator(startDt) }
                .getOrNull()
                ?: return null

        // 跳过 now 之前的 occurrence（避免从 dtstart 开始遍历）。
        runCatching { iterator.fastForward(nowDt) }

        if (!iterator.hasNext()) return null
        val next = runCatching { iterator.nextDateTime() }.getOrNull() ?: return null
        return formatDmfsDateTimeToLocalString(next)
    }

    private fun toDmfsDateTime(local: LocalDateTime): DateTime {
        // lib-recur 的 month 是 0-based。
        return DateTime(
            local.year,
            local.monthValue - 1,
            local.dayOfMonth,
            local.hour,
            local.minute,
            local.second,
        )
    }

    private fun formatDmfsDateTimeToLocalString(dt: DateTime): String {
        // DateTime.toString() 形如：yyyyMMdd 或 yyyyMMdd'T'HHmmss（floating）。
        val raw = dt.toString()
        val s = raw.trim()
        val y = s.substring(0, 4)
        val m = s.substring(4, 6)
        val d = s.substring(6, 8)
        val hasTime = s.length >= 15 && s[8] == 'T'
        val hh = if (hasTime) s.substring(9, 11) else "00"
        val mm = if (hasTime) s.substring(11, 13) else "00"
        val ss = if (hasTime) s.substring(13, 15) else "00"
        return "$y-$m-$d $hh:$mm:$ss"
    }
}

