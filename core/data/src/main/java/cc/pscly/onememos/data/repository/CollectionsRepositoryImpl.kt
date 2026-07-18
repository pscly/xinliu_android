@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package cc.pscly.onememos.data.repository

import cc.pscly.onememos.core.database.dao.CollectionDao
import cc.pscly.onememos.core.database.dao.TodoSyncDao
import cc.pscly.onememos.core.database.entity.CollectionItemEntity
import cc.pscly.onememos.core.database.entity.TodoSyncOutboxEntity
import cc.pscly.onememos.data.mapper.toDomain
import cc.pscly.onememos.domain.collections.bumpClientUpdatedAtMs
import cc.pscly.onememos.domain.collections.isMoveValid
import cc.pscly.onememos.domain.model.LoginMode
import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.domain.repository.CollectionsRepository
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.sync.TodoSyncScheduler
import cc.pscly.onememos.domain.util.OwnerKeyProvider
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class CollectionsRepositoryImpl @Inject constructor(
    private val collectionDao: CollectionDao,
    private val todoSyncDao: TodoSyncDao,
    private val settingsRepository: SettingsRepository,
    private val todoSyncScheduler: TodoSyncScheduler,
    private val ownerKeyProvider: OwnerKeyProvider,
) : CollectionsRepository {
    private val gson = Gson()

    private fun currentOwnerKeyOrNull(): String? = ownerKeyProvider.currentOwnerKeyOrNull()

    private suspend fun isCollectionsEnabled(): Boolean {
        val s = settingsRepository.settings.first()
        if (s.loginMode != LoginMode.BACKEND) return false
        if (s.token.trim().isBlank()) return false
        return currentOwnerKeyOrNull() != null
    }

    override fun observeChildren(parentId: String?): Flow<List<cc.pscly.onememos.domain.model.CollectionItem>> =
        settingsRepository.settings
            .map { s -> s.loginMode == LoginMode.BACKEND && s.token.isNotBlank() }
            .distinctUntilChanged()
            .flatMapLatest { ok ->
                val ownerKey = currentOwnerKeyOrNull()
                if (!ok || ownerKey == null) {
                    flowOf(emptyList())
                } else {
                    collectionDao.observeChildren(ownerKey = ownerKey, parentId = parentId)
                        .map { list -> list.map { it.toDomain() } }
                }
            }

    override fun observeAll(): Flow<List<cc.pscly.onememos.domain.model.CollectionItem>> =
        settingsRepository.settings
            .map { s -> s.loginMode == LoginMode.BACKEND && s.token.isNotBlank() }
            .distinctUntilChanged()
            .flatMapLatest { ok ->
                val ownerKey = currentOwnerKeyOrNull()
                if (!ok || ownerKey == null) {
                    flowOf(emptyList())
                } else {
                    collectionDao.observeAll(ownerKey = ownerKey)
                        .map { list -> list.map { it.toDomain() } }
                }
            }

    override suspend fun createFolder(
        parentId: String?,
        name: String,
        color: String?,
    ): String {
        if (!isCollectionsEnabled()) return ""
        val ownerKey = currentOwnerKeyOrNull() ?: return ""

        val nowMs = System.currentTimeMillis()
        val nowIso = Instant.now().toString()
        val id = UUID.randomUUID().toString()
        val sortOrder = nextSortOrder(ownerKey = ownerKey, parentId = parentId)

        val entity =
            CollectionItemEntity(
                ownerKey = ownerKey,
                id = id,
                itemType = ITEM_TYPE_FOLDER,
                parentId = parentId,
                name = name.trim().ifBlank { "未命名文件夹" },
                color = normalizeColor(color),
                refType = null,
                refId = null,
                sortOrder = sortOrder,
                clientUpdatedAtMs = nowMs,
                createdAt = nowIso,
                updatedAt = nowIso,
                deletedAt = null,
                localOnly = false,
                refLocalUuid = null,
            )
        collectionDao.upsert(entity)

        enqueueUpsertIfNeeded(ownerKey = ownerKey, entity = entity, nowMs = nowMs)
        todoSyncScheduler.requestSync()
        return id
    }

    override suspend fun addMemoRef(
        parentId: String?,
        memo: Memo,
        color: String?,
        displayName: String?,
    ): String {
        if (!isCollectionsEnabled()) return ""
        val ownerKey = currentOwnerKeyOrNull() ?: return ""

        val nowMs = System.currentTimeMillis()
        val nowIso = Instant.now().toString()
        val id = UUID.randomUUID().toString()
        val sortOrder = nextSortOrder(ownerKey = ownerKey, parentId = parentId)

        val serverId = memo.serverId?.trim()?.takeIf { it.isNotBlank() }
        val entity =
            CollectionItemEntity(
                ownerKey = ownerKey,
                id = id,
                itemType = ITEM_TYPE_NOTE_REF,
                parentId = parentId,
                name = displayName?.trim().orEmpty(),
                color = normalizeColor(color),
                refType = REF_TYPE_MEMOS_MEMO,
                refId = serverId,
                sortOrder = sortOrder,
                clientUpdatedAtMs = nowMs,
                createdAt = nowIso,
                updatedAt = nowIso,
                deletedAt = null,
                localOnly = false,
                refLocalUuid = if (serverId == null) memo.uuid else null,
        )
        collectionDao.upsert(entity)

        val enqueued = enqueueUpsertIfNeeded(ownerKey = ownerKey, entity = entity, nowMs = nowMs)
        if (enqueued) todoSyncScheduler.requestSync()
        return id
    }

    override suspend fun rename(
        id: String,
        name: String,
    ) {
        if (!isCollectionsEnabled()) return
        val ownerKey = currentOwnerKeyOrNull() ?: return

        val existing = collectionDao.getById(ownerKey = ownerKey, id = id) ?: return
        if (existing.deletedAt != null) return

        val nowMs = System.currentTimeMillis()
        val nowIso = Instant.now().toString()
        val nextName =
            if (existing.itemType == ITEM_TYPE_FOLDER) {
                name.trim().ifBlank { "未命名文件夹" }
            } else {
                name.trim()
            }
        val updated =
            existing.copy(
                name = nextName,
                clientUpdatedAtMs = bumpClientUpdatedAtMs(nowMs = nowMs, previousClientUpdatedAtMs = existing.clientUpdatedAtMs),
                updatedAt = nowIso,
            )
        collectionDao.upsert(updated)

        val enqueued = enqueueUpsertIfNeeded(ownerKey = ownerKey, entity = updated, nowMs = nowMs)
        if (enqueued) todoSyncScheduler.requestSync()
    }

    override suspend fun recolor(
        ids: List<String>,
        color: String?,
    ) {
        if (!isCollectionsEnabled()) return
        val ownerKey = currentOwnerKeyOrNull() ?: return
        if (ids.isEmpty()) return

        val nowMs = System.currentTimeMillis()
        val nowIso = Instant.now().toString()
        val nextColor = normalizeColor(color)

        val byId = collectionDao.listAll(ownerKey = ownerKey, includeDeleted = true).associateBy { it.id }
        val updated =
            ids.mapNotNull { byId[it] }
                .filter { it.deletedAt == null }
                .map { row ->
                    row.copy(
                        color = nextColor,
                        clientUpdatedAtMs = bumpClientUpdatedAtMs(nowMs = nowMs, previousClientUpdatedAtMs = row.clientUpdatedAtMs),
                        updatedAt = nowIso,
                    )
                }
        if (updated.isEmpty()) return
        collectionDao.upsertAll(updated)

        val enqueuedAny = updated.any { enqueueUpsertIfNeeded(ownerKey = ownerKey, entity = it, nowMs = nowMs) }
        if (enqueuedAny) todoSyncScheduler.requestSync()
    }

    override suspend fun move(
        ids: List<String>,
        targetParentId: String?,
    ) {
        if (!isCollectionsEnabled()) return
        val ownerKey = currentOwnerKeyOrNull() ?: return
        if (ids.isEmpty()) return

        if (targetParentId != null) {
            val parent = collectionDao.getById(ownerKey = ownerKey, id = targetParentId) ?: return
            if (parent.deletedAt != null) return
            if (parent.itemType != ITEM_TYPE_FOLDER) return
        }

        val all = collectionDao.listAll(ownerKey = ownerKey, includeDeleted = true)
        val byId = all.associateBy { it.id }
        val folderParentById = all.filter { it.itemType == ITEM_TYPE_FOLDER }.associate { it.id to it.parentId }

        val nowMs = System.currentTimeMillis()
        val nowIso = Instant.now().toString()

        val updated =
            ids.mapNotNull { byId[it] }
                .filter { it.deletedAt == null }
                .filter { row ->
                    if (row.itemType != ITEM_TYPE_FOLDER) return@filter true
                    isMoveValid(parentById = folderParentById, movingFolderId = row.id, targetParentId = targetParentId)
                }
                .map { row ->
                    row.copy(
                        parentId = targetParentId,
                        clientUpdatedAtMs = bumpClientUpdatedAtMs(nowMs = nowMs, previousClientUpdatedAtMs = row.clientUpdatedAtMs),
                        updatedAt = nowIso,
                    )
                }
        if (updated.isEmpty()) return
        collectionDao.upsertAll(updated)

        val enqueuedAny = updated.any { enqueueUpsertIfNeeded(ownerKey = ownerKey, entity = it, nowMs = nowMs) }
        if (enqueuedAny) todoSyncScheduler.requestSync()
    }

    override suspend fun reorder(
        parentId: String?,
        orderedIds: List<String>,
    ) {
        if (!isCollectionsEnabled()) return
        val ownerKey = currentOwnerKeyOrNull() ?: return
        if (orderedIds.isEmpty()) return

        val children = collectionDao.listChildren(ownerKey = ownerKey, parentId = parentId)
        val byId = children.associateBy { it.id }

        val nowMs = System.currentTimeMillis()
        val nowIso = Instant.now().toString()

        val updated =
            orderedIds.mapIndexedNotNull { index, id ->
                val row = byId[id] ?: return@mapIndexedNotNull null
                if (row.deletedAt != null) return@mapIndexedNotNull null
                row.copy(
                    sortOrder = index,
                    clientUpdatedAtMs = bumpClientUpdatedAtMs(nowMs = nowMs, previousClientUpdatedAtMs = row.clientUpdatedAtMs),
                    updatedAt = nowIso,
                )
            }
        if (updated.isEmpty()) return
        collectionDao.upsertAll(updated)

        val enqueuedAny = updated.any { enqueueUpsertIfNeeded(ownerKey = ownerKey, entity = it, nowMs = nowMs) }
        if (enqueuedAny) todoSyncScheduler.requestSync()
    }

    override suspend fun delete(id: String) {
        if (!isCollectionsEnabled()) return
        val ownerKey = currentOwnerKeyOrNull() ?: return

        val root = collectionDao.getById(ownerKey = ownerKey, id = id) ?: return
        if (root.deletedAt != null) return

        val subtree = collectionDao.listSubtree(ownerKey = ownerKey, rootId = id)
        if (subtree.isEmpty()) return

        val nowMs = System.currentTimeMillis()
        val nowIso = Instant.now().toString()
        val maxPrevMs = subtree.maxOf { it.clientUpdatedAtMs }
        val bumped = bumpClientUpdatedAtMs(nowMs = nowMs, previousClientUpdatedAtMs = maxPrevMs)

        collectionDao.tombstoneSubtree(
            ownerKey = ownerKey,
            rootId = id,
            deletedAt = nowIso,
            clientUpdatedAtMs = bumped,
            updatedAt = nowIso,
        )

        val idsToDelete =
            subtree
                .asSequence()
                .filter { !it.localOnly }
                .filter { it.deletedAt == null }
                .filter { it.itemType != ITEM_TYPE_NOTE_REF || !it.refId.isNullOrBlank() }
                .map { it.id }
                .distinct()
                .toList()

        var enqueuedAny = false
        for (entityId in idsToDelete) {
            enqueueDelete(ownerKey = ownerKey, entityId = entityId, clientUpdatedAtMs = bumped, nowMs = nowMs)
            enqueuedAny = true
        }

        if (enqueuedAny) todoSyncScheduler.requestSync()
    }

    override suspend fun batchDelete(ids: List<String>) {
        if (ids.isEmpty()) return
        ids.forEach { delete(it) }
    }

    override suspend fun addMemoToFavorites(memoUuid: String): String {
        if (memoUuid.isBlank()) return ""

        // 使用真实 ownerKey，未登录则用本地键
        val ownerKey = currentOwnerKeyOrNull() ?: LOCAL_OWNER_KEY

        // 查找或创建「收藏」文件夹（本地优先，不依赖网络）
        val folderId = findOrCreateFavoritesFolder(ownerKey)
        if (folderId.isBlank()) return ""

        // 幂等：已存在则不重复添加
        val existing =
            collectionDao.listChildren(ownerKey = ownerKey, parentId = folderId)
                .firstOrNull { it.refLocalUuid == memoUuid && it.deletedAt == null }
        if (existing != null) return existing.id

        val nowMs = System.currentTimeMillis()
        val nowIso = Instant.now().toString()
        val id = UUID.randomUUID().toString()
        val sortOrder = nextSortOrder(ownerKey = ownerKey, parentId = folderId)

        val entity =
            CollectionItemEntity(
                ownerKey = ownerKey,
                id = id,
                itemType = ITEM_TYPE_NOTE_REF,
                parentId = folderId,
                name = "",
                color = null,
                refType = REF_TYPE_MEMOS_MEMO,
                refId = null,
                sortOrder = sortOrder,
                clientUpdatedAtMs = nowMs,
                createdAt = nowIso,
                updatedAt = nowIso,
                deletedAt = null,
                localOnly = true,
                refLocalUuid = memoUuid,
            )
        collectionDao.upsert(entity)
        return id
    }

    override suspend fun backfillMemoRefId(
        memoUuid: String,
        memoServerId: String,
    ) {
        if (!isCollectionsEnabled()) return
        val ownerKey = currentOwnerKeyOrNull() ?: return
        val serverId = memoServerId.trim()
        if (serverId.isBlank()) return
        val uuid = memoUuid.trim()
        if (uuid.isBlank()) return

        val targets =
            collectionDao.listMemoRefBackfillTargets(
                ownerKey = ownerKey,
                refType = REF_TYPE_MEMOS_MEMO,
                memoUuid = uuid,
            )
        if (targets.isEmpty()) return

        val nowMs = System.currentTimeMillis()
        val nowIso = Instant.now().toString()

        val updated =
            targets.map { row ->
                row.copy(
                    refId = serverId,
                    refLocalUuid = null,
                    clientUpdatedAtMs = bumpClientUpdatedAtMs(nowMs = nowMs, previousClientUpdatedAtMs = row.clientUpdatedAtMs),
                    updatedAt = nowIso,
                )
            }
        collectionDao.upsertAll(updated)

        val enqueuedAny = updated.any { enqueueUpsertIfNeeded(ownerKey = ownerKey, entity = it, nowMs = nowMs) }
        if (enqueuedAny) todoSyncScheduler.requestSync()
    }

    private fun normalizeColor(color: String?): String? = color?.trim()?.takeIf { it.isNotBlank() }

    private suspend fun nextSortOrder(
        ownerKey: String,
        parentId: String?,
    ): Int {
        val children = collectionDao.listChildren(ownerKey = ownerKey, parentId = parentId, includeDeleted = false)
        val max = children.maxOfOrNull { it.sortOrder } ?: -1
        return max + 1
    }

    private fun buildUpsertData(entity: CollectionItemEntity): Map<String, Any?>? {
        if (entity.localOnly) return null
        if (entity.deletedAt != null) return null

        val itemType = entity.itemType
        if (itemType != ITEM_TYPE_FOLDER && itemType != ITEM_TYPE_NOTE_REF) return null

        if (itemType == ITEM_TYPE_FOLDER) {
            if (entity.name.trim().isBlank()) return null
        }
        if (itemType == ITEM_TYPE_NOTE_REF) {
            val refType = entity.refType?.trim().orEmpty()
            val refId = entity.refId?.trim().orEmpty()
            if (refType.isBlank()) return null
            if (refId.isBlank()) return null
        }

        return buildMap {
            put("item_type", itemType)
            put("parent_id", entity.parentId)
            put("name", entity.name)
            put("color", entity.color)
            put("sort_order", entity.sortOrder)

            if (itemType == ITEM_TYPE_NOTE_REF) {
                put("ref_type", entity.refType)
                put("ref_id", entity.refId)
            }
        }
    }

    private suspend fun enqueueUpsertIfNeeded(
        ownerKey: String,
        entity: CollectionItemEntity,
        nowMs: Long,
    ): Boolean {
        val data = buildUpsertData(entity) ?: return false
        todoSyncDao.upsertOutbox(
            TodoSyncOutboxEntity(
                ownerKey = ownerKey,
                resource = RESOURCE_COLLECTION_ITEM,
                entityId = entity.id,
                op = OP_UPSERT,
                clientUpdatedAtMs = entity.clientUpdatedAtMs,
                dataJson = gson.toJson(data),
                state = STATE_PENDING,
                lastError = null,
                createdAtMs = nowMs,
            ),
        )
        return true
    }

    private suspend fun enqueueDelete(
        ownerKey: String,
        entityId: String,
        clientUpdatedAtMs: Long,
        nowMs: Long,
    ) {
        todoSyncDao.upsertOutbox(
            TodoSyncOutboxEntity(
                ownerKey = ownerKey,
                resource = RESOURCE_COLLECTION_ITEM,
                entityId = entityId,
                op = OP_DELETE,
                clientUpdatedAtMs = clientUpdatedAtMs,
                dataJson = null,
                state = STATE_PENDING,
                lastError = null,
                createdAtMs = nowMs,
            ),
        )
    }

    private suspend fun findOrCreateFavoritesFolder(ownerKey: String): String {
        val existing =
            collectionDao.listAll(ownerKey = ownerKey)
                .firstOrNull {
                    it.itemType == ITEM_TYPE_FOLDER &&
                        it.deletedAt == null &&
                        it.name.trim() == FAVORITE_FOLDER_NAME
                }
                ?.id
        if (existing != null) return existing

        val nowMs = System.currentTimeMillis()
        val nowIso = Instant.now().toString()
        val id = UUID.randomUUID().toString()

        val entity =
            CollectionItemEntity(
                ownerKey = ownerKey,
                id = id,
                itemType = ITEM_TYPE_FOLDER,
                parentId = null,
                name = FAVORITE_FOLDER_NAME,
                color = null,
                refType = null,
                refId = null,
                sortOrder = 0,
                clientUpdatedAtMs = nowMs,
                createdAt = nowIso,
                updatedAt = nowIso,
                deletedAt = null,
                localOnly = true,
                refLocalUuid = null,
            )
        collectionDao.upsert(entity)
        return id
    }

    private companion object {
        private const val RESOURCE_COLLECTION_ITEM = "collection_item"

        private const val OP_UPSERT = "upsert"
        private const val OP_DELETE = "delete"
        private const val STATE_PENDING = "PENDING"

        private const val ITEM_TYPE_FOLDER = "folder"
        private const val ITEM_TYPE_NOTE_REF = "note_ref"

        private const val REF_TYPE_MEMOS_MEMO = "memos_memo"

        private const val LOCAL_OWNER_KEY = "local"
        private const val FAVORITE_FOLDER_NAME = "收藏"
    }
}
