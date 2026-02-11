package cc.pscly.onememos.worker

import java.io.IOException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
internal object WorkerRetryPolicy {
    internal const val ATTEMPT_CAP: Int = 3

    internal sealed interface Decision {
        data object PropagateCancellation : Decision

        data class Classified(
            val retry: Boolean,
            val httpCode: Int,
            val userMessage: String,
        ) : Decision
    }

    internal fun classify(err: Throwable, runAttemptCount: Int): Decision {
        if (err is CancellationException) return Decision.PropagateCancellation

        val base = when (err) {
            is HttpException -> classifyHttp(err)
            is IOException -> Decision.Classified(
                retry = true,
                httpCode = 0,
                userMessage = limitMessage("网络异常：${err.message ?: "IO错误"}"),
            )
            else -> Decision.Classified(
                retry = true,
                httpCode = 0,
                userMessage = limitMessage("未知错误：${err.javaClass.simpleName}${err.message?.let { "：$it" } ?: ""}"),
            )
        }

        return applyAttemptCapIfNeeded(base, runAttemptCount)
    }

    internal fun classifyHttpCode(code: Int, runAttemptCount: Int): Decision.Classified {
        val base = classifyHttpCodeBase(code)
        return applyAttemptCapIfNeeded(base, runAttemptCount)
    }

    private fun applyAttemptCapIfNeeded(
        base: Decision.Classified,
        runAttemptCount: Int,
    ): Decision.Classified {
        if (base.retry && runAttemptCount >= ATTEMPT_CAP) {
            return base.copy(
                retry = false,
                userMessage = limitMessage(base.userMessage + "（已达重试上限）"),
            )
        }
        return base
    }

    private fun classifyHttp(err: HttpException): Decision.Classified {
        return classifyHttpCodeBase(err.code())
    }

    private fun classifyHttpCodeBase(code: Int): Decision.Classified {
        return when {
            code == 401 || code == 403 -> Decision.Classified(
                retry = false,
                httpCode = code,
                userMessage = "HTTP $code：认证失效，请重新登录",
            )
            code == 408 -> Decision.Classified(
                retry = true,
                httpCode = code,
                userMessage = "HTTP 408：请求超时，稍后重试",
            )
            code == 429 -> Decision.Classified(
                retry = true,
                httpCode = code,
                userMessage = "HTTP 429：请求过于频繁，稍后重试",
            )
            code in 400..499 -> Decision.Classified(
                retry = false,
                httpCode = code,
                userMessage = "HTTP $code：请求被拒绝",
            )
            code in 500..599 -> Decision.Classified(
                retry = true,
                httpCode = code,
                userMessage = "HTTP $code：服务器异常，稍后重试",
            )
            else -> Decision.Classified(
                retry = true,
                httpCode = code,
                userMessage = limitMessage("HTTP $code：请求失败"),
            )
        }
    }

    private const val MAX_USER_MESSAGE_CHARS: Int = 160

    private fun limitMessage(msg: String): String {
        if (msg.length <= MAX_USER_MESSAGE_CHARS) return msg
        return msg.take(MAX_USER_MESSAGE_CHARS - 3) + "..."
    }
}
