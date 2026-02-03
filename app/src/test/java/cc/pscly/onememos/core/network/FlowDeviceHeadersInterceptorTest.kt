package cc.pscly.onememos.core.network

import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Test

class FlowDeviceHeadersInterceptorTest {
    @Test
    fun intercept_addsDeviceHeaders() {
        val interceptor =
            FlowDeviceHeadersInterceptor(
                object : FlowDeviceInfoProvider {
                    override fun deviceId(): String = "abc123"

                    override fun deviceName(): String = "Pixel 9"
                },
            )

        val request =
            Request.Builder()
                .url("https://example.com/api/v1/auth/login")
                .build()

        val chain = CapturingChain(request)
        interceptor.intercept(chain)

        val out = chain.proceededRequest!!
        assertEquals("abc123", out.header("X-Flow-Device-Id"))
        assertEquals("Pixel 9", out.header("X-Flow-Device-Name"))
    }

    private class CapturingChain(
        private val input: Request,
    ) : Interceptor.Chain {
        var proceededRequest: Request? = null

        override fun request(): Request = input

        override fun proceed(request: Request): Response {
            proceededRequest = request
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(okhttp3.ResponseBody.create(null, ByteArray(0)))
                .build()
        }

        override fun call(): okhttp3.Call {
            throw UnsupportedOperationException("not needed")
        }

        override fun connectTimeoutMillis(): Int = 0

        override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = this

        override fun readTimeoutMillis(): Int = 0

        override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = this

        override fun writeTimeoutMillis(): Int = 0

        override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = this

        override fun connection(): okhttp3.Connection? = null
    }
}
