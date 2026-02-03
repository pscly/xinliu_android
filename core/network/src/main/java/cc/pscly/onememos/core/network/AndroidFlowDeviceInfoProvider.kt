package cc.pscly.onememos.core.network

import android.content.Context
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidFlowDeviceInfoProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : FlowDeviceInfoProvider {
    override fun deviceId(): String {
        // 首选 ANDROID_ID：无权限、对同一签名/用户相对稳定。
        val androidId =
            runCatching {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            }.getOrNull().orEmpty().trim()
        if (androidId.isNotBlank()) return androidId

        // 极端情况下 ANDROID_ID 可能为空：退化为本机生成并持久化的 UUID。
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_FALLBACK_ID, "").orEmpty().trim()
        if (existing.isNotBlank()) return existing

        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_FALLBACK_ID, created).apply()
        return created
    }

    override fun deviceName(): String {
        val manu = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val device = Build.DEVICE.orEmpty().trim()
        val base = listOf(manu, model).filter { it.isNotBlank() }.joinToString(" ")
        return if (device.isBlank()) base else "$base ($device)"
    }

    private companion object {
        private const val PREFS = "one_memos_device"
        private const val KEY_FALLBACK_ID = "flow_device_fallback_id"
    }
}
