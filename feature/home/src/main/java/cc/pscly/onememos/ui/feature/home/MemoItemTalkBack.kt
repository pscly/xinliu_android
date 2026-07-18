package cc.pscly.onememos.ui.feature.home

import cc.pscly.onememos.domain.derived.MarkdownDeriver
import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.domain.model.MemoServerState
import cc.pscly.onememos.domain.model.SyncStatus
import cc.pscly.onememos.domain.tag.TagExtractor
import cc.pscly.onememos.ui.util.DateTimeFormatter

/**
 * Memo 卡片 TalkBack 合并朗读文案（纯函数，便于单元测试）。
 * 浏览→编辑主路径：单节点播报时间、摘要、同步状态与标签，避免子节点碎读。
 */
internal object MemoItemTalkBack {
    fun contentDescription(
        memo: Memo,
        selectionMode: Boolean,
        selected: Boolean,
    ): String {
        val tags =
            if (memo.tags.isNotEmpty()) {
                memo.tags
            } else {
                TagExtractor.extractAll(memo.content)
            }
        val preview =
            memo.plainPreview
                .ifBlank { MarkdownDeriver.plainPreview(memo.content, maxChars = 80) }
                .ifBlank { "无文字内容" }
                .replace('\n', ' ')
                .take(80)
        val status = statusLabel(memo.serverState, memo.syncStatus)
        val timeLabel = DateTimeFormatter.formatYmdHm(memo.createdAt)
        val selectionLabel =
            when {
                !selectionMode -> null
                selected -> "已选中"
                else -> "未选中"
            }
        return buildString {
            append("随笔，")
            append(timeLabel)
            append("，")
            append(preview)
            append("，")
            append(status)
            if (tags.isNotEmpty()) {
                append("，标签 ")
                append(tags.take(3).joinToString("、"))
            }
            if (selectionLabel != null) {
                append("，")
                append(selectionLabel)
            }
        }
    }

    fun statusLabel(
        serverState: MemoServerState,
        syncStatus: SyncStatus,
    ): String =
        if (serverState == MemoServerState.ARCHIVED) {
            when (syncStatus) {
                SyncStatus.LOCAL_ONLY -> "已归档（本地）"
                SyncStatus.DIRTY -> "归档中"
                SyncStatus.SYNCING -> "归档同步中"
                SyncStatus.SYNCED -> "已归档"
                SyncStatus.FAILED -> "归档失败"
            }
        } else {
            when (syncStatus) {
                SyncStatus.LOCAL_ONLY -> "仅本地"
                SyncStatus.DIRTY -> "待同步"
                SyncStatus.SYNCING -> "同步中"
                SyncStatus.SYNCED -> "已同步"
                SyncStatus.FAILED -> "失败"
            }
        }
}
