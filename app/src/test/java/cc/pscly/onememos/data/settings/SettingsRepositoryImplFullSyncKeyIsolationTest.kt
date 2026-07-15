package cc.pscly.onememos.data.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cc.pscly.onememos.domain.model.FullSyncStage
import cc.pscly.onememos.domain.model.FullSyncStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsRepositoryImplFullSyncKeyIsolationTest {
    @Test
    fun acknowledgement_isIsolatedBySyncKey_andRestoredWhenSwitchingBack() =
        runBlocking {
            val context: Context = ApplicationProvider.getApplicationContext()
            val repo = SettingsRepositoryImpl(context = context, encryptedTokenStorage = FakeTokenStorage())

            repo.setServerUrl("https://fullsync-ack-isolation.example")
            repo.setCurrentUserCreator("users/ack-a")
            repo.setFullSyncSuccess("run-a", FullSyncStage.NORMAL, 1, 2)
            repo.acknowledgeFullSyncCompletion("run-a")

            repo.setCurrentUserCreator("users/ack-b")
            repo.setFullSyncSuccess("run-b", FullSyncStage.NORMAL, 3, 4)
            val b = repo.settings.first().fullSync
            assertEquals("", b.acknowledgedSuccessRunId)

            repo.setCurrentUserCreator("users/ack-a")
            val restoredA = repo.settings.first().fullSync
            assertEquals(FullSyncStatus.SUCCESS, restoredA.status)
            assertEquals("run-a", restoredA.runId)
            assertEquals("run-a", restoredA.acknowledgedSuccessRunId)
        }

    @Test
    fun switchingCreator_doesNotLeakFullSyncState_andRestoresWhenSwitchingBack() =
        runBlocking {
            val context: Context = ApplicationProvider.getApplicationContext()
            val repo =
                SettingsRepositoryImpl(
                    context = context,
                    encryptedTokenStorage = FakeTokenStorage(),
                )

            repo.setServerUrl("https://fullsync-key-isolation-a.example")

            // Key A
            repo.setCurrentUserCreator("users/isolation-a")
            repo.setFullSyncSuccess(
                runId = "run-a",
                stage = FullSyncStage.NORMAL,
                pagesFetched = 1,
                itemsFetched = 2,
            )
            val a1 = repo.settings.first().fullSync
            assertEquals(FullSyncStatus.SUCCESS, a1.status)
            assertEquals("run-a", a1.runId)
            assertTrue(a1.lastSuccessAt > 0L)
            val aLastSuccessAt = a1.lastSuccessAt

            // Switch to key B (same server, different creator)
            repo.setCurrentUserCreator("users/isolation-b")
            val b0 = repo.settings.first().fullSync
            assertEquals(FullSyncStatus.IDLE, b0.status)
            assertEquals("", b0.runId)
            assertEquals(0L, b0.lastSuccessAt)
            assertEquals("", b0.lastError)

            repo.setFullSyncFailed(
                runId = "run-b",
                stage = FullSyncStage.ARCHIVED,
                pagesFetched = 7,
                itemsFetched = 8,
                error = "boom",
            )
            val b1 = repo.settings.first().fullSync
            assertEquals(FullSyncStatus.FAILED, b1.status)
            assertEquals("run-b", b1.runId)
            assertEquals(FullSyncStage.ARCHIVED, b1.stage)
            assertEquals(7, b1.pagesFetched)
            assertEquals(8, b1.itemsFetched)
            assertEquals("boom", b1.lastError)

            // Switch back to key A, history should be preserved
            repo.setCurrentUserCreator("users/isolation-a")
            val a2 = repo.settings.first().fullSync
            assertEquals(FullSyncStatus.SUCCESS, a2.status)
            assertEquals("run-a", a2.runId)
            assertEquals(aLastSuccessAt, a2.lastSuccessAt)
        }

    @Test
    fun switchingServerUrl_doesNotLeakFullSyncState_andRestoresWhenSwitchingBack() =
        runBlocking {
            val context: Context = ApplicationProvider.getApplicationContext()
            val repo =
                SettingsRepositoryImpl(
                    context = context,
                    encryptedTokenStorage = FakeTokenStorage(),
                )

            repo.setCurrentUserCreator("users/isolation-server")

            // Server A
            repo.setServerUrl("https://fullsync-server-isolation-a.example")
            repo.setFullSyncSuccess(
                runId = "run-server-a",
                stage = FullSyncStage.NORMAL,
                pagesFetched = 10,
                itemsFetched = 20,
            )
            val a1 = repo.settings.first().fullSync
            assertEquals(FullSyncStatus.SUCCESS, a1.status)
            assertEquals("run-server-a", a1.runId)
            assertTrue(a1.lastSuccessAt > 0L)
            val aLastSuccessAt = a1.lastSuccessAt

            // Switch to server B
            repo.setServerUrl("https://fullsync-server-isolation-b.example")
            val b0 = repo.settings.first().fullSync
            assertEquals(FullSyncStatus.IDLE, b0.status)
            assertEquals("", b0.runId)
            assertEquals(0L, b0.lastSuccessAt)

            repo.setFullSyncRunning("run-server-b")
            val b1 = repo.settings.first().fullSync
            assertEquals(FullSyncStatus.RUNNING, b1.status)
            assertEquals("run-server-b", b1.runId)

            // Switch back to server A
            repo.setServerUrl("https://fullsync-server-isolation-a.example")
            val a2 = repo.settings.first().fullSync
            assertEquals(FullSyncStatus.SUCCESS, a2.status)
            assertEquals("run-server-a", a2.runId)
            assertEquals(aLastSuccessAt, a2.lastSuccessAt)
        }

    private class FakeTokenStorage : TokenStorage {
        private var token: String = ""

        override fun getToken(): String = token

        override fun setToken(token: String) {
            this.token = token
        }
    }
}
