package cc.pscly.onememos.settings.account

import android.app.Application
import cc.pscly.onememos.core.network.ChangePasswordRequest
import cc.pscly.onememos.core.network.FlowAuthRequest
import cc.pscly.onememos.core.network.FlowAuthResponse
import cc.pscly.onememos.core.network.FlowBackendApi
import cc.pscly.onememos.core.network.FlowChangePasswordResponse
import cc.pscly.onememos.data.auth.FlowBackendCredentialStorage
import cc.pscly.onememos.domain.model.AppSettings
import cc.pscly.onememos.domain.model.FullSyncStage
import cc.pscly.onememos.domain.model.FullSyncState
import cc.pscly.onememos.domain.model.FullSyncStatus
import cc.pscly.onememos.domain.model.GlobalSyncState
import cc.pscly.onememos.domain.model.LoginMode
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.QuickInsertTimeFormat
import cc.pscly.onememos.domain.model.SyncWorkState
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemeDescriptor
import cc.pscly.onememos.domain.model.ThemePalette
import cc.pscly.onememos.domain.model.TodoReminderMode
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.settings.AccountSyncHealth
import cc.pscly.onememos.domain.settings.AccountSyncSettingsCommand
import cc.pscly.onememos.domain.settings.AccountSyncSettingsResult
import cc.pscly.onememos.domain.settings.FullResyncProgress
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.domain.sync.SyncScheduler
import cc.pscly.onememos.domain.sync.FullResyncScheduleResult
import cc.pscly.onememos.domain.sync.SyncStatusMonitor
import cc.pscly.onememos.domain.sync.TodoReminderScheduler
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AccountSyncSettingsCapabilityImplTest {
    @Test
    fun healthMapper_coversTenStatesWithPriority() {
        val progress = FullResyncProgress(FullSyncStage.NORMAL, 1, 2)

        fun base(
            hasConfiguration: Boolean = true,
            signedIn: Boolean = true,
            authenticationExpired: Boolean = false,
            fullResyncRunning: Boolean = false,
            fullResyncError: SettingsCapabilityError? = null,
            fullResyncCompletedAt: Long? = null,
            fullResyncCompletionId: String? = null,
            syncing: Boolean = false,
            queued: Boolean = false,
            syncError: SettingsCapabilityError? = null,
            lastSuccessAtEpochMs: Long? = 100L,
        ) = AccountSyncHealthInput(
            hasConfiguration = hasConfiguration,
            signedIn = signedIn,
            authenticationExpired = authenticationExpired,
            fullResyncRunning = fullResyncRunning,
            fullResyncProgress = progress,
            fullResyncError = fullResyncError,
            fullResyncCompletedAt = fullResyncCompletedAt,
            fullResyncCompletionId = fullResyncCompletionId,
            syncing = syncing,
            queued = queued,
            syncError = syncError,
            lastSuccessAtEpochMs = lastSuccessAtEpochMs,
        )

        assertEquals(
            AccountSyncHealth.Unbound,
            AccountSyncHealthMapper.map(base(hasConfiguration = false, signedIn = false)),
        )
        assertEquals(
            AccountSyncHealth.ConfiguredSignedOut,
            AccountSyncHealthMapper.map(base(signedIn = false)),
        )
        assertEquals(
            AccountSyncHealth.AuthenticationExpired,
            AccountSyncHealthMapper.map(
                base(
                    authenticationExpired = true,
                    fullResyncRunning = true,
                    fullResyncError = SettingsCapabilityError.Unknown("x"),
                    syncing = true,
                    queued = true,
                    syncError = SettingsCapabilityError.NetworkUnavailable,
                ),
            ),
        )
        assertEquals(
            AccountSyncHealth.FullResyncRunning(progress),
            AccountSyncHealthMapper.map(base(fullResyncRunning = true, syncing = true)),
        )
        assertEquals(
            AccountSyncHealth.FullResyncFailed(SettingsCapabilityError.Unknown("FULL")),
            AccountSyncHealthMapper.map(
                base(
                    fullResyncError = SettingsCapabilityError.Unknown("FULL"),
                    syncError = SettingsCapabilityError.NetworkUnavailable,
                ),
            ),
        )
        assertEquals(
            AccountSyncHealth.FullResyncCompleted(
                completionId = "run-999",
                completedAtEpochMs = 999L,
            ),
            AccountSyncHealthMapper.map(
                base(
                    fullResyncCompletedAt = 999L,
                    fullResyncCompletionId = "run-999",
                    syncing = true,
                ),
            ),
        )
        assertEquals(
            AccountSyncHealth.Syncing,
            AccountSyncHealthMapper.map(base(syncing = true, queued = true)),
        )
        assertEquals(
            AccountSyncHealth.Queued,
            AccountSyncHealthMapper.map(base(queued = true)),
        )
        assertEquals(
            AccountSyncHealth.Failed(SettingsCapabilityError.NetworkUnavailable),
            AccountSyncHealthMapper.map(base(syncError = SettingsCapabilityError.NetworkUnavailable)),
        )
        assertEquals(
            AccountSyncHealth.Healthy(100L),
            AccountSyncHealthMapper.map(base()),
        )
    }

    @Test
    fun observe_mapsTenStatesFromSettingsAndGlobal() =
        runBlocking {
            val harness = Harness()
            val cap = harness.capability

            harness.settings.value = AppSettings()
            harness.global.value = GlobalSyncState()
            assertTrue(cap.observe().first().health is AccountSyncHealth.Unbound)

            harness.settings.value = AppSettings(serverUrl = "https://x", token = "")
            assertTrue(cap.observe().first().health is AccountSyncHealth.ConfiguredSignedOut)

            harness.settings.value = AppSettings(serverUrl = "https://x", token = "t")
            harness.global.value = GlobalSyncState(lastSuccessAt = 10L)
            assertEquals(AccountSyncHealth.Healthy(10L), cap.observe().first().health)

            harness.global.value =
                GlobalSyncState(workState = SyncWorkState.RUNNING, lastSuccessAt = 10L)
            assertTrue(cap.observe().first().health is AccountSyncHealth.Syncing)

            harness.global.value =
                GlobalSyncState(workState = SyncWorkState.ENQUEUED, lastSuccessAt = 10L)
            assertTrue(cap.observe().first().health is AccountSyncHealth.Queued)

            harness.global.value =
                GlobalSyncState(
                    lastSuccessAt = 10L,
                    lastError = "boom",
                    lastErrorHttpCode = 500,
                    networkOnline = false,
                )
            assertEquals(
                AccountSyncHealth.Failed(SettingsCapabilityError.NetworkUnavailable),
                cap.observe().first().health,
            )

            harness.global.value =
                GlobalSyncState(
                    lastSuccessAt = 10L,
                    lastError = "unauthorized",
                    lastErrorHttpCode = 401,
                    workState = SyncWorkState.IDLE,
                )
            assertTrue(cap.observe().first().health is AccountSyncHealth.AuthenticationExpired)

            harness.global.value = GlobalSyncState(lastSuccessAt = 10L)
            harness.settings.value =
                AppSettings(
                    serverUrl = "https://x",
                    token = "t",
                    fullSync =
                        FullSyncState(
                            status = FullSyncStatus.RUNNING,
                            stage = FullSyncStage.ARCHIVED,
                            pagesFetched = 2,
                            itemsFetched = 3,
                        ),
                )
            val running = cap.observe().first().health as AccountSyncHealth.FullResyncRunning
            assertEquals(FullSyncStage.ARCHIVED, running.progress.stage)
            assertEquals(2, running.progress.pagesFetched)
            assertEquals(3, running.progress.itemsFetched)

            harness.settings.value =
                AppSettings(
                    serverUrl = "https://x",
                    token = "t",
                    fullSync =
                        FullSyncState(
                            status = FullSyncStatus.FAILED,
                            lastError = "timeout",
                        ),
                )
            assertTrue(cap.observe().first().health is AccountSyncHealth.FullResyncFailed)

            harness.settings.value =
                AppSettings(
                    serverUrl = "https://x",
                    token = "t",
                    fullSync =
                        FullSyncState(
                            status = FullSyncStatus.FAILED,
                            lastError = "HTTP 401 unauthorized",
                        ),
                )
            assertTrue(cap.observe().first().health is AccountSyncHealth.AuthenticationExpired)

            harness.settings.value =
                AppSettings(
                    serverUrl = "https://x",
                    token = "t",
                    fullSync =
                        FullSyncState(
                            status = FullSyncStatus.SUCCESS,
                            runId = "run-1234",
                            lastSuccessAt = 1234L,
                        ),
                )
            assertEquals(
                AccountSyncHealth.FullResyncCompleted(
                    completionId = "run-1234",
                    completedAtEpochMs = 1234L,
                ),
                cap.observe().first().health,
            )
        }

    @Test
    fun acknowledgement_yieldsToLatestStableHealth_andNewRunBecomesCompletedAgain() =
        runBlocking {
            val harness = Harness()
            harness.settings.value =
                signedInSettings(
                    FullSyncState(
                        status = FullSyncStatus.SUCCESS,
                        runId = "run-1",
                        lastSuccessAt = 1234L,
                    ),
                )
            harness.global.value = GlobalSyncState(lastSuccessAt = 99L)

            assertEquals(
                AccountSyncHealth.FullResyncCompleted("run-1", 1234L),
                harness.capability.observe().first().health,
            )
            assertEquals(
                AccountSyncSettingsResult.Success,
                harness.capability.execute(
                    AccountSyncSettingsCommand.AcknowledgeFullResyncCompletion("run-1"),
                ),
            )
            assertEquals(AccountSyncHealth.Healthy(99L), harness.capability.observe().first().health)

            harness.global.value = GlobalSyncState(workState = SyncWorkState.RUNNING, lastSuccessAt = 99L)
            assertEquals(AccountSyncHealth.Syncing, harness.capability.observe().first().health)

            harness.global.value = GlobalSyncState(workState = SyncWorkState.ENQUEUED, lastSuccessAt = 99L)
            assertEquals(AccountSyncHealth.Queued, harness.capability.observe().first().health)

            harness.global.value =
                GlobalSyncState(
                    lastSuccessAt = 99L,
                    lastError = "offline",
                    lastErrorHttpCode = 500,
                    networkOnline = false,
                )
            assertEquals(
                AccountSyncHealth.Failed(SettingsCapabilityError.NetworkUnavailable),
                harness.capability.observe().first().health,
            )

            harness.global.value = GlobalSyncState(lastSuccessAt = 99L)
            harness.settings.value =
                signedInSettings(
                    FullSyncState(
                        status = FullSyncStatus.SUCCESS,
                        runId = "run-2",
                        acknowledgedSuccessRunId = "run-1",
                        lastSuccessAt = 2345L,
                    ),
                )
            harness.capability.execute(
                AccountSyncSettingsCommand.AcknowledgeFullResyncCompletion("run-1"),
            )
            assertEquals(
                AccountSyncHealth.FullResyncCompleted("run-2", 2345L),
                harness.capability.observe().first().health,
            )
        }

    @Test
    fun concurrentSyncNow_secondIsIgnoredDuplicate() =
        runBlocking {
            val harness = Harness(holdMs = 250L)
            val first =
                async(Dispatchers.IO) {
                    harness.capability.execute(AccountSyncSettingsCommand.SyncNow)
                }
            // 等第一笔真正拿到锁并进入 scheduler 阻塞
            while (harness.syncScheduler.syncCalls.get() == 0) {
                delay(5)
            }
            val second =
                withContext(Dispatchers.IO) {
                    harness.capability.execute(AccountSyncSettingsCommand.SyncNow)
                }
            assertEquals(AccountSyncSettingsResult.IgnoredDuplicate, second)
            assertEquals(AccountSyncSettingsResult.Success, first.await())
            assertEquals(1, harness.syncScheduler.syncCalls.get())
        }

    @Test
    fun concurrentFullResync_secondIsIgnoredDuplicate() =
        runBlocking {
            val handoff = CompletableDeferred<Unit>()
            val harness = Harness(fullResyncHandoff = handoff)
            val first =
                async(Dispatchers.IO) {
                    harness.capability.execute(AccountSyncSettingsCommand.FullResync)
                }
            while (harness.syncScheduler.fullResyncCalls.get() == 0) {
                delay(5)
            }
            val second =
                withContext(Dispatchers.IO) {
                    harness.capability.execute(AccountSyncSettingsCommand.FullResync)
                }
            assertEquals(AccountSyncSettingsResult.IgnoredDuplicate, second)
            assertEquals(
                AccountSyncSettingsCommand.FullResync,
                harness.capability.observe().first().commandInFlight,
            )
            handoff.complete(Unit)
            assertEquals(AccountSyncSettingsResult.Success, first.await())
            assertEquals(1, harness.syncScheduler.fullResyncCalls.get())
        }

    @Test
    fun schedulerDuplicateBusyAndFailure_preserveTypedCapabilityResults() =
        runBlocking {
            val duplicate = Harness(fullResyncResult = FullResyncScheduleResult.Duplicate)
            assertEquals(
                AccountSyncSettingsResult.IgnoredDuplicate,
                duplicate.capability.execute(AccountSyncSettingsCommand.FullResync),
            )

            val busy = Harness(fullResyncResult = FullResyncScheduleResult.Busy)
            assertEquals(
                AccountSyncSettingsResult.Failure(SettingsCapabilityError.AlreadyRunning),
                busy.capability.execute(AccountSyncSettingsCommand.FullResync),
            )

            val failed = Harness(fullResyncFailure = IllegalStateException("WorkManager enqueue failed"))
            assertEquals(
                AccountSyncSettingsResult.Failure(SettingsCapabilityError.PlatformUnavailable),
                failed.capability.execute(AccountSyncSettingsCommand.FullResync),
            )
        }

    @Test
    fun schedulerCancellation_isPropagated() =
        runBlocking {
            val harness = Harness(fullResyncFailure = CancellationException("cancelled"))
            var cancellation: CancellationException? = null
            try {
                harness.capability.execute(AccountSyncSettingsCommand.FullResync)
            } catch (caught: CancellationException) {
                cancellation = caught
            }
            assertEquals("cancelled", cancellation?.message)
        }

    @Test
    fun logout_clearsAuthAndReschedulesReminders() =
        runBlocking {
            val harness = Harness()
            harness.settings.value =
                AppSettings(
                    serverUrl = "https://x",
                    token = "tok",
                    loginMode = LoginMode.BACKEND,
                    currentUserCreator = "users/1",
                )
            harness.credentialStorage.set("alice", "pw")
            val result = harness.capability.execute(AccountSyncSettingsCommand.Logout)
            assertEquals(AccountSyncSettingsResult.Success, result)
            assertEquals("", harness.settingsRepo.token)
            assertEquals(LoginMode.UNKNOWN, harness.settingsRepo.loginMode)
            assertEquals("", harness.settingsRepo.creator)
            assertEquals(null, harness.credentialStorage.get())
            assertEquals(1, harness.reminderScheduler.calls.get())
        }

    private class Harness(
        holdMs: Long = 0L,
        fullResyncHandoff: CompletableDeferred<Unit>? = null,
        fullResyncResult: FullResyncScheduleResult = FullResyncScheduleResult.Accepted("request-1"),
        fullResyncFailure: Throwable? = null,
    ) {
        val settings = MutableStateFlow(AppSettings())
        val global = MutableStateFlow(GlobalSyncState())
        val settingsRepo = FakeSettingsRepository(settings)
        val syncScheduler =
            FakeSyncScheduler(
                holdMs = holdMs,
                fullResyncHandoff = fullResyncHandoff,
                fullResyncResult = fullResyncResult,
                fullResyncFailure = fullResyncFailure,
            )
        val reminderScheduler = FakeReminderScheduler()
        val credentialStorage =
            FlowBackendCredentialStorage(RuntimeEnvironment.getApplication())
        val api = FakeFlowBackendApi()
        val capability =
            AccountSyncSettingsCapabilityImpl(
                settingsRepository = settingsRepo,
                syncStatusMonitor =
                    object : SyncStatusMonitor {
                        override val globalState: Flow<GlobalSyncState> = global
                    },
                syncScheduler = syncScheduler,
                todoReminderScheduler = reminderScheduler,
                credentialStorage = credentialStorage,
                flowBackendApi = api,
            )
    }

    private class FakeSettingsRepository(
        private val flow: MutableStateFlow<AppSettings>,
    ) : SettingsRepository {
        var token: String = ""
        var loginMode: LoginMode = LoginMode.UNKNOWN
        var creator: String = ""

        override val settings: Flow<AppSettings> = flow

        override suspend fun setWelcomeCompleted(completed: Boolean) = Unit

        override suspend fun setServerUrl(url: String) = Unit

        override suspend fun setToken(token: String) {
            this.token = token
            flow.value = flow.value.copy(token = token)
        }

        override suspend fun setLoginMode(mode: LoginMode) {
            this.loginMode = mode
            flow.value = flow.value.copy(loginMode = mode)
        }

        override suspend fun setCurrentUserCreator(creator: String) {
            this.creator = creator
            flow.value = flow.value.copy(currentUserCreator = creator)
        }

        override suspend fun setDev2Unlocked(unlocked: Boolean) = Unit

        override suspend fun setDev2ShowPublicWorkspaceMemos(enabled: Boolean) = Unit

        override suspend fun setThemePalette(palette: ThemePalette) = Unit
        override suspend fun setThemeDescriptor(descriptor: ThemeDescriptor) = Unit

        override suspend fun setThemeMode(mode: ThemeMode) = Unit

        override suspend fun setDefaultVisibility(visibility: MemoVisibility) = Unit

        override suspend fun setRegexSearchEnabled(enabled: Boolean) = Unit

        override suspend fun setShowTagCountsInFilter(enabled: Boolean) = Unit

        override suspend fun setQuickCaptureOverlayEnabled(enabled: Boolean) = Unit

        override suspend fun setQuickInsertTimeEnabled(enabled: Boolean) = Unit

        override suspend fun setQuickInsertTimeFormat(format: QuickInsertTimeFormat) = Unit

        override suspend fun setSealStampDurationMs(durationMs: Int) = Unit

        override suspend fun setOfflineImagePrefetchEnabled(enabled: Boolean) = Unit

        override suspend fun setOfflineImagePrefetchMaxMemos(count: Int) = Unit

        override suspend fun setOfflineImagePrefetchMaxImages(count: Int) = Unit

        override suspend fun setAttachmentCacheMaxMb(mb: Int) = Unit

        override suspend fun setAttachmentUploadMaxMb(mb: Int) = Unit

        override suspend fun setTodoReminderMode(mode: TodoReminderMode) = Unit

        override suspend fun setCalendarIntegrationEnabled(enabled: Boolean) = Unit

        override suspend fun setCalendarIntegrationCalendarId(calendarId: Long?) = Unit

        override suspend fun setCalendarIntegrationSyncReminders(enabled: Boolean) = Unit

        override suspend fun setLastSyncSuccess() = Unit

        override suspend fun setLastSyncError(error: String, httpCode: Int) = Unit

        override suspend fun setDevAutoTagLineKeywords(raw: String) = Unit

        override suspend fun setDevShowAutoTagLineInHome(show: Boolean) = Unit

        override suspend fun setDevShowAutoTagLineInView(show: Boolean) = Unit

        override suspend fun setDevShowAutoTagLineInEdit(show: Boolean) = Unit

        override suspend fun setDevHomeRichPreviewStickyLimit(limit: Int) = Unit

        override suspend fun setFullSyncRunning(runId: String) = Unit

        override suspend fun setFullSyncProgress(
            runId: String,
            stage: FullSyncStage,
            pagesFetched: Int,
            itemsFetched: Int,
        ) = Unit

        override suspend fun setFullSyncSuccess(
            runId: String,
            stage: FullSyncStage,
            pagesFetched: Int,
            itemsFetched: Int,
        ) = Unit

        override suspend fun setFullSyncFailed(
            runId: String,
            stage: FullSyncStage,
            pagesFetched: Int,
            itemsFetched: Int,
            error: String,
        ) = Unit

        override suspend fun acknowledgeFullSyncCompletion(runId: String) {
            val fullSync = flow.value.fullSync
            if (fullSync.status != FullSyncStatus.SUCCESS || fullSync.runId != runId.trim()) return
            flow.value =
                flow.value.copy(
                    fullSync = fullSync.copy(acknowledgedSuccessRunId = runId.trim()),
                )
        }
    }

    private class FakeSyncScheduler(
        private val holdMs: Long,
        private val fullResyncHandoff: CompletableDeferred<Unit>?,
        private val fullResyncResult: FullResyncScheduleResult,
        private val fullResyncFailure: Throwable?,
    ) : SyncScheduler {
        val syncCalls = AtomicInteger(0)
        val fullResyncCalls = AtomicInteger(0)

        override fun requestSync() {
            syncCalls.incrementAndGet()
            if (holdMs > 0L) {
                Thread.sleep(holdMs)
            }
        }

        override suspend fun requestFullResync(): FullResyncScheduleResult {
            fullResyncCalls.incrementAndGet()
            fullResyncHandoff?.await()
            fullResyncFailure?.let { throw it }
            return fullResyncResult
        }
    }

    private fun signedInSettings(fullSync: FullSyncState): AppSettings =
        AppSettings(
            serverUrl = "https://x",
            token = "t",
            fullSync = fullSync,
        )

    private class FakeReminderScheduler : TodoReminderScheduler {
        val calls = AtomicInteger(0)

        override fun requestReschedule() {
            calls.incrementAndGet()
        }
    }

    private class FakeFlowBackendApi : FlowBackendApi {
        override suspend fun register(body: FlowAuthRequest): Response<FlowAuthResponse> {
            error("unused")
        }

        override suspend fun login(body: FlowAuthRequest): Response<FlowAuthResponse> {
            error("unused")
        }

        override suspend fun changePassword(
            token: String,
            body: ChangePasswordRequest,
        ): Response<FlowChangePasswordResponse> {
            return Response.success(FlowChangePasswordResponse(ok = true))
        }
    }
}
