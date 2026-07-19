package cc.pscly.onememos.ui.feature.editor

import androidx.lifecycle.SavedStateHandle
import cc.pscly.onememos.domain.model.AppSettings
import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.domain.model.MemoServerState
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.SyncStatus
import cc.pscly.onememos.navigation.EditorKey
import cc.pscly.onememos.ui.filter.MemoFilterStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * 回归：首页 Navigation3 经 [EditorViewModel.bind] 进入编辑页时，
 * 可编辑性必须与旧 uuidArg init 路径一致：
 * `serverId.isNullOrBlank() || (syncStatus != SYNCED && syncStatus != SYNCING)`。
 *
 * 该回归测试防止 bind() 再次遗漏 canEdit/attachmentsEditable 写入，从而退回初值 true、使远端 SYNCED/SYNCING 漂移为可编辑。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class EditorViewModelBindTest(
    private val caseName: String,
    private val serverId: String?,
    private val syncStatus: SyncStatus,
    private val expectedCanEdit: Boolean,
) {
    @get:Rule
    internal val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun bind_setsCanEditAndAttachmentsEditable_matchingLegacyUuidArgRule() =
        runBlocking {
            // Given：无 uuid 的 SavedStateHandle，确保只走 bind 路径
            val uuid = "memo-uuid-$caseName"
            val memo =
                Memo(
                    uuid = uuid,
                    serverId = serverId,
                    creator = "users/1",
                    content = "body",
                    createdAt = 1L,
                    updatedAt = 2L,
                    serverState = MemoServerState.NORMAL,
                    visibility = MemoVisibility.PRIVATE,
                    pinned = false,
                    syncStatus = syncStatus,
                    attachments = emptyList(),
                    lastSyncError = null,
                )
            val viewModel =
                EditorViewModel(
                    memoRepository = FakeMemoRepository(memo),
                    settingsRepository = FakeSettingsRepository(flowOf(AppSettings())),
                    cacheRepository = FakeCacheRepository(),
                    syncScheduler = FakeSyncScheduler(),
                    filterStore = MemoFilterStore(),
                    savedStateHandle = SavedStateHandle(),
                )

            // When：首页 Navigation3 入口调用 bind(EditorKey)
            viewModel.bind(EditorKey(uuid = uuid))

            // Then：可编辑性与旧 init 路径一致
            await {
                viewModel.uiState.value.uuid == uuid &&
                    viewModel.uiState.value.syncStatus == syncStatus &&
                    viewModel.uiState.value.loadError == null
            }
            val state = viewModel.uiState.value
            assertEquals(
                "case=$caseName serverId=$serverId syncStatus=$syncStatus canEdit",
                expectedCanEdit,
                state.canEdit,
            )
            assertEquals(
                "case=$caseName serverId=$serverId syncStatus=$syncStatus attachmentsEditable",
                expectedCanEdit,
                state.attachmentsEditable,
            )
        }

    private suspend fun await(
        timeoutMs: Long = 1_500L,
        condition: suspend () -> Boolean,
    ) {
        withTimeout(timeoutMs) {
            while (!condition()) {
                delay(10)
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): Collection<Array<Any?>> =
            listOf(
                // 远端已同步：只读
                arrayOf("remote_SYNCED", "memos/1", SyncStatus.SYNCED, false),
                // 远端同步中：只读（避免写回覆盖）
                arrayOf("remote_SYNCING", "memos/2", SyncStatus.SYNCING, false),
                // 远端失败：可编辑重试
                arrayOf("remote_FAILED", "memos/3", SyncStatus.FAILED, true),
                // 远端脏数据：可编辑
                arrayOf("remote_DIRTY", "memos/4", SyncStatus.DIRTY, true),
                // 仅本地（serverId 为空），即便 SYNCED 也必须可编辑
                arrayOf("localOnly_null_serverId_SYNCED", null, SyncStatus.SYNCED, true),
                // 仅本地（serverId 空白）
                arrayOf("localOnly_blank_serverId_SYNCED", "  ", SyncStatus.SYNCED, true),
            )
    }
}
