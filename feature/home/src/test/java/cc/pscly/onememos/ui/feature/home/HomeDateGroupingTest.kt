package cc.pscly.onememos.ui.feature.home

import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.domain.model.MemoAttachment
import cc.pscly.onememos.domain.model.MemoServerState
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.SyncStatus
import cc.pscly.onememos.ui.util.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDateGroupingTest {
    @Test
    fun `空列表 返回空结果`() {
        val result = buildGroupedItemsFromList(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `单条 memo 产生一个 DateHeader 加一个 MemoEntry`() {
        val createdAt = 1_700_042_400_000L // 2023-11-15 noon CST
        val memo = fakeMemo(uuid = "a", createdAt = createdAt)
        val result = buildGroupedItemsFromList(listOf(memo))

        assertEquals(2, result.size)
        assertTrue(result[0] is GroupedListItem.DateHeader)
        assertTrue(result[1] is GroupedListItem.MemoEntry)

        val header = result[0] as GroupedListItem.DateHeader
        val expectedDate = DateTimeFormatter.formatYmd(createdAt)
        assertEquals(expectedDate, header.dateKey)
        assertEquals(createdAt, header.epochMillis)
    }

    @Test
    fun `同一天多条 memo 只产生一个 DateHeader`() {
        val sameDay = 1_700_042_400_000L
        val memo1 = fakeMemo(uuid = "a", createdAt = sameDay)
        val memo2 = fakeMemo(uuid = "b", createdAt = sameDay + 3600_000) // +1h 同一天
        val memo3 = fakeMemo(uuid = "c", createdAt = sameDay + 7200_000) // +2h 同一天

        val result = buildGroupedItemsFromList(listOf(memo1, memo2, memo3))

        // DateHeader(1) + Memo(1) + Memo(2) + Memo(3) = 4
        assertEquals(4, result.size)
        assertTrue(result[0] is GroupedListItem.DateHeader)
        assertTrue(result[1] is GroupedListItem.MemoEntry)
        assertTrue(result[2] is GroupedListItem.MemoEntry)
        assertTrue(result[3] is GroupedListItem.MemoEntry)

        assertEquals("a", (result[1] as GroupedListItem.MemoEntry).memo.uuid)
        assertEquals("b", (result[2] as GroupedListItem.MemoEntry).memo.uuid)
        assertEquals("c", (result[3] as GroupedListItem.MemoEntry).memo.uuid)
    }

    @Test
    fun `不同日期的 memo 各自产生 DateHeader`() {
        val day1 = 1_700_042_400_000L // 2023-11-15 noon CST
        val day2 = day1 + 86_400_000L // 2023-11-16 noon CST
        val day3 = day2 + 86_400_000L // 2023-11-17 noon CST

        val memo1 = fakeMemo(uuid = "a", createdAt = day1)
        val memo2 = fakeMemo(uuid = "b", createdAt = day2)
        val memo3 = fakeMemo(uuid = "c", createdAt = day3)

        val result = buildGroupedItemsFromList(listOf(memo1, memo2, memo3))

        assertEquals(6, result.size) // 3 headers + 3 memos
        val date1 = DateTimeFormatter.formatYmd(day1)
        val date2 = DateTimeFormatter.formatYmd(day2)
        val date3 = DateTimeFormatter.formatYmd(day3)
        assertEquals("date_header_$date1", (result[0] as GroupedListItem.DateHeader).itemKey)
        assertTrue(result[1] is GroupedListItem.MemoEntry)
        assertEquals("date_header_$date2", (result[2] as GroupedListItem.DateHeader).itemKey)
        assertTrue(result[3] is GroupedListItem.MemoEntry)
        assertEquals("date_header_$date3", (result[4] as GroupedListItem.DateHeader).itemKey)
        assertTrue(result[5] is GroupedListItem.MemoEntry)
    }

    @Test
    fun `null memo 产生 LoadingEntry 但不产生 DateHeader`() {
        val memo1 = fakeMemo(uuid = "a", createdAt = 1_700_042_400_000L)
        val result = buildGroupedItemsFromList(listOf(memo1, null, null))

        assertEquals(4, result.size)
        assertTrue(result[0] is GroupedListItem.DateHeader)
        assertTrue(result[1] is GroupedListItem.MemoEntry)
        assertTrue(result[2] is GroupedListItem.LoadingEntry)
        assertTrue(result[3] is GroupedListItem.LoadingEntry)
    }

    @Test
    fun `交错日期：同一天可被后续同一天归组`() {
        val day1 = 1_700_042_400_000L // 2023-11-15 noon CST
        val day2 = day1 + 86_400_000L // 2023-11-16 noon CST

        val memo1 = fakeMemo(uuid = "a", createdAt = day1)
        val memo2 = fakeMemo(uuid = "b", createdAt = day2)
        val memo3 = fakeMemo(uuid = "c", createdAt = day1) // 回到 day1

        val result = buildGroupedItemsFromList(listOf(memo1, memo2, memo3))

        val date1 = DateTimeFormatter.formatYmd(day1)
        val date2 = DateTimeFormatter.formatYmd(day2)

        // day1 header + memo_a + day2 header + memo_b + day1 header + memo_c = 6
        assertEquals(6, result.size)
        assertEquals("date_header_$date1", (result[0] as GroupedListItem.DateHeader).itemKey)
        assertEquals("date_header_$date2", (result[2] as GroupedListItem.DateHeader).itemKey)
        assertEquals("date_header_$date1", (result[4] as GroupedListItem.DateHeader).itemKey)
    }

    private fun fakeMemo(
        uuid: String,
        createdAt: Long,
    ): Memo =
        Memo(
            uuid = uuid,
            serverId = null,
            creator = null,
            content = "test content",
            createdAt = createdAt,
            updatedAt = createdAt,
            serverState = MemoServerState.NORMAL,
            visibility = MemoVisibility.PRIVATE,
            pinned = false,
            syncStatus = SyncStatus.SYNCED,
            attachments = emptyList<MemoAttachment>(),
            lastSyncError = null,
        )
}
