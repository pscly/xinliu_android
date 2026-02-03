package cc.pscly.onememos.core.network

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

interface FlowDeviceInfoProvider {
    fun deviceId(): String

    fun deviceName(): String
}

/**
 * 为 Flow Backend 的请求附带设备信息，便于后端侧观测“设备机器码/机型”。
 */
class FlowDeviceHeadersInterceptor @Inject constructor(
    private val deviceInfoProvider: FlowDeviceInfoProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val deviceId = deviceInfoProvider.deviceId().trim()
        val deviceName = deviceInfoProvider.deviceName().trim()

        if (deviceId.isBlank() && deviceName.isBlank()) {
            return chain.proceed(request)
        }

        val builder = request.newBuilder()
        if (deviceId.isNotBlank()) {
            builder.header("X-Flow-Device-Id", deviceId)
        }
        if (deviceName.isNotBlank()) {
            builder.header("X-Flow-Device-Name", deviceName)
        }
        return chain.proceed(builder.build())
    }
}
