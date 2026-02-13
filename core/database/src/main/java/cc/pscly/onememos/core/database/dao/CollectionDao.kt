package cc.pscly.onememos.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cc.pscly.onememos.core.database.entity.CollectionItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collection_items WHERE ownerKey = :ownerKey AND id = :id LIMIT 1")
    suspend fun getById(
        ownerKey: String,
        id: String,
    ): CollectionItemEntity?

    @Query(
        "SELECT * FROM collection_items " +
            "WHERE ownerKey = :ownerKey " +
            "AND ((:parentId IS NULL AND parentId IS NULL) OR parentId = :parentId) " +
            "AND (:includeDeleted = 1 OR deletedAt IS NULL) " +
            "ORDER BY sortOrder ASC, clientUpdatedAtMs DESC, id ASC",
    )
    fun observeChildren(
        ownerKey: String,
        parentId: String?,
        includeDeleted: Boolean = false,
    ): Flow<List<CollectionItemEntity>>

    @Query(
        "SELECT * FROM collection_items " +
            "WHERE ownerKey = :ownerKey " +
            "AND ((:parentId IS NULL AND parentId IS NULL) OR parentId = :parentId) " +
            "AND (:includeDeleted = 1 OR deletedAt IS NULL) " +
            "ORDER BY sortOrder ASC, clientUpdatedAtMs DESC, id ASC",
    )
    suspend fun listChildren(
        ownerKey: String,
        parentId: String?,
        includeDeleted: Boolean = false,
    ): List<CollectionItemEntity>

    @Query(
        "SELECT * FROM collection_items " +
            "WHERE ownerKey = :ownerKey " +
            "AND (:includeDeleted = 1 OR deletedAt IS NULL)",
    )
    suspend fun listAll(
        ownerKey: String,
        includeDeleted: Boolean = false,
    ): List<CollectionItemEntity>

    @Query(
        "SELECT * FROM collection_items " +
            "WHERE ownerKey = :ownerKey " +
            "AND (:includeDeleted = 1 OR deletedAt IS NULL)",
    )
    fun observeAll(
        ownerKey: String,
        includeDeleted: Boolean = false,
    ): Flow<List<CollectionItemEntity>>

    @Query(
        "SELECT * FROM collection_items " +
            "WHERE ownerKey = :ownerKey " +
            "AND localOnly = 0 " +
            "AND deletedAt IS NULL " +
            "AND refType = :refType " +
            "AND refId IS NULL " +
            "AND refLocalUuid = :memoUuid",
    )
    suspend fun listMemoRefBackfillTargets(
        ownerKey: String,
        refType: String,
        memoUuid: String,
    ): List<CollectionItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CollectionItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CollectionItemEntity>)

    @Query(
        "WITH RECURSIVE subtree(id) AS (" +
            "  SELECT id FROM collection_items WHERE ownerKey = :ownerKey AND id = :rootId " +
            "  UNION ALL " +
            "  SELECT c.id FROM collection_items c INNER JOIN subtree s ON c.parentId = s.id " +
            "  WHERE c.ownerKey = :ownerKey" +
            ") " +
            "UPDATE collection_items " +
            "SET deletedAt = :deletedAt, clientUpdatedAtMs = :clientUpdatedAtMs, updatedAt = :updatedAt " +
            "WHERE ownerKey = :ownerKey AND id IN subtree",
    )
    suspend fun tombstoneSubtree(
        ownerKey: String,
        rootId: String,
        deletedAt: String,
        clientUpdatedAtMs: Long,
        updatedAt: String,
    )

    @Query(
        "WITH RECURSIVE subtree(id) AS (" +
            "  SELECT id FROM collection_items WHERE ownerKey = :ownerKey AND id = :rootId " +
            "  UNION ALL " +
            "  SELECT c.id FROM collection_items c INNER JOIN subtree s ON c.parentId = s.id " +
            "  WHERE c.ownerKey = :ownerKey" +
            ") " +
            "SELECT * FROM collection_items WHERE ownerKey = :ownerKey AND id IN subtree",
    )
    suspend fun listSubtree(
        ownerKey: String,
        rootId: String,
    ): List<CollectionItemEntity>
}
