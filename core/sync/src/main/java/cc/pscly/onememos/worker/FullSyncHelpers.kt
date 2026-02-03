package cc.pscly.onememos.worker

import cc.pscly.onememos.core.database.dao.MemoDao
import cc.pscly.onememos.core.database.entity.MemoAttachmentEntity
import cc.pscly.onememos.core.database.entity.MemoEntity
import cc.pscly.onememos.core.database.model.MemoWithAttachments
import cc.pscly.onememos.core.network.MemosApi
import cc.pscly.onememos.core.network.MemosUrls
import cc.pscly.onememos.core.network.dto.MemoDto
import cc.pscly.onememos.domain.derived.MemoDerivedFieldsDeriver
import cc.pscly.onememos.domain.model.FullSyncStage
import cc.pscly.onememos.domain.model.MemoServerState
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.SyncStatus
import java.time.Instant

internal class FullSyncTracker {
    var stage: FullSyncStage = FullSyncStage.NORMAL
    var pagesFetchedTotal: Int = 0
    var itemsFetchedTotal: Int = 0
}

internal class CreatorState(
    var currentCreator: String,
)

/**
 * 全量同步分页逻辑：按 NORMAL -> ARCHIVED 两段拉取，直到 nextPageToken 为空。
 *
 * 说明：
 * - 为了便于单元测试，这里通过回调注入“进度写回 / creator 写回 / 停止检查”。
 * - 真实落库逻辑由 [upsertRemoteMemoFromDtoIfSafe] 负责。
 */
internal suspend fun refreshFromServerFull(
    serverBase: String,
    runId: String,
    tracker: FullSyncTracker,
    memoDao: MemoDao,
    memosApi: MemosApi,
    creatorState: CreatorState,
    ensureActive: suspend () -> Unit,
    onCreatorResolved: suspend (String) -> Unit,
    onProgress: suspend (
        runId: String,
        stage: FullSyncStage,
        pagesFetched: Int,
        itemsFetched: Int,
    ) -> Unit,
) {
    val stages =
        listOf(
            FullSyncStage.NORMAL to MemoServerState.NORMAL.name,
            FullSyncStage.ARCHIVED to MemoServerState.ARCHIVED.name,
        )

    for ((stage, state) in stages) {
        ensureActive()
        tracker.stage = stage

        var pageToken: String? = null
        val seenTokens = HashSet<String>()
        while (true) {
            ensureActive()

            // 以“请求 token”去重，避免服务端返回重复 nextPageToken 导致死循环。
            val tokenKey = pageToken.orEmpty()
            if (!seenTokens.add(tokenKey)) {
                throw IllegalStateException("全量同步分页 token 重复（stage=$stage）：$tokenKey")
            }

            val response =
                memosApi.listMemos(
                    url = MemosUrls.listMemos(serverBase),
                    pageSize = 500,
                    pageToken = pageToken,
                    state = state,
                    orderBy = "name asc",
                )

            for (dto in response.memos) {
                upsertRemoteMemoFromDtoIfSafe(
                    dto = dto,
                    memoDao = memoDao,
                    creatorState = creatorState,
                    onCreatorResolved = onCreatorResolved,
                )
            }

            tracker.pagesFetchedTotal += 1
            tracker.itemsFetchedTotal += response.memos.size
            onProgress(
                runId,
                stage,
                tracker.pagesFetchedTotal,
                tracker.itemsFetchedTotal,
            )

            pageToken = response.nextPageToken
            if (pageToken.isNullOrBlank()) break
        }
    }
}

/**
 * 全量同步入口：执行分页拉取并在成功后回调写入 SUCCESS。
 *
 * 注意：异常（含取消）由调用方处理；这里仅保证“只有成功跑完才触发 successCallback”。
 */
internal suspend fun performFullSync(
    serverBase: String,
    runId: String,
    tracker: FullSyncTracker,
    memoDao: MemoDao,
    memosApi: MemosApi,
    initialCreator: String,
    ensureActive: suspend () -> Unit,
    onCreatorResolved: suspend (String) -> Unit,
    onProgress: suspend (
        runId: String,
        stage: FullSyncStage,
        pagesFetched: Int,
        itemsFetched: Int,
    ) -> Unit,
    onSuccess: suspend (
        runId: String,
        stage: FullSyncStage,
        pagesFetched: Int,
        itemsFetched: Int,
    ) -> Unit,
) {
    val creatorState = CreatorState(currentCreator = initialCreator)

    refreshFromServerFull(
        serverBase = serverBase,
        runId = runId,
        tracker = tracker,
        memoDao = memoDao,
        memosApi = memosApi,
        creatorState = creatorState,
        ensureActive = ensureActive,
        onCreatorResolved = onCreatorResolved,
        onProgress = onProgress,
    )

    onSuccess(
        runId,
        FullSyncStage.ARCHIVED,
        tracker.pagesFetchedTotal,
        tracker.itemsFetchedTotal,
    )
}

/**
 * 将服务端 memo 写入本地（含附件），但会遵循“本地未同步则不覆盖”的保护逻辑。
 */
internal suspend fun upsertRemoteMemoFromDtoIfSafe(
    dto: MemoDto,
    memoDao: MemoDao,
    creatorState: CreatorState,
    onCreatorResolved: suspend (String) -> Unit,
) {
    val serverId = dto.name ?: return

    if (creatorState.currentCreator.isBlank() && parseVisibility(dto.visibility) == MemoVisibility.PRIVATE) {
        val c = dto.creator?.trim().orEmpty()
        if (c.isNotBlank()) {
            creatorState.currentCreator = c
            onCreatorResolved(c)
        }
    }

    // 本地如果有未同步的同名记录，避免拉取覆盖本地更改
    val localWithAttachments: MemoWithAttachments? = memoDao.getMemoByServerId(serverId)
    val local = localWithAttachments?.memo
    if (local != null && local.syncStatus != SyncStatus.SYNCED) return

    val entity = dto.toMemoEntity(existing = local)
    val attachmentEntities =
        dto.toAttachmentEntities(
            memoUuid = entity.uuid,
            existingAttachments = localWithAttachments?.attachments,
        )
    memoDao.upsertMemoWithAttachments(
        memo = entity,
        attachments = attachmentEntities,
    )
}

internal fun MemoDto.toMemoEntity(existing: MemoEntity?): MemoEntity {
    val serverId = name ?: ""
    val created = parseEpochMillis(displayTime) ?: parseEpochMillis(createTime) ?: System.currentTimeMillis()
    val updated = parseEpochMillis(updateTime) ?: created
    val contentText = content.orEmpty()
    val derived = MemoDerivedFieldsDeriver.derive(content = contentText)

    return MemoEntity(
        localId = existing?.localId ?: 0,
        uuid = existing?.uuid ?: serverId,
        serverId = serverId,
        creator = creator ?: existing?.creator,
        content = contentText,
        plainPreview = derived.plainPreview,
        tagsText = derived.tagsText,
        derivedVersion = derived.derivedVersion,
        derivedAt = derived.derivedAt,
        createdAt = created,
        updatedAt = updated,
        serverState = parseServerState(state),
        visibility = parseVisibility(visibility),
        pinned = pinned ?: false,
        syncStatus = SyncStatus.SYNCED,
        lastSyncError = null,
    )
}

internal fun MemoDto.toAttachmentEntities(
    memoUuid: String,
    existingAttachments: List<MemoAttachmentEntity>?,
): List<MemoAttachmentEntity> =
    attachments.orEmpty().mapIndexedNotNull { index, a ->
        val remoteName = a.name ?: return@mapIndexedNotNull null
        val existing = existingAttachments?.firstOrNull { it.remoteName == remoteName }
        MemoAttachmentEntity(
            memoUuid = memoUuid,
            localUri = null,
            cacheUri = existing?.cacheUri,
            remoteName = remoteName,
            filename = a.filename,
            mimeType = a.type,
            createdAt = parseEpochMillis(a.createTime) ?: System.currentTimeMillis(),
            sortOrder = index,
        )
    }

private fun parseEpochMillis(raw: String?): Long? =
    runCatching { if (raw.isNullOrBlank()) null else Instant.parse(raw).toEpochMilli() }.getOrNull()

private fun parseServerState(raw: String?): MemoServerState =
    runCatching { if (raw.isNullOrBlank()) null else MemoServerState.valueOf(raw) }.getOrNull()
        ?: MemoServerState.NORMAL

private fun parseVisibility(raw: String?): MemoVisibility =
    runCatching { if (raw.isNullOrBlank()) null else MemoVisibility.valueOf(raw) }.getOrNull()
        ?: MemoVisibility.PRIVATE
