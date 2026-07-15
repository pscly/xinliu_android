package cc.pscly.onememos.domain.repository

import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.domain.model.MemoAttachmentDraft
import kotlinx.coroutines.flow.Flow

interface MemoRepository {
    fun observeMemos(): Flow<List<Memo>>

    fun observeArchivedMemos(): Flow<List<Memo>>

    /**
     * 用于"标签联想/统计"等需求：需要看到全部记录（包含已归档）。
     */
    fun observeAllMemos(): Flow<List<Memo>>

    /**
     * 用于"标签联想/统计/启动推断 creator"等轻量场景：只看最近一部分记录，避免全量订阅导致启动卡顿。
     */
    fun observeRecentMemos(limit: Int): Flow<List<Memo>>

    /**
     * 用于 Profile：只加载时间范围内的记录（按 createdAt 正序），避免订阅全量 memos。
     *
     * 约定：endExclusive 为开区间，便于用 [monthStart, nextMonthStart) 表达整个月。
     */
    fun observeMemosByCreatedAtRange(startInclusive: Long, endExclusive: Long): Flow<List<Memo>>

    /**
     * 用于"极速记录-续写"：按更新时间倒序，列出最近编辑的（非归档）记录。
     */
    suspend fun listRecentEditedActiveMemos(limit: Int): List<Memo>

    suspend fun getMemo(uuid: String): Memo?

    suspend fun archiveMemo(uuid: String)

    suspend fun unarchiveMemo(uuid: String)

    suspend fun updateMemoContent(uuid: String, content: String)

    suspend fun createLocalMemo(
        content: String,
        resourceUris: List<String>,
    ): String

    suspend fun updateLocalMemo(
        uuid: String,
        content: String,
        resourceUris: List<String>,
    )

    /**
     * 用于"编辑历史记录"的完整更新：允许同时更新内容与附件（增删/排序）。
     */
    suspend fun updateMemoDraft(
        uuid: String,
        content: String,
        attachments: List<MemoAttachmentDraft>,
    )
}
