package cc.pscly.onememos.data.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cc.pscly.onememos.domain.model.FullSyncStage
import cc.pscly.onememos.domain.model.FullSyncStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsRepositoryImplFullSyncRunIdTest {
    @Test
    fun runIdGuard_preventsOldRunFromOverwritingNewerRun() =
        runBlocking {
            val context: Context = ApplicationProvider.getApplicationContext()
            val repo =
                SettingsRepositoryImpl(
                    context = context,
                    encryptedTokenStorage = FakeTokenStorage(),
                )

            repo.setServerUrl("https://example.com")
            repo.setCurrentUserCreator("users/1")

            val newRunId = "new-run"
            repo.setFullSyncRunning(newRunId)

            // 旧 runId 的写入应被忽略
            repo.setFullSyncProgress(
                runId = "old-run",
                stage = FullSyncStage.ARCHIVED,
                pagesFetched = 10,
                itemsFetched = 999,
            )
            repo.setFullSyncSuccess(
                runId = "old-run",
                stage = FullSyncStage.ARCHIVED,
                pagesFetched = 10,
                itemsFetched = 999,
            )

            repo.setFullSyncFailed(
                runId = "old-run",
                stage = FullSyncStage.ARCHIVED,
                pagesFetched = 10,
                itemsFetched = 999,
                error = "boom",
            )
            repo.setFullSyncCancelled(
                runId = "old-run",
                stage = FullSyncStage.ARCHIVED,
                pagesFetched = 10,
                itemsFetched = 999,
            )

            val s1 = repo.settings.first().fullSync
            assertEquals(FullSyncStatus.RUNNING, s1.status)
            assertEquals(newRunId, s1.runId)
            assertEquals(FullSyncStage.NORMAL, s1.stage)
            assertEquals(0, s1.pagesFetched)
            assertEquals(0, s1.itemsFetched)
            assertEquals("", s1.lastError)

            // 多账号隔离：切换到另一个 syncKey 后，不应被上一账号的 storedRunId 阻断。
            repo.setCurrentUserCreator("users/2")
            repo.setFullSyncProgress(
                runId = "run-for-users-2",
                stage = FullSyncStage.ARCHIVED,
                pagesFetched = 3,
                itemsFetched = 4,
            )

            val s2 = repo.settings.first().fullSync
            assertEquals(FullSyncStatus.RUNNING, s2.status)
            assertEquals("run-for-users-2", s2.runId)
            assertEquals(FullSyncStage.ARCHIVED, s2.stage)
            assertEquals(3, s2.pagesFetched)
            assertEquals(4, s2.itemsFetched)
        }

    private class FakeTokenStorage : TokenStorage {
        private var token: String = ""

        override fun getToken(): String = token

        override fun setToken(token: String) {
            this.token = token
        }
    }
}
