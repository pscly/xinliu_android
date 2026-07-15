package cc.pscly.onememos.ui.feature.auth

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import cc.pscly.onememos.core.network.ChangePasswordRequest
import cc.pscly.onememos.core.network.FlowAuthRequest
import cc.pscly.onememos.core.network.FlowAuthResponse
import cc.pscly.onememos.core.network.FlowBackendApi
import cc.pscly.onememos.core.network.FlowChangePasswordResponse
import cc.pscly.onememos.core.network.MemosApi
import cc.pscly.onememos.core.network.MemosCurrentUserResolver
import cc.pscly.onememos.core.network.dto.AttachmentDto
import cc.pscly.onememos.core.network.dto.CreateAttachmentRequestDto
import cc.pscly.onememos.core.network.dto.CreateMemoRequestDto
import cc.pscly.onememos.core.network.dto.EmptyDto
import cc.pscly.onememos.core.network.dto.GetCurrentUserResponseDto
import cc.pscly.onememos.core.network.dto.ListMemosResponseDto
import cc.pscly.onememos.core.network.dto.MemoDto
import cc.pscly.onememos.core.network.dto.MemosUserDto
import cc.pscly.onememos.core.network.dto.SetMemoAttachmentsRequestDto
import cc.pscly.onememos.core.network.dto.UpdateMemoRequestDto
import cc.pscly.onememos.data.auth.FlowBackendCredentialStorage
import cc.pscly.onememos.domain.model.AppSettings
import cc.pscly.onememos.domain.model.FullSyncStage
import cc.pscly.onememos.domain.model.LoginMode
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette
import cc.pscly.onememos.domain.model.TodoReminderMode
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.sync.SyncScheduler
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.RequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        FlowBackendCredentialStorage(context).clear()
    }

    @Test
    fun saveCustom_whenCreatorResolveFails_doesNotPersistSession() =
        runBlocking {
            val settingsRepository = MutableSettingsRepository(AppSettings(dev2Unlocked = true))
            val syncScheduler = FakeSyncScheduler()
            val memosApi =
                RecordingMemosApi().apply {
                    authMeError = IllegalStateException("401")
                    currentUserError = IllegalStateException("401")
                    authStatusError = IllegalStateException("401")
                }
            val viewModel =
                AuthViewModel(
                    flowBackendApi = FakeFlowBackendApi(),
                    currentUserResolver = MemosCurrentUserResolver(memosApi),
                    settingsRepository = settingsRepository,
                    syncScheduler = syncScheduler,
                    flowBackendCredentialStorage = FlowBackendCredentialStorage(context),
                    savedStateHandle = SavedStateHandle(),
                )

            viewModel.updateCustomServerUrl("https://memos.example")
            viewModel.updateCustomToken("bad-token")
            viewModel.saveCustom()

            await { !viewModel.uiState.value.loading }

            val snapshot = settingsRepository.settings.first()
            assertEquals("", snapshot.serverUrl)
            assertEquals("", snapshot.token)
            assertEquals(LoginMode.UNKNOWN, snapshot.loginMode)
            assertEquals(0, syncScheduler.requestCount)
            assertNotNull(viewModel.uiState.value.error)
        }

    @Test
    fun submitBackend_whenResolverSucceeds_persistsSessionAndCredentials() =
        runBlocking {
            val settingsRepository = MutableSettingsRepository(AppSettings(dev2Unlocked = true))
            val syncScheduler = FakeSyncScheduler()
            val credentialStorage = FlowBackendCredentialStorage(context).apply { clear() }
            val memosApi =
                RecordingMemosApi().apply {
                    authMeWithAuthorizationResponse = GetCurrentUserResponseDto(user = MemosUserDto(name = "users/alice"))
                }
            val viewModel =
                AuthViewModel(
                    flowBackendApi =
                        FakeFlowBackendApi(
                    response =
                                Response.success(
                                    FlowAuthResponse(
                                        token = "backend-token",
                                        serverUrl = "https://memos.example",
                                    ),
                                ),
                        ),
                    currentUserResolver = MemosCurrentUserResolver(memosApi),
                    settingsRepository = settingsRepository,
                    syncScheduler = syncScheduler,
                    flowBackendCredentialStorage = credentialStorage,
                    savedStateHandle = SavedStateHandle(),
                )

            viewModel.updateUsername("alice")
            viewModel.updatePassword("secret123")
            viewModel.submitBackend()

            await { syncScheduler.requestCount == 1 }

            val snapshot = settingsRepository.settings.first()
            assertEquals("https://memos.example", snapshot.serverUrl)
            assertEquals("backend-token", snapshot.token)
            assertEquals(LoginMode.BACKEND, snapshot.loginMode)
            assertEquals("users/alice", snapshot.currentUserCreator)
            assertEquals(true, snapshot.welcomeCompleted)
            assertEquals("alice", credentialStorage.get()?.username)
            assertEquals("secret123", credentialStorage.get()?.password)
            assertNull(viewModel.uiState.value.error)
        }

    private suspend fun await(timeoutMs: Long = 1_500L, condition: suspend () -> Boolean) {
        withTimeout(timeoutMs) {
            while (!condition()) {
                delay(10)
            }
        }
    }

    class MainDispatcherRule : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(Dispatchers.Unconfined)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }

    private class FakeSyncScheduler : SyncScheduler {
        var requestCount: Int = 0

        override fun requestSync() {
            requestCount += 1
        }

        override suspend fun requestFullResync(): cc.pscly.onememos.domain.sync.FullResyncScheduleResult {
            return cc.pscly.onememos.domain.sync.FullResyncScheduleResult.Accepted("fake-run")
        }
    }

    private class FakeFlowBackendApi(
        private val response: Response<FlowAuthResponse> = Response.success(FlowAuthResponse()),
    ) : FlowBackendApi {
        override suspend fun register(body: FlowAuthRequest): Response<FlowAuthResponse> = response

        override suspend fun login(body: FlowAuthRequest): Response<FlowAuthResponse> = response

        override suspend fun changePassword(
            token: String,
            body: ChangePasswordRequest,
        ): Response<FlowChangePasswordResponse> = Response.success(null)
    }

    private class MutableSettingsRepository(
        initial: AppSettings,
    ) : SettingsRepository {
        private val state = MutableStateFlow(initial)
        override val settings: Flow<AppSettings> = state

        override suspend fun setWelcomeCompleted(completed: Boolean) {
            state.value = state.value.copy(welcomeCompleted = completed)
        }

        override suspend fun setServerUrl(url: String) {
            state.value = state.value.copy(serverUrl = url)
        }

        override suspend fun setToken(token: String) {
            state.value = state.value.copy(token = token)
        }

        override suspend fun setLoginMode(mode: LoginMode) {
            state.value = state.value.copy(loginMode = mode)
        }

        override suspend fun setCurrentUserCreator(creator: String) {
            state.value = state.value.copy(currentUserCreator = creator)
        }

        override suspend fun setDev2Unlocked(unlocked: Boolean) {
            state.value = state.value.copy(dev2Unlocked = unlocked)
        }

        override suspend fun setDev2ShowPublicWorkspaceMemos(enabled: Boolean) = Unit
        override suspend fun setThemePalette(palette: ThemePalette) = Unit
        override suspend fun setThemeMode(mode: ThemeMode) = Unit
        override suspend fun setDefaultVisibility(visibility: MemoVisibility) = Unit
        override suspend fun setRegexSearchEnabled(enabled: Boolean) = Unit
        override suspend fun setShowTagCountsInFilter(enabled: Boolean) = Unit
        override suspend fun setQuickCaptureOverlayEnabled(enabled: Boolean) = Unit
        override suspend fun setQuickInsertTimeEnabled(enabled: Boolean) = Unit
        override suspend fun setQuickInsertTimeFormat(format: cc.pscly.onememos.domain.model.QuickInsertTimeFormat) = Unit
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
        override suspend fun setFullSyncProgress(runId: String, stage: FullSyncStage, pagesFetched: Int, itemsFetched: Int) = Unit
        override suspend fun setFullSyncSuccess(runId: String, stage: FullSyncStage, pagesFetched: Int, itemsFetched: Int) = Unit
        override suspend fun acknowledgeFullSyncCompletion(runId: String) = Unit
        override suspend fun setFullSyncFailed(runId: String, stage: FullSyncStage, pagesFetched: Int, itemsFetched: Int, error: String) = Unit
    }

    private class RecordingMemosApi : MemosApi {
        var authMeWithAuthorizationResponse: GetCurrentUserResponseDto? = null
        var authMeError: Throwable? = null
        var currentUserError: Throwable? = null
        var authStatusError: Throwable? = null

        override suspend fun authMe(url: String): GetCurrentUserResponseDto =
            throw IllegalStateException("not expected")

        override suspend fun authMeWithAuthorization(
            url: String,
            authorization: String,
        ): GetCurrentUserResponseDto {
            authMeError?.let { throw it }
            return authMeWithAuthorizationResponse ?: throw IllegalStateException("missing authMeWithAuthorizationResponse")
        }

        override suspend fun authStatus(url: String): JsonObject =
            throw IllegalStateException("not expected")

        override suspend fun authStatusWithAuthorization(
            url: String,
            authorization: String,
        ): JsonObject {
            authStatusError?.let { throw it }
            throw IllegalStateException("missing authStatus fallback")
        }

        override suspend fun currentUser(url: String): JsonObject =
            throw IllegalStateException("not expected")

        override suspend fun currentUserWithAuthorization(
            url: String,
            authorization: String,
        ): JsonObject {
            currentUserError?.let { throw it }
            throw IllegalStateException("missing currentUser fallback")
        }

        override suspend fun listMemos(
            url: String,
            pageSize: Int,
            pageToken: String?,
            state: String?,
            orderBy: String?,
            filter: String?,
            showDeleted: Boolean?,
        ): ListMemosResponseDto = ListMemosResponseDto()

        override suspend fun getMemo(url: String): MemoDto = MemoDto()
        override suspend fun createMemo(url: String, memo: CreateMemoRequestDto): MemoDto = MemoDto()
        override suspend fun updateMemo(url: String, updateMask: String, memo: UpdateMemoRequestDto): MemoDto = MemoDto()
        override suspend fun setMemoAttachments(url: String, body: SetMemoAttachmentsRequestDto): EmptyDto = EmptyDto()
        override suspend fun createAttachment(url: String, attachment: CreateAttachmentRequestDto): AttachmentDto = AttachmentDto()
        override suspend fun createAttachmentRaw(url: String, body: RequestBody): AttachmentDto = AttachmentDto()
    }
}
