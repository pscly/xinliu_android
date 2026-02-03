package cc.pscly.onememos.ui.feature.profile

import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.domain.model.MemoServerState
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ProfileModelsTest {
    private val zoneId: ZoneId = ZoneId.of("UTC")

    @Test
    fun dateRangeSelection_normalizes_and_counts_days() {
        val sel =
            DateRangeSelection(
                anchor = LocalDate.of(2026, 1, 10),
                current = LocalDate.of(2026, 1, 8),
            )

        assertEquals(LocalDate.of(2026, 1, 8), sel.start)
        assertEquals(LocalDate.of(2026, 1, 10), sel.end)
        assertEquals(3, sel.daysCount)
    }

    @Test
    fun month_heatmap_aligns_gridStart_to_monday_and_counts_per_day() {
        val monthStart = LocalDate.of(2026, 1, 1)
        val monthEnd = LocalDate.of(2026, 1, 31)
        val month = java.time.YearMonth.of(2026, 1)
        val memos =
            listOf(
                memoAt(LocalDate.of(2026, 1, 3), 8),
                memoAt(LocalDate.of(2026, 1, 3), 9),
                memoAt(LocalDate.of(2026, 1, 5), 10),
                // 非本月：应被过滤
                memoAt(LocalDate.of(2025, 12, 31), 23),
            )

        val model = buildHeatmapUiModel(memos = memos, zoneId = zoneId, month = month)

        assertEquals(month, model.month)
        assertEquals(monthStart, model.activeStart)
        assertEquals(monthEnd, model.activeEnd)
        assertEquals(java.time.DayOfWeek.MONDAY, model.gridStart.dayOfWeek)
        assertEquals(2, model.counts[LocalDate.of(2026, 1, 3)])
        assertEquals(1, model.counts[LocalDate.of(2026, 1, 5)])
        assertEquals(null, model.counts[LocalDate.of(2025, 12, 31)])

        // 网格行数必须覆盖 monthEnd
        val lastCellDate = model.gridStart.plusDays((model.rows * 7L) - 1)
        assertTrue(!lastCellDate.isBefore(model.activeEnd))
    }

    @Test
    fun selectedSections_groups_by_day_and_sorts_ascending() {
        val selection =
            DateRangeSelection(
                anchor = LocalDate.of(2026, 1, 3),
                current = LocalDate.of(2026, 1, 5),
            )
        val memos =
            listOf(
                memoAt(LocalDate.of(2026, 1, 5), 10),
                memoAt(LocalDate.of(2026, 1, 3), 9),
                memoAt(LocalDate.of(2026, 1, 3), 8),
            )

        val sections = buildSelectedSections(memos = memos, zoneId = zoneId, selection = selection)

        assertEquals(listOf(LocalDate.of(2026, 1, 3), LocalDate.of(2026, 1, 5)), sections.map { it.date })
        assertEquals(listOf(8, 9), sections.first().memos.map { DateTimeParts.hourOf(it.createdAt) })
        assertEquals(listOf(10), sections.last().memos.map { DateTimeParts.hourOf(it.createdAt) })
    }

    private fun memoAt(date: LocalDate, hour: Int): Memo {
        val createdAt = date.atTime(hour, 0).atZone(zoneId).toInstant().toEpochMilli()
        return Memo(
            uuid = "uuid_${date}_$hour",
            serverId = null,
            creator = null,
            content = "hi",
            createdAt = createdAt,
            updatedAt = createdAt,
            serverState = MemoServerState.NORMAL,
            visibility = MemoVisibility.PRIVATE,
            pinned = false,
            syncStatus = SyncStatus.SYNCED,
            attachments = emptyList(),
            lastSyncError = null,
        )
    }

    /**
     * 测试里少引入一堆 time formatter 依赖，直接用 UTC 拆出小时即可。
     */
    private object DateTimeParts {
        fun hourOf(epochMillis: Long): Int =
            java.time.Instant.ofEpochMilli(epochMillis).atZone(ZoneId.of("UTC")).hour
    }
}
