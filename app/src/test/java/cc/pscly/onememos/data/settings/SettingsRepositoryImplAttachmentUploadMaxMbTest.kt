package cc.pscly.onememos.data.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsRepositoryImplAttachmentUploadMaxMbTest {
    @Test
    fun dev2Locked_attachmentUploadMaxMb_alwaysFallsBackTo50() =
        runBlocking {
            val context: Context = ApplicationProvider.getApplicationContext()
            val repo =
                SettingsRepositoryImpl(
                    context = context,
                    encryptedTokenStorage = FakeTokenStorage(),
                )

            repo.setDev2Unlocked(false)

            val s = repo.settings.first()
            assertEquals(50, s.attachmentUploadMaxMb)
        }

    @Test
    fun dev2Locked_settingUploadMaxTo1024_stillReturns50() =
        runBlocking {
            val context: Context = ApplicationProvider.getApplicationContext()
            val repo =
                SettingsRepositoryImpl(
                    context = context,
                    encryptedTokenStorage = FakeTokenStorage(),
                )

            repo.setDev2Unlocked(false)
            repo.setAttachmentUploadMaxMb(1024)

            val s = repo.settings.first()
            assertEquals(50, s.attachmentUploadMaxMb)
        }

    @Test
    fun dev2Unlocked_settingUploadMaxTo1024_returns1024() =
        runBlocking {
            val context: Context = ApplicationProvider.getApplicationContext()
            val repo =
                SettingsRepositoryImpl(
                    context = context,
                    encryptedTokenStorage = FakeTokenStorage(),
                )

            repo.setDev2Unlocked(true)
            repo.setAttachmentUploadMaxMb(1024)

            val s = repo.settings.first()
            assertEquals(1024, s.attachmentUploadMaxMb)
        }

    @Test
    fun dev2Unlocked_uploadMax_isClampedTo1To1024() =
        runBlocking {
            val context: Context = ApplicationProvider.getApplicationContext()
            val repo =
                SettingsRepositoryImpl(
                    context = context,
                    encryptedTokenStorage = FakeTokenStorage(),
                )

            repo.setDev2Unlocked(true)

            repo.setAttachmentUploadMaxMb(99999)
            assertEquals(1024, repo.settings.first().attachmentUploadMaxMb)

            repo.setAttachmentUploadMaxMb(0)
            assertEquals(1, repo.settings.first().attachmentUploadMaxMb)
        }

    private class FakeTokenStorage : TokenStorage {
        private var token: String = ""

        override fun getToken(): String = token

        override fun setToken(token: String) {
            this.token = token
        }
    }
}
