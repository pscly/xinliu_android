package cc.pscly.onememos.core.network

import android.app.Application
import cc.pscly.onememos.domain.model.AppSettings
import cc.pscly.onememos.domain.model.FullSyncStage
import cc.pscly.onememos.domain.model.LoginMode
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette
import cc.pscly.onememos.domain.model.TodoReminderMode
import cc.pscly.onememos.domain.repository.SettingsRepository
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppSettingsStateTest {
    @Test
    fun settingsFlowThrows_doesNotCrashAndSnapshotStillCallable() =
        runBlocking {
            val uncaught = AtomicReference<Throwable?>(null)
            val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaught.compareAndSet(null, e) }
            try {
                val state =
                    AppSettingsState(
                        NoopSettingsRepository(
                            flow { throw RuntimeException("boom") },
                        ),
                    )

                delay(50)

                assertNull("settings 采集异常不应作为 uncaught exception 传播", uncaught.get())

                val snapshot = state.snapshot()
                assertNotNull(snapshot)
                assertEquals(AppSettings(), snapshot)
            } finally {
                Thread.setDefaultUncaughtExceptionHandler(oldHandler)
            }
        }

    private class NoopSettingsRepository(
        override val settings: Flow<AppSettings> = emptyFlow(),
    ) : SettingsRepository {
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
    }
}
