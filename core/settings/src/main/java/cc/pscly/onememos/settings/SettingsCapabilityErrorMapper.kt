package cc.pscly.onememos.settings

import android.content.ActivityNotFoundException
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import retrofit2.HttpException

/**
 * 将平台/网络异常映射为纯领域 [SettingsCapabilityError]。
 * 禁止把 exception.message、HTTP body、WorkInfo 等细节泄漏到领域层。
 */
object SettingsCapabilityErrorMapper {
    fun map(throwable: Throwable): SettingsCapabilityError {
        return when (throwable) {
            is SettingsCapabilityError -> throwable
            is SecurityException -> SettingsCapabilityError.PermissionDenied
            is ActivityNotFoundException -> SettingsCapabilityError.PlatformUnavailable
            is HttpException -> mapHttp(throwable.code())
            is UnknownHostException,
            is ConnectException,
            is SocketTimeoutException,
            is IOException,
            -> SettingsCapabilityError.NetworkUnavailable
            else -> {
                val msg = throwable.message.orEmpty()
                when {
                    msg.contains("WorkManager", ignoreCase = true) ||
                        msg.contains("WorkInfo", ignoreCase = true) ->
                        SettingsCapabilityError.PlatformUnavailable
                    else -> SettingsCapabilityError.Unknown(diagnosticCode = throwable::class.java.simpleName)
                }
            }
        }
    }

    fun mapHttp(code: Int): SettingsCapabilityError =
        when (code) {
            401, 403 -> SettingsCapabilityError.AuthenticationExpired
            in 500..599 -> SettingsCapabilityError.NetworkUnavailable
            in 400..499 -> SettingsCapabilityError.InvalidInput
            else -> SettingsCapabilityError.Unknown(diagnosticCode = "HTTP_$code")
        }
}
