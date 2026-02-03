package cc.pscly.onememos.ui.feature.profile

import cc.pscly.onememos.domain.model.Memo
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.NavigableMap
import java.util.TreeMap
import kotlin.math.ceil

data class DateRangeSelection(
    val anchor: LocalDate,
    val current: LocalDate,
) {
    val start: LocalDate get() = minOf(anchor, current)
    val end: LocalDate get() = maxOf(anchor, current)

    val daysCount: Int
        get() = (ChronoUnit.DAYS.between(start, end) + 1).toInt().coerceAtLeast(1)
}

data class HeatmapUiModel(
    val month: YearMonth,
    val activeStart: LocalDate,
    val activeEnd: LocalDate,
    val gridStart: LocalDate,
    // 日历视图：列固定为 7（周一 -> 周日），行数按当月需要的周数计算（通常 4~6）。
    val rows: Int,
    val counts: Map<LocalDate, Int>,
    val maxCount: Int,
)

data class ProfileDaySection(
    val date: LocalDate,
    val memos: List<Memo>,
)

internal fun buildHeatmapUiModel(
    memos: List<Memo>,
    zoneId: ZoneId,
    month: YearMonth = YearMonth.now(zoneId),
): HeatmapUiModel {
    val dayIndex = buildMemoDayIndex(memos = memos, zoneId = zoneId)
    return buildHeatmapUiModel(dayIndex = dayIndex, month = month)
}

internal fun buildSelectedSections(
    memos: List<Memo>,
    zoneId: ZoneId,
    selection: DateRangeSelection,
): List<ProfileDaySection> {
    val dayIndex = buildMemoDayIndex(memos = memos, zoneId = zoneId)
    return buildSelectedSections(dayIndex = dayIndex, selection = selection)
}

/**
 * 构建“按天索引”的 memo 集合（按 createdAt 正序）。
 * 用途：在日历拖动选择时避免全量 memos 反复过滤/排序造成卡顿。
 */
internal fun buildMemoDayIndex(
    memos: List<Memo>,
    zoneId: ZoneId,
): NavigableMap<LocalDate, List<Memo>> {
    if (memos.isEmpty()) return TreeMap()

    val sorted = memos.sortedBy { it.createdAt }
    val tmp = TreeMap<LocalDate, MutableList<Memo>>()

    for (memo in sorted) {
        val day = Instant.ofEpochMilli(memo.createdAt).atZone(zoneId).toLocalDate()
        tmp.getOrPut(day) { mutableListOf() }.add(memo)
    }

    val out = TreeMap<LocalDate, List<Memo>>()
    tmp.forEach { (day, list) -> out[day] = list }
    return out
}

internal fun buildHeatmapUiModel(
    dayIndex: Map<LocalDate, List<Memo>>,
    month: YearMonth,
): HeatmapUiModel {
    // 本月日历热力图：仅统计“本月”每天的记录数量。
    val monthStart = month.atDay(1)
    val monthEnd = month.atEndOfMonth()

    val counts = HashMap<LocalDate, Int>(32)
    var maxCount = 0

    var d = monthStart
    while (!d.isAfter(monthEnd)) {
        val c = dayIndex[d]?.size ?: 0
        if (c > 0) counts[d] = c
        if (c > maxCount) maxCount = c
        d = d.plusDays(1)
    }

    // 周起始：周一。将月初对齐到当周周一，便于按“日历网格”绘制。
    val gridStart = monthStart.minusDays((monthStart.dayOfWeek.value - 1).toLong())

    // 网格尾部补齐到周日（保证矩形网格）。
    val gridEnd = monthEnd.plusDays((7 - monthEnd.dayOfWeek.value).toLong())
    val totalDays = ChronoUnit.DAYS.between(gridStart, gridEnd).toInt() + 1
    val rows = ceil(totalDays / 7.0).toInt().coerceAtLeast(1)

    return HeatmapUiModel(
        month = month,
        activeStart = monthStart,
        activeEnd = monthEnd,
        gridStart = gridStart,
        rows = rows,
        counts = counts,
        maxCount = maxCount,
    )
}

internal fun buildSelectedSections(
    dayIndex: NavigableMap<LocalDate, List<Memo>>,
    selection: DateRangeSelection,
): List<ProfileDaySection> {
    if (dayIndex.isEmpty()) return emptyList()

    val start = selection.start
    val end = selection.end
    val sub = dayIndex.subMap(start, true, end, true)
    if (sub.isEmpty()) return emptyList()

    return sub.entries.map { (date, memos) -> ProfileDaySection(date = date, memos = memos) }
}
