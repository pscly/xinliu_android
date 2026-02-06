package cc.pscly.onememos.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cc.pscly.onememos.core.database.entity.TodoSyncOutboxEntity
import cc.pscly.onememos.core.database.entity.TodoSyncStateEntity

@Dao
interface TodoSyncDao {
    @Query(
        "SELECT * FROM todo_sync_outbox " +
            "WHERE ownerKey = :ownerKey " +
            "AND state = 'PENDING' " +
            "ORDER BY createdAtMs ASC " +
            "LIMIT :limit",
    )
    suspend fun listPendingOutbox(
        ownerKey: String,
        limit: Int,
    ): List<TodoSyncOutboxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOutbox(entity: TodoSyncOutboxEntity)

    @Query(
        "UPDATE todo_sync_outbox " +
            "SET state = :state, lastError = :lastError " +
            "WHERE ownerKey = :ownerKey AND resource = :resource AND entityId = :entityId",
    )
    suspend fun markOutboxState(
        ownerKey: String,
        resource: String,
        entityId: String,
        state: String,
        lastError: String?,
    )

    @Query("DELETE FROM todo_sync_outbox WHERE ownerKey = :ownerKey AND resource = :resource AND entityId = :entityId")
    suspend fun deleteOutbox(
        ownerKey: String,
        resource: String,
        entityId: String,
    )

    @Query("SELECT * FROM todo_sync_state WHERE ownerKey = :ownerKey LIMIT 1")
    suspend fun getSyncState(ownerKey: String): TodoSyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncState(entity: TodoSyncStateEntity)
}

