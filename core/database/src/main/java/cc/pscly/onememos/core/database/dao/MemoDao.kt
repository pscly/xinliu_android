package cc.pscly.onememos.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.paging.PagingSource
import cc.pscly.onememos.core.database.entity.MemoEntity
import cc.pscly.onememos.core.database.entity.MemoAttachmentEntity
import cc.pscly.onememos.core.database.model.MemoWithAttachments
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoDao {
    @Transaction
    // 归档后应从“随笔”立即隐藏（即使还在同步中），归档列表可查看同步失败原因并可恢复。
    @Query("SELECT * FROM memos WHERE serverState != 'ARCHIVED' ORDER BY createdAt DESC")
    fun observeMemos(): Flow<List<MemoWithAttachments>>

    @Transaction
    @Query("SELECT * FROM memos WHERE serverState = 'ARCHIVED' ORDER BY createdAt DESC")
    fun observeArchivedMemos(): Flow<List<MemoWithAttachments>>

    @Transaction
    @Query("SELECT * FROM memos ORDER BY createdAt DESC")
    fun observeAllMemos(): Flow<List<MemoWithAttachments>>

    @Transaction
    @Query("SELECT * FROM memos ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecentMemos(limit: Int): Flow<List<MemoWithAttachments>>

    /**
     * 按 createdAt 的时间范围观察记录（用于 Profile 只加载“当前月”数据，避免订阅全量 memos）。
     *
     * 约定：endExclusive 为开区间，便于用 [monthStart, nextMonthStart) 表达整个月。
     */
    @Transaction
    @Query(
        "SELECT * FROM memos " +
            "WHERE createdAt >= :startInclusive AND createdAt < :endExclusive " +
            "ORDER BY createdAt ASC, localId ASC",
    )
    fun observeMemosByCreatedAtRange(
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<List<MemoWithAttachments>>

    @Transaction
    @Query("SELECT * FROM memos WHERE serverState != 'ARCHIVED' ORDER BY createdAt DESC, localId DESC")
    fun pagingActiveAll(): PagingSource<Int, MemoWithAttachments>

    @Transaction
    @Query("SELECT * FROM memos WHERE serverState != 'ARCHIVED' AND serverId IS NULL ORDER BY createdAt DESC, localId DESC")
    fun pagingActiveLocalOnly(): PagingSource<Int, MemoWithAttachments>

    @Transaction
    @Query(
        "SELECT * FROM memos " +
            "WHERE serverState != 'ARCHIVED' " +
            "AND (serverId IS NULL OR (visibility != 'PUBLIC' AND creator = :creator)) " +
            "ORDER BY createdAt DESC, localId DESC",
    )
    fun pagingActiveForCreator(creator: String): PagingSource<Int, MemoWithAttachments>

    @Transaction
    @Query("SELECT * FROM memos WHERE serverState = 'ARCHIVED' ORDER BY createdAt DESC, localId DESC")
    fun pagingArchivedAll(): PagingSource<Int, MemoWithAttachments>

    @Transaction
    @Query("SELECT * FROM memos WHERE serverState = 'ARCHIVED' AND serverId IS NULL ORDER BY createdAt DESC, localId DESC")
    fun pagingArchivedLocalOnly(): PagingSource<Int, MemoWithAttachments>

    @Transaction
    @Query(
        "SELECT * FROM memos " +
            "WHERE serverState = 'ARCHIVED' " +
            "AND (serverId IS NULL OR (visibility != 'PUBLIC' AND creator = :creator)) " +
            "ORDER BY createdAt DESC, localId DESC",
    )
    fun pagingArchivedForCreator(creator: String): PagingSource<Int, MemoWithAttachments>

    @Transaction
    @Query("SELECT * FROM memos WHERE uuid = :uuid LIMIT 1")
    suspend fun getMemo(uuid: String): MemoWithAttachments?

    @Query("SELECT * FROM memos WHERE uuid = :uuid LIMIT 1")
    suspend fun getMemoEntity(uuid: String): MemoEntity?

    @Transaction
    @Query("SELECT * FROM memos WHERE serverId = :serverId LIMIT 1")
    suspend fun getMemoByServerId(serverId: String): MemoWithAttachments?

    @Query("SELECT * FROM memos WHERE serverId = :serverId LIMIT 1")
    suspend fun getMemoEntityByServerId(serverId: String): MemoEntity?

    @Transaction
    @Query("SELECT * FROM memos WHERE syncStatus != 'SYNCED' ORDER BY createdAt ASC")
    suspend fun listMemosNeedingSync(): List<MemoWithAttachments>

    @Query("SELECT COUNT(1) FROM memos WHERE syncStatus != 'SYNCED'")
    fun observeMemosNeedingSyncCount(): Flow<Int>

    @Transaction
    @Query("SELECT * FROM memos WHERE serverState != 'ARCHIVED' ORDER BY createdAt DESC LIMIT :limit")
    suspend fun listRecentActiveMemos(limit: Int): List<MemoWithAttachments>

    @Transaction
    @Query("SELECT * FROM memos WHERE serverState != 'ARCHIVED' ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun listRecentEditedActiveMemos(limit: Int): List<MemoWithAttachments>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMemoIgnore(entity: MemoEntity): Long

    @Update
    suspend fun updateMemo(entity: MemoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttachments(entities: List<MemoAttachmentEntity>)

    @Query("DELETE FROM memo_attachments WHERE memoUuid = :memoUuid")
    suspend fun deleteAttachmentsForMemo(memoUuid: String)

    @Query("UPDATE memo_attachments SET cacheUri = :cacheUri WHERE memoUuid = :memoUuid AND remoteName = :remoteName")
    suspend fun updateAttachmentCacheUri(
        memoUuid: String,
        remoteName: String,
        cacheUri: String?,
    )

    @Query("UPDATE memo_attachments SET cacheUri = NULL")
    suspend fun clearAllAttachmentCacheUris()

    @Query("UPDATE memo_attachments SET cacheUri = NULL WHERE remoteName LIKE '%' || '/' || :remoteId")
    suspend fun clearAttachmentCacheUriByRemoteId(remoteId: String)

    @Query("DELETE FROM memos WHERE uuid = :uuid")
    suspend fun deleteMemo(uuid: String)

    /**
     * 以 uuid 作为业务唯一键进行 Upsert。
     *
     * 重要：不能使用 OnConflictStrategy.REPLACE，否则会触发“删除 + 插入”，在存在外键级联时会导致附件等子表被误删。
     */
    @Transaction
    suspend fun upsertMemo(entity: MemoEntity) {
        val insertedId = insertMemoIgnore(entity)
        if (insertedId != -1L) return

        val existing = getMemoEntity(entity.uuid) ?: return
        updateMemo(entity.copy(localId = existing.localId))
    }

    @Transaction
    suspend fun upsertMemoWithAttachments(
        memo: MemoEntity,
        attachments: List<MemoAttachmentEntity>,
    ) {
        upsertMemo(memo)
        deleteAttachmentsForMemo(memo.uuid)
        if (attachments.isNotEmpty()) {
            upsertAttachments(attachments)
        }
    }

    /**
     * 将一个“本地 uuid”迁移为“服务端 uuid（通常就是 memos/{id}）”。
     * 用于：CreateMemo 成功后，将记录重键到服务端 name，避免后续拉取出现重复。
     */
    @Transaction
    suspend fun replaceMemoUuid(
        oldUuid: String,
        newUuid: String,
        newServerId: String,
    ) {
        if (oldUuid == newUuid) {
            return
        }
        val existing = getMemo(oldUuid) ?: return

        val newMemo =
            existing.memo.copy(
                localId = 0,
                uuid = newUuid,
                serverId = newServerId,
            )

        val newAttachments =
            existing.attachments.map { a ->
                a.copy(
                    id = 0,
                    memoUuid = newUuid,
                )
            }

        upsertMemoWithAttachments(memo = newMemo, attachments = newAttachments)
        deleteMemo(oldUuid)
    }

    @Query("SELECT * FROM memos WHERE derivedVersion < :targetVersion ORDER BY localId ASC LIMIT :limit")
    suspend fun listMemosWithOutdatedDerivedFields(
        targetVersion: Int,
        limit: Int,
    ): List<MemoEntity>

    @Query("SELECT COUNT(1) FROM memos WHERE derivedVersion < :targetVersion")
    suspend fun countMemosWithOutdatedDerivedFields(targetVersion: Int): Int

    @Query(
        "UPDATE memos SET " +
            "plainPreview = :plainPreview, " +
            "tagsText = :tagsText, " +
            "derivedVersion = :derivedVersion, " +
            "derivedAt = :derivedAt " +
            "WHERE localId = :localId",
    )
    suspend fun updateDerivedFields(
        localId: Long,
        plainPreview: String,
        tagsText: String,
        derivedVersion: Int,
        derivedAt: Long,
    )
}
