package cc.pscly.onememos.settings

import cc.pscly.onememos.domain.model.AppSettings
import cc.pscly.onememos.domain.model.FullSyncStage
import cc.pscly.onememos.domain.model.FullSyncState
import cc.pscly.onememos.domain.model.FullSyncStatus
import cc.pscly.onememos.domain.model.GlobalSyncState
import cc.pscly.onememos.domain.model.LastSyncState
import cc.pscly.onememos.domain.model.LoginMode
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.QuickInsertTimeFormat
import cc.pscly.onememos.domain.model.SyncWorkState
import cc.pscly.onememos.domain.model.ThemeDescriptor
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette
import cc.pscly.onememos.domain.model.TodoReminderMode
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.settings.SectionSummaryState
import cc.pscly.onememos.domain.settings.SettingsHubCapability
import cc.pscly.onememos.domain.settings.SummaryIssueKind
import cc.pscly.onememos.domain.sync.SyncStatusMonitor
import cc.pscly.onememos.update.AppIdentityPort
import cc.pscly.onememos.update.AppUpdateManager
import cc.pscly.onememos.update.AppUpdatePhase
import cc.pscly.onememos.update.AppUpdateStore
import cc.pscly.onememos.update.AppUpdateUiState
import cc.pscly.onememos.update.GitHubReleaseDto
import cc.pscly.onememos.update.GitHubUpdateApi
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import android.app.Application

/**
 * Hub 只读摘要：六项独立状态、异常优先、零副作用。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SettingsHubCapabilityImplTest {
    @Test
    fun observe_mapsSixReadySections_withoutSideEffects() =
        runBlocking {
            val harness = Harness(
                settings =
                    AppSettings(
                        serverUrl = "https://example.com",
                        token = "tok",
                        defaultVisibility = MemoVisibility.PRIVATE,
                        regexSearchEnabled = true,
                        todoReminderMode = TodoReminderMode.SMART,
                        calendarIntegrationEnabled = true,
                        calendarIntegrationCalendarId = 7L,
                        offlineImagePrefetchEnabled = true,
                        attachmentCacheMaxMb = 512,
                        themeDescriptor = ThemeDescriptor.fromLegacyPalette(ThemePalette.INDIGO),
                        themeMode = ThemeMode.DARK,
                        lastSync = LastSyncState(lastSuccessAt = 1_700_000_000_000L),
                    ),
                global =
                    GlobalSyncState(
                        workState = SyncWorkState.IDLE,
                        lastSuccessAt = 1_700_000_000_000L,
                    ),
            )
            val snap = harness.capability.observe().first { allReady(it) }

            val account = snap.accountSync as SectionSummaryState.Ready
            assertEquals("ACCOUNT_HEALTHY", account.primary.value)
            assertEquals("LAST_SUCCESS_AT_1700000000000", account.secondary?.value)
            assertNull(account.issue)

            val record = snap.recordEditing as SectionSummaryState.Ready
            assertEquals("VISIBILITY_PRIVATE", record.primary.value)
            assertEquals("REGEX_ON", record.secondary?.value)

            val reminder = snap.reminderCalendar as SectionSummaryState.Ready
            assertEquals("REMINDER_SMART", reminder.primary.value)
            assertEquals("CALENDAR_CONNECTED", reminder.secondary?.value)
            assertNull(reminder.issue)

            val storage = snap.storageOffline as SectionSummaryState.Ready
            assertEquals("PREFETCH_ON", storage.primary.value)
            assertEquals("CACHE_LIMIT_MB_512", storage.secondary?.value)

            val appearance = snap.appearanceInteraction as SectionSummaryState.Ready
            assertEquals("THEME_INDIGO", appearance.primary.value)
            assertEquals("MODE_DARK", appearance.secondary?.value)

            val about = snap.aboutAdvanced as SectionSummaryState.Ready
            assertEquals("VERSION_1.9.0", about.primary.value)
            assertTrue(about.secondary?.value?.startsWith("UPDATE_") == true)

            assertEquals(0, harness.api.calls.get())
            assertEquals(0, harness.sideEffects.syncCalls.get())
            assertEquals(0, harness.sideEffects.fullResyncCalls.get())
            assertEquals(0, harness.sideEffects.scanCalls.get())
            assertEquals(0, harness.sideEffects.calendarWriteCalls.get())
            assertEquals(0, harness.sideEffects.permissionCalls.get())
            assertEquals(0, harness.sideEffects.exportCalls.get())
        }

    @Test
    fun observe_prefersAuthIssue_andKeepsOtherSectionsReady() =
        runBlocking {
            val harness = Harness(
                settings =
                    AppSettings(
                        serverUrl = "https://example.com",
                        token = "tok",
                        calendarIntegrationEnabled = true,
                        calendarIntegrationCalendarId = null,
                    ),
                global =
                    GlobalSyncState(
                        lastError = "unauthorized",
                        lastErrorHttpCode = 401,
                        lastSuccessAt = 100L,
                    ),
            )
            val snap = harness.capability.observe().first { allReady(it) }

            val account = snap.accountSync as SectionSummaryState.Ready
            assertEquals("ACCOUNT_AUTH_EXPIRED", account.primary.value)
            assertEquals(SummaryIssueKind.AUTHENTICATION_EXPIRED, account.issue?.kind)

            val reminder = snap.reminderCalendar as SectionSummaryState.Ready
            assertEquals(SummaryIssueKind.PERMISSION_REQUIRED, reminder.issue?.kind)

            assertTrue(snap.recordEditing is SectionSummaryState.Ready)
            assertTrue(snap.storageOffline is SectionSummaryState.Ready)
            assertTrue(snap.appearanceInteraction is SectionSummaryState.Ready)
            assertTrue(snap.aboutAdvanced is SectionSummaryState.Ready)

            assertEquals(0, harness.api.calls.get())
            assertEquals(0, harness.sideEffects.syncCalls.get())
        }

    @Test
    fun observe_oneSectionError_doesNotBlockOthers() =
        runBlocking {
            val harness = Harness(settingsFailAfter = 1)
            val snap =
                harness.capability.observe().first {
                    it.accountSync is SectionSummaryState.Error ||
                        it.recordEditing is SectionSummaryState.Error
                }

            val errored =
                listOf(
                    snap.accountSync,
                    snap.recordEditing,
                    snap.reminderCalendar,
                    snap.storageOffline,
                    snap.appearanceInteraction,
                ).count { it is SectionSummaryState.Error }
            assertTrue(errored >= 1)
            assertTrue(snap.aboutAdvanced is SectionSummaryState.Ready || snap.aboutAdvanced is SectionSummaryState.Loading)
            assertEquals(0, harness.api.calls.get())
        }

    @Test
    fun hubCapability_hasNoExecuteOrRefreshMembers() {
        val methods = SettingsHubCapability::class.java.declaredMethods.map { it.name }.toSet()
        assertTrue(methods.contains("observe"))
        assertTrue(methods.none { it.startsWith("execute") })
        assertTrue(methods.none { it.contains("refresh", ignoreCase = true) })
        assertTrue(methods.none { it.contains("request", ignoreCase = true) })
        assertTrue(methods.none { it.contains("check", ignoreCase = true) })
    }

    private fun allReady(snap: cc.pscly.onememos.domain.settings.SettingsHubSnapshot): Boolean =
        listOf(
            snap.accountSync,
            snap.recordEditing,
            snap.reminderCalendar,
            snap.storageOffline,
            snap.appearanceInteraction,
            snap.aboutAdvanced,
        ).all { it is SectionSummaryState.Ready }

    private class SideEffectCounters {
        val syncCalls = AtomicInteger(0)
        val fullResyncCalls = AtomicInteger(0)
        val scanCalls = AtomicInteger(0)
        val calendarWriteCalls = AtomicInteger(0)
        val permissionCalls = AtomicInteger(0)
        val exportCalls = AtomicInteger(0)
    }

    private class Harness(
        settings: AppSettings = AppSettings(),
        global: GlobalSyncState = GlobalSyncState(),
        settingsFailAfter: Int = Int.MAX_VALUE,
    ) {
        val sideEffects = SideEffectCounters()
        val settingsFlow = MutableStateFlow(settings)
        val globalFlow = MutableStateFlow(global)
        val api = FakeGitHubUpdateApi()
        val appIdentity =
            object : AppIdentityPort {
                override val applicationId: String = "cc.pscly.onememos"
                override val versionName: String = "1.9.0"
                override val versionCode: Long = 190L
                override val fileProviderAuthority: String = "cc.pscly.onememos.fileprovider"
            }
        private val settingsRepo = FakeSettingsRepository(settingsFlow, settingsFailAfter)
        private val updateManager =
            AppUpdateManager(
                context = RuntimeEnvironment.getApplication(),
                api = api,
                store = AppUpdateStore(RuntimeEnvironment.getApplication()),
                appIdentity = appIdentity,
            )
        val capability =
            SettingsHubCapabilityImpl(
                settingsRepository = settingsRepo,
                syncStatusMonitor =
                    object : SyncStatusMonitor {
                        override val globalState: Flow<GlobalSyncState> = globalFlow
                    },
                appUpdateManager = updateManager,
                appIdentity = appIdentity,
            )
    }

    private class FakeGitHubUpdateApi : GitHubUpdateApi {
        val calls = AtomicInteger(0)

        override suspend fun latestStableRelease(): GitHubReleaseDto {
            calls.incrementAndGet()
            error("hub must not check updates")
        }
    }

    private class FakeSettingsRepository(
        private val flowSource: MutableStateFlow<AppSettings>,
        private val failAfter: Int = Int.MAX_VALUE,
    ) : SettingsRepository {
        private val reads = AtomicInteger(0)
        override val settings: Flow<AppSettings> =
            flowSource.map {
                val n = reads.incrementAndGet()
                if (n > failAfter) error("forced settings failure")
                it
            }

        override suspend fun setWelcomeCompleted(completed: Boolean) = Unit
        override suspend fun setServerUrl(url: String) = Unit
        override suspend fun setToken(token: String) = Unit
        override suspend fun setLoginMode(mode: LoginMode) = Unit
        override suspend fun setCurrentUserCreator(creator: String) = Unit
        override suspend fun setDev2Unlocked(unlocked: Boolean) = Unit
        override suspend fun setDev2ShowPublicWorkspaceMemos(enabled: Boolean) = Unit
        override suspend fun setThemePalette(palette: ThemePalette) = Unit
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
        override suspend fun acknowledgeFullSyncCompletion(runId: String) = Unit
        override suspend fun setFullSyncFailed(
            runId: String,
            stage: FullSyncStage,
            pagesFetched: Int,
            itemsFetched: Int,
            error: String,
        ) = Unit
    }
}
