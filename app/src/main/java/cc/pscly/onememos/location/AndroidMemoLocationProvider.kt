package cc.pscly.onememos.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import cc.pscly.onememos.domain.location.MemoLocationProvider
import cc.pscly.onememos.domain.model.MemoLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 基于系统 LocationManager 的位置提供者（不引入 GMS 依赖，国内机型也能用）。
 *
 * 策略：
 * 1. [prime] 在记录页/悬浮窗打开时调用，异步刷新“最近已知位置”；
 * 2. [latestOrNull] 保存时优先取缓存，尽量不等待；
 * 3. 没有定位权限、系统定位关闭、超时 → 一律返回 null，由调用方跳过位置。
 */
@Singleton
class AndroidMemoLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : MemoLocationProvider {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var cached: MemoLocation? = null
    private var lastPrimedAtMs: Long = 0L

    private fun locationManager(): LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    override fun prime() {
        if (!hasLocationPermission()) return
        val now = System.currentTimeMillis()
        synchronized(lock) {
            if (now - lastPrimedAtMs < PRIME_INTERVAL_MS) return
            lastPrimedAtMs = now
        }
        scope.launch { refreshFromLastKnown() }
    }

    override suspend fun latestOrNull(timeoutMs: Long): MemoLocation? {
        synchronized(lock) { cached }?.let { return it }
        if (!hasLocationPermission()) return null
        if (timeoutMs <= 0L) return null
        val fix = withTimeoutOrNull(timeoutMs) { awaitSingleFix() } ?: return null
        return store(fix)
    }

    @SuppressLint("MissingPermission")
    private fun refreshFromLastKnown() {
        val manager = locationManager() ?: return
        val now = System.currentTimeMillis()
        val candidates =
            runCatching {
                PROVIDERS.mapNotNull { provider ->
                    runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
                }
            }.getOrDefault(emptyList())
        val freshest =
            candidates
                .filter { now - it.time <= LAST_KNOWN_MAX_AGE_MS }
                .maxByOrNull { it.time }
        freshest?.let { store(it) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitSingleFix(): Location? =
        suspendCancellableCoroutine { continuation ->
            val manager = locationManager()
            val provider =
                manager?.let { m ->
                    PROVIDERS.firstOrNull { p -> runCatching { m.isProviderEnabled(p) }.getOrDefault(false) }
                }
            if (manager == null || provider == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { runCatching { signal.cancel() } }
            val callbackDone = java.util.concurrent.atomic.AtomicBoolean(false)
            runCatching {
                manager.getCurrentLocation(
                    provider,
                    signal,
                    context.mainExecutor,
                ) { location ->
                    if (callbackDone.compareAndSet(false, true) && continuation.isActive) {
                        continuation.resume(location)
                    }
                }
            }.onFailure {
                if (callbackDone.compareAndSet(false, true) && continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }

    private fun store(location: Location): MemoLocation? {
        val mapped =
            MemoLocation.of(
                latitude = location.latitude,
                longitude = location.longitude,
            ) ?: return null
        synchronized(lock) { cached = mapped }
        return mapped
    }

    private companion object {
        /** 优先用网络定位（更快更省电），再退到 GPS。 */
        val PROVIDERS = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)

        /** 同一个页面内多次 prime 的最小间隔。 */
        const val PRIME_INTERVAL_MS = 15_000L

        /** lastKnown 太旧就不用了（超过 30 分钟视为不可信）。 */
        const val LAST_KNOWN_MAX_AGE_MS = 30 * 60 * 1000L
    }
}
