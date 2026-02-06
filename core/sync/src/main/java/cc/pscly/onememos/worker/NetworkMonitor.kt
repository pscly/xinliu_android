package cc.pscly.onememos.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 轻量网络状态监测：用于 UI 解释“为什么没有同步”（例如离线/无网络）。
 *
 * 注意：
 * - 这是“是否具备可用网络”的 best-effort 判断，不保证能访问特定服务器。
 * - 若系统服务不可用或权限不足，默认回退为 true（避免误报离线导致用户困惑）。
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    private val _isOnline = MutableStateFlow(checkOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isOnline.value = checkOnline()
            }

            override fun onLost(network: Network) {
                _isOnline.value = checkOnline()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                _isOnline.value = checkOnline()
            }
        }

    init {
        runCatching {
            connectivityManager?.registerDefaultNetworkCallback(callback)
        }
    }

    private fun checkOnline(): Boolean =
        runCatching {
            val cm = connectivityManager ?: return@runCatching true
            val network = cm.activeNetwork ?: return@runCatching false
            val caps = cm.getNetworkCapabilities(network) ?: return@runCatching false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrElse { true }
}

