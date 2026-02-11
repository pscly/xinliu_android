package cc.pscly.onememos.core.network

import android.util.Log
import cc.pscly.onememos.domain.model.AppSettings
import cc.pscly.onememos.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp Interceptor 不能挂起读取 DataStore，因此这里把设置缓存为一个内存快照。
 */
@Singleton
class AppSettingsState @Inject constructor(
    settingsRepository: SettingsRepository,
) {
    @Volatile
    private var latest: AppSettings = AppSettings()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            while (isActive) {
                try {
                    settingsRepository.settings.collectLatest { latest = it }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Log.e(TAG, "AppSettingsState 收集设置失败，将重试", t)
                    delay(RETRY_DELAY_MS)
                }
            }
        }
    }

    fun snapshot(): AppSettings = latest

    private companion object {
        private const val TAG = "app_settings_state"
        private const val RETRY_DELAY_MS = 1_000L
    }
}
