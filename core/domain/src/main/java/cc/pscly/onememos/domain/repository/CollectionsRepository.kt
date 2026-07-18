package cc.pscly.onememos.domain.repository

import cc.pscly.onememos.domain.model.CollectionItem
import cc.pscly.onememos.domain.model.Memo
import kotlinx.coroutines.flow.Flow

interface CollectionsRepository {
    fun observeChildren(parentId: String?): Flow<List<CollectionItem>>

    fun observeAll(): Flow<List<CollectionItem>>

    suspend fun createFolder(
        parentId: String?,
        name: String,
        color: String?,
    ): String

    suspend fun addMemoRef(
        parentId: String?,
        memo: Memo,
        color: String?,
        displayName: String? = null,
    ): String

    /** 把 memo 收藏到锦囊内置「收藏」文件夹（找不到则创建）。本地优先，不依赖网络。幂等。 */
    suspend fun addMemoToFavorites(memoUuid: String): String

    suspend fun rename(
        id: String,
        name: String,
    )

    suspend fun recolor(
        ids: List<String>,
        color: String?,
    )

    suspend fun move(
        ids: List<String>,
        targetParentId: String?,
    )

    suspend fun reorder(
        parentId: String?,
        orderedIds: List<String>,
    )

    suspend fun delete(id: String)

    suspend fun batchDelete(ids: List<String>)

    suspend fun backfillMemoRefId(
        memoUuid: String,
        memoServerId: String,
    )
}
