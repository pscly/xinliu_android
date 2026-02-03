package cc.pscly.onememos.worker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.workDataOf
import cc.pscly.onememos.core.database.dao.MemoDao
import cc.pscly.onememos.core.database.entity.MemoAttachmentEntity
import cc.pscly.onememos.core.database.entity.MemoEntity
import cc.pscly.onememos.core.network.MemosApi
import cc.pscly.onememos.core.network.MemosIdentityParser
import cc.pscly.onememos.core.network.MemosUrls
import cc.pscly.onememos.core.network.dto.AttachmentRefDto
import cc.pscly.onememos.core.network.dto.CreateAttachmentRequestDto
import cc.pscly.onememos.core.network.dto.CreateMemoRequestDto
import cc.pscly.onememos.core.network.dto.MemoDto
import cc.pscly.onememos.core.network.dto.SetMemoAttachmentsRequestDto
import cc.pscly.onememos.core.network.dto.UpdateMemoRequestDto
import cc.pscly.onememos.domain.derived.MemoDerivedFieldsDeriver
import cc.pscly.onememos.domain.model.FullSyncStage
import cc.pscly.onememos.domain.model.FullSyncStatus
import cc.pscly.onememos.domain.model.MemoServerState
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.SyncStatus
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.core.database.model.MemoWithAttachments
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.time.Instant
import kotlin.math.max
import android.webkit.MimeTypeMap
import java.util.UUID

@HiltWorker
class MemosSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val memoDao: MemoDao,
    private val memosApi: MemosApi,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val settings = settingsRepository.settings.first()
        val serverBase = MemosUrls.normalizeServerBase(settings.serverUrl) ?: return Result.success()
        if (settings.token.isBlank()) return Result.success()

        val isPeriodic = inputData.getBoolean(KEY_IS_PERIODIC, false)
        val forceFull = inputData.getBoolean(KEY_FORCE_FULL_SYNC, false)
        val isFollowup = inputData.getBoolean(KEY_FOLLOWUP_SYNC, false)

        // 周期任务与一次性任务使用不同 unique name，会发生重叠。
        // 规则：fullSync 正在 RUNNING 时，周期任务直接跳过，避免重复进入 full sync/相互踩状态。
        if (isPeriodic && settings.fullSync.status == FullSyncStatus.RUNNING) {
            return Result.success()
        }

        // 周期任务/补跑任务永远不触发 full sync；仅普通一次性任务保留“非 SUCCESS 自动 full sync / forceFull 强制 full sync”。
        val needFull = (!isPeriodic && !isFollowup) && (forceFull || settings.fullSync.status != FullSyncStatus.SUCCESS)
        val fullRunId = if (needFull) UUID.randomUUID().toString() else null
        val fullTracker = FullSyncTracker()

        return try {
            ensureCurrentUserCreator(serverBase, settings.currentUserCreator)

            // 仅在需要全量同步时写入 RUNNING；普通同步不改变 DataStore 状态。
            if (needFull && fullRunId != null) {
                settingsRepository.setFullSyncRunning(fullRunId)
            }

            syncPending(serverBase)

            if (needFull && fullRunId != null) {
                performFullSync(
                    serverBase = serverBase,
                    runId = fullRunId,
                    tracker = fullTracker,
                    memoDao = memoDao,
                    memosApi = memosApi,
                    initialCreator = settingsRepository.settings.first().currentUserCreator.trim(),
                    ensureActive = ::ensureNotStopped,
                    onCreatorResolved = settingsRepository::setCurrentUserCreator,
                    onProgress = { id, stage, pages, items ->
                        settingsRepository.setFullSyncProgress(
                            runId = id,
                            stage = stage,
                            pagesFetched = pages,
                            itemsFetched = items,
                        )
                    },
                    onSuccess = { id, stage, pages, items ->
                        settingsRepository.setFullSyncSuccess(
                            runId = id,
                            stage = stage,
                            pagesFetched = pages,
                            itemsFetched = items,
                        )
                    },
                )
            } else {
                refreshFromServer(serverBase)
            }

            // 同步完成后触发一次“离线图片预取”。若用户在设置里关闭，PrefetchWorker 会快速退出。
            enqueuePrefetchWork()

            // requestSync 使用 KEEP，运行中触发的新同步意图会被吞掉；
            // 因此在本轮成功结束时，如果仍有 DIRTY，则补跑一次（最多一次，避免无限循环）。
            maybeEnqueueFollowupSync(isPeriodic = isPeriodic, isFollowup = isFollowup)
            Result.success()
        } catch (e: CancellationException) {
            // 取消由 WorkManager/协程调度触发；必须透传，避免被当成失败后重试。
            if (needFull && fullRunId != null) {
                // 取消也要落盘，避免 UI/后续逻辑一直认为“正在全量同步”。
                // 必须在 NonCancellable 下写入，否则可能在取消传播过程中被中断。
                withContext(NonCancellable) {
                    settingsRepository.setFullSyncCancelled(
                        runId = fullRunId,
                        stage = fullTracker.stage,
                        pagesFetched = fullTracker.pagesFetchedTotal,
                        itemsFetched = fullTracker.itemsFetchedTotal,
                    )
                }
            }
            throw e
        } catch (e: HttpException) {
            // 401/403：大概率 token 无效，重试意义不大，等待用户修复后再触发
            if (e.code() == 401 || e.code() == 403) {
                if (needFull && fullRunId != null) {
                    // 全量同步需要落盘失败状态；但不重试。
                    settingsRepository.setFullSyncFailed(
                        runId = fullRunId,
                        stage = fullTracker.stage,
                        pagesFetched = fullTracker.pagesFetchedTotal,
                        itemsFetched = fullTracker.itemsFetchedTotal,
                        error = "鉴权失败，请重新登录",
                    )
                }
                Result.success()
            } else {
                if (needFull && fullRunId != null) {
                    settingsRepository.setFullSyncFailed(
                        runId = fullRunId,
                        stage = fullTracker.stage,
                        pagesFetched = fullTracker.pagesFetchedTotal,
                        itemsFetched = fullTracker.itemsFetchedTotal,
                        error = e.message?.take(200) ?: "全量同步失败",
                    )
                }
                Result.retry()
            }
        } catch (e: Exception) {
            if (needFull && fullRunId != null) {
                settingsRepository.setFullSyncFailed(
                    runId = fullRunId,
                    stage = fullTracker.stage,
                    pagesFetched = fullTracker.pagesFetchedTotal,
                    itemsFetched = fullTracker.itemsFetchedTotal,
                    error = e.message?.take(200) ?: "全量同步失败",
                )
            }
            Result.retry()
        }
    }

    private suspend fun ensureNotStopped() {
        if (isStopped) throw CancellationException("Worker stopped")
        currentCoroutineContext().ensureActive()
    }

    private suspend fun ensureCurrentUserCreator(serverBase: String, current: String) {
        if (current.isNotBlank()) return
        val resolved = resolveCurrentUserCreator(serverBase) ?: return
        settingsRepository.setCurrentUserCreator(resolved)
    }

    private suspend fun resolveCurrentUserCreator(serverBase: String): String? {
        val fromAuthStatus =
            runCatching {
                val payload = memosApi.authStatus(MemosUrls.authStatus(serverBase))
                MemosIdentityParser.extractCreatorName(payload)
            }.getOrNull()
        if (!fromAuthStatus.isNullOrBlank()) return fromAuthStatus

        val fromUsersMe =
            runCatching {
                val payload = memosApi.currentUser(MemosUrls.currentUser(serverBase))
                MemosIdentityParser.extractCreatorName(payload)
            }.getOrNull()
        if (!fromUsersMe.isNullOrBlank()) return fromUsersMe

        return null
    }

    private fun enqueuePrefetchWork() {
        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        val request =
            OneTimeWorkRequestBuilder<AttachmentPrefetchWorker>()
                .setConstraints(constraints)
                .addTag(AttachmentPrefetchWorker.TAG)
                .build()

        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            AttachmentPrefetchWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private suspend fun syncPending(serverBase: String) {
        val pending = memoDao.listMemosNeedingSync()
        for (item in pending) {
            val originalMemo = item.memo
            try {
                // 标记“同步中”，给 UI 一个更明确的反馈（同时清空旧错误）。
                memoDao.upsertMemo(
                    originalMemo.copy(
                        syncStatus = SyncStatus.SYNCING,
                        lastSyncError = null,
                    ),
                )

                // 重新读取（可能在上一句已更新），保证拿到最新 serverId
                val currentBefore = memoDao.getMemo(originalMemo.uuid) ?: continue
                val createdNow = currentBefore.memo.serverId.isNullOrBlank()

                val serverId =
                    if (currentBefore.memo.serverId.isNullOrBlank()) {
                        val created = createMemoOnServer(serverBase, currentBefore.memo)
                        val name = created.name ?: throw IllegalStateException("服务端未返回 memo.name")
                        val createdCreator = created.creator?.trim().orEmpty()
                        if (createdCreator.isNotBlank()) {
                            val currentCreator = settingsRepository.settings.first().currentUserCreator.trim()
                            if (currentCreator.isBlank()) {
                                settingsRepository.setCurrentUserCreator(createdCreator)
                            }
                        }

                        // CreateMemo 成功后：只回写 serverId，不再“重键 uuid”。
                        memoDao.upsertMemo(
                            currentBefore.memo.copy(
                                serverId = name,
                                creator = created.creator ?: currentBefore.memo.creator,
                            ),
                        )
                        name
                    } else {
                        currentBefore.memo.serverId
                    }

                if (serverId.isNullOrBlank()) {
                    throw IllegalStateException("memo.serverId 为空")
                }

                // 再次读取（可能刚回写 serverId）
                val current = memoDao.getMemo(originalMemo.uuid) ?: continue

                if (!createdNow) {
                    // 冲突处理（LWW）：若远端更新更“新”，则保留远端为主，并生成一条“冲突副本”上传为新 memo，
                    // 避免出现“本地覆盖服务端导致远端改动丢失”。
                    val remote =
                        runCatching {
                            memosApi.getMemo(url = MemosUrls.memo(serverBase, serverId))
                        }.getOrNull()

                    val remoteUpdatedAt =
                        remote?.let {
                            parseEpochMillis(it.updateTime) ?: parseEpochMillis(it.displayTime) ?: parseEpochMillis(it.createTime)
                        }

                    val localUpdatedAt = current.memo.updatedAt
                    if (remote != null && remoteUpdatedAt != null && remoteUpdatedAt > localUpdatedAt) {
                        handleRemoteNewerConflict(
                            serverBase = serverBase,
                            originalServerId = serverId,
                            local = current,
                            remote = remote,
                            remoteUpdatedAt = remoteUpdatedAt,
                            localUpdatedAt = localUpdatedAt,
                        )
                        continue
                    }

                    // 对“非新建 memo”，正常走 PATCH 更新。
                    updateMemoOnServer(serverBase, current.memo)
                }

                // 上传本地附件（若有）。注意：Room 的 @Relation 顺序不保证，因此这里按 sortOrder/createdAt 稳定排序。
                val orderedAttachments =
                    current.attachments.sortedWith(
                        compareBy<MemoAttachmentEntity> { it.sortOrder }
                            .thenBy { it.createdAt }
                            .thenBy { it.id },
                    )

                val uploadedAttachments = uploadAttachmentsIfNeeded(serverBase, current.memo, orderedAttachments)

                // 将“所有远端附件”设置回 memo（用于：新增绑定、删除绑定、以及顺序同步）。
                // 允许 attachments 为空：用于“删除全部附件”。
                val remoteAttachmentRefs =
                    uploadedAttachments
                        .mapNotNull { it.remoteName }
                        .distinct()
                        .map { AttachmentRefDto(name = it) }

                memosApi.setMemoAttachments(
                    url = MemosUrls.memoAttachments(serverBase, serverId),
                    body = SetMemoAttachmentsRequestDto(
                        name = serverId,
                        attachments = remoteAttachmentRefs,
                    ),
                )

                // 标记成功
                memoDao.upsertMemo(
                    current.memo.copy(
                        syncStatus = SyncStatus.SYNCED,
                        lastSyncError = null,
                    ),
                )
            } catch (e: Exception) {
                val message = e.message?.take(200) ?: "同步失败"
                val entityToUpdate = memoDao.getMemoEntity(originalMemo.uuid) ?: originalMemo
                memoDao.upsertMemo(
                    entityToUpdate.copy(
                        syncStatus = SyncStatus.FAILED,
                        lastSyncError = message,
                    ),
                )
            }
        }
    }

    private suspend fun maybeEnqueueFollowupSync(isPeriodic: Boolean, isFollowup: Boolean) {
        if (isPeriodic || isFollowup) return

        val remaining = memoDao.listMemosNeedingSync()
        if (remaining.isEmpty()) return

        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        val request =
            OneTimeWorkRequestBuilder<MemosSyncWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_FOLLOWUP_SYNC to true))
                // Android 14 更容易冻结后台进程：能拿到 quota 时尽快执行；拿不到则自动降级为普通任务
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(TAG)
                .build()

        // 优先 append 到当前 unique work 链末尾，避免 REPLACE 造成自取消；
        // 若 WorkManager 版本不支持 APPEND，则退化为独立 unique name（只保证“不自取消”）。
        val append = runCatching { ExistingWorkPolicy.valueOf("APPEND") }.getOrNull()
        if (append != null) {
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                append,
                request,
            )
        } else {
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                UNIQUE_FOLLOWUP_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }

    private suspend fun createMemoOnServer(
        serverBase: String,
        memo: MemoEntity,
    ): MemoDto {
        val request =
            CreateMemoRequestDto(
                state = memo.serverState.name,
                content = memo.content,
                visibility = memo.visibility.name,
            )
        return memosApi.createMemo(
            url = MemosUrls.createMemo(serverBase),
            memo = request,
        )
    }

    private suspend fun updateMemoOnServer(
        serverBase: String,
        memo: MemoEntity,
    ) {
        val serverId = memo.serverId ?: return

        val updateMask = listOf("content", "visibility", "pinned", "state").joinToString(",")
        val request =
            UpdateMemoRequestDto(
                name = serverId,
                state = memo.serverState.name,
                content = memo.content,
                visibility = memo.visibility.name,
                pinned = memo.pinned,
            )

        memosApi.updateMemo(
            url = MemosUrls.memo(serverBase, serverId),
            updateMask = updateMask,
            memo = request,
        )
    }

    private suspend fun handleRemoteNewerConflict(
        serverBase: String,
        originalServerId: String,
        local: MemoWithAttachments,
        remote: MemoDto,
        remoteUpdatedAt: Long,
        localUpdatedAt: Long,
    ) {
        val localMemo = local.memo

        // 1) 先把本地改动以“冲突副本”方式保留下来（并尽量在本次同步中直接上传）。
        val conflictUuid = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val conflictContent =
            buildString {
                appendLine("【冲突副本】原记录：$originalServerId")
                appendLine("本地编辑时间：${Instant.ofEpochMilli(localUpdatedAt)}")
                appendLine("服务器更新时间：${Instant.ofEpochMilli(remoteUpdatedAt)}")
                appendLine()
                append(localMemo.content)
            }
        val derived = MemoDerivedFieldsDeriver.derive(content = conflictContent, now = now)

        val conflictMemo =
            MemoEntity(
                uuid = conflictUuid,
                serverId = null,
                creator = localMemo.creator,
                content = conflictContent,
                plainPreview = derived.plainPreview,
                tagsText = derived.tagsText,
                derivedVersion = derived.derivedVersion,
                derivedAt = derived.derivedAt,
                createdAt = now,
                updatedAt = now,
                serverState = localMemo.serverState,
                visibility = localMemo.visibility,
                pinned = localMemo.pinned,
                syncStatus = SyncStatus.SYNCING,
                lastSyncError = null,
            )

        val localOnlyAttachments =
            local.attachments
                .filter { !it.localUri.isNullOrBlank() }
                .sortedWith(
                    compareBy<MemoAttachmentEntity> { it.sortOrder }
                        .thenBy { it.createdAt }
                        .thenBy { it.id },
                )
                .mapIndexed { index, a ->
                    a.copy(
                        id = 0,
                        memoUuid = conflictUuid,
                        // 冲突副本作为“新 memo”上传：这里强制视为本地附件，避免复用旧 remoteName 带来的绑定失败风险。
                        cacheUri = null,
                        remoteName = null,
                        sortOrder = index,
                    )
                }

        memoDao.upsertMemoWithAttachments(memo = conflictMemo, attachments = localOnlyAttachments)

        try {
            val created = createMemoOnServer(serverBase, conflictMemo)
            val conflictServerId = created.name ?: throw IllegalStateException("服务端未返回 memo.name")

            val createdCreator = created.creator?.trim().orEmpty()
            if (createdCreator.isNotBlank()) {
                val currentCreator = settingsRepository.settings.first().currentUserCreator.trim()
                if (currentCreator.isBlank()) {
                    settingsRepository.setCurrentUserCreator(createdCreator)
                }
            }

            memoDao.upsertMemo(
                conflictMemo.copy(
                    serverId = conflictServerId,
                    creator = created.creator ?: conflictMemo.creator,
                ),
            )

            val current = memoDao.getMemo(conflictUuid)
                ?: throw IllegalStateException("冲突副本写入/读取本地数据库失败")
            val orderedAttachments =
                current.attachments.sortedWith(
                    compareBy<MemoAttachmentEntity> { it.sortOrder }
                        .thenBy { it.createdAt }
                        .thenBy { it.id },
                )
            val uploadedAttachments = uploadAttachmentsIfNeeded(serverBase, current.memo, orderedAttachments)

            val remoteAttachmentRefs =
                uploadedAttachments
                    .mapNotNull { it.remoteName }
                    .distinct()
                    .map { AttachmentRefDto(name = it) }

            memosApi.setMemoAttachments(
                url = MemosUrls.memoAttachments(serverBase, conflictServerId),
                body = SetMemoAttachmentsRequestDto(
                    name = conflictServerId,
                    attachments = remoteAttachmentRefs,
                ),
            )

            memoDao.upsertMemo(
                current.memo.copy(
                    syncStatus = SyncStatus.SYNCED,
                    lastSyncError = null,
                ),
            )
        } catch (e: Exception) {
            val message = e.message?.take(200) ?: "同步失败"
            val entityToUpdate = memoDao.getMemoEntity(conflictUuid) ?: conflictMemo
            memoDao.upsertMemo(
                entityToUpdate.copy(
                    syncStatus = SyncStatus.FAILED,
                    lastSyncError = message,
                ),
            )
        }

        // 2) 远端更新较新：回滚本地原记录为远端版本，避免覆盖服务端。
        val entity = remote.toMemoEntity(existing = localMemo)
        val attachmentEntities =
            remote.toAttachmentEntities(
                memoUuid = entity.uuid,
                existingAttachments = local.attachments,
            )
        memoDao.upsertMemoWithAttachments(
            memo = entity,
            attachments = attachmentEntities,
        )
    }

    private suspend fun uploadAttachmentsIfNeeded(
        serverBase: String,
        memo: MemoEntity,
        attachments: List<MemoAttachmentEntity>,
    ): List<MemoAttachmentEntity> {
        val serverId = memo.serverId ?: return attachments

        val updated = attachments.toMutableList()
        for ((index, attachment) in attachments.withIndex()) {
            if (!attachment.remoteName.isNullOrBlank()) continue
            val localUri = attachment.localUri ?: continue

            val uri = Uri.parse(localUri)
            val originalFilename = attachment.filename ?: resolveFilename(uri) ?: "attachment_${attachment.id}"
            val originalMimeType = resolveMimeType(uri = uri, filename = originalFilename, explicitMime = attachment.mimeType)

            val payload = prepareUploadPayload(uri = uri, filename = originalFilename, mimeType = originalMimeType)
            val contentBase64 = Base64.encodeToString(payload.bytes, Base64.NO_WRAP)
            val created =
                memosApi.createAttachment(
                    url = MemosUrls.createAttachment(serverBase),
                    attachment = CreateAttachmentRequestDto(
                        filename = payload.filename,
                        contentBase64 = contentBase64,
                        type = payload.mimeType,
                        memo = serverId,
                        externalLink = null,
                    ),
                )

            val remoteName = created.name ?: throw IllegalStateException("服务端未返回 attachment.name")

            val newEntity =
                attachment.copy(
                    remoteName = remoteName,
                    filename = created.filename ?: payload.filename,
                    mimeType = created.type ?: payload.mimeType,
                )
            memoDao.upsertAttachments(listOf(newEntity))
            updated[index] = newEntity
        }
        return updated
    }

    private data class UploadPayload(
        val bytes: ByteArray,
        val filename: String,
        val mimeType: String,
    )

    private fun prepareUploadPayload(uri: Uri, filename: String, mimeType: String): UploadPayload {
        // memos API 需要 base64；为避免大图直接读入内存导致 OOM，这里对图片做“降采样 + 压缩”。
        val isImage = mimeType.startsWith("image/")
        val isGif = mimeType.equals("image/gif", ignoreCase = true)

        if (isImage && !isGif) {
            val compressed = runCatching { compressImage(uri, filename, mimeType) }.getOrNull()
            if (compressed != null) return compressed
        }

        val bytes = openInputStreamSmart(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("无法读取附件（可能权限已失效）：$uri")
        return UploadPayload(bytes = bytes, filename = filename, mimeType = mimeType)
    }

    private fun compressImage(uri: Uri, filename: String, mimeType: String): UploadPayload? {
        val resolver = applicationContext.contentResolver
        val source =
            if (uri.scheme.equals("file", ignoreCase = true)) {
                val path = uri.path ?: return null
                ImageDecoder.createSource(File(path))
            } else {
                ImageDecoder.createSource(resolver, uri)
            }

        val maxSide = 2048
        val bitmap =
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val longest = max(w, h).coerceAtLeast(1)
                if (longest > maxSide) {
                    val scale = maxSide.toFloat() / longest.toFloat()
                    decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                }
                // 避免占用过多 GPU/硬件位图内存，后台上传更稳
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }

        val usePng = bitmap.hasAlpha() && mimeType.equals("image/png", ignoreCase = true)
        val outFormat = if (usePng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val outMime = if (usePng) "image/png" else "image/jpeg"
        val outFilename = normalizeFilenameForMime(filename, outMime)
        val quality = if (usePng) 100 else 85

        val out = ByteArrayOutputStream()
        val ok = bitmap.compress(outFormat, quality, out)
        if (!ok) return null

        val bytes = out.toByteArray()
        if (bytes.isEmpty()) return null
        return UploadPayload(bytes = bytes, filename = outFilename, mimeType = outMime)
    }

    private fun openInputStreamSmart(uri: Uri): java.io.InputStream? {
        return if (uri.scheme.equals("file", ignoreCase = true)) {
            val path = uri.path ?: return null
            runCatching { FileInputStream(File(path)) }.getOrNull()
        } else {
            runCatching { applicationContext.contentResolver.openInputStream(uri) }.getOrNull()
        }
    }

    private fun normalizeFilenameForMime(filename: String, mimeType: String): String {
        val base = filename.substringBeforeLast('.', missingDelimiterValue = filename).ifBlank { "image" }
        return when (mimeType.lowercase()) {
            "image/jpeg" -> "$base.jpg"
            "image/png" -> "$base.png"
            else -> filename
        }
    }

    private fun resolveFilename(uri: Uri): String? {
        if (uri.scheme.equals("file", ignoreCase = true)) {
            val path = uri.path ?: return null
            return runCatching { File(path).name }.getOrNull()
        }
        return try {
            applicationContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveMimeType(uri: Uri, filename: String, explicitMime: String?): String {
        if (!explicitMime.isNullOrBlank()) return explicitMime

        val resolverType = runCatching { applicationContext.contentResolver.getType(uri) }.getOrNull()
        if (!resolverType.isNullOrBlank()) return resolverType

        val ext = filename.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        if (ext.isNotBlank()) {
            val guessed = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            if (!guessed.isNullOrBlank()) return guessed
        }

        return "application/octet-stream"
    }

    private fun parseEpochMillis(raw: String?): Long? =
        runCatching { if (raw.isNullOrBlank()) null else Instant.parse(raw).toEpochMilli() }.getOrNull()

    private suspend fun refreshFromServer(serverBase: String) {
        var pageToken: String? = null
        var pages = 0
        val creatorState = CreatorState(currentCreator = settingsRepository.settings.first().currentUserCreator.trim())
        while (pages < 4) { // 最多拉 4 页（约 200 条），避免首次就拉爆
            val response =
                memosApi.listMemos(
                    url = MemosUrls.listMemos(serverBase),
                    pageSize = 50,
                    pageToken = pageToken,
                    state = MemoServerState.NORMAL.name,
                    orderBy = "pinned desc, display_time desc",
                )

            for (dto in response.memos) {
                upsertRemoteMemoFromDtoIfSafe(
                    dto = dto,
                    memoDao = memoDao,
                    creatorState = creatorState,
                    onCreatorResolved = settingsRepository::setCurrentUserCreator,
                )
            }

            pageToken = response.nextPageToken
            if (pageToken.isNullOrBlank()) break
            pages += 1
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "one_memos_sync"
        const val TAG = "one_memos_sync"

        private const val UNIQUE_FOLLOWUP_WORK_NAME = "one_memos_sync_followup"

        // WorkManager inputData: 是否强制触发全量同步。
        const val KEY_FORCE_FULL_SYNC = "force_full_sync"

        // WorkManager inputData: 是否为周期同步（周期任务不触发 full sync）。
        const val KEY_IS_PERIODIC = "is_periodic"

        // WorkManager inputData: 是否为补跑同步（补跑只做轻量同步，不再触发 full sync，且避免无限补跑）。
        const val KEY_FOLLOWUP_SYNC = "followup_sync"
    }
}
