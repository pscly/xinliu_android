package cc.pscly.onememos.data.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cc.pscly.onememos.domain.model.QuickInsertTimeFormat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsRepositoryImplQuickInsertTimeFormatTest {
    @Test
    fun quickInsertTimeFormat_parse_nullFallsBackToFullDateTime() {
        assertEquals(
            QuickInsertTimeFormat.FULL_DATETIME,
            QuickInsertTimeFormat.fromStorage(null),
        )
    }

    @Test
    fun quickInsertTimeFormat_roundTripPersistsSelectedValue() =
        runBlocking {
            val context: Context = ApplicationProvider.getApplicationContext()
            val repo =
                SettingsRepositoryImpl(
                    context = context,
                    encryptedTokenStorage = FakeTokenStorage(),
                )

            repo.setQuickInsertTimeFormat(QuickInsertTimeFormat.TIME_ONLY)

            val settings = repo.settings.first()

            assertEquals(QuickInsertTimeFormat.TIME_ONLY, settings.quickInsertTimeFormat)
        }

    @Test
    fun quickInsertTimeFormat_parse_invalidValueFallsBackToFullDateTime() {
        assertEquals(
            QuickInsertTimeFormat.FULL_DATETIME,
            QuickInsertTimeFormat.fromStorage("not-a-format"),
        )
        assertEquals(
            QuickInsertTimeFormat.FULL_DATETIME,
            QuickInsertTimeFormat.fromStorage(""),
        )
    }

    private class FakeTokenStorage : TokenStorage {
        private var token: String = ""

        override fun getToken(): String = token

        override fun setToken(token: String) {
            this.token = token
        }
    }
}
