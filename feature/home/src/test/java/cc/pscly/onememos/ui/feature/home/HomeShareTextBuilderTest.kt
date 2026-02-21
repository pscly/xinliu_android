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

class HomeShareTextBuilderTest {
    @Test
    fun `空列表 返回固定占位`() {
        assertEquals("(分享内容为空)", HomeShareTextBuilder.build(emptyList()))
    }

    @Test
    fun `多条 memo 按输入顺序拼接 时间加全文 且分隔符固定`() {
        val createdAt1 = 1_700_000_000_000L
        val createdAt2 = createdAt1 + 60_000L
        val memo1 =
            fakeMemo(
                uuid = "a",
                createdAt = createdAt1,
                content = "第一条\nline2",
            )
        val memo2 =
            fakeMemo(
                uuid = "b",
                createdAt = createdAt2,
                content = "第二条",
            )

        val text = HomeShareTextBuilder.build(listOf(memo1, memo2))

        val time1 = DateTimeFormatter.formatYmdHm(createdAt1)
        val time2 = DateTimeFormatter.formatYmdHm(createdAt2)

        assertTrue(text.contains("$time1\n${memo1.content}"))
        assertTrue(text.contains("$time2\n${memo2.content}"))
        assertTrue(text.contains("\n\n---\n\n"))
        assertTrue(text.indexOf("$time1\n${memo1.content}") < text.indexOf("$time2\n${memo2.content}"))

        val expected = "$time1\n${memo1.content}\n\n---\n\n$time2\n${memo2.content}"
        assertEquals(expected, text)
    }

    private fun fakeMemo(
        uuid: String,
        createdAt: Long,
        content: String,
    ): Memo =
        Memo(
            uuid = uuid,
            serverId = null,
            creator = null,
            content = content,
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
