package cc.pscly.onememos.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cc.pscly.onememos.core.database.entity.TodoItemEntity
import cc.pscly.onememos.core.database.entity.TodoListEntity
import cc.pscly.onememos.core.database.entity.TodoOccurrenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Query(
        "SELECT * FROM todo_lists " +
            "WHERE ownerKey = :ownerKey " +
            "AND (:includeDeleted = 1 OR deletedAt IS NULL) " +
            "AND (:includeArchived = 1 OR archived = 0) " +
            "ORDER BY sortOrder ASC, clientUpdatedAtMs DESC",
    )
    fun observeLists(
        ownerKey: String,
        includeArchived: Boolean,
        includeDeleted: Boolean = false,
    ): Flow<List<TodoListEntity>>

    @Query("SELECT * FROM todo_lists WHERE ownerKey = :ownerKey AND id = :id LIMIT 1")
    suspend fun getList(ownerKey: String, id: String): TodoListEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertList(entity: TodoListEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLists(entities: List<TodoListEntity>)

    @Query(
        "SELECT i.* FROM todo_items i " +
            "INNER JOIN todo_lists l ON i.ownerKey = l.ownerKey AND i.listId = l.id " +
            "WHERE i.ownerKey = :ownerKey " +
            "AND (:includeDeleted = 1 OR i.deletedAt IS NULL) " +
            "AND (:listId IS NULL OR i.listId = :listId) " +
            "AND (:status IS NULL OR i.status = :status) " +
            "AND (:includeArchivedLists = 1 OR l.archived = 0) " +
            "AND (:tagNeedle IS NULL OR i.tagsText LIKE '%' || :tagNeedle || '%') " +
            "ORDER BY i.sortOrder ASC, i.clientUpdatedAtMs DESC",
    )
    fun observeItems(
        ownerKey: String,
        listId: String?,
        status: String?,
        includeArchivedLists: Boolean,
        includeDeleted: Boolean,
        tagNeedle: String?,
    ): Flow<List<TodoItemEntity>>

    @Query("SELECT * FROM todo_items WHERE ownerKey = :ownerKey AND id = :id LIMIT 1")
    suspend fun getItem(ownerKey: String, id: String): TodoItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(entity: TodoItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(entities: List<TodoItemEntity>)

    @Query(
        "SELECT * FROM todo_occurrences " +
            "WHERE ownerKey = :ownerKey " +
            "AND itemId = :itemId " +
            "AND (:includeDeleted = 1 OR deletedAt IS NULL) " +
            "ORDER BY recurrenceIdLocal DESC",
    )
    fun observeOccurrences(
        ownerKey: String,
        itemId: String,
        includeDeleted: Boolean,
    ): Flow<List<TodoOccurrenceEntity>>

    @Query("SELECT * FROM todo_occurrences WHERE ownerKey = :ownerKey AND id = :id LIMIT 1")
    suspend fun getOccurrence(ownerKey: String, id: String): TodoOccurrenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOccurrence(entity: TodoOccurrenceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOccurrences(entities: List<TodoOccurrenceEntity>)
}

