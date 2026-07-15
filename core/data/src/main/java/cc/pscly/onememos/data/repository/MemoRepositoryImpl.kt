package cc.pscly.onememos.data.repository

import cc.pscly.onememos.core.database.dao.MemoDao
import cc.pscly.onememos.core.database.entity.MemoAttachmentEntity
import cc.pscly.onememos.core.database.entity.MemoEntity
import cc.pscly.onememos.data.mapper.toDomain
import cc.pscly.onememos.domain.derived.MemoDerivedFieldsDeriver
import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.domain.model.MemoAttachmentDraft
import cc.pscly.onememos.domain.model.MemoServerState
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.SyncStatus
import cc.pscly.onememos.domain.repository.MemoRepository
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class MemoRepositoryImpl @Inject constructor(
    private val memoDao: MemoDao,
    private val settingsRepository: SettingsRepository,
    private val syncScheduler: SyncScheduler,
) : MemoRepository {
    private suspend fun defaultVisibilityForNewMemo(): MemoVisibility {
        val settings = settingsRepository.settings.first()
        // 未开启开发者模式2时，默认统一提交到“工作区”（PROTECTED），并且不暴露可见性选项。
        return if (settings.dev2Unlocked) settings.defaultVisibility else MemoVisibility.PROTECTED
    }

    private suspend fun nextSyncStatus(serverId: String?): SyncStatus {
        val settings = settingsRepository.settings.first()
        val bound = settings.serverUrl.isNotBlank() && settings.token.isNotBlank()
        return if (!bound && serverId.isNullOrBlank()) {
            SyncStatus.LOCAL_ONLY
        } else {
            SyncStatus.DIRTY
        }
    }

    override fun observeMemos(): Flow<List<Memo>> =
        memoDao.observeMemos().map { list -> list.map { it.toDomain() } }

    override fun observeArchivedMemos(): Flow<List<Memo>> =
        memoDao.observeArchivedMemos().map { list -> list.map { it.toDomain() } }

    override fun observeAllMemos(): Flow<List<Memo>> =
        memoDao.observeAllMemos().map { list -> list.map { it.toDomain() } }

    override fun observeRecentMemos(limit: Int): Flow<List<Memo>> =
        memoDao.observeRecentMemos(limit).map { list -> list.map { it.toDomain() } }

    override fun observeMemosByCreatedAtRange(
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<List<Memo>> =
        memoDao.observeMemosByCreatedAtRange(startInclusive, endExclusive)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun listRecentEditedActiveMemos(limit: Int): List<Memo> =
        memoDao.listRecentEditedActiveMemos(limit).map { it.toDomain() }

    override suspend fun getMemo(uuid: String): Memo? {
        val id = uuid.trim()
        if (id.isBlank()) return null

        val byUuid = memoDao.getMemo(id)?.toDomain()
        if (byUuid != null) return byUuid
        return memoDao.getMemoByServerId(id)?.toDomain()
    }

    override suspend fun archiveMemo(uuid: String) {
        val existing = memoDao.getMemo(uuid)?.memo ?: return
        val now = System.currentTimeMillis()
        memoDao.upsertMemo(
            existing.copy(
                serverState = MemoServerState.ARCHIVED,
                updatedAt = now,
                syncStatus = nextSyncStatus(existing.serverId),
                lastSyncError = null,
            ),
        )
        syncScheduler.requestSync()
    }

    override suspend fun unarchiveMemo(uuid: String) {
        val existing = memoDao.getMemo(uuid)?.memo ?: return
        val now = System.currentTimeMillis()
        memoDao.upsertMemo(
            existing.copy(
                serverState = MemoServerState.NORMAL,
                updatedAt = now,
                syncStatus = nextSyncStatus(existing.serverId),
                lastSyncError = null,
            ),
        )
        syncScheduler.requestSync()
    }

    override suspend fun updateMemoContent(uuid: String, content: String) {
        val existing = memoDao.getMemo(uuid)?.memo ?: return
        val now = System.currentTimeMillis()
        val derived = MemoDerivedFieldsDeriver.derive(content = content, now = now)
        memoDao.upsertMemo(
            existing.copy(
                content = content,
                plainPreview = derived.plainPreview,
                tagsText = derived.tagsText,
                derivedVersion = derived.derivedVersion,
                derivedAt = derived.derivedAt,
                updatedAt = now,
                syncStatus = nextSyncStatus(existing.serverId),
                lastSyncError = null,
            ),
        )
        syncScheduler.requestSync()
    }

    override suspend fun createLocalMemo(
        content: String,
        resourceUris: List<String>,
    ): String {
        val now = System.currentTimeMillis()
        val uuid = UUID.randomUUID().toString()
        val defaultVisibility = defaultVisibilityForNewMemo()
        val derived = MemoDerivedFieldsDeriver.derive(content = content, now = now)

        val memo = MemoEntity(
            uuid = uuid,
            serverId = null,
            creator = null,
            content = content,
            plainPreview = derived.plainPreview,
            tagsText = derived.tagsText,
            derivedVersion = derived.derivedVersion,
            derivedAt = derived.derivedAt,
            createdAt = now,
            updatedAt = now,
            serverState = MemoServerState.NORMAL,
            visibility = defaultVisibility,
            pinned = false,
            syncStatus = nextSyncStatus(serverId = null),
            lastSyncError = null,
        )

        val attachments = resourceUris.mapIndexed { index, uri ->
            MemoAttachmentEntity(
                memoUuid = uuid,
                localUri = uri,
                remoteName = null,
                filename = null,
                mimeType = null,
                createdAt = now,
                sortOrder = index,
            )
        }

        memoDao.upsertMemoWithAttachments(memo = memo, attachments = attachments)
        syncScheduler.requestSync()
        return uuid
    }

    override suspend fun updateLocalMemo(
        uuid: String,
        content: String,
        resourceUris: List<String>,
    ) {
        val existingWithAttachments = memoDao.getMemo(uuid)
        val existingMemo = existingWithAttachments?.memo
        val existingAttachments = existingWithAttachments?.attachments.orEmpty()

        val createdAt = existingMemo?.createdAt ?: System.currentTimeMillis()
        val serverId = existingMemo?.serverId
        val serverState = existingMemo?.serverState ?: MemoServerState.NORMAL
        val visibility = existingMemo?.visibility ?: defaultVisibilityForNewMemo()
        val pinned = existingMemo?.pinned ?: false

        val now = System.currentTimeMillis()
        val derived = MemoDerivedFieldsDeriver.derive(content = content, now = now)
        val memo = MemoEntity(
            uuid = uuid,
            serverId = serverId,
            creator = existingMemo?.creator,
            content = content,
            plainPreview = derived.plainPreview,
            tagsText = derived.tagsText,
            derivedVersion = derived.derivedVersion,
            derivedAt = derived.derivedAt,
            createdAt = createdAt,
            updatedAt = now,
            serverState = serverState,
            visibility = visibility,
            pinned = pinned,
            syncStatus = nextSyncStatus(serverId),
            lastSyncError = null,
        )

        val attachments = resourceUris.mapIndexed { index, uri ->
            val existing = existingAttachments.firstOrNull { it.localUri == uri }
            MemoAttachmentEntity(
                memoUuid = uuid,
                localUri = uri,
                cacheUri = existing?.cacheUri,
                remoteName = existing?.remoteName,
                filename = existing?.filename,
                mimeType = existing?.mimeType,
                createdAt = existing?.createdAt ?: now,
                sortOrder = existing?.sortOrder ?: index,
            )
        }

        memoDao.upsertMemoWithAttachments(memo = memo, attachments = attachments)
        syncScheduler.requestSync()
    }

    override suspend fun updateMemoDraft(
        uuid: String,
        content: String,
        attachments: List<MemoAttachmentDraft>,
    ) {
        val existingWithAttachments = memoDao.getMemo(uuid)
        val existingMemo = existingWithAttachments?.memo
        val existingAttachments = existingWithAttachments?.attachments.orEmpty()

        val createdAt = existingMemo?.createdAt ?: System.currentTimeMillis()
        val serverId = existingMemo?.serverId
        val serverState = existingMemo?.serverState ?: MemoServerState.NORMAL
        val visibility = existingMemo?.visibility ?: defaultVisibilityForNewMemo()
        val pinned = existingMemo?.pinned ?: false

        val now = System.currentTimeMillis()
        val derived = MemoDerivedFieldsDeriver.derive(content = content, now = now)
        val memo =
            MemoEntity(
                uuid = uuid,
                serverId = serverId,
                creator = existingMemo?.creator,
                content = content,
                plainPreview = derived.plainPreview,
                tagsText = derived.tagsText,
                derivedVersion = derived.derivedVersion,
                derivedAt = derived.derivedAt,
                createdAt = createdAt,
                updatedAt = now,
                serverState = serverState,
                visibility = visibility,
                pinned = pinned,
                syncStatus = nextSyncStatus(serverId),
                lastSyncError = null,
            )

        val newEntities =
            attachments.mapIndexed { index, draft ->
                val existing =
                    when {
                        !draft.localUri.isNullOrBlank() -> existingAttachments.firstOrNull { it.localUri == draft.localUri }
                        !draft.remoteName.isNullOrBlank() -> existingAttachments.firstOrNull { it.remoteName == draft.remoteName }
                        else -> null
                    }

                MemoAttachmentEntity(
                    memoUuid = uuid,
                    localUri = draft.localUri,
                    cacheUri = existing?.cacheUri,
                    remoteName = draft.remoteName ?: existing?.remoteName,
                    filename = draft.filename ?: existing?.filename,
                    mimeType = draft.mimeType ?: existing?.mimeType,
                    createdAt = draft.createdAt.takeIf { it > 0 } ?: existing?.createdAt ?: now,
                    sortOrder = index,
                )
            }

        memoDao.upsertMemoWithAttachments(memo = memo, attachments = newEntities)
        syncScheduler.requestSync()
    }
}
