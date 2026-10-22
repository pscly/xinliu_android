package cc.pscly.onememos.data.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 本次新增的两个“新建笔记自动加工”开关：
 * - 默认必须为开启（含老用户升级：key 从未写入过即视为 true）；
 * - 用户手动关闭后必须持久化为 false。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsRepositoryImplAutoEnrichTest {
    private fun newRepo(): SettingsRepositoryImpl {
        val context: Context = ApplicationProvider.getApplicationContext()
        return SettingsRepositoryImpl(
            context = context,
            encryptedTokenStorage = FakeTokenStorage(),
        )
    }

    @Test
    fun autoEnrich_defaultsToEnabled_andPersistsExplicitToggles() =
        runBlocking {
            val repo = newRepo()

            // 1) 默认开启（老用户升级路径：DataStore 里没有这两个 key）
            val defaults = repo.settings.first()
            assertTrue("autoInsertTimestamp 默认应为开启", defaults.autoInsertTimestamp)
            assertTrue("autoAppendLocation 默认应为开启", defaults.autoAppendLocation)

            // 2) 关闭后持久化
            repo.setAutoInsertTimestamp(false)
            repo.setAutoAppendLocation(false)

            val disabled = repo.settings.first()
            assertFalse(disabled.autoInsertTimestamp)
            assertFalse(disabled.autoAppendLocation)

            // 3) 再次打开也能持久化
            repo.setAutoInsertTimestamp(true)
            repo.setAutoAppendLocation(true)

            val enabled = repo.settings.first()
            assertTrue(enabled.autoInsertTimestamp)
            assertTrue(enabled.autoAppendLocation)
        }

    private class FakeTokenStorage : TokenStorage {
        private var token: String = ""

        override fun getToken(): String = token

        override fun setToken(token: String) {
            this.token = token
        }
    }
}
