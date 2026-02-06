package cc.pscly.onememos.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cc.pscly.onememos.core.database.dao.MemoDao
import cc.pscly.onememos.core.database.dao.TodoDao
import cc.pscly.onememos.core.database.dao.TodoSyncDao
import cc.pscly.onememos.core.database.entity.MemoAttachmentEntity
import cc.pscly.onememos.core.database.entity.MemoEntity
import cc.pscly.onememos.core.database.entity.TodoItemEntity
import cc.pscly.onememos.core.database.entity.TodoListEntity
import cc.pscly.onememos.core.database.entity.TodoOccurrenceEntity
import cc.pscly.onememos.core.database.entity.TodoSyncOutboxEntity
import cc.pscly.onememos.core.database.entity.TodoSyncStateEntity

@Database(
    entities = [
        MemoEntity::class,
        MemoAttachmentEntity::class,
        TodoListEntity::class,
        TodoItemEntity::class,
        TodoOccurrenceEntity::class,
        TodoSyncOutboxEntity::class,
        TodoSyncStateEntity::class,
    ],
    version = 10,
    exportSchema = false,
)
@TypeConverters(RoomConverters::class)
abstract class OneMemosDatabase : RoomDatabase() {
    abstract fun memoDao(): MemoDao
    abstract fun todoDao(): TodoDao
    abstract fun todoSyncDao(): TodoSyncDao

    companion object {
        val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 仅新增排序字段，默认 0，旧数据继续按 createdAt/id 排序即可。
                    db.execSQL("ALTER TABLE memo_attachments ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                }
            }

        val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 新增附件持久缓存路径字段（可为空），用于“真正本地缓存/离线秒开”。
                    db.execSQL("ALTER TABLE memo_attachments ADD COLUMN cacheUri TEXT")
                }
            }

        val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 目标：
                    // - memos 表引入 localId 自增主键（不再把 uuid 当主键），uuid 继续保持业务唯一。
                    // - 同步状态迁移：PENDING -> DIRTY（语义更贴合“待上传/待同步”）。
                    // - 重建 memo_attachments 外键：引用 memos.uuid（uuid 上有唯一索引）。
                    db.execSQL("PRAGMA foreign_keys=OFF")

                    db.execSQL("ALTER TABLE memos RENAME TO memos_old")

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS memos (
                            localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            uuid TEXT NOT NULL,
                            serverId TEXT,
                            content TEXT NOT NULL,
                            createdAt INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL,
                            serverState TEXT NOT NULL,
                            visibility TEXT NOT NULL,
                            pinned INTEGER NOT NULL,
                            syncStatus TEXT NOT NULL,
                            lastSyncError TEXT
                        )
                        """.trimIndent(),
                    )
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_memos_uuid ON memos(uuid)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_memos_serverId ON memos(serverId)")

                    db.execSQL(
                        """
                        INSERT INTO memos (
                            uuid, serverId, content, createdAt, updatedAt, serverState, visibility, pinned, syncStatus, lastSyncError
                        )
                        SELECT
                            uuid,
                            serverId,
                            content,
                            createdAt,
                            updatedAt,
                            serverState,
                            visibility,
                            pinned,
                            CASE WHEN syncStatus = 'PENDING' THEN 'DIRTY' ELSE syncStatus END,
                            lastSyncError
                        FROM memos_old
                        """.trimIndent(),
                    )

                    db.execSQL("ALTER TABLE memo_attachments RENAME TO memo_attachments_old")
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS memo_attachments (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            memoUuid TEXT NOT NULL,
                            localUri TEXT,
                            cacheUri TEXT,
                            remoteName TEXT,
                            filename TEXT,
                            mimeType TEXT,
                            createdAt INTEGER NOT NULL,
                            sortOrder INTEGER NOT NULL DEFAULT 0,
                            FOREIGN KEY(memoUuid) REFERENCES memos(uuid) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent(),
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_memo_attachments_memoUuid ON memo_attachments(memoUuid)")
                    db.execSQL(
                        """
                        INSERT INTO memo_attachments (
                            id, memoUuid, localUri, cacheUri, remoteName, filename, mimeType, createdAt, sortOrder
                        )
                        SELECT
                            id, memoUuid, localUri, cacheUri, remoteName, filename, mimeType, createdAt, sortOrder
                        FROM memo_attachments_old
                        """.trimIndent(),
                    )

                    db.execSQL("DROP TABLE memos_old")
                    db.execSQL("DROP TABLE memo_attachments_old")

                    db.execSQL("PRAGMA foreign_keys=ON")
                }
            }

        val MIGRATION_6_7 =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 新增 creator 字段，用于“只看自己的历史”过滤（memos API 的 Memo.creator）。
                    db.execSQL("ALTER TABLE memos ADD COLUMN creator TEXT")
                }
            }

        val MIGRATION_7_8 =
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 主页性能：预计算派生字段（预览/标签索引），避免滚动路径重复解析。
                    db.execSQL("ALTER TABLE memos ADD COLUMN plainPreview TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE memos ADD COLUMN tagsText TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE memos ADD COLUMN derivedVersion INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE memos ADD COLUMN derivedAt INTEGER NOT NULL DEFAULT 0")
                }
            }

        val MIGRATION_8_9 =
            object : Migration(8, 9) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 仅新增索引：不改表结构，降低升级风险。
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_memos_serverState_createdAt_localId ON memos(serverState, createdAt, localId)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_memos_serverState_updatedAt ON memos(serverState, updatedAt)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_memos_syncStatus_createdAt ON memos(syncStatus, createdAt)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_memos_creator_serverState_createdAt ON memos(creator, serverState, createdAt)",
                    )

                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_memo_attachments_memoUuid_remoteName ON memo_attachments(memoUuid, remoteName)",
                    )
                }
            }

        val MIGRATION_9_10 =
            object : Migration(9, 10) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Todo（待办）离线表：本次仅新增表/索引，不改动既有 memo 表结构，降低升级风险。
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS todo_lists (
                            ownerKey TEXT NOT NULL,
                            id TEXT NOT NULL,
                            name TEXT NOT NULL,
                            color TEXT,
                            sortOrder INTEGER NOT NULL,
                            archived INTEGER NOT NULL,
                            deletedAt TEXT,
                            clientUpdatedAtMs INTEGER NOT NULL,
                            updatedAt TEXT NOT NULL,
                            PRIMARY KEY(ownerKey, id)
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_todo_lists_ownerKey_archived_sortOrder ON todo_lists(ownerKey, archived, sortOrder)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_todo_lists_ownerKey_deletedAt ON todo_lists(ownerKey, deletedAt)",
                    )

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS todo_items (
                            ownerKey TEXT NOT NULL,
                            id TEXT NOT NULL,
                            listId TEXT NOT NULL,
                            parentId TEXT,
                            title TEXT NOT NULL,
                            note TEXT NOT NULL,
                            status TEXT NOT NULL,
                            priority INTEGER NOT NULL,
                            sortOrder INTEGER NOT NULL,
                            tagsJson TEXT NOT NULL,
                            tagsText TEXT NOT NULL,
                            remindersJson TEXT NOT NULL,
                            dueAtLocal TEXT,
                            completedAtLocal TEXT,
                            isRecurring INTEGER NOT NULL,
                            rrule TEXT,
                            dtstartLocal TEXT,
                            tzid TEXT NOT NULL,
                            deletedAt TEXT,
                            clientUpdatedAtMs INTEGER NOT NULL,
                            updatedAt TEXT NOT NULL,
                            PRIMARY KEY(ownerKey, id)
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_todo_items_ownerKey_listId_status_sortOrder ON todo_items(ownerKey, listId, status, sortOrder)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_todo_items_ownerKey_deletedAt ON todo_items(ownerKey, deletedAt)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_todo_items_ownerKey_clientUpdatedAtMs ON todo_items(ownerKey, clientUpdatedAtMs)",
                    )

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS todo_occurrences (
                            ownerKey TEXT NOT NULL,
                            id TEXT NOT NULL,
                            itemId TEXT NOT NULL,
                            tzid TEXT NOT NULL,
                            recurrenceIdLocal TEXT NOT NULL,
                            statusOverride TEXT,
                            titleOverride TEXT,
                            noteOverride TEXT,
                            dueAtOverrideLocal TEXT,
                            completedAtLocal TEXT,
                            deletedAt TEXT,
                            clientUpdatedAtMs INTEGER NOT NULL,
                            updatedAt TEXT NOT NULL,
                            PRIMARY KEY(ownerKey, id)
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_todo_occurrences_ownerKey_itemId_recurrenceIdLocal ON todo_occurrences(ownerKey, itemId, recurrenceIdLocal)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_todo_occurrences_ownerKey_deletedAt ON todo_occurrences(ownerKey, deletedAt)",
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS index_todo_occurrences_ownerKey_itemId_tzid_recurrenceIdLocal ON todo_occurrences(ownerKey, itemId, tzid, recurrenceIdLocal)",
                    )

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS todo_sync_outbox (
                            ownerKey TEXT NOT NULL,
                            resource TEXT NOT NULL,
                            entityId TEXT NOT NULL,
                            op TEXT NOT NULL,
                            clientUpdatedAtMs INTEGER NOT NULL,
                            dataJson TEXT,
                            state TEXT NOT NULL,
                            lastError TEXT,
                            createdAtMs INTEGER NOT NULL,
                            PRIMARY KEY(ownerKey, resource, entityId)
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_todo_sync_outbox_ownerKey_state_createdAtMs ON todo_sync_outbox(ownerKey, state, createdAtMs)",
                    )

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS todo_sync_state (
                            ownerKey TEXT NOT NULL,
                            cursor INTEGER NOT NULL,
                            running INTEGER NOT NULL,
                            lastSyncAtMs INTEGER NOT NULL,
                            lastError TEXT,
                            PRIMARY KEY(ownerKey)
                        )
                        """.trimIndent(),
                    )
                }
            }
    }
}
