package cc.pscly.onememos.ui.feature.home

import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.domain.model.MemoServerState
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.SyncStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Memo 卡片 TalkBack 合并文案契约：含时间/摘要/状态，多选态追加选中说明。
 */
class MemoItemTalkBackTest {
    @Test
    fun contentDescription_includesPreviewStatusAndTags() {
        val memo =
            sampleMemo(
                content = "今日记录 #工作 #灵感",
                plainPreview = "今日记录",
                tags = listOf("工作", "灵感"),
                syncStatus = SyncStatus.SYNCED,
            )
        val desc =
            MemoItemTalkBack.contentDescription(
                memo = memo,
                selectionMode = false,
                selected = false,
            )
        assertTrue(desc.startsWith("随笔，"))
        assertTrue(desc.contains("今日记录"))
        assertTrue(desc.contains("已同步"))
        assertTrue(desc.contains("标签 工作、灵感"))
        assertFalse(desc.contains("已选中"))
    }

    @Test
    fun contentDescription_selectionMode_appendsSelected() {
        val memo = sampleMemo(plainPreview = "草稿", syncStatus = SyncStatus.DIRTY)
        val desc =
            MemoItemTalkBack.contentDescription(
                memo = memo,
                selectionMode = true,
                selected = true,
            )
        assertTrue(desc.contains("待同步"))
        assertTrue(desc.endsWith("已选中") || desc.contains("，已选中"))
    }

    @Test
    fun statusLabel_archivedFailed() {
        assertTrue(
            MemoItemTalkBack.statusLabel(MemoServerState.ARCHIVED, SyncStatus.FAILED) == "归档失败",
        )
        assertTrue(
            MemoItemTalkBack.statusLabel(MemoServerState.NORMAL, SyncStatus.LOCAL_ONLY) == "仅本地",
        )
    }

    private fun sampleMemo(
        content: String = "hello",
        plainPreview: String = content,
        tags: List<String> = emptyList(),
        syncStatus: SyncStatus = SyncStatus.SYNCED,
    ): Memo =
        Memo(
            uuid = "u-1",
            serverId = null,
            creator = null,
            content = content,
            plainPreview = plainPreview,
            tags = tags,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L,
            serverState = MemoServerState.NORMAL,
            visibility = MemoVisibility.PRIVATE,
            pinned = false,
            syncStatus = syncStatus,
            attachments = emptyList(),
            lastSyncError = null,
        )
}
