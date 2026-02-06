package cc.pscly.onememos

import android.app.Activity
import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import cc.pscly.onememos.worker.MemoDerivedFieldsRebuildScheduler
import cc.pscly.onememos.data.auth.FlowBackendTokenRefresher
import cc.pscly.onememos.domain.sync.TodoReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class OneMemosApplication : Application(), Configuration.Provider, ImageLoaderFactory {
    // Lazy 注入用于降低冷启动对象构建成本。
    @Inject lateinit var workerFactoryLazy: Lazy<HiltWorkerFactory>
    @Inject lateinit var okHttpClientLazy: Lazy<OkHttpClient>
    @Inject lateinit var flowBackendTokenRefresherLazy: Lazy<FlowBackendTokenRefresher>
    @Inject lateinit var todoReminderSchedulerLazy: Lazy<TodoReminderScheduler>

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var startedActivityCount = 0
    private var lastStopWasConfigChange = false

    private val imageDiskCacheDirName = "one_memos_image_cache"
    private val imageDiskCacheMaxBytes: Long = 256L * 1024 * 1024 // 256MB

    override fun onCreate() {
        super.onCreate()

        // 每次进入前台都尝试向 Flow Backend 换取一次 token（若使用 BACKEND 登录且本机已保存账号密码）。
        // 失败不阻塞 UI，且内部带并发保护。
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    val wasInBackground = startedActivityCount == 0
                    startedActivityCount++

                    // 配置变更（旋转等）会触发 stop/start：避免在这类“伪前台切换”时重复刷新。
                    if (wasInBackground && !lastStopWasConfigChange) {
                        appScope.launch { flowBackendTokenRefresherLazy.get().refreshIfPossible() }
                    }
                    lastStopWasConfigChange = false
                }

                override fun onActivityStopped(activity: Activity) {
                    startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
                    lastStopWasConfigChange = activity.isChangingConfigurations
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )

        // 派生字段回填会触发 WorkManager 初始化；不要在 Application.onCreate 的同步路径里做，
        // 进程启动稍晚后再调度（WorkRequest 仍保持 initialDelaySeconds=45），避免影响冷启动。
        appScope.launch {
            delay(2_000)
            MemoDerivedFieldsRebuildScheduler.enqueue(
                context = this@OneMemosApplication,
                initialDelaySeconds = 45,
            )
        }

        // Todo 提醒：应用启动后调度一次重排 + 启用周期兜底，避免“后台同步拉到提醒但未进入待办页”时不生效。
        appScope.launch {
            delay(3_000)
            todoReminderSchedulerLazy.get().requestReschedule()
        }
    }

    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder()
                .setWorkerFactory(workerFactoryLazy.get())
                .build()

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(okHttpClientLazy.get())
            .crossfade(true)
            // 与 Hilt 提供的 ImageLoader 保持一致：启用磁盘缓存，减少反复请求与流量消耗。
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve(imageDiskCacheDirName))
                    .maxSizeBytes(imageDiskCacheMaxBytes)
                    .build()
            }
            .build()
}
