package cc.pscly.onememos.data.auth

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cc.pscly.onememos.core.network.FlowAuthRequest
import cc.pscly.onememos.core.network.FlowAuthResponse
import cc.pscly.onememos.core.network.FlowBackendApi
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
import cc.pscly.onememos.domain.model.AppSettings
import cc.pscly.onememos.domain.model.FullSyncStage
import cc.pscly.onememos.domain.model.LoginMode
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette
import cc.pscly.onememos.domain.model.TodoReminderMode
import cc.pscly.onememos.domain.repository.SettingsRepository
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.RequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FlowBackendTokenRefresherTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        FlowBackendCredentialStorage(context).clear()
    }

    @Test
    fun refreshIfPossible_updatesCreatorWhenResolverSucceeds() =
        runBlocking {
            val settingsRepository =
                FakeSettingsRepository(
                    AppSettings(
                        loginMode = LoginMode.BACKEND,
                        dev2Unlocked = true,
                    ),
                )
            val credentialStorage = FlowBackendCredentialStorage(context).apply { clear(); set("alice", "secret") }
            val memosApi =
                RecordingMemosApi().apply {
                    authMeWithAuthorizationResponse = GetCurrentUserResponseDto(user = MemosUserDto(name = "users/alice"))
                }
            val refresher =
                FlowBackendTokenRefresher(
                    settingsRepository = settingsRepository,
                    flowBackendApi =
                        FakeFlowBackendApi(
                            response =
                                Response.success(
                                    FlowAuthResponse(
                                        token = "new-token",
                                        serverUrl = "https://memos.example",
                                    ),
                                ),
                        ),
                    flowBackendCredentialStorage = credentialStorage,
                    currentUserResolver = MemosCurrentUserResolver(memosApi),
                )

            refresher.refreshIfPossible()

            assertEquals("https://memos.example", settingsRepository.snapshot.serverUrl)
            assertEquals("new-token", settingsRepository.snapshot.token)
            assertEquals("users/alice", settingsRepository.snapshot.currentUserCreator)
            assertEquals("Bearer new-token", memosApi.lastAuthorizationHeader)
        }

    @Test
    fun refreshIfPossible_keepsExistingCreatorWhenResolverFails() =
        runBlocking {
            val settingsRepository =
                FakeSettingsRepository(
                    AppSettings(
                        loginMode = LoginMode.BACKEND,
                        currentUserCreator = "users/existing",
                    ),
                )
            val credentialStorage = FlowBackendCredentialStorage(context).apply { clear(); set("alice", "secret") }
            val memosApi =
                RecordingMemosApi().apply {
                    authMeError = IllegalStateException("401")
                    currentUserError = IllegalStateException("401")
                    authStatusError = IllegalStateException("401")
                }
            val refresher =
                FlowBackendTokenRefresher(
                    settingsRepository = settingsRepository,
                    flowBackendApi =
                        FakeFlowBackendApi(
                            response =
                                Response.success(
                                    FlowAuthResponse(
                                        token = "new-token",
                                    ),
                                ),
                        ),
                    flowBackendCredentialStorage = credentialStorage,
                    currentUserResolver = MemosCurrentUserResolver(memosApi),
                )

            refresher.refreshIfPossible()

            assertEquals("new-token", settingsRepository.snapshot.token)
            assertEquals("users/existing", settingsRepository.snapshot.currentUserCreator)
        }

    private class FakeFlowBackendApi(
        private val response: Response<FlowAuthResponse>,
    ) : FlowBackendApi {
        override suspend fun register(body: FlowAuthRequest): Response<FlowAuthResponse> = response

        override suspend fun login(body: FlowAuthRequest): Response<FlowAuthResponse> = response

        override suspend fun changePassword(
            token: String,
            body: cc.pscly.onememos.core.network.ChangePasswordRequest,
        ): Response<cc.pscly.onememos.core.network.FlowChangePasswordResponse> = Response.success(null)
    }

    private class FakeSettingsRepository(
        initial: AppSettings,
    ) : SettingsRepository {
        private val state = MutableStateFlow(initial)
        override val settings: Flow<AppSettings> = state
        val snapshot: AppSettings
            get() = state.value

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
        var lastAuthorizationHeader: String? = null

        override suspend fun authMe(url: String): GetCurrentUserResponseDto =
            throw IllegalStateException("not expected")

        override suspend fun authMeWithAuthorization(
            url: String,
            authorization: String,
        ): GetCurrentUserResponseDto {
            lastAuthorizationHeader = authorization
            authMeError?.let { throw it }
            return authMeWithAuthorizationResponse ?: throw IllegalStateException("missing authMeWithAuthorizationResponse")
        }

        override suspend fun authStatus(url: String): JsonObject =
            throw IllegalStateException("not expected")

        override suspend fun authStatusWithAuthorization(
            url: String,
            authorization: String,
        ): JsonObject {
            lastAuthorizationHeader = authorization
            authStatusError?.let { throw it }
            throw IllegalStateException("missing authStatus fallback")
        }

        override suspend fun currentUser(url: String): JsonObject =
            throw IllegalStateException("not expected")

        override suspend fun currentUserWithAuthorization(
            url: String,
            authorization: String,
        ): JsonObject {
            lastAuthorizationHeader = authorization
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
