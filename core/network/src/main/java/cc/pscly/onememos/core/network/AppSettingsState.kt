package cc.pscly.onememos.core.network

import cc.pscly.onememos.domain.model.AppSettings
import cc.pscly.onememos.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
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
            settingsRepository.settings.collectLatest { latest = it }
        }
    }

    fun snapshot(): AppSettings = latest
}
